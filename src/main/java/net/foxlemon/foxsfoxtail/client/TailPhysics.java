package net.foxlemon.foxsfoxtail.client;

import net.minecraft.world.phys.Vec3;

public class TailPhysics {

    private Vec3 xp;
    private Vec3 y, yd;
    private float k1, k2, k3;
    private final float PI = (float) Math.PI;

    public TailPhysics(float f, float z, float r, Vec3 x0)
    {
        configure(f, z, r);
        xp = x0;
        y = x0;
        yd = Vec3.ZERO;
    }

    // Retune coefficients without resetting the current motion or previous input.
    public void configure(float f, float z, float r) {
        if (!Float.isFinite(f) || f <= 0) {
            throw new IllegalArgumentException("Frequency must be positive and finite");
        }

        k1 = z / (PI * f);
        k2 = 1 / ((2 * PI * f) * (2 * PI * f));
        k3 = r * z / (2 * PI * f);

    }

    public Vec3 Update(float T, Vec3 x) {
        return Update(T, x, null);
    }

    public Vec3 Update(float T, Vec3 x, Vec3 xd) {

        if (!Float.isFinite(T) || T <= 0) {
            return y;
        }

        if (xd == null) {
            xd = x.subtract(xp).scale(1.0/T);
        }
        xp = x;

        float k2_stable = Math.max(k2, 1.1f * (T*T/4 + T*k1/2));
        y = y.add(yd.scale(T));
        yd = yd.add(
            x.add(xd.scale(k3))
            .subtract(y)
            .subtract(yd.scale(k1))
            .scale(T / k2_stable)
        );

        return y;
    }
    
}
