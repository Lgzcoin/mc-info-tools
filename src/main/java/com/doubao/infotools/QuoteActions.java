package com.doubao.infotools;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.Map;

/** 报价快捷回复：聊天里识别到报价后，补一行可点击的「/msg 对方 ...」，点一下复制。 */
public final class QuoteActions {
    private static final Map<String, Long> LAST = new HashMap<>();
    private static String lastCommand = "";

    private QuoteActions() {
    }

    public static void onMessage(String text, String sender) {
        try {
            if (!InfoTools.config().quoteCopyEnabled || text == null || text.isBlank()
                    || sender == null || sender.isBlank() || "系统".equals(sender)) {
                return;
            }
            PriceParser.Quote q = PriceParser.parse(text);
            if (q == null) {
                return;
            }
            long now = System.currentTimeMillis();
            String key = sender + "|" + q.itemId() + "|" + q.direction();
            Long last = LAST.get(key);
            if (last != null && now - last < 120_000) {
                return;
            }
            LAST.put(key, now);
            if (LAST.size() > 200) {
                LAST.entrySet().removeIf(e -> now - e.getValue() > 600_000);
            }
            String body = q.raw().replace("\n", " ").replace("\r", " ");
            if (body.length() > 90) {
                body = body.substring(0, 90) + "…";
            }
            String command = "/msg " + sender + " " + body;
            lastCommand = command;
            Component line = Component.literal("[报价] 点这里复制回复 ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(command).withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.CopyToClipboard(command))));
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null && mc.gui.hud != null) {
                mc.gui.hud.getChat().addClientSystemMessage(line);
            }
        } catch (Throwable ignored) {
        }
    }

    public static String lastText() {
        return lastCommand.isEmpty() ? "还没有识别到可回复的报价。" : "上次报价回复：\n" + lastCommand;
    }

    public static String copyLast() {
        if (lastCommand.isEmpty()) {
            return "还没有识别到可回复的报价。";
        }
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(lastCommand);
            return "已复制到剪贴板：" + lastCommand;
        } catch (Throwable t) {
            return "复制失败，手动复制：" + lastCommand;
        }
    }

    public static String clear() {
        lastCommand = "";
        LAST.clear();
        return "报价回复缓存已清空。";
    }
}
