# Fox's Foxtail

A cosmetic Minecraft mod by **FoxLemon** that adds a fox tail to the player, with adjustable angles and spring-based movement.

This branch ports the NeoForge mod to Minecraft 1.21.1 for ATM10. It builds, but the tail and settings still need in-game testing before release.

## Current target

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge development target | 21.1.251 |
| Java | 21 |
| Mod build | mc1.21.1-beta.1 (unreleased) |

The JAR from this project is for NeoForge. Other Minecraft versions and loaders require a separate compatible build.

## Features

### Appearance and poses

- A textured fox tail attached to the player's torso on normal and slim player models. It follows torso poses and flashes with the player when hurt.
- A root, middle, and tip hierarchy, with leg avoidance and smooth tail-angle changes for crouching, swimming, crawling, elytra flight, sleeping, and riding.

### Motion and block contact

- Separate springs for the root, middle, and tip. Player movement, stationary turns, and torso roll drive the tail; the tip follows the middle with its own delayed, half-strength bend.
- The root uses 8% of the movement-driven bend so the base stays relatively steady. When the tail touches blocks, the middle and tip also pull the root away from the obstacle.
- Invisible inner collision boxes let some outer fur overlap blocks. Contact with block collision shapes bends the tail away without changing the player's movement or block physics.
- Collision checks use the actual rotated guide boxes. Iris shadow renders are excluded from physics sampling to prevent false ground contact with shaders enabled.
- A configurable soft bend limit adds exponentially stronger resistance and damping past the chosen angle while still allowing stronger forces to bend farther.

### Controls

- Adjustable resting root angle from **−90° to +90°**, plus movement and spring settings.
- A settings screen with sliders, number input boxes, an animated preview, Reset defaults, Save, and Cancel.
- An optional key binding to open the settings screen, unassigned by default.

Movement-driven physics and block contact currently run for the local player only. Other players can render with tails, but do not receive their own simulated motion on your client. Entity collision is not included.

## Installation

1. Set up a Minecraft **1.21.1** instance with NeoForge **21.1.251**.
2. Place the built mod JAR in that instance's `mods` folder.
3. Launch Minecraft and switch to third person to see the tail.

The tail rendering and settings run on the client. Other players need the mod on their own clients to see its effects. Compatibility with animation mods and modpacks still needs individual testing.

## Settings

Open **Mods → Fox's Foxtail → Config**. You can also assign **Open Tail Settings** under **Options → Controls → Key Binds → Fox's Foxtail**; no key is assigned by default.

| Setting | Effect |
| --- | --- |
| Movement strength | How strongly movement and torso roll drive the spring. |
| Frequency | How quickly the spring responds; higher values feel stiffer. |
| Damping | How much the spring's oscillation is reduced. |
| Soft-limit damping power | How quickly extra damping grows after a segment bends beyond the soft limit. |
| Response | How the spring reacts initially to changes in its target. |
| Soft bend limit | Extra restoring force grows exponentially beyond this angle, per segment and axis. This is a soft limit, so stronger motion can still bend farther. |
| Root angle | Raises or lowers the whole tail. The middle and tip inherit this angle. |

Type a value in the box beside a slider for precise control. The preview uses a repeating test impulse to demonstrate the draft settings. Join a world to see the player preview. **Save** applies and stores changes; **Cancel** or Escape discards them. **Reset defaults** resets the draft, which must still be saved.

## Building from source

Use **JDK 21** and run the Gradle wrapper from the project directory.

### macOS / Linux

```sh
bash ./gradlew build
```

### Windows

```powershell
.\gradlew.bat build
```

The mod JAR is written to `build/libs/`. With the current project settings, its name is `foxsfoxtail-mc1.21.1-beta.1.jar`. Change `mod_version` in `gradle.properties` when preparing a new version. The internal version is `1.21.1-beta.1`: NeoForge requires it to start with a number. The build adds `mc` to the JAR filename, and release tags can use that same prefix.

To launch the development client:

```sh
bash ./gradlew runClient
```

On Windows, use `.\gradlew.bat runClient`. The development game's files are stored in `run/`.

## Project layout

- `src/main/java/net/foxlemon/foxsfoxtail/` — mod entry point and configuration.
- `src/main/java/net/foxlemon/foxsfoxtail/client/` — rendering, model-data loader, animation, spring physics, block contact, and settings screen.
- `src/main/resources/assets/foxsfoxtail/` — model JSON, texture, and language resources.
- `src/main/templates/META-INF/neoforge.mods.toml` — mod metadata; Gradle fills in values from `gradle.properties`.
- `model/fox_tail.bbmodel` — editable Blockbench model.
- `tools/export_tail_model.py` — converts the Blockbench project to the runtime model JSON.

## Changing the tail model

Edit `model/fox_tail.bbmodel` in Blockbench, then run `python3 tools/export_tail_model.py` from the repository root. This updates `src/main/resources/assets/foxsfoxtail/model/entity/fox_tail.json`. Run `python3 tools/export_tail_model.py --check` to verify the export is current. No generated Java code needs to be pasted into `FoxTailModel`; that class now handles animation only.

The `tail → middle → tip` group hierarchy is required by the animation. Keep one inner collision guide cube in each group, named with the `collision` prefix. The exporter stores these guides in the JSON but does not draw them. If you move the attachment point on a new rig, update the JSON's `attachment` coordinates after exporting; later exports preserve that value. The exporter supports box UV cubes and cube rotations; rotated groups and per-face UV models are not supported.

A resource pack can replace `assets/foxsfoxtail/model/entity/fox_tail.json` and `assets/foxsfoxtail/texture/entity/fox_tail.png`. Reloading resources updates the tail model without recompiling the mod. This is a custom Fox's Foxtail model format, not a raw `.bbmodel` or Java export. Invalid replacement models fall back to the bundled tail and log a warning.

In this JSON, `pivot` and `attachment` are measured in model pixels, `rotation` uses radians, each cube's `box` is `[x, y, z, width, height, depth]`, and each bone's `collision` is `[minX, minY, minZ, maxX, maxY, maxZ]`. The three animated bones and their collision boxes are required even when the visible cubes change.

## Credits and license

Created by **FoxLemon**, using the NeoForge MDK and Blockbench.

The project declares **MIT** in its mod metadata. See `TEMPLATE_LICENSE.txt` for the NeoForge MDK template's original license notice.
