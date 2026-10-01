package net.foxlemon.foxsfoxtail.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.foxlemon.foxsfoxtail.FoxsFoxTail;
import net.foxlemon.foxsfoxtail.FoxTailConfig;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

// Client setup and local-player simulation. Ticks advance the springs; rendering samples them.
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
        var category = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "controls"));
        event.registerCategory(category);
        // UNKNOWN leaves the shortcut unassigned until the player chooses a key.
        openSettingsKey = new KeyMapping("key.foxsfoxtail.open_settings",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category);
        event.register(openSettingsKey);
    }

    // Render-data keys carry values to FoxTailModel.setupAnim(); they do not move parts themselves.
    // The vector contains angular offsets in radians: X twist, Y/Z bend, not a position.
    public static final ContextKey<Vec3> ROOT_ROTATION =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "root_rotation"));

    public static final ContextKey<Vec3> MIDDLE_ROTATION =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "middle_rotation"));

    public static final ContextKey<Vec3> TIP_ROTATION =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "tip_rotation"));
    // Root elevation is stored separately in degrees; the model converts it to radians.
    public static final ContextKey<Double> TAIL_ANGLE =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "tail_angle"));
    
    // Leg-clearance offsets in degrees, unlike the spring rotations above.
    public  static final ContextKey<Vec3> TAIL_AVOIDANCE = 
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "tail_avoidance"));
    // Marks the local player's render state so other players cannot drive our spring.
    private static final ContextKey<Boolean> LOCAL_TAIL =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "local_tail"));

    // Only this player owns simulated motion; remote players receive a resting pose.
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
        // Open our custom settings screen from the Mods menu.
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

        // Choose one pose target in priority order, then approach it smoothly each tick.
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

        // Expire old contacts if rendering stops; otherwise an unseen obstacle keeps pushing.
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

    // One independent spring and interpolation history for each segment.
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
            // Minecraft runs 20 simulation ticks per second; the spring uses seconds.
            current = spring.Update(0.05f, target);
            return current;
        }

        private Vec3 sample(float partialTick) {
            return previous.lerp(current, partialTick);
        }
    }

    public static void disablePhysicsRecording(AvatarRenderState state) {
        // The settings preview must not feed its artificial pose back into gameplay physics.
        state.setRenderData(LOCAL_TAIL, false);
    }

    public static boolean shouldSampleBlockCollision(AvatarRenderState state) {
        // Accept at most one gameplay collision sample per tick, never a settings preview.
        return state.getRenderDataOrDefault(LOCAL_TAIL, false)
            && !Minecraft.getInstance().isPaused() && lastBlockSampleTick != physicsTick;
    }

    public static void recordBlockCollision(AvatarRenderState state, TailBlockCollision.Bends bends) {
        if (!state.getRenderDataOrDefault(LOCAL_TAIL, false)) return;
        blockBends = bends;
        lastBlockSampleTick = physicsTick;
        hasBlockSample = true;
    }

    // Rendering only supplies the latest pose; tickTail advances the spring.
    public static void recordBaseRoll(AvatarRenderState state, double roll) {
        if (!state.getRenderDataOrDefault(LOCAL_TAIL, false)
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

    @SubscribeEvent
    public static void registerTailRenderData(
            RegisterRenderStateModifiersEvent event) {

        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {

                // Other players receive no simulated bend; physics currently tracks only our player.
                Vec3 rootRotation = Vec3.ZERO;
                Vec3 middleRotation = Vec3.ZERO;
                Vec3 tipRotation = Vec3.ZERO;
                double rootAngle = FoxTailConfig.TAIL_ANGLE.get();

                boolean localPlayer = trackedPlayer != null
                    && avatar.getUUID().equals(trackedPlayer.getUUID());

                if (localPlayer && rootMotion != null && middleMotion != null && tipMotion != null) {
                    float partialTick = Minecraft.getInstance().getDeltaTracker()
                        .getGameTimeDeltaPartialTick(true);

                    rootRotation = rootMotion.sample(partialTick);
                    middleRotation = middleMotion.sample(partialTick);
                    tipRotation = tipMotion.sample(partialTick);
                    rootAngle = previousRootAngle
                        + (currentRootAngle - previousRootAngle) * partialTick;
                }

                // Always write all segment rotations; render states can be reused.
                state.setRenderData(ROOT_ROTATION, rootRotation);
                state.setRenderData(MIDDLE_ROTATION, middleRotation);
                state.setRenderData(TIP_ROTATION, tipRotation);
                state.setRenderData(TAIL_ANGLE, rootAngle);
                state.setRenderData(LOCAL_TAIL, localPlayer);
            }
        });
    }

    @SubscribeEvent
    public static void registerTailModelReload(AddClientReloadListenersEvent event) {
        // Resource reloads can replace geometry without recompiling the mod.
        event.addListener(FoxTailGeometry.FILE, new FoxTailGeometry.Reload());
    }

    @SubscribeEvent 
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        // Attach a tail layer to each player skin model, including normal and slim.
        for (PlayerModelType type : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> playRenderer = event.getPlayerRenderer(type);
            if (playRenderer != null) {
                playRenderer.addLayer(new FoxTailRenderLayers(playRenderer));
            }
        }
    }

}
