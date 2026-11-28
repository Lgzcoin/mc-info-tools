package com.doubao.infotools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产日报：定时把「背包 + 仓库索引.txt 里的箱子」加起来，和上一次比，
 * 变化写进 资产变动.txt，当天汇总写进 资产日报.txt。
 * 只读，不动背包。
 */
public final class AssetLedger {
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, Integer> LAST = new LinkedHashMap<>();
    private static final Map<String, Integer> GAIN = new LinkedHashMap<>();
    private static final Map<String, Integer> LOSS = new LinkedHashMap<>();
    private static String day = "";
    private static long lastSnapMs = 0;
    private static int ticks = 0;
    private static boolean baselineDone = false;

    private AssetLedger() {
    }

    private static Path gameFile(String name) {
        return Files2.path(name);
    }

    private static boolean loaded = false;
    private static String owner = "";

    public static void load() {
        if (loaded || Files2.name().equals("未进世界")) {
            return;
        }
        loaded = true;
        try {
            Path f = gameFile("资产今日.txt");
            if (!Files.exists(f)) {
                return;
            }
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|");
                if (p.length >= 2 && p[0].equals("DAY")) {
                    day = p[1];
                } else if (p.length >= 3 && p[0].equals("G")) {
                    GAIN.put(p[1], safeInt(p[2]));
                } else if (p.length >= 3 && p[0].equals("L")) {
                    LOSS.put(p[1], safeInt(p[2]));
                }
            }
            Path totals = gameFile("资产总计.txt");
            if (Files.exists(totals)) {
                for (String raw : Files.readAllLines(totals, StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int i = line.lastIndexOf('=');
                    if (i > 0) {
                        LAST.put(line.substring(0, i), safeInt(line.substring(i + 1)));
                    }
                }
                baselineDone = !LAST.isEmpty();
            }
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("读资产记录失败", t);
        }
    }

    public static void tick(Minecraft mc) {
        ticks++;
        int snapSeconds = Math.max(10, InfoTools.config().assetSnapSeconds);
        if (ticks % (snapSeconds * 20) != 0) {
            return;
        }
        snapshot(mc, false);
    }

    /** 立刻记一笔。 */
    public static void snapshotNow(Minecraft mc) {
        snapshot(mc, true);
    }

    private static void snapshot(Minecraft mc, boolean force) {
        try {
            LocalPlayer p = mc.player;
            if (p == null) {
                return;
            }
            String me = Files2.name();
            if (!me.equals(owner)) {
                owner = me;
                LAST.clear();
                GAIN.clear();
                LOSS.clear();
                day = "";
                baselineDone = false;
                loaded = false;
            }
            if (!loaded) {
                load();
            }
            if (!force && ticks == 0) {
                return;
            }
            rolloverIfNeeded();

            Map<String, Integer> totals = new LinkedHashMap<>();
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st == null || st.isEmpty()) {
                    continue;
                }
                totals.merge(idOf(st), st.getCount(), Integer::sum);
            }
            for (Map.Entry<String, Integer> e : chestTotals().entrySet()) {
                totals.merge(e.getKey(), e.getValue(), Integer::sum);
            }

            List<String> lines = new ArrayList<>();
            if (!baselineDone) {
                baselineDone = true;
            } else {
                java.util.Set<String> keys = new java.util.TreeSet<>();
                keys.addAll(LAST.keySet());
                keys.addAll(totals.keySet());
                for (String k : keys) {
                    int before = LAST.getOrDefault(k, 0);
                    int after = totals.getOrDefault(k, 0);
                    int d = after - before;
                    if (d == 0) {
                        continue;
                    }
                    if (d > 0) {
                        GAIN.merge(k, d, Integer::sum);
                    } else {
                        LOSS.merge(k, -d, Integer::sum);
                    }
                    lines.add(LocalDateTime.now().format(FULL) + "|" + (d > 0 ? "+" : "") + d + "|" + k
                            + "|剩余 " + after);
                }
            }

            LAST.clear();
            LAST.putAll(totals);
            lastSnapMs = System.currentTimeMillis();

            if (!lines.isEmpty()) {
                append(gameFile("资产变动.txt"), String.join("\n", lines) + "\n");
            }
            saveTotals();
            saveToday();
            writeDaily();

            if (force) {
                Notify.chat("已记一笔资产：" + kinds(totals) + " 种 / " + total(totals) + " 个");
            }
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("资产快照失败（已忽略）", t);
        }
    }

    private static void rolloverIfNeeded() {
        String now = LocalDate.now().toString();
        if (now.equals(day)) {
            return;
        }
        if (!day.isEmpty()) {
            archiveDay();
        }
        day = now;
        GAIN.clear();
        LOSS.clear();
    }

    private static void archiveDay() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("==== ").append(day).append(" ====\n");
            sb.append("收入：").append(top(GAIN, 8, "+")).append('\n');
            sb.append("支出：").append(top(LOSS, 8, "-")).append("\n\n");
            append(gameFile("资产历史.txt"), sb.toString());
        } catch (Throwable ignored) {
        }
    }

    private static void writeDaily() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# 资产日报 ").append(day).append('\n');
            sb.append("# 收入 = 这段时间新增；支出 = 减少（存进没被索引的箱子也会显示成支出）\n");
            sb.append("收入：").append(top(GAIN, 12, "+")).append('\n');
            sb.append("支出：").append(top(LOSS, 12, "-")).append('\n');
            Files.writeString(gameFile("资产日报.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static void saveTotals() {
        try {
            StringBuilder sb = new StringBuilder("# 资产总计（背包+仓库索引）\n");
            for (Map.Entry<String, Integer> e : LAST.entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
            Files.writeString(gameFile("资产总计.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static void saveToday() {
        try {
            StringBuilder sb = new StringBuilder("# 今日增减，重启后接着算\n");
            sb.append("DAY|").append(day).append('\n');
            for (Map.Entry<String, Integer> e : GAIN.entrySet()) {
                sb.append("G|").append(e.getKey()).append('|').append(e.getValue()).append('\n');
            }
            for (Map.Entry<String, Integer> e : LOSS.entrySet()) {
                sb.append("L|").append(e.getKey()).append('|').append(e.getValue()).append('\n');
            }
            Files.writeString(gameFile("资产今日.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    /** 读 autominer 写的 仓库索引.txt，把箱子里的东西也算进总资产。 */
    private static Map<String, Integer> chestTotals() {
        Map<String, Integer> map = new LinkedHashMap<>();
        try {
            Path f = gameFile("仓库索引.txt");
            if (!Files.exists(f)) {
                return map;
            }
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (!line.startsWith("CONTAINER|")) {
                    continue;
                }
                String[] p = line.split("\\|", -1);
                if (p.length < 5 || p[4].isBlank()) {
                    continue;
                }
                for (String pair : p[4].split(",")) {
                    int eq = pair.lastIndexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String item = pair.substring(0, eq).trim();
                    int n = safeInt(pair.substring(eq + 1));
                    if (n > 0) {
                        map.merge(item, n, Integer::sum);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    public static String summary() {
        if (LAST.isEmpty()) {
            return "还没有资产快照。等 " + InfoTools.config().assetSnapSeconds + " 秒，或敲 /info assets snap 立刻记一笔。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("资产（背包+仓库索引）：").append(kinds(LAST)).append(" 种 / ").append(total(LAST)).append(" 个");
        if (lastSnapMs > 0) {
            sb.append("，最近快照 ").append(formatTime(lastSnapMs));
        }
        sb.append('\n');
        sb.append("今日收入：").append(top(GAIN, 6, "+")).append('\n');
        sb.append("今日支出：").append(top(LOSS, 6, "-"));
        if (GAIN.isEmpty() && LOSS.isEmpty()) {
            sb.append("（还没变化）");
        }
        return sb.toString();
    }

    private static String formatTime(long ms) {
        try {
            return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms),
                    java.time.ZoneId.systemDefault()).format(HMS);
        } catch (Throwable t) {
            return "?";
        }
    }

    public static String logTail() {
        try {
            Path f = gameFile("资产变动.txt");
            if (!Files.exists(f)) {
                return "还没有变动记录。";
            }
            List<String> all = Files.readAllLines(f, StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - 12);
            StringBuilder sb = new StringBuilder("最近 ").append(all.size() - from).append(" 条变动：");
            for (int i = from; i < all.size(); i++) {
                String[] p = all.get(i).split("\\|");
                if (p.length >= 3) {
                    String t = p[0].length() >= 19 ? p[0].substring(11, 19) : p[0];
                    sb.append('\n').append(t).append("  ")
                            .append(p[1]).append(' ').append(Names.of(p[2]))
                            .append("（").append(p.length > 3 ? p[3] : "").append("）");
                } else {
                    sb.append('\n').append(all.get(i));
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "读 资产变动.txt 失败：" + t.getClass().getSimpleName();
        }
    }

    public static void resetToday() {
        GAIN.clear();
        LOSS.clear();
        saveToday();
        writeDaily();
    }

    public static String line() {
        return "资产 " + kinds(LAST) + "种/" + total(LAST) + "个，今日 +" + sum(GAIN) + " / -" + sum(LOSS);
    }

    private static int sum(Map<String, Integer> m) {
        int s = 0;
        for (int v : m.values()) {
            s += v;
        }
        return s;
    }

    private static String kinds(Map<String, Integer> m) {
        return Integer.toString(m.size());
    }

    private static int total(Map<String, Integer> m) {
        int s = 0;
        for (int v : m.values()) {
            s += v;
        }
        return s;
    }

    private static String top(Map<String, Integer> m, int n, String sign) {
        if (m.isEmpty()) {
            return "无";
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(m.entrySet());
        list.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(n, list.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(Names.of(list.get(i).getKey())).append(' ').append(sign)
                    .append(list.get(i).getValue());
        }
        if (list.size() > shown) {
            sb.append(" …共 ").append(list.size()).append(" 种");
        }
        return sb.toString();
    }

    private static String idOf(ItemStack st) {
        String id = ItemIds.of(st);
        return id == null ? "unknown" : id;
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void append(Path f, String text) {
        try {
            Files.writeString(f, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }
}
