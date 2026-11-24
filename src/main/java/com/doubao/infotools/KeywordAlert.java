package com.doubao.infotools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 关键词告警：聊天/系统消息里出现配置的关键词，就在聊天框报一行 + 响一声 + 记 告警记录.txt。
 */
public final class KeywordAlert {
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, Long> RECENT = new HashMap<>();

    private KeywordAlert() {
    }

    private static Path file() {
        return Files2.path("告警记录.txt");
    }

    public static boolean enabled() {
        return InfoTools.config().alertEnabled;
    }

    public static void setEnabled(boolean on) {
        InfoTools.config().alertEnabled = on;
        InfoTools.saveConfig();
    }

    public static void onMessage(String text) {
        if (!enabled()) {
            return;
        }
        if (text == null || text.isBlank()) {
            return;
        }
        if (text.startsWith("[信息]") || text.startsWith("[告警]")) {
            return;
        }
        String low = text.toLowerCase(Locale.ROOT);
        for (String kw : InfoTools.config().keywords) {
            if (kw.isBlank()) {
                continue;
            }
            if (low.contains(kw.toLowerCase(Locale.ROOT))) {
                hit(kw, text);
                return;
            }
        }
    }

    /** 手动测试，不受开关限制。 */
    public static String test(String text) {
        String low = text.toLowerCase(Locale.ROOT);
        for (String kw : InfoTools.config().keywords) {
            if (!kw.isBlank() && low.contains(kw.toLowerCase(Locale.ROOT))) {
                hit(kw, text, true);
                return "命中「" + kw + "」：模板没问题，真的出现就会这样提示。";
            }
        }
        return "这段话没命中任何关键词。当前关键词：" + String.join("、", InfoTools.config().keywords);
    }

    private static void hit(String kw, String text) {
        hit(kw, text, false);
    }

    private static void hit(String kw, String text, boolean force) {
        long now = System.currentTimeMillis();
        String key = kw + "|" + text;
        Long last = RECENT.get(key);
        if (!force && last != null && now - last < 3000) {
            return;
        }
        RECENT.put(key, now);
        String shortText = text.length() > 90 ? text.substring(0, 90) + "…" : text;
        Notify.alert("命中「" + kw + "」：" + shortText);
        try {
            String line = LocalDateTime.now().format(FULL) + "|" + kw + "|" + text + "\n";
            Files.writeString(file(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }

    public static void tick(net.minecraft.client.Minecraft mc) {
        if (RECENT.size() > 200) {
            long now = System.currentTimeMillis();
            RECENT.entrySet().removeIf(e -> now - e.getValue() > 60_000);
        }
    }

    public static String listText() {
        return "关键词告警：" + (enabled() ? "开" : "关") + "，共 " + InfoTools.config().keywords.size()
                + " 个：" + String.join("、", InfoTools.config().keywords);
    }

    public static String add(String kw) {
        if (kw == null || kw.isBlank()) {
            return "关键词不能为空。";
        }
        if (InfoTools.config().keywords.contains(kw)) {
            return "「" + kw + "」已经在列表里了。";
        }
        InfoTools.config().keywords.add(kw);
        InfoTools.saveConfig();
        return "已加入「" + kw + "」，现在聊到就会提醒。";
    }

    public static String remove(String kw) {
        if (InfoTools.config().keywords.remove(kw)) {
            InfoTools.saveConfig();
            return "已移除「" + kw + "」。";
        }
        return "列表里没有「" + kw + "」。";
    }

    public static String line() {
        return (enabled() ? "开" : "关") + "（" + InfoTools.config().keywords.size() + " 个词）";
    }
}
