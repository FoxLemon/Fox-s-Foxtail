package net.foxlemon.foxsfoxtail;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;

// NeoForge's entry point. The mod ID must match neoforge.mods.toml and the assets namespace.
@Mod(FoxsFoxTail.MODID)
public class FoxsFoxTail {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "foxsfoxtail";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

}
