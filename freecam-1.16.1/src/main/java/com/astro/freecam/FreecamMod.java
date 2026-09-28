package com.astro.freecam;

import com.astro.freecam.config.FreecamConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(FreecamMod.MOD_ID)
public final class FreecamMod {
    public static final String MOD_ID = "freecam";

    public FreecamMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FreecamConfig.SPEC, "freecam-client.toml");
    }
}
