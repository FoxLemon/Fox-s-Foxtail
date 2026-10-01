package net.foxlemon.foxsfoxtail;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;

// NeoForge's entry point. The mod ID must match neoforge.mods.toml and the assets namespace.
@Mod(FoxsFoxTail.MODID)
public class FoxsFoxTail {

    // Shared namespace for metadata, assets, and client registration.
    public static final String MODID = "foxsfoxtail";
    // Shared logger for messages from the mod.
    public static final Logger LOGGER = LogUtils.getLogger();

}
