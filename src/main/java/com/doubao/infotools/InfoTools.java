package com.doubao.infotools;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 信息工具：只采集、只显示，不改背包、不动方块、不需要 OP。
 * 三块：资产日报 / 掉落物倒计时与死亡点 / 聊天关键词告警。
 */
public class InfoTools implements ClientModInitializer {
    public static final String MOD_ID = "infotools";
    public static final String MOD_VERSION = "1.1.0";
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

        // 聊天与系统消息都看一眼，服务器把聊天当系统消息发也能命中
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) {
                return;
            }
            onChat(safeText(message));
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
                onChat(safeText(message)));

        ClientTickEvents.END_CLIENT_TICK.register(InfoTools::tick);

        LOGGER.info("信息工具 v{} 已加载：游戏里敲 /info 看状态", MOD_VERSION);
    }

    private static void onChat(String text) {
        KeywordAlert.onMessage(text);
        Market.onMessage(text);
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
            InfoStatusWriter.tick(mc);
        } catch (Throwable t) {
            LOGGER.warn("tick 出错（已忽略，不影响游戏）", t);
        }
    }
}
