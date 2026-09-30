package net.foxlemon.foxsfoxtail.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.foxlemon.foxsfoxtail.FoxsFoxTail;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

// The model recipe lives in a resource-pack-overridable JSON file. The three
// named bones are the animation contract; decorative children may vary freely.
final class FoxTailGeometry {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final Identifier FILE = Identifier.fromNamespaceAndPath(
        FoxsFoxTail.MODID, "model/entity/fox_tail.json");
    private static final Geometry BUNDLED = readBundled();
    private static volatile Geometry active = BUNDLED;

    private FoxTailGeometry() {}

    static Geometry current() { return active; }

    record Geometry(LayerDefinition layer, Identifier texture, Vec3 attachment,
                    Vec3 rootPivot, AABB rootBox, AABB middleBox, AABB tipBox) {
        FoxTailModel bakeModel() {
            // Every renderer needs its own mutable ModelParts for animation.
            return new FoxTailModel(layer.bakeRoot(), attachment);
        }

        Vec3 renderOffset() {
            return rootPivot.add(attachment).scale(-1.0 / 16.0);
        }
    }

    static final class Reload extends SimplePreparableReloadListener<Geometry> {
        @Override
        protected Geometry prepare(ResourceManager resources, ProfilerFiller profiler) {
            var resource = resources.getResource(FILE);
            if (resource.isEmpty()) return BUNDLED;
            try (Reader reader = resource.get().openAsReader()) {
                return parse(reader);
            } catch (IOException | RuntimeException error) {
                LOGGER.warn("Invalid tail model from {}. Using the bundled model.", FILE, error);
                return BUNDLED;
            }
        }

        @Override
        protected void apply(Geometry geometry, ResourceManager resources, ProfilerFiller profiler) {
            active = geometry;
        }
    }

    private static Geometry readBundled() {
        try (var stream = FoxTailGeometry.class.getResourceAsStream("/assets/foxsfoxtail/model/entity/fox_tail.json")) {
            if (stream == null) throw new IllegalStateException("Missing bundled tail model " + FILE);
            return parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read bundled tail model " + FILE, error);
        }
    }

    private static Geometry parse(Reader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        if (json.get("format").getAsInt() != 1) {
            throw new IllegalArgumentException("Unsupported tail model format");
        }
        Identifier texture = Identifier.parse(json.get("texture").getAsString());
        JsonArray textureSize = array(json, "texture_size", 2);
        int width = textureSize.get(0).getAsInt();
        int height = textureSize.get(1).getAsInt();
        if (width < 1 || height < 1 || width > 1024 || height > 1024) {
            throw new IllegalArgumentException("Invalid tail texture size");
        }
        Vec3 attachment = vector(json, "attachment");
        JsonArray parts = json.getAsJsonArray("parts");
        if (parts.size() != 1 || !"tail".equals(parts.get(0).getAsJsonObject().get("name").getAsString())) {
            throw new IllegalArgumentException("Tail model needs one top-level part named tail");
        }
        JsonObject tailPart = parts.get(0).getAsJsonObject();
        if (tailPart.has("rotation") && vector(tailPart, "rotation").lengthSqr() > 1.0e-12) {
            throw new IllegalArgumentException("The tail root must have zero rest rotation");
        }
        Vec3 rootPivot = vector(tailPart, "pivot");
        MeshDefinition mesh = new MeshDefinition();
        Map<String, AABB> colliders = new HashMap<>();
        addPart(mesh.getRoot(), tailPart, colliders, new int[1], 0);
        LayerDefinition layer = LayerDefinition.create(mesh, width, height);
        // Validate the rig before it can replace a working model on resource reload.
        layer.bakeRoot().getChild("tail").getChild("middle").getChild("tip");
        for (String bone : new String[]{"tail", "middle", "tip"}) {
            if (!colliders.containsKey(bone)) {
                throw new IllegalArgumentException("Missing collision box for " + bone);
            }
        }
        return new Geometry(layer, texture, attachment, rootPivot,
            colliders.get("tail"), colliders.get("middle"), colliders.get("tip"));
    }

    private static void addPart(PartDefinition parent, JsonObject json, Map<String, AABB> colliders,
                                int[] count, int depth) {
        if (depth > 16 || ++count[0] > 128) throw new IllegalArgumentException("Tail model is too large");
        String name = json.get("name").getAsString();
        if (name.isBlank()) throw new IllegalArgumentException("Empty tail part name");
        Vec3 pivot = vector(json, "pivot");
        Vec3 rotation = json.has("rotation") ? vector(json, "rotation") : Vec3.ZERO;
        CubeListBuilder cubes = CubeListBuilder.create();
        if (json.has("cubes")) {
            for (var entry : json.getAsJsonArray("cubes")) {
                JsonObject cube = entry.getAsJsonObject();
                JsonArray uv = array(cube, "uv", 2);
                JsonArray box = array(cube, "box", 6);
                float[] b = numbers(box);
                if (b[3] < 0 || b[4] < 0 || b[5] < 0) {
                    throw new IllegalArgumentException("Negative cube size in " + name);
                }
                cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                    .addBox(b[0], b[1], b[2], b[3], b[4], b[5]);
            }
        }
        PartDefinition part = parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(
            (float) pivot.x, (float) pivot.y, (float) pivot.z,
            (float) rotation.x, (float) rotation.y, (float) rotation.z));
        if (json.has("collision")) {
            float[] b = numbers(array(json, "collision", 6));
            if (b[0] >= b[3] || b[1] >= b[4] || b[2] >= b[5]) {
                throw new IllegalArgumentException("Invalid collision box in " + name);
            }
            if (colliders.putIfAbsent(name, new AABB(b[0], b[1], b[2], b[3], b[4], b[5])) != null) {
                throw new IllegalArgumentException("Duplicate collision part " + name);
            }
        }
        if (json.has("children")) {
            Set<String> childNames = new HashSet<>();
            for (var child : json.getAsJsonArray("children")) {
                JsonObject childPart = child.getAsJsonObject();
                if (!childNames.add(childPart.get("name").getAsString())) {
                    throw new IllegalArgumentException("Duplicate child in " + name);
                }
                addPart(part, childPart, colliders, count, depth + 1);
            }
        }
    }

    private static JsonArray array(JsonObject json, String name, int length) {
        JsonArray array = json.getAsJsonArray(name);
        if (array.size() != length) throw new IllegalArgumentException("Expected " + length + " values for " + name);
        return array;
    }

    private static Vec3 vector(JsonObject json, String name) {
        float[] values = numbers(array(json, name, 3));
        return new Vec3(values[0], values[1], values[2]);
    }

    private static float[] numbers(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).getAsFloat();
            if (!Float.isFinite(values[i]) || Math.abs(values[i]) > 1024) {
                throw new IllegalArgumentException("Invalid tail model coordinate");
            }
        }
        return values;
    }
}
