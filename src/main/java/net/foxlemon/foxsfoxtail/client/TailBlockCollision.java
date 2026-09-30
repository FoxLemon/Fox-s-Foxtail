package net.foxlemon.foxsfoxtail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Visual-only block contact. These inner boxes match the collision cubes in fox_tail.bbmodel.
// The Blockbench cubes are guides; they are deliberately absent from createBodyLayer().
final class TailBlockCollision {
    private static final AABB ROOT_BOX = new AABB(-8, -7, -1, -6, -5, 1);
    private static final AABB MIDDLE_BOX = new AABB(0, -1, -1, 7, 1, 1);
    private static final AABB TIP_BOX = new AABB(0, -1, -1, 3, 1, 1);
    // Probe a pixel beyond the inner collision cubes so the spring can start
    // turning before the visible fur passes deeply into a wall.
    private static final double CONTACT_MARGIN = 1.0;
    private static final double MAX_PUSH = 0.25;
    private static final double MAX_BEND = Math.toRadians(35);

    record Bends(Vec3 root, Vec3 middle, Vec3 tip) {
        static final Bends ZERO = new Bends(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
    }

    private TailBlockCollision() {}

    static Bends sample(FoxTailModel model, PoseStack poseStack, AvatarRenderState state,
                        ClientLevel level, AbstractClientPlayer player, Vector3f entityOrigin) {
        // The render layer has already positioned the tail behind the player's torso.
        // Pose each part so the probes inherit the same root, middle, and tip rotations.
        model.setupAnim(state);
        poseStack.pushPose();
        model.rootCollisionPart().translateAndRotate(poseStack);
        Vec3 root = bendForBox(poseStack.last().pose(), ROOT_BOX, state, level, player, entityOrigin);

        poseStack.pushPose();
        model.middleCollisionPart().translateAndRotate(poseStack);
        Vec3 middle = bendForBox(poseStack.last().pose(), MIDDLE_BOX, state, level, player, entityOrigin);

        poseStack.pushPose();
        model.tipCollisionPart().translateAndRotate(poseStack);
        Vec3 tip = bendForBox(poseStack.last().pose(), TIP_BOX, state, level, player, entityOrigin);
        poseStack.popPose();
        poseStack.popPose();
        poseStack.popPose();
        return new Bends(root, middle, tip);
    }

    private static Vec3 bendForBox(Matrix4f transform, AABB localBox, AvatarRenderState state,
                                   ClientLevel level, AbstractClientPlayer player, Vector3f entityOrigin) {
        // The tail runs along local X. Expand only its cross-section; extending
        // the ends would make the base react to blocks behind the player.
        AABB worldBox = worldBox(transform, localBox.inflate(0, CONTACT_MARGIN, CONTACT_MARGIN),
            state, entityOrigin);
        Vec3 push = Vec3.ZERO;
        // Minecraft supplies world-space shapes, including slabs, fences, and modded block shapes.
        for (VoxelShape shape : level.getBlockCollisions(player, worldBox)) {
            for (AABB blockBox : shape.toAabbs()) {
                if (worldBox.intersects(blockBox)) {
                    push = push.add(shortestPush(worldBox, blockBox));
                }
            }
        }
        if (push.lengthSqr() == 0) return Vec3.ZERO;
        if (push.lengthSqr() > MAX_PUSH * MAX_PUSH) push = push.normalize().scale(MAX_PUSH);

        Vec3 pivot = worldPoint(transform, 0, 0, 0, state, entityOrigin);
        Vec3 center = worldPoint(transform,
            (localBox.minX + localBox.maxX) * 0.5 / 16.0,
            (localBox.minY + localBox.maxY) * 0.5 / 16.0,
            (localBox.minZ + localBox.maxZ) * 0.5 / 16.0, state, entityOrigin);
        Vec3 lever = center.subtract(pivot);
        double inverseLever = 1.0 / Math.max(lever.lengthSqr(), 1.0 / 256.0);
        Vec3 torque = lever.cross(push);
        if (torque.lengthSqr() < lever.lengthSqr() * push.lengthSqr() * 0.02) {
            // A block directly behind the tail pushes along its length. That cannot rotate
            // a joint, so curl toward the clearer vertical side instead.
            double direction = level.noBlockCollision(player, worldBox.move(0, 0.125, 0))
                || !level.noBlockCollision(player, worldBox.move(0, -0.125, 0)) ? 1 : -1;
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
        Vec3 xAxis = worldPoint(transform, 1, 0, 0, state, entityOrigin).subtract(pivot).normalize();
        Vec3 yAxis = worldPoint(transform, 0, 1, 0, state, entityOrigin).subtract(pivot).normalize();
        Vec3 zAxis = worldPoint(transform, 0, 0, 1, state, entityOrigin).subtract(pivot).normalize();
        return new Vec3(
            clamp(torque.dot(xAxis)),
            clamp(torque.dot(yAxis)),
            clamp(torque.dot(zAxis)));
    }

    private static AABB worldBox(Matrix4f transform, AABB box, AvatarRenderState state,
                                 Vector3f entityOrigin) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    Vec3 point = worldPoint(transform,
                        (x == 0 ? box.minX : box.maxX) / 16.0,
                        (y == 0 ? box.minY : box.maxY) / 16.0,
                        (z == 0 ? box.minZ : box.maxZ) / 16.0, state, entityOrigin);
                    minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                    minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
                    minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Vec3 worldPoint(Matrix4f transform, double x, double y, double z,
                                   AvatarRenderState state, Vector3f entityOrigin) {
        Vector3f point = transform.transformPosition((float) x, (float) y, (float) z, new Vector3f());
        return new Vec3(state.x + point.x - entityOrigin.x,
            state.y + point.y - entityOrigin.y,
            state.z + point.z - entityOrigin.z);
    }

    private static Vec3 shortestPush(AABB tail, AABB block) {
        double[] distances = {
            block.minX - tail.maxX, block.maxX - tail.minX,
            block.minY - tail.maxY, block.maxY - tail.minY,
            block.minZ - tail.maxZ, block.maxZ - tail.minZ
        };
        int best = 0;
        for (int i = 1; i < distances.length; i++) {
            if (Math.abs(distances[i]) < Math.abs(distances[best])) best = i;
        }
        return switch (best) {
            case 0, 1 -> new Vec3(distances[best], 0, 0);
            case 2, 3 -> new Vec3(0, distances[best], 0);
            default -> new Vec3(0, 0, distances[best]);
        };
    }

    private static double clamp(double angle) {
        return Math.max(-MAX_BEND, Math.min(MAX_BEND, angle));
    }
}
