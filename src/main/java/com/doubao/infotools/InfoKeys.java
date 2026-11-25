package com.doubao.infotools;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class InfoKeys {
    public static KeyMapping hud;

    private InfoKeys() {
    }

    public static void register() {
        hud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.infotools.hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                KeyMapping.Category.MISC));
    }

    public static void tick() {
        if (hud == null) {
            return;
        }
        while (hud.consumeClick()) {
            Hud.toggle();
        }
    }
}
