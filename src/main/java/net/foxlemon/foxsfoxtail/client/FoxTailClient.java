package net.foxlemon.foxsfoxtail.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.foxlemon.foxsfoxtail.FoxsFoxTail;
import net.foxlemon.foxsfoxtail.FoxTailConfig;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.ClientTickEvent;

// Load client setup here and automatically subscribe the static event handlers below.
@Mod(value = FoxsFoxTail.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = FoxsFoxTail.MODID, value = Dist.CLIENT)
public class FoxTailClient {

    // The base should move less than the flexible middle and tip.
    static final double ROOT_STRENGTH_MULTIPLIER = 0.08;
    // The tip adds half as much bend as the middle, while retaining its own lag.
    static final double TIP_STRENGTH_MULTIPLIER = 0.5;
    // A block touching the outer tail should pull the base away even when its
    // own small collision box is still inside the player's footprint.
    private static final double MIDDLE_TO_ROOT_CONTACT = 0.6;
    private static final double TIP_TO_ROOT_CONTACT = 0.3;

    private static KeyMapping openSettingsKey;

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        // UNKNOWN leaves the shortcut unassigned until the player chooses a key.
        openSettingsKey = new KeyMapping("key.foxsfoxtail.open_settings",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.foxsfoxtail");
        event.register(openSettingsKey);
    }

    // The settings preview temporarily supplies its own pose during GUI rendering.
    private static FoxTailModel.Pose previewPose;

    // This initial test simulates only the local player's tail.
    private static AbstractClientPlayer trackedPlayer;

    // Each segment owns its spring and the two snapshots used for smooth rendering.
    private static SegmentMotion rootMotion;
    private static SegmentMotion middleMotion;
    private static SegmentMotion tipMotion;

    // The local player's rendered collision probes supply targets for the next physics tick.
    private static TailBlockCollision.Bends blockBends = TailBlockCollision.Bends.ZERO;
    private static int lastBlockSampleTick;
    private static boolean hasBlockSample;

    // Player movement is shared input, so it is sampled only once per tick.
    private static Vec3 previousVelocity = Vec3.ZERO;
    private static float previousBodyYaw;

    // Torso roll samples from the render layer, in radians. These are not root-angle samples.
    private static double previousBaseRoll, latestBaseRoll;
    private static boolean hasBaseRoll;
    private static int physicsTick, lastRollTick;
    // Read the saved angle only after NeoForge has loaded the client config.
    private static float previousRootAngle;
    private static float currentRootAngle;

    public FoxTailClient(ModContainer container) {
        // Register the saved client settings before opening their editor or reading them in-game.
        container.registerConfig(ModConfig.Type.CLIENT, FoxTailConfig.SPEC);
        // Open our slider screen from the Mods menu.
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (modContainer, parent) -> new TailSettingsScreen(parent));
    }

    @SubscribeEvent
    public static void tickTail(ClientTickEvent.Post event) {
        // Advance physics after a client tick, independently of how many frames are drawn.
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;

        if (openSettingsKey != null) {
            while (openSettingsKey.consumeClick()) {
                if (player != null && minecraft.screen == null) {
                    minecraft.setScreen(new TailSettingsScreen(null));
                }
            }
        }

        if (player == null) {
            // Discard old motion when leaving the world.
            trackedPlayer = null;
            rootMotion = middleMotion = tipMotion = null;
            previousVelocity = Vec3.ZERO;
            previousBodyYaw = 0.0F;
            hasBaseRoll = false;
            hasBlockSample = false;
            blockBends = TailBlockCollision.Bends.ZERO;
            return;
        }

        // Initialize again after joining a world or re-spawning.
        if (player != trackedPlayer) {
            trackedPlayer = player;
            rootMotion = new SegmentMotion();
            middleMotion = new SegmentMotion();
            tipMotion = new SegmentMotion();
            previousVelocity = player.getDeltaMovement();
            previousBodyYaw = player.yBodyRot;
            hasBaseRoll = false;
            hasBlockSample = false;
            blockBends = TailBlockCollision.Bends.ZERO;
            previousRootAngle = currentRootAngle = FoxTailConfig.TAIL_ANGLE.get().floatValue();
        }

        if (minecraft.isPaused()) {
            // Ignore any camera/body rotation that happens while simulation is paused.
            previousBodyYaw = player.yBodyRot;
            hasBlockSample = false;
            return;
        }
        
        float restingAngle = FoxTailConfig.TAIL_ANGLE.get().floatValue();
        float targetAngle = restingAngle;

        // Pose angle diction 
        if (player.isSleeping()) {
            targetAngle = TailPose.SLEEP_ANGLE;
        } else if (player.isFallFlying() || player.isSwimming() || player.isVisuallyCrawling()) {
            targetAngle = TailPose.SWIM_AND_ELYTRA_ANGLE;
        } else if (player.isPassenger()) {
            // Raise low resting angles for sitting, but preserve an already higher angle.
            targetAngle = Math.max(restingAngle, TailPose.SIT_ANGLE);
        } else if (player.isCrouching()) {
            targetAngle = TailPose.CROUCH_ANGLE;
        }

        previousRootAngle = currentRootAngle;
        currentRootAngle = TailPose.approachAngle(currentRootAngle, targetAngle);
        
        physicsTick++;

        Vec3 currentVelocity = player.getDeltaMovement();
        // A change in velocity drives the bend; constant velocity adds no new acceleration.
        Vec3 acceleration = currentVelocity.subtract(previousVelocity);
        previousVelocity = currentVelocity;

        // Body yaw is in degrees; trigonometry and spring targets use radians.
        double yaw = Math.toRadians(player.yBodyRot);
        double turn = Math.toRadians(Math.IEEEremainder(player.yBodyRot - previousBodyYaw, 360.0));
        previousBodyYaw = player.yBodyRot;

        // Approximate tail-local acceleration using body yaw and the layer's -90-degree Y turn.
        // This does not yet account for torso tilt or the configured root elevation.
        double localY = -acceleration.y;
        double localZ = acceleration.x * Math.cos(yaw) + acceleration.z * Math.sin(yaw);

        // Strength scales the driving motion, not spring stiffness (controlled by frequency).
        double strength = FoxTailConfig.MOVEMENT_STRENGTH.get();
        double bendY = localZ * strength;
        double bendZ = -localY * strength;
        double twistX = 0.0;

        // A stationary turn has no linear acceleration. Push the tail opposite
        // the body's yaw change so its segments lag and then settle.
        // Ignore abrupt teleports or rotation corrections rather than whipping the tail.
        if (Math.abs(turn) <= Math.toRadians(90)) {
            bendY -= turn * strength * 0.5;
        }
        
        if (hasBaseRoll && physicsTick - lastRollTick <= 2) {
            // Use only recent rendered poses; react to the change in roll, not the held angle.
            double change = latestBaseRoll - previousBaseRoll;
            // Take the short route if an angle crosses from +PI to -PI.
            change = Math.atan2(Math.sin(change), Math.cos(change));
            // The layer's -90 degree Y turn maps torso +Z to tail -X.
            // Positive X therefore makes the middle lag behind positive torso roll.
            twistX = change * strength;
            previousBaseRoll = latestBaseRoll;
        } else {
            // Re-establish a baseline after the player stops being rendered.
            hasBaseRoll = false;
        }

        // Keep the driving motion: the spring now resists bending beyond its soft limit.
        Vec3 target = new Vec3(twistX, bendY, bendZ);
        // Root-angle changes are not yet fed into this target to produce segment lag.

        TailBlockCollision.Bends contact = hasBlockSample && physicsTick - lastBlockSampleTick <= 2
            ? blockBends : TailBlockCollision.Bends.ZERO;

        // Only movement uses the gentle root multiplier. Share outer-segment
        // block contact with the root so the whole tail can turn away from a wall.
        Vec3 rootContact = contact.root()
            .add(contact.middle().scale(MIDDLE_TO_ROOT_CONTACT))
            .add(contact.tip().scale(TIP_TO_ROOT_CONTACT));
        rootMotion.tick(target.scale(ROOT_STRENGTH_MULTIPLIER).add(rootContact));
        // The tip follows the middle's output, adding another stage of lag.
        Vec3 middle = middleMotion.tick(target.add(contact.middle()));
        tipMotion.tick(middle.scale(TIP_STRENGTH_MULTIPLIER).add(contact.tip()));
    }

    private static final class SegmentMotion {
        private final TailPhysics spring = new TailPhysics(
            FoxTailConfig.FREQUENCY.get().floatValue(),
            FoxTailConfig.DAMPING.get().floatValue(),
            FoxTailConfig.RESPONSE.get().floatValue(), Vec3.ZERO);
        private Vec3 previous = Vec3.ZERO;
        private Vec3 current = Vec3.ZERO;

        private Vec3 tick(Vec3 target) {
            previous = current;
            // Retune without resetting motion when the settings change.
            spring.configure(
                FoxTailConfig.FREQUENCY.get().floatValue(),
                FoxTailConfig.DAMPING.get().floatValue(),
                FoxTailConfig.RESPONSE.get().floatValue());
            spring.setBendLimit(Math.toRadians(FoxTailConfig.MAX_BEND.get()));
            spring.setSoftLimitDampingMultiplier(FoxTailConfig.SOFT_LIMIT_DAMPING_MULTIPLIER.get());
            current = spring.Update(0.05f, target);
            return current;
        }

        private Vec3 sample(float partialTick) {
            return previous.lerp(current, partialTick);
        }
    }

    static void setPreviewPose(FoxTailModel.Pose pose) {
        previewPose = pose;
    }

    static boolean isLocal(AbstractClientPlayer player) {
        return trackedPlayer == player;
    }

    static boolean shouldSampleBlockCollision(AbstractClientPlayer player) {
        return previewPose == null && isLocal(player)
            && !Minecraft.getInstance().isPaused() && lastBlockSampleTick != physicsTick;
    }

    public static void recordBlockCollision(AbstractClientPlayer player, TailBlockCollision.Bends bends) {
        if (!isLocal(player)) return;
        blockBends = bends;
        lastBlockSampleTick = physicsTick;
        hasBlockSample = true;
    }

    // Rendering only supplies the latest pose; tickTail advances the spring.
    public static void recordBaseRoll(AbstractClientPlayer player, double roll) {
        if (!isLocal(player) || previewPose != null
                || Minecraft.getInstance().isPaused() || !Double.isFinite(roll)) {
            return;
        }
        if (!hasBaseRoll || physicsTick - lastRollTick > 2) {
            // First sample after a gap establishes a baseline instead of causing a sudden twist.
            previousBaseRoll = roll;
        }
        latestBaseRoll = roll;
        lastRollTick = physicsTick;
        hasBaseRoll = true;
    }

    static FoxTailModel.Pose samplePose(AbstractClientPlayer player, float partialTick, Vec3 avoidance) {
        if (previewPose != null) {
            return new FoxTailModel.Pose(previewPose.root(), previewPose.middle(),
                previewPose.tip(), previewPose.angle(), avoidance);
        }
        // Other players currently show the resting pose without local simulation.
        if (!isLocal(player) || rootMotion == null || middleMotion == null || tipMotion == null) {
            return new FoxTailModel.Pose(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                FoxTailConfig.TAIL_ANGLE.get(), avoidance);
        }
        float fraction = Math.max(0, Math.min(1, partialTick));
        double rootAngle = previousRootAngle + (currentRootAngle - previousRootAngle) * fraction;
        return new FoxTailModel.Pose(rootMotion.sample(fraction), middleMotion.sample(fraction),
            tipMotion.sample(fraction), rootAngle, avoidance);
    }

    @SubscribeEvent
    public static void registerTailModelReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new FoxTailGeometry.Reload());
    }

    @SubscribeEvent 
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        // Attach a tail layer to each player skin model, including normal and slim.
        for (PlayerSkin.Model type : event.getSkins()) {
            PlayerRenderer playRenderer = event.getSkin(type);
            if (playRenderer != null) {
                playRenderer.addLayer(new FoxTailRenderLayers(playRenderer));
            }
        }
    }

}
