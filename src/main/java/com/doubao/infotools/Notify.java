package com.doubao.infotools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** 聊天提示 + 提示音，全部 try/catch，出错不影响游戏。 */
public final class Notify {
    private Notify() {
    }

    public static void chat(String text) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("[信息] " + text));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void chatRaw(String text) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(text));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void ping() {
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 0.8f));
        } catch (Throwable ignored) {
        }
    }

    public static void alert(String text) {
        chat(text);
        ping();
    }
}
