package net.foxlemon.foxsfoxtail.client;

import net.foxlemon.foxsfoxtail.FoxsFoxTail;
import net.foxlemon.foxsfoxtail.FoxTailConfig;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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

// Load client setup here and automatically subscribe the static event handlers below.
@Mod(value = FoxsFoxTail.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = FoxsFoxTail.MODID, value = Dist.CLIENT)
public class FoxTailClient {

    // Render-data keys carry values to FoxTailModel.setupAnim(); they do not move parts themselves.
    // The vector contains angular offsets in radians: X twist, Y/Z bend, not a position.
    public static final ContextKey<Vec3> MIDDLE_ROTATION =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "middle_rotation"));

    public static final ContextKey<Vec3> TIP_ROTATION =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "tip_rotation"));
    // Root elevation is stored separately in degrees; the model converts it to radians.
    public static final ContextKey<Double> TAIL_ANGLE =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "tail_angle"));
    
    public  static final ContextKey<Vec3> TAIL_AVOIDANCE = 
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "tail_avoidance"));
    // Marks the local player's render state so other players cannot drive our spring.
    private static final ContextKey<Boolean> LOCAL_TAIL =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "local_tail"));

    // This initial test simulates only the local player's tail.
    private static AbstractClientPlayer trackedPlayer;

    private static TailPhysics middleSpring;
    private static TailPhysics tipSpring;

    // Keep two tick snapshots so drawing can interpolate smoothly between them.
    private static Vec3 previousVelocity = Vec3.ZERO;

    private static Vec3 previousMiddleRotation = Vec3.ZERO;
    private static Vec3 currentMiddleRotation = Vec3.ZERO;
    private static Vec3 previousTipRotation = Vec3.ZERO;
    private static Vec3 currentTipRotation = Vec3.ZERO;

    // Torso roll samples from the render layer, in radians. These are not root-angle samples.
    private static double previousBaseRoll, latestBaseRoll;
    private static boolean hasBaseRoll;
    private static int physicsTick, lastRollTick;

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

        if (player == null) {
            // Discard old motion when leaving the world.
            trackedPlayer = null;
            middleSpring = tipSpring = null;
            previousMiddleRotation = currentMiddleRotation = Vec3.ZERO;
            previousTipRotation = currentTipRotation = Vec3.ZERO;
            previousVelocity = Vec3.ZERO;
            hasBaseRoll = false;
            return;
        }

        // Initialize again after joining a world or re-spawning.
        if (player != trackedPlayer) {
            trackedPlayer = player;
            middleSpring = new TailPhysics(
                FoxTailConfig.FREQUENCY.get().floatValue(),
                FoxTailConfig.DAMPING.get().floatValue(),
                FoxTailConfig.RESPONSE.get().floatValue(), Vec3.ZERO);
            tipSpring = new TailPhysics(
                FoxTailConfig.FREQUENCY.get().floatValue(),
                FoxTailConfig.DAMPING.get().floatValue(),
                FoxTailConfig.RESPONSE.get().floatValue(), Vec3.ZERO);
            previousMiddleRotation = currentMiddleRotation = Vec3.ZERO;
            previousTipRotation = currentTipRotation = Vec3.ZERO;
            previousVelocity = player.getDeltaMovement();
            hasBaseRoll = false;
        }

        if (minecraft.isPaused()) {
            return;
        }
        physicsTick++;

        Vec3 currentVelocity = player.getDeltaMovement();
        // A change in velocity drives the bend; constant velocity adds no new acceleration.
        Vec3 acceleration = currentVelocity.subtract(previousVelocity);
        previousVelocity = currentVelocity;

        // Body yaw is in degrees; trigonometry uses radians.
        double yaw = Math.toRadians(player.yBodyRot);

        // Approximate tail-local acceleration using body yaw and the layer's -90-degree Y turn.
        // This does not yet account for torso tilt or the configured root elevation.
        double localY = -acceleration.y;
        double localZ = acceleration.x * Math.cos(yaw) + acceleration.z * Math.sin(yaw);

        // Strength scales the driving motion, not spring stiffness (controlled by frequency).
        double strength = FoxTailConfig.MOVEMENT_STRENGTH.get();
        double bendY = localZ * strength;
        double bendZ = -localY * strength;
        double twistX = 0.0;
        
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

        // Config stores degrees; the model and physics use radians.
        double maxBend = Math.toRadians(FoxTailConfig.MAX_BEND.get());
        // Clamp the target only; the spring's resulting rotation can still overshoot it.
        bendY = Math.max(-maxBend, Math.min(maxBend, bendY));
        bendZ = Math.max(-maxBend, Math.min(maxBend, bendZ));
        twistX = Math.max(-maxBend, Math.min(maxBend, twistX));

        Vec3 target = new Vec3(twistX, bendY, bendZ);
        // Root-angle changes are not yet fed into this target to produce segment lag.

        // Assign the returned vectors to the stored snapshots, not helper parameters.
        // Both springs currently receive the same target and settings.
        previousMiddleRotation = currentMiddleRotation;
        previousTipRotation = currentTipRotation;
        currentMiddleRotation = physicsTick(middleSpring, target);
        currentTipRotation = physicsTick(tipSpring, target);
    }

    private static Vec3 physicsTick(TailPhysics spring, Vec3 target) {
        // Apply config changes without resetting the spring's motion.
        spring.configure(
            FoxTailConfig.FREQUENCY.get().floatValue(),
            FoxTailConfig.DAMPING.get().floatValue(),
            FoxTailConfig.RESPONSE.get().floatValue());
        // One normal 20 Hz game tick is 0.05 seconds. setupAnim only applies the result.
        return spring.Update(0.05f, target);
    }

    public static void disablePhysicsRecording(AvatarRenderState state) {
        // The settings preview must not feed its artificial pose back into gameplay physics.
        state.setRenderData(LOCAL_TAIL, false);
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
                Vec3 middleRotation = Vec3.ZERO;
                Vec3 tipRotation = Vec3.ZERO;

                boolean localPlayer = trackedPlayer != null
                    && avatar.getUUID().equals(trackedPlayer.getUUID());

                if (localPlayer) {
                    float partialTick = Minecraft.getInstance().getDeltaTracker()
                        .getGameTimeDeltaPartialTick(true);

                    middleRotation = previousMiddleRotation.lerp(
                        currentMiddleRotation, partialTick);

                    tipRotation = previousTipRotation.lerp(
                        currentTipRotation, partialTick);
                }

                // Always write both, including zero values for other players.
                state.setRenderData(MIDDLE_ROTATION, middleRotation);
                state.setRenderData(TIP_ROTATION, tipRotation);
                state.setRenderData(TAIL_ANGLE, FoxTailConfig.TAIL_ANGLE.get());
                state.setRenderData(LOCAL_TAIL, localPlayer);
            }
        });
    }

    @SubscribeEvent 
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // Register the geometry recipe; :: passes the method to be called later.
        event.registerLayerDefinition(FoxTailModel.MY_LAYER, FoxTailModel::createBodyLayer);
    }

    @SubscribeEvent 
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        // Attach a tail layer to each player skin model, including normal and slim.
        for (PlayerModelType type : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> playRenderer = event.getPlayerRenderer(type);
            if (playRenderer != null) {
                playRenderer.addLayer(new FoxTailRenderLayers(playRenderer,event.getEntityModels()));
            }
        }
    }

}
