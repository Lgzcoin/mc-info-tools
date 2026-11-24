package com.doubao.infotools;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商店广告记录：聊天里出现“商店/店铺/促销…”等触发词就记下来，之后用 /info shop 搜索。
 * 不点链接、不自动回复，只做本地索引。
 */
public final class ShopAd {
    private record Ad(long time, String sender, String keyword, String text) {
    }

    private static final Map<String, Long> RECENT = new HashMap<>();
    private static final int MAX_RECORDS = 2000;

    private ShopAd() {
    }

    public static void onMessage(String text, String sender) {
        try {
            if (text == null || text.isBlank() || text.startsWith("[信息]") || text.startsWith("[告警]")) {
                return;
            }
            String body = text.trim();
            if (body.length() < 4 || body.length() > 300) {
                return;
            }
            String hit = null;
            for (String kw : InfoTools.config().shopKeywords) {
                if (!kw.isBlank() && body.contains(kw)) {
                    hit = kw;
                    break;
                }
            }
            if (hit == null) {
                return;
            }
            String who = sender == null || sender.isBlank() ? "系统" : sender;
            long now = System.currentTimeMillis();
            String key = who + "|" + body;
            Long last = RECENT.get(key);
            if (last != null && now - last < 600_000) {
                return;
            }
            RECENT.put(key, now);
            if (RECENT.size() > 300) {
                RECENT.entrySet().removeIf(e -> now - e.getValue() > 3_600_000);
            }
            append(now, who, hit, body);
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("商店广告记录出错（已忽略）", t);
        }
    }

    private static void append(long time, String who, String keyword, String text) {
        try {
            String line = time + "|" + who.replace("|", "/") + "|" + keyword + "|"
                    + text.replace("|", "/").replace("\n", " ").replace("\r", " ") + "\n";
            Path f = Files2.path("商店广告.txt");
            Files.writeString(f, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (Files.size(f) > 400_000) {
                trim(f);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void trim(Path f) {
        try {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            if (lines.size() <= MAX_RECORDS) {
                return;
            }
            List<String> keep = new ArrayList<>(lines.subList(lines.size() - MAX_RECORDS, lines.size()));
            Files.writeString(f, String.join("\n", keep) + "\n", StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static List<Ad> all() {
        List<Ad> out = new ArrayList<>();
        try {
            Path root = FabricLoader.getInstance().getGameDir().resolve("信息工具");
            if (!Files.exists(root)) {
                return out;
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                for (Path dir : ds) {
                    Path f = dir.resolve("商店广告.txt");
                    if (!Files.isRegularFile(f)) {
                        continue;
                    }
                    for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String[] p = raw.trim().split("\\|", 4);
                        if (p.length < 4) {
                            continue;
                        }
                        try {
                            out.add(new Ad(Long.parseLong(p[0]), p[1], p[2], p[3]));
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static String recent() {
        List<Ad> list = sorted();
        if (list.isEmpty()) {
            return "还没有商店广告。触发词：" + String.join("、", InfoTools.config().shopKeywords)
                    + "\n聊天里出现这些词就会记；也可以 /info shop add 词 自己加。";
        }
        StringBuilder sb = new StringBuilder("商店广告 ").append(list.size()).append(" 条，最近：");
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            Ad a = list.get(i);
            sb.append('\n').append(ago(a.time)).append(" [").append(a.sender).append("] ")
                    .append(shorten(a.text, 70));
        }
        sb.append("\n搜别的：/info shop 钻石；触发词：/info shop words");
        return sb.toString();
    }

    public static String search(String query) {
        if (query == null || query.isBlank()) {
            return recent();
        }
        String q = query.toLowerCase();
        List<Ad> hits = new ArrayList<>();
        for (Ad a : sorted()) {
            if (a.text.toLowerCase().contains(q) || a.sender.toLowerCase().contains(q)) {
                hits.add(a);
            }
        }
        if (hits.isEmpty()) {
            return "没搜到含「" + query + "」的商店广告（共 " + all().size() + " 条）。";
        }
        StringBuilder sb = new StringBuilder("含「").append(query).append("」的广告 ").append(hits.size()).append(" 条：");
        for (int i = 0; i < Math.min(10, hits.size()); i++) {
            Ad a = hits.get(i);
            sb.append('\n').append(ago(a.time)).append(" [").append(a.sender).append("] ")
                    .append(shorten(a.text, 80));
        }
        if (hits.size() > 10) {
            sb.append("\n…还有 ").append(hits.size() - 10).append(" 条");
        }
        return sb.toString();
    }

    public static String addKeyword(String kw) {
        if (kw == null || kw.isBlank()) {
            return "触发词不能为空。";
        }
        if (InfoTools.config().shopKeywords.contains(kw)) {
            return "「" + kw + "」已经在触发词里了。";
        }
        InfoTools.config().shopKeywords.add(kw);
        InfoTools.saveConfig();
        return "已加入触发词「" + kw + "」，之后聊天出现就会记录。";
    }

    public static String removeKeyword(String kw) {
        if (InfoTools.config().shopKeywords.remove(kw)) {
            InfoTools.saveConfig();
            return "已移除触发词「" + kw + "」。";
        }
        return "触发词里没有「" + kw + "」。";
    }

    public static String words() {
        return "商店广告触发词（" + InfoTools.config().shopKeywords.size() + " 个）："
                + String.join("、", InfoTools.config().shopKeywords);
    }

    public static void clear() {
        try {
            Files.deleteIfExists(Files2.path("商店广告.txt"));
        } catch (Throwable ignored) {
        }
    }

    public static String line() {
        return all().size() + " 条（触发词 " + InfoTools.config().shopKeywords.size() + " 个）";
    }

    private static List<Ad> sorted() {
        List<Ad> list = all();
        list.sort(Comparator.comparingLong(Ad::time).reversed());
        return list;
    }

    private static String ago(long time) {
        long sec = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (sec < 60) {
            return sec + "秒前";
        }
        if (sec < 3600) {
            return (sec / 60) + "分前";
        }
        if (sec < 86400) {
            return (sec / 3600) + "小时前";
        }
        return (sec / 86400) + "天前";
    }

    private static String shorten(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }
}
