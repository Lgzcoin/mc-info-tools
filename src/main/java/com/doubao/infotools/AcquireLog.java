package com.doubao.infotools;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天收购记录：谁在收、收什么、收多少。
 *
 * 触发词默认是「收 / 求购 / 收购」。数量支持：
 * 1w / 1W / w / W = 10000，1k / 1K = 1000，2组 = 128 个，1盒 = 1728 个，
 * one = 1。记录写到 信息工具/<玩家名>/收购记录.txt，/info buy log 查看。
 */
public final class AcquireLog {
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern QTY = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(万|[wWkK千]|组|盒|个|颗|块|份|one)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern STANDALONE_W = Pattern.compile("(?<![A-Za-z0-9])[wW](?![A-Za-z0-9])");
    private static final Pattern ONE = Pattern.compile("(?<![A-Za-z0-9])one(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_PREFIX = Pattern.compile(
            "^\\s*(?:<([^>]{2,16})>|\\[([^\\]]{2,16})\\]|([A-Za-z0-9_]{3,16})\\s*[:：])");
    private static final Map<String, Long> RECENT = new HashMap<>();
    private static final int MAX_RECORDS = 3000;

    private AcquireLog() {
    }

    public record Rec(long time, String who, String itemId, String itemName,
                      String qtyText, double qtyValue, String raw) {
    }

    public static void onMessage(String text, String sender) {
        try {
            if (!InfoTools.config().acquireEnabled) {
                return;
            }
            if (text == null || text.isBlank()) {
                return;
            }
            String body = text.replaceAll("§.", "").trim();
            if (body.startsWith("[信息]") || body.startsWith("[告警]")) {
                return;
            }
            if (!looksAcquire(body)) {
                return;
            }
            String who = senderOf(body, sender);
            String content = stripPrefix(body, who);
            String itemId = PriceParser.resolveItem(content);
            String itemName = itemId == null ? unknownItem(content) : Names.of(itemId);
            if (itemName == null || itemName.isBlank()) {
                return;
            }
            Qty qty = quantity(content);
            long now = System.currentTimeMillis();
            String key = who + "|" + content;
            Long last = RECENT.get(key);
            if (last != null && now - last < 600_000) {
                return;
            }
            RECENT.put(key, now);
            if (RECENT.size() > 400) {
                RECENT.entrySet().removeIf(e -> now - e.getValue() > 3_600_000);
            }
            append(now, who, itemId == null ? "" : itemId, itemName, qty, content);
            if (InfoTools.config().acquireAlert) {
                Notify.alert("收购记录：" + who + " 收 " + itemName
                        + (qty.text().isBlank() ? "" : " " + qty.text()));
            }
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("收购记录出错（已忽略）", t);
        }
    }

    private static boolean looksAcquire(String text) {
        if (text.contains("收到") && !text.contains("收购")) {
            return false;
        }
        for (String kw : InfoTools.config().acquireKeywords) {
            if (!kw.isBlank() && text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private static String senderOf(String text, String sender) {
        if (sender != null && !sender.isBlank() && !"系统".equals(sender) && !"玩家".equals(sender)) {
            return cleanName(sender);
        }
        Matcher m = NAME_PREFIX.matcher(text);
        if (m.find()) {
            String n = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
            if (n != null && !n.isBlank()) {
                return cleanName(n);
            }
        }
        return "未知";
    }

    private static String stripPrefix(String text, String who) {
        String s = text;
        if (who != null && !who.isBlank() && !"未知".equals(who)) {
            s = s.replaceFirst("^\\s*<" + Pattern.quote(who) + ">\\s*", "");
            s = s.replaceFirst("^\\s*\\[" + Pattern.quote(who) + "\\]\\s*", "");
            s = s.replaceFirst("^\\s*" + Pattern.quote(who) + "\\s*[:：]\\s*", "");
        }
        return s.trim();
    }

    private static String cleanName(String s) {
        String n = s == null ? "" : s.replaceAll("§.", "").trim();
        if (n.length() > 16) {
            n = n.substring(0, 16);
        }
        return n.isBlank() ? "未知" : n;
    }

    private static String unknownItem(String text) {
        int at = -1;
        for (String kw : InfoTools.config().acquireKeywords) {
            int i = kw.isBlank() ? -1 : text.indexOf(kw);
            if (i >= 0 && (at < 0 || i < at)) {
                at = i;
            }
        }
        if (at < 0) {
            return null;
        }
        String tail = text.substring(at).trim();
        for (String kw : InfoTools.config().acquireKeywords) {
            if (!kw.isBlank() && tail.startsWith(kw)) {
                tail = tail.substring(kw.length()).trim();
                break;
            }
        }
        tail = tail.replaceFirst("^[：:，,、\\s]+", "");
        Matcher m = Pattern.compile("^([^\\d\\s，,。；;！？!?]{1,20})").matcher(tail);
        if (!m.find()) {
            return null;
        }
        String name = m.group(1).trim();
        if (name.isBlank() || name.equals("人") || name.equals("什么") || name.equals("个")) {
            return null;
        }
        return name;
    }

    private record Qty(String text, double value) {
    }

    private static Qty quantity(String text) {
        Matcher m = QTY.matcher(text);
        if (m.find()) {
            String num = m.group(1);
            String unit = m.group(2);
            if (unit == null || unit.isBlank()) {
                return new Qty(num, safe(num));
            }
            String u = unit.toLowerCase(Locale.ROOT);
            double v = safe(num);
            if (u.equals("万") || u.equals("w")) {
                v *= 10000;
            } else if (u.equals("千") || u.equals("k")) {
                v *= 1000;
            } else if (u.equals("组")) {
                v *= 64;
            } else if (u.equals("盒")) {
                v *= 1728;
            } else if (u.equals("one")) {
                v = 1;
            }
            return new Qty(num + unit, v);
        }
        if (STANDALONE_W.matcher(text).find()) {
            return new Qty("w", 10000);
        }
        if (ONE.matcher(text).find()) {
            return new Qty("one", 1);
        }
        return new Qty("", 0);
    }

    private static double safe(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void append(long time, String who, String itemId, String itemName, Qty qty, String rawText) {
        try {
            String raw = (rawText == null || rawText.isBlank())
                    ? (who + " 收 " + itemName + (qty.text().isBlank() ? "" : " " + qty.text()))
                    : rawText;
            String line = time + "|" + who.replace("|", "/") + "|" + itemId + "|"
                    + itemName.replace("|", "/") + "|" + qty.text().replace("|", "/") + "|"
                    + qty.value() + "|" + raw.replace("|", "/") + "\n";
            Path f = Files2.path("收购记录.txt");
            Files.writeString(f, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (Files.size(f) > 600_000) {
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

    private static List<Rec> all() {
        List<Rec> out = new ArrayList<>();
        try {
            Path root = FabricLoader.getInstance().getGameDir().resolve("信息工具");
            if (!Files.exists(root)) {
                return out;
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                for (Path dir : ds) {
                    Path f = dir.resolve("收购记录.txt");
                    if (!Files.isRegularFile(f)) {
                        continue;
                    }
                    for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        Rec r = parseLine(raw);
                        if (r != null) {
                            out.add(r);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Rec parseLine(String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        String[] p = line.split("\\|", 7);
        if (p.length < 6) {
            return null;
        }
        try {
            String itemId = p[2];
            String itemName = p[3];
            String qtyText = p[4];
            double qtyValue = safe(p[5]);
            String text = p.length >= 7 ? p[6] : (p[1] + " 收 " + itemName);
            return new Rec(Long.parseLong(p[0]), p[1], itemId, itemName, qtyText, qtyValue, text);
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<Rec> sorted() {
        List<Rec> list = all();
        list.sort(Comparator.comparingLong(Rec::time).reversed());
        return list;
    }

    public static String recent(int n) {
        List<Rec> list = sorted();
        if (list.isEmpty()) {
            return "还没有收购记录。聊天里出现「收钻石 1w」「求购下界合金锭 2组」会自动记；\n"
                    + "触发词：" + String.join("、", InfoTools.config().acquireKeywords);
        }
        StringBuilder sb = new StringBuilder("收购记录（最近 ").append(Math.min(n, list.size()))
                .append(" 条 / 共 ").append(list.size()).append(" 条）：");
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            Rec r = list.get(i);
            sb.append('\n').append(ago(r.time())).append(" §f").append(r.who).append(" §7收 §f")
                    .append(r.itemName);
            if (!r.qtyText().isBlank()) {
                sb.append(" §e").append(r.qtyText());
            }
        }
        sb.append("\n按人查：/info buy who 玩家名　按物品查：/info buy 钻石");
        return sb.toString();
    }

    public static String byPlayer(String name) {
        if (name == null || name.isBlank()) {
            return recent(15);
        }
        String q = name.toLowerCase(Locale.ROOT);
        List<Rec> list = new ArrayList<>();
        for (Rec r : sorted()) {
            if (r.who().toLowerCase(Locale.ROOT).contains(q)) {
                list.add(r);
            }
        }
        if (list.isEmpty()) {
            return "没有「" + name + "」的收购记录。";
        }
        StringBuilder sb = new StringBuilder(name).append(" 的收购记录 ").append(list.size()).append(" 条：");
        for (int i = 0; i < Math.min(15, list.size()); i++) {
            Rec r = list.get(i);
            sb.append('\n').append(ago(r.time())).append(" 收 ").append(r.itemName());
            if (!r.qtyText().isBlank()) {
                sb.append(" ").append(r.qtyText());
            }
        }
        return sb.toString();
    }

    public static String forItem(String text) {
        String id = PriceParser.resolveItem(text == null ? "" : text);
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        List<Rec> list = new ArrayList<>();
        for (Rec r : sorted()) {
            if ((id != null && id.equals(r.itemId()))
                    || (!q.isBlank() && (r.itemName().toLowerCase(Locale.ROOT).contains(q)
                    || r.raw().toLowerCase(Locale.ROOT).contains(q)))) {
                list.add(r);
            }
        }
        if (list.isEmpty()) {
            return "没有「" + text + "」的收购记录。";
        }
        StringBuilder sb = new StringBuilder("收「").append(text).append("」的人 ").append(list.size()).append(" 条：");
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            Rec r = list.get(i);
            sb.append('\n').append(r.who()).append("  ").append(r.qtyText().isBlank() ? "数量未写" : r.qtyText())
                    .append("  ").append(ago(r.time()));
        }
        return sb.toString();
    }

    public static void clear() {
        try {
            Files.deleteIfExists(Files2.path("收购记录.txt"));
        } catch (Throwable ignored) {
        }
    }

    private static long countAt = 0;
    private static int countCache = -1;

    public static int count() {
        long now = System.currentTimeMillis();
        if (countCache < 0 || now - countAt > 10_000) {
            countAt = now;
            countCache = all().size();
        }
        return countCache;
    }

    public static String line() {
        return count() + " 条";
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
}
