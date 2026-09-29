package net.foxlemon.foxsfoxtail;

import net.neoforged.neoforge.common.ModConfigSpec;

// Saved client settings. NeoForge's config screen provides editing and reset controls.
public class FoxTailConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MOVEMENT_STRENGTH = BUILDER
        .translation("foxsfoxtail.configuration.movementStrength")
        .comment("How strongly movement changes the target bend. This is not spring stiffness.")
        .defineInRange("movementStrength", 8.0, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue FREQUENCY = BUILDER
        .translation("foxsfoxtail.configuration.frequency")
        .comment("Spring frequency in Hz. Higher values respond faster and feel stiffer.")
        .defineInRange("frequency", 1.0, 0.1, 10.0);

    public static final ModConfigSpec.DoubleValue DAMPING = BUILDER
        .translation("foxsfoxtail.configuration.damping")
        .comment("Damping ratio: below 1 oscillates, 1 is critical damping, above 1 settles without oscillation.")
        .defineInRange("damping", 0.25, 0.0, 2.0);

    public static final ModConfigSpec.DoubleValue SOFT_LIMIT_DAMPING_MULTIPLIER = BUILDER
        .translation("foxsfoxtail.configuration.softLimitDampingMultiplier")
        .comment("Multiplies damping for all segment bends beyond its soft limit. 1 keeps the normal damping.")
        .defineInRange("softLimitDampingMultiplier", 3.0, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue RESPONSE = BUILDER
        .translation("foxsfoxtail.configuration.response")
        .comment("Response to target changes. Negative values anticipate; larger positive values emphasize the initial response.")
        .defineInRange("response", 1.0, -2.0, 2.0);

    public static final ModConfigSpec.DoubleValue MAX_BEND = BUILDER
        .translation("foxsfoxtail.configuration.maximumTargetBend")
        .comment("Soft bend limit per segment and axis in degrees. Extra restoring force grows exponentially beyond this angle; it is not a hard cap.")
        .defineInRange("maximumTargetBend", 15.0, 0.0, 90.0);

    public static final ModConfigSpec.DoubleValue TAIL_ANGLE = BUILDER
        .comment("Root elevation in degrees: negative lowers the tail, positive raises it.")
        .defineInRange("tailAngle", -40, -90.0, 90.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
