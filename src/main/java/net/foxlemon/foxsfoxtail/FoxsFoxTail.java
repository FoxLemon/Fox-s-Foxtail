package net.foxlemon.foxsfoxtail;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

// NeoForge's entry point. The mod ID must match neoforge.mods.toml and the assets namespace.
@Mod(FoxsFoxTail.MODID)
public class FoxsFoxTail {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "foxsfoxtail";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public FoxsFoxTail(IEventBus modEventBus, ModContainer modContainer) {
        // NeoForge supplies the event bus and container when loading the mod.
        // Rendering is registered by FoxTailClient.
        // Register configuration here once FoxTailConfig is implemented.
    }

}
