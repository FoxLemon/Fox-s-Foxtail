package net.foxlemon.foxsfoxtail.client;

import org.joml.Math;

import net.minecraft.world.phys.Vec3;

public class TailPose {
    // For quick access to the animation angles
    private static final float SWAY_ANGLE = 25;
    private static final float TWIST_ANGLE = 15;
    private static final float DEGREE_PER_TICK = 12.0F;

    public static final float CROUCH_ANGLE = -35;
    public static final float SWIM_AND_ELYTRA_ANGLE = -60;
    public static final float SIT_ANGLE = 0;
    public static final float SIT_WITH_OBSTRUCTION_ANGLE = 60;
    public static final float SLEEP_ANGLE = -90;

    private TailPose() {}

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    public static Vec3 legAvoidance(float lPitch, float rPitch, float movementAmount) {
        float lBack = Math.max(0, (float) Math.sin(lPitch));
        float rBack = Math.max(0, (float) Math.sin(rPitch));

        float difference = (rBack - lBack) * clamp(movementAmount * 4, 0, 1);

        float swayY = difference * SWAY_ANGLE;
        float twistX = difference * TWIST_ANGLE;

        return new Vec3(twistX,swayY,0);
    }

    public static float approachAngle(float current, float target) {
        float change = Math.max(-DEGREE_PER_TICK, Math.min(DEGREE_PER_TICK, target - current));
        return current + change;
    }

}
