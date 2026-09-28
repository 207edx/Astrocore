package com.astro.freecam.client;

import com.astro.freecam.FreecamMod;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = FreecamMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FreecamKeybinds {
    public static KeyBinding TOGGLE;
    public static KeyBinding PLAYER_CONTROL;
    public static KeyBinding RESET_TRIPOD;

    private FreecamKeybinds() {}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        TOGGLE = new KeyBinding("key.freecam.toggle", InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_F4, "key.categories.freecam");
        PLAYER_CONTROL = new KeyBinding("key.freecam.player_control", InputMappings.Type.KEYSYM, -1, "key.categories.freecam");
        RESET_TRIPOD = new KeyBinding("key.freecam.reset_tripod", InputMappings.Type.KEYSYM, -1, "key.categories.freecam");
        ClientRegistry.registerKeyBinding(TOGGLE);
        ClientRegistry.registerKeyBinding(PLAYER_CONTROL);
        ClientRegistry.registerKeyBinding(RESET_TRIPOD);
    }
}
