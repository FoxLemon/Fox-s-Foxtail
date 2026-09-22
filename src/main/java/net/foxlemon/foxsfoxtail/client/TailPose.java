package net.foxlemon.foxsfoxtail.client;

import org.joml.Math;

import net.minecraft.world.phys.Vec3;

public class TailPose {
    // For quick access to the animation angles
    private static float swayAngle = 25;
    private static float twistAngle = 15; 
    private static float crouchAngle = -35;

    private TailPose() {}

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    public static Vec3 legAvoidance(float lPitch, float rPitch, float movementAmount) {
        float lBack = Math.max(0, (float) Math.sin(lPitch));
        float rBack = Math.max(0, (float) Math.sin(rPitch));

        float difference = (rBack - lBack) * clamp(movementAmount * 4, 0, 1);

        float swayY = difference * swayAngle;
        float twistX = difference * twistAngle;

        return new Vec3(twistX,swayY,0);
    }

    public static float approachCrouch(float currentAngle, boolean isCrouching) {
        return clamp(currentAngle + (isCrouching ? 0.2F : -0.2F), 0, 1);
    }

    public static float crouchElevation(float restingAngle, float crouchWeight) {
        // Blend from the configured angle to -35 degrees while crouching.
        return restingAngle + (crouchAngle - restingAngle) * crouchWeight;
    }

    
}
