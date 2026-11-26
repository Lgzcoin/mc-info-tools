package com.doubao.infotools;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class InfoKeys {
    public static KeyMapping hud;
    public static KeyMapping look;

    private InfoKeys() {
    }

    public static void register() {
        hud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.infotools.hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                KeyMapping.Category.MISC));
        look = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.infotools.look",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KeyMapping.Category.MISC));
    }

    public static void tick(Minecraft mc) {
        if (hud != null) {
            while (hud.consumeClick()) {
                Hud.toggle();
            }
        }
        if (look == null) {
            return;
        }
        // 界面里的按键由 ScreenKeyboardEvents 处理；这里只处理游戏世界里的 V。
        if (mc.gui.screen() != null) {
            while (look.consumeClick()) {
                // 丢弃界面里按下的记录，避免关界面后补触发
            }
            return;
        }
        while (look.consumeClick()) {
            LookCard.trigger(mc, null);
        }
    }
}
