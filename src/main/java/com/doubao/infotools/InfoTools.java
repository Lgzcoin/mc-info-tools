package com.doubao.infotools;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 信息工具：只采集、只显示，不改背包、不动方块、不需要 OP。
 * 三块：资产日报 / 掉落物倒计时与死亡点 / 聊天关键词告警。
 */
public class InfoTools implements ClientModInitializer {
    public static final String MOD_ID = "infotools";
    public static final String MOD_VERSION = "1.5.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static InfoConfig config;

    public static InfoConfig config() {
        return config;
    }

    public static void reloadConfig() {
        config = InfoConfig.load();
    }

    public static void saveConfig() {
        if (config != null) {
            try {
                config.save();
            } catch (Throwable t) {
                LOGGER.warn("保存配置失败", t);
            }
        }
    }

    @Override
    public void onInitializeClient() {
        reloadConfig();
        AssetLedger.load();
        DeathTracker.load();
        InfoCommands.register();
        InfoKeys.register();
        LookCard.init();
        Hud.init();
        // 左上角悬浮信息板：不用敲命令
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                (graphics, delta) -> Hud.render(graphics));
        // 按键速查卡（V）：独立于左上角信息板，按 I 隐藏信息板也仍然显示
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "look"),
                (graphics, delta) -> {
                    if (Minecraft.getInstance().gui.screen() == null) {
                        LookCard.render(graphics);
                    }
                });

        // 聊天与系统消息都看一眼，服务器把聊天当系统消息发也能命中
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) {
                return;
            }
            onChat(safeText(message), "系统");
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
                onChat(safeText(message), senderName(sender)));

        ClientTickEvents.END_CLIENT_TICK.register(InfoTools::tick);

        LOGGER.info("信息工具 v{} 已加载：游戏里敲 /info 看状态", MOD_VERSION);
    }

    private static void onChat(String text, String sender) {
        KeywordAlert.onMessage(text);
        Market.onMessage(text);
        ShopAd.onMessage(text, sender);
    }

    private static String senderName(com.mojang.authlib.GameProfile profile) {
        try {
            if (profile != null && profile.name() != null) {
                return profile.name();
            }
        } catch (Throwable ignored) {
        }
        return "玩家";
    }

    private static String safeText(net.minecraft.network.chat.Component c) {
        try {
            return c == null ? "" : c.getString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void tick(Minecraft mc) {
        try {
            AssetLedger.tick(mc);
            DeathTracker.tick(mc);
            DropTracker.tick(mc);
            KeywordAlert.tick(mc);
            SignShop.tick(mc);
            InfoStatusWriter.tick(mc);
            InfoKeys.tick(mc);
            Hud.tick(mc);
        } catch (Throwable t) {
            LOGGER.warn("tick 出错（已忽略，不影响游戏）", t);
        }
    }
}
