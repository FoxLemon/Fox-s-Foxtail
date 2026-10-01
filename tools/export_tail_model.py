"""Convert this mod's Blockbench modded-entity project into its runtime model JSON.

Run from the repository root: python3 tools/export_tail_model.py
The tail, middle, and tip groups each need one collision guide cube.
"""

import argparse
import json
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "model/fox_tail.bbmodel"
DESTINATION = ROOT / "src/main/resources/assets/foxsfoxtail/model/entity/fox_tail.json"


def rounded(values):
    return [round(value, 6) for value in values]


def local_box(cube, origin):
    # Java entity coordinates reverse Blockbench X/Y; cube offsets are relative to the bone.
    start, end = cube["from"], cube["to"]
    return rounded([
        origin[0] - end[0], origin[1] - end[1], start[2] - origin[2],
        end[0] - start[0], end[1] - start[1], end[2] - start[2],
    ])


def collision_box(cube, origin):
    x, y, z, width, height, depth = local_box(cube, origin)
    if min(width, height, depth) <= 0:
        raise ValueError(f"Collision guide {cube['name']} must have a positive size")
    return rounded([x, y, z, x + width, y + height, z + depth])


def relative_pivot(origin, parent_origin, is_root=False):
    # Root Y uses the Java entity baseline of 24 pixels; child pivots are parent-relative.
    if is_root:
        return rounded([-origin[0], 24 - origin[1], origin[2]])
    return rounded([
        parent_origin[0] - origin[0],
        parent_origin[1] - origin[1],
        origin[2] - parent_origin[2],
    ])


def rotation(element):
    x, y, z = element.get("rotation", [0, 0, 0])
    return rounded([-math.radians(x), -math.radians(y), math.radians(z)])


def convert_group(node, parent_origin, groups, elements, is_root=False):
    group = groups[node["uuid"]]
    if not group.get("export", True):
        raise ValueError(f"Required group {group['name']} is marked not exportable")
    if any(group.get("rotation", [0, 0, 0])):
        raise ValueError(f"Rotated group {group['name']} is not supported; rotate its cubes instead")
    origin = group.get("origin", [0, 0, 0])
    result = {"name": group["name"], "pivot": relative_pivot(origin, parent_origin, is_root)}
    cubes = []
    children = []
    for child in node.get("children", []):
        if isinstance(child, dict):
            children.append(convert_group(child, origin, groups, elements))
            continue
        cube = elements[child]
        if cube["name"].startswith("collision"):
            # Guide cubes become collision data only, never visible geometry.
            if "collision" in result:
                raise ValueError(f"Multiple collision guides in {group['name']}")
            result["collision"] = collision_box(cube, origin)
            continue
        if not cube.get("export", True):
            continue
        if not cube.get("box_uv", True) or cube.get("mirror_uv", False):
            raise ValueError(f"Cube {cube['name']} needs Blockbench box UV mode")
        uv = cube.get("uv_offset", [0, 0])
        angles = rotation(cube)
        if any(angles):
            # ModelPart rotates whole parts, so rotated cubes need their own child part.
            cube_origin = cube.get("origin", origin)
            children.append({
                "name": cube["name"] + "_r1",
                "pivot": relative_pivot(cube_origin, origin),
                "rotation": angles,
                "cubes": [{"uv": uv, "box": local_box(cube, cube_origin)}],
            })
        else:
            cubes.append({"uv": uv, "box": local_box(cube, origin)})
    if cubes:
        result["cubes"] = cubes
    if children:
        result["children"] = children
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if the exported JSON is out of date")
    parser.add_argument("source", nargs="?", type=Path, default=SOURCE)
    parser.add_argument("output", nargs="?", type=Path, default=DESTINATION)
    args = parser.parse_args()
    project = json.loads(args.source.read_text(encoding="utf-8"))
    if project.get("meta", {}).get("model_format") != "modded_entity":
        raise ValueError("Expected a Blockbench modded-entity project")
    if not project.get("meta", {}).get("box_uv"):
        raise ValueError("Expected Blockbench box UV mode")
    groups = {group["uuid"]: group for group in project["groups"]}
    elements = {element["uuid"]: element for element in project["elements"]}
    outliner = project["outliner"]
    if len(outliner) != 1:
        raise ValueError("Expected one top-level tail group")
    previous = json.loads(args.output.read_text(encoding="utf-8")) if args.output.exists() else {}
    result = {
        "format": 1,
        "texture": previous.get("texture", "foxsfoxtail:texture/entity/fox_tail.png"),
        "texture_size": [project["resolution"]["width"], project["resolution"]["height"]],
        # Set this once for a new rig; subsequent exports retain the chosen point.
        "attachment": previous.get("attachment", [-8, -6, 0]),
        "parts": [convert_group(outliner[0], [0, 0, 0], groups, elements, True)],
    }
    tail = result["parts"][0]
    if tail["name"] != "tail":
        raise ValueError("The rig must contain tail → middle → tip groups")
    middle = next((child for child in tail.get("children", []) if child["name"] == "middle"), None)
    if middle is None:
        raise ValueError("The tail group needs a middle child")
    if not any(child["name"] == "tip" for child in middle.get("children", [])):
        raise ValueError("The middle group needs a tip child")
    for part in (tail, middle, next(child for child in middle["children"] if child["name"] == "tip")):
        if "collision" not in part:
            raise ValueError(f"Missing collision guide in {part['name']}")
    if args.check:
        # Validation mode compares data without modifying the exported model.
        if previous != result:
            raise SystemExit(f"Outdated tail model: run python3 tools/export_tail_model.py")
        print(f"Tail model is current: {args.output}")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {args.output}")


if __name__ == "__main__":
    main()
