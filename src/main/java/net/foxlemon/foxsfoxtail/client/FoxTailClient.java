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

    // Key used to pass the calculated rotation into the model's render state.
    public static final ContextKey<Vec3> TAIL_ROTATION =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "tail_rotation"));
    public static final ContextKey<Double> TAIL_ANGLE =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "tail_angle"));
    private static final ContextKey<Boolean> LOCAL_TAIL =
        new ContextKey<>(Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "local_tail"));

    // This initial test simulates only the local player's tail.
    private static AbstractClientPlayer trackedPlayer;
    private static TailPhysics middleSpring;
    private static Vec3 previousRotation = Vec3.ZERO;
    private static Vec3 currentRotation = Vec3.ZERO;
    private static Vec3 previousVelocity = Vec3.ZERO;
    private static double previousBaseRoll, latestBaseRoll;
    private static boolean hasBaseRoll;
    private static int physicsTick, lastRollTick;

    
    public FoxTailClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, FoxTailConfig.SPEC);
        // Open our slider screen from the Mods menu.
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (modContainer, parent) -> new TailSettingsScreen(parent));
    }

    @SubscribeEvent
    public static void tickTail(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;

        if (player == null) {
            trackedPlayer = null;
            middleSpring = null;
            previousRotation = currentRotation = Vec3.ZERO;
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
            previousRotation = currentRotation = Vec3.ZERO;
            previousVelocity = player.getDeltaMovement();
            hasBaseRoll = false;
        }

        if (minecraft.isPaused()) {
            return;
        }
        physicsTick++;

        Vec3 currentVelocity = player.getDeltaMovement();
        Vec3 acceleration = currentVelocity.subtract(previousVelocity);
        previousVelocity = currentVelocity;

        // Body yaw is in degrees; trigonometry uses radians.
        double yaw = Math.toRadians(player.yBodyRot);

        // Convert world acceleration into your tail's local coordinates.
        // These directions include your layer's -90-degree Y rotation.
        double localY = -acceleration.y;
        double localZ = acceleration.x * Math.cos(yaw) + acceleration.z * Math.sin(yaw);

        // Bend opposite the acceleration.
        double strength = FoxTailConfig.MOVEMENT_STRENGTH.get();
        double bendY = localZ * strength;
        double bendZ = -localY * strength;
        double twistX = 0.0;
        
        if (hasBaseRoll && physicsTick - lastRollTick <= 2) {
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
        bendY = Math.max(-maxBend, Math.min(maxBend, bendY));
        bendZ = Math.max(-maxBend, Math.min(maxBend, bendZ));
        twistX = Math.max(-maxBend, Math.min(maxBend, twistX));

        Vec3 target = new Vec3(twistX, bendY, bendZ);

        previousRotation = currentRotation;
        // Apply config changes without resetting the spring's motion.
        middleSpring.configure(
            FoxTailConfig.FREQUENCY.get().floatValue(),
            FoxTailConfig.DAMPING.get().floatValue(),
            FoxTailConfig.RESPONSE.get().floatValue());
        currentRotation = middleSpring.Update(0.05f, target);
    }

    public static void disablePhysicsRecording(AvatarRenderState state) {
        state.setRenderData(LOCAL_TAIL, false);
    }

    // Rendering only supplies the latest pose; tickTail advances the spring.
    public static void recordBaseRoll(AvatarRenderState state, double roll) {
        if (!state.getRenderDataOrDefault(LOCAL_TAIL, false)
                || Minecraft.getInstance().isPaused() || !Double.isFinite(roll)) {
            return;
        }
        if (!hasBaseRoll || physicsTick - lastRollTick > 2) {
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

                Vec3 rotation = Vec3.ZERO;
                var minecraft = Minecraft.getInstance();

                if (trackedPlayer != null && avatar.getUUID().equals(trackedPlayer.getUUID())) {
                    float partialTick = minecraft.getDeltaTracker()
                        .getGameTimeDeltaPartialTick(true);

                    rotation = previousRotation.lerp(currentRotation, partialTick);
                }

                // Always set a value: render states can be reused.
                state.setRenderData(TAIL_ROTATION, rotation);
                state.setRenderData(TAIL_ANGLE, FoxTailConfig.TAIL_ANGLE.get());
                state.setRenderData(LOCAL_TAIL,
                    trackedPlayer != null && avatar.getUUID().equals(trackedPlayer.getUUID()));
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
