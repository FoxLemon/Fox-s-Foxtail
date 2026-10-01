package net.foxlemon.foxsfoxtail.client;

import net.minecraft.world.phys.Vec3;

// Three independent angular spring axes. Inputs/outputs are radians; time is in seconds.
public class TailPhysics {

    private static final int SUBSTEPS = 4;
    // Beyond the soft limit, each additional 10 degrees increases resistance exponentially.
    private static final double SOFT_LIMIT_WIDTH = Math.toRadians(10);
    private double bendLimit = Double.POSITIVE_INFINITY;
    // This value controls the exponent of damping beyond the soft bend limit.
    private double softLimitDampingMultiplier = 1.0;
    // xp: previous target, y: simulated angle, yd: angular velocity in radians per second.
    private Vec3 xp;
    private Vec3 y, yd;
    // Coefficients derived from frequency (f), damping ratio (z), and response (r).
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
        // Estimate target velocity when the caller supplies only an angular target.
        return Update(T, x, null);
    }

    // The limit is a local angular offset per axis, in radians, not root elevation.
    public void setBendLimit(double radians) {
        if (!Double.isFinite(radians) || radians < 0) {
            throw new IllegalArgumentException("Bend limit must be nonnegative and finite");
        }
        bendLimit = radians;
    }

    public void setSoftLimitDampingMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0) {
            throw new IllegalArgumentException("Soft-limit damping multiplier must be nonnegative and finite");
        }
        softLimitDampingMultiplier = multiplier;
    }

    public Vec3 Update(float T, Vec3 x, Vec3 xd) {
        // xd is optional target angular velocity, not the player's movement velocity.

        if (!Float.isFinite(T) || T <= 0) {
            return y;
        }

        if (xd == null) {
            xd = x.subtract(xp).scale(1.0/T);
        }
        // Keep the baseline current even when an explicit velocity was supplied.
        xp = x;

        Vec3 drive = x.add(xd.scale(k3));
        // Smaller integration steps improve the response near the nonlinear soft limit.
        double step = T / (double) SUBSTEPS;
        for (int i = 0; i < SUBSTEPS; i++) {
            Vec3 next = new Vec3(
                stepAxis(y.x, yd.x, drive.x, step),
                stepAxis(y.y, yd.y, drive.y, step),
                stepAxis(y.z, yd.z, drive.z, step));
            yd = next.subtract(y).scale(1.0 / step);
            y = next;
        }

        return y;
    }

    private double stepAxis(double angle, double velocity, double drive, double step) {
        // Implicit integration stays stable even when the exponential force becomes steep.
        double denominator = k2 + step * k1 + step * step;
        double freeAngle = (angle * (k2 + step * k1)
            + step * k2 * velocity + step * step * drive) / denominator;
        if (Math.abs(freeAngle) > bendLimit && Math.abs(freeAngle) > Math.abs(angle)
                && softLimitDampingMultiplier > 0 && k1 > 0) {
            // Resist further outward motion by e^(power * excess / 10 degrees).
            // Returning toward the limit keeps normal damping so the tail cannot get stuck.
            double excess = Math.abs(freeAngle) - bendLimit;
            double exponent = Math.min(40, softLimitDampingMultiplier * excess / SOFT_LIMIT_WIDTH);
            double damping = k1 * Math.exp(exponent);
            denominator = k2 + step * damping + step * step;
            freeAngle = (angle * (k2 + step * damping)
                + step * k2 * velocity + step * step * drive) / denominator;
        }
        double magnitude = Math.abs(freeAngle);
        if (magnitude <= bendLimit) {
            return freeAngle;
        }

        double weight = step * step / denominator;
        double lower = bendLimit;
        double upper = magnitude;
        // Solve the additional restoring force rather than snapping to an angle boundary.
        for (int i = 0; i < 40; i++) {
            double candidate = lower + (upper - lower) * 0.5;
            double excess = candidate - bendLimit;
            // Subtract the linear term so the extra force starts smoothly at the limit.
            double resistance = SOFT_LIMIT_WIDTH * Math.expm1(excess / SOFT_LIMIT_WIDTH) - excess;
            if (candidate + weight * resistance > magnitude) {
                upper = candidate;
            } else {
                lower = candidate;
            }
        }
        return Math.copySign(lower + (upper - lower) * 0.5, freeAngle);
    }
}
