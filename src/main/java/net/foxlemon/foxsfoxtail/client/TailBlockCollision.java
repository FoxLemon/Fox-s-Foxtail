package net.foxlemon.foxsfoxtail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Visual-only block contact. Model packs provide the inner collision guides.
final class TailBlockCollision {
    private static final double MAX_PUSH = 0.25;
    private static final double MAX_BEND = Math.toRadians(35);

    record Bends(Vec3 root, Vec3 middle, Vec3 tip) {
        static final Bends ZERO = new Bends(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
    }

    private TailBlockCollision() {}

    static Bends sample(FoxTailModel model, FoxTailGeometry.Geometry geometry,
                        PoseStack poseStack, ClientLevel level, AbstractClientPlayer player,
                        Vec3 worldPosition, Vector3f entityOrigin) {
        // The render layer has already positioned the tail behind the player's torso.
        // Pose each part so the probes inherit the same root, middle, and tip rotations.
        poseStack.pushPose();
        model.rootCollisionPart().translateAndRotate(poseStack);
        Vec3 root = bendForBox(poseStack.last().pose(), geometry.rootBox(), worldPosition, level, player, entityOrigin);

        poseStack.pushPose();
        model.middleCollisionPart().translateAndRotate(poseStack);
        Vec3 middle = bendForBox(poseStack.last().pose(), geometry.middleBox(), worldPosition, level, player, entityOrigin);

        poseStack.pushPose();
        model.tipCollisionPart().translateAndRotate(poseStack);
        Vec3 tip = bendForBox(poseStack.last().pose(), geometry.tipBox(), worldPosition, level, player, entityOrigin);
        poseStack.popPose();
        poseStack.popPose();
        poseStack.popPose();
        return new Bends(root, middle, tip);
    }

    private static Vec3 bendForBox(Matrix4f transform, AABB localBox, Vec3 worldPosition,
                                   ClientLevel level, AbstractClientPlayer player, Vector3f entityOrigin) {
        // The enclosing AABB only finds nearby blocks. Test the actual rotated
        // guide as well, without enlarging the collision volume from Blockbench.
        AABB worldBox = worldBox(transform, localBox, worldPosition, entityOrigin);
        OrientedBox guide = orientedBox(transform, localBox, worldPosition, entityOrigin);
        Vec3 push = Vec3.ZERO;
        // Minecraft supplies world-space shapes, including slabs, fences, and modded block shapes.
        for (VoxelShape shape : level.getBlockCollisions(player, worldBox)) {
            for (AABB blockBox : shape.toAabbs()) {
                push = push.add(guide.pushFrom(blockBox));
            }
        }
        if (push.lengthSqr() == 0) return Vec3.ZERO;
        if (push.lengthSqr() > MAX_PUSH * MAX_PUSH) push = push.normalize().scale(MAX_PUSH);

        Vec3 pivot = worldPoint(transform, 0, 0, 0, worldPosition, entityOrigin);
        Vec3 center = worldPoint(transform,
            (localBox.minX + localBox.maxX) * 0.5 / 16.0,
            (localBox.minY + localBox.maxY) * 0.5 / 16.0,
            (localBox.minZ + localBox.maxZ) * 0.5 / 16.0, worldPosition, entityOrigin);
        Vec3 lever = center.subtract(pivot);
        double inverseLever = 1.0 / Math.max(lever.lengthSqr(), 1.0 / 256.0);
        Vec3 torque = lever.cross(push);
        if (torque.lengthSqr() < lever.lengthSqr() * push.lengthSqr() * 0.02) {
            // A block directly behind the tail pushes along its length. That cannot rotate
            // a joint, so curl toward the clearer vertical side instead.
            double direction = clearAt(guide, worldBox, 0.125, level, player)
                || !clearAt(guide, worldBox, -0.125, level, player) ? 1 : -1;
            Vec3 perpendicular = new Vec3(0, direction, 0);
            Vec3 along = lever.normalize();
            perpendicular = perpendicular.subtract(along.scale(perpendicular.dot(along))).normalize();
            if (perpendicular.lengthSqr() < 1.0e-6) {
                perpendicular = new Vec3(1, 0, 0);
            }
            torque = lever.cross(perpendicular.scale(push.length()));
        }
        torque = torque.scale(inverseLever);

        // Convert the world-space push into rotations around this segment's local axes.
        Vec3 xAxis = worldPoint(transform, 1, 0, 0, worldPosition, entityOrigin).subtract(pivot).normalize();
        Vec3 yAxis = worldPoint(transform, 0, 1, 0, worldPosition, entityOrigin).subtract(pivot).normalize();
        Vec3 zAxis = worldPoint(transform, 0, 0, 1, worldPosition, entityOrigin).subtract(pivot).normalize();
        return new Vec3(
            clamp(torque.dot(xAxis)),
            clamp(torque.dot(yAxis)),
            clamp(torque.dot(zAxis)));
    }

    private static AABB worldBox(Matrix4f transform, AABB box, Vec3 worldPosition,
                                 Vector3f entityOrigin) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    Vec3 point = worldPoint(transform,
                        (x == 0 ? box.minX : box.maxX) / 16.0,
                        (y == 0 ? box.minY : box.maxY) / 16.0,
                        (z == 0 ? box.minZ : box.maxZ) / 16.0, worldPosition, entityOrigin);
                    minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                    minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
                    minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Vec3 worldPoint(Matrix4f transform, double x, double y, double z,
                                   Vec3 worldPosition, Vector3f entityOrigin) {
        Vector3f point = transform.transformPosition((float) x, (float) y, (float) z, new Vector3f());
        return new Vec3(worldPosition.x + point.x - entityOrigin.x,
            worldPosition.y + point.y - entityOrigin.y,
            worldPosition.z + point.z - entityOrigin.z);
    }

    private static OrientedBox orientedBox(Matrix4f transform, AABB box,
                                           Vec3 worldPosition, Vector3f origin) {
        Vec3 center = worldPoint(transform, (box.minX + box.maxX) / 32,
            (box.minY + box.maxY) / 32, (box.minZ + box.maxZ) / 32, worldPosition, origin);
        Vec3 pivot = worldPoint(transform, 0, 0, 0, worldPosition, origin);
        Vec3[] edges = {
            worldPoint(transform, 1, 0, 0, worldPosition, origin).subtract(pivot),
            worldPoint(transform, 0, 1, 0, worldPosition, origin).subtract(pivot),
            worldPoint(transform, 0, 0, 1, worldPosition, origin).subtract(pivot)
        };
        double[] halfSizes = {(box.maxX - box.minX) / 32,
            (box.maxY - box.minY) / 32, (box.maxZ - box.minZ) / 32};
        for (int i = 0; i < 3; i++) {
            halfSizes[i] *= edges[i].length();
            edges[i] = edges[i].normalize();
        }
        return new OrientedBox(center, edges, halfSizes);
    }

    private static boolean clearAt(OrientedBox guide, AABB bounds, double y,
                                   ClientLevel level, AbstractClientPlayer player) {
        OrientedBox moved = new OrientedBox(guide.center.add(0, y, 0), guide.axes, guide.halfSizes);
        for (VoxelShape shape : level.getBlockCollisions(player, bounds.move(0, y, 0))) {
            for (AABB block : shape.toAabbs()) {
                if (moved.pushFrom(block).lengthSqr() > 0) return false;
            }
        }
        return true;
    }

    // Separating-axis testing rejects empty corners of the enclosing AABB.
    // The smallest overlap gives a push out of the solid block's collision box.
    record OrientedBox(Vec3 center, Vec3[] axes, double[] halfSizes) {
        Vec3 pushFrom(AABB block) {
            Vec3[] worldAxes = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
            Vec3[] candidates = new Vec3[15];
            for (int i = 0; i < 3; i++) {
                candidates[i] = axes[i];
                candidates[3 + i] = worldAxes[i];
                for (int j = 0; j < 3; j++) candidates[6 + i * 3 + j] = axes[i].cross(worldAxes[j]);
            }
            Vec3 delta = center.subtract(new Vec3((block.minX + block.maxX) / 2,
                (block.minY + block.maxY) / 2, (block.minZ + block.maxZ) / 2));
            double depth = Double.POSITIVE_INFINITY;
            Vec3 direction = Vec3.ZERO;
            for (Vec3 candidate : candidates) {
                if (candidate.lengthSqr() < 1.0e-12) continue;
                Vec3 axis = candidate.normalize();
                double radius = 0;
                for (int i = 0; i < 3; i++) radius += halfSizes[i] * Math.abs(axes[i].dot(axis));
                double blockRadius = (block.maxX - block.minX) / 2 * Math.abs(axis.x)
                    + (block.maxY - block.minY) / 2 * Math.abs(axis.y)
                    + (block.maxZ - block.minZ) / 2 * Math.abs(axis.z);
                double distance = delta.dot(axis);
                double overlap = radius + blockRadius - Math.abs(distance);
                if (overlap <= 1.0e-7) return Vec3.ZERO;
                if (overlap < depth) {
                    depth = overlap;
                    direction = axis.scale(distance < 0 ? -1 : 1);
                }
            }
            return direction.scale(depth);
        }
    }

    private static double clamp(double angle) {
        return Math.max(-MAX_BEND, Math.min(MAX_BEND, angle));
    }
}
