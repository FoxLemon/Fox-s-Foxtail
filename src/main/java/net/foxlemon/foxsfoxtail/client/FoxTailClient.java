package net.foxlemon.foxsfoxtail.client;

import net.foxlemon.foxsfoxtail.FoxsFoxTail;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Load client setup here and automatically subscribe the static event handlers below.
@Mod(value = FoxsFoxTail.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = FoxsFoxTail.MODID, value = Dist.CLIENT)
public class FoxTailClient {

    public FoxTailClient(ModContainer container) {
        // Provide NeoForge's standard config screen for any registered settings.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // TODO: Implement onClientSetup
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
