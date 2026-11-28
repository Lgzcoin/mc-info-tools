package com.doubao.infotools;

import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 挂机收益：每分钟比一次背包净增和经验，按小时记账。 */
public final class IncomeStats {
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static Map<String, Integer> last = new LinkedHashMap<>();
    private static int lastXp = -1;
    private static int ticks = 0;
    private static String cachedDay = "";
    private static String cacheLine = "";
    private static long cacheAt = 0L;
    private static String owner = "";

    private IncomeStats() {
    }

    private static Path file() {
        return Files2.path("收益统计.txt");
    }

    public static void tick(Minecraft mc) {
        ticks++;
        if (ticks % 1200 != 0 || mc.player == null) {
            return;
        }
        try {
            String me = Files2.name();
            if (!me.equals(owner)) {
                owner = me;
                last = new LinkedHashMap<>();
                lastXp = -1;
                cacheAt = 0L;
            }
            Map<String, Integer> now = Worth.inventoryCounts(mc);
            int xp = mc.player.totalExperience;
            if (last.isEmpty() && lastXp < 0) {
                last = now;
                lastXp = xp;
                return;
            }
            String hour = LocalDateTime.now().format(HOUR);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : now.entrySet()) {
                int before = last.getOrDefault(e.getKey(), 0);
                int gain = e.getValue() - before;
                if (gain > 0) {
                    sb.append(hour).append('|').append(e.getKey()).append('|').append(gain).append('\n');
                }
            }
            if (xp > lastXp) {
                sb.append(hour).append("|__xp__|").append(xp - lastXp).append('\n');
            }
            if (sb.length() > 0) {
                Files.writeString(file(), sb.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                cachedDay = "";
                cacheAt = 0L;
            }
            last = now;
            lastXp = xp;
        } catch (Throwable ignored) {
        }
    }

    public static void resetBaseline() {
        last = new LinkedHashMap<>();
        lastXp = -1;
    }

    public static String summary() {
        List<Row> rows = readToday();
        if (rows.isEmpty()) {
            return "今天还没有收益记录。挂机/挖矿一分钟后再看。";
        }
        Map<String, Integer> items = new LinkedHashMap<>();
        int xp = 0;
        for (Row r : rows) {
            if ("__xp__".equals(r.item)) {
                xp += r.count;
            } else {
                items.merge(r.item, r.count, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> top = new ArrayList<>(items.entrySet());
        top.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> -e.getValue()));
        StringBuilder sb = new StringBuilder("今日收益：");
        int total = items.values().stream().mapToInt(Integer::intValue).sum();
        sb.append("物品净增 ").append(total).append(" 件，经验 +").append(xp);
        for (int i = 0; i < Math.min(6, top.size()); i++) {
            sb.append('\n').append(Names.of(top.get(i).getKey())).append(" +").append(top.get(i).getValue());
        }
        sb.append("\n只算背包净增（从箱子取东西也算），按分钟采样。");
        return sb.toString();
    }

    public static String dayLine(Minecraft mc) {
        try {
            String today = LocalDateTime.now().format(DAY);
            long now = System.currentTimeMillis();
            if (today.equals(cachedDay) && now - cacheAt < 30_000L) {
                return cacheLine;
            }
            List<Row> rows = readToday();
            int total = 0;
            int xp = 0;
            for (Row r : rows) {
                if ("__xp__".equals(r.item)) {
                    xp += r.count;
                } else {
                    total += r.count;
                }
            }
            cachedDay = today;
            cacheAt = now;
            cacheLine = "今日 +" + total + " 件 XP+" + xp;
            return cacheLine;
        } catch (Throwable t) {
            return "今日 暂无";
        }
    }

    private record Row(String hour, String item, int count) {
    }

    private static List<Row> readToday() {
        List<Row> out = new ArrayList<>();
        String today = LocalDateTime.now().format(DAY);
        try {
            Path f = file();
            if (!Files.exists(f)) {
                return out;
            }
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String[] p = raw.trim().split("\\|", 3);
                if (p.length < 3 || !p[0].startsWith(today)) {
                    continue;
                }
                try {
                    out.add(new Row(p[0], p[1], Integer.parseInt(p[2])));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static String clear() {
        try {
            Files.deleteIfExists(file());
        } catch (Throwable ignored) {
        }
        cachedDay = "";
        return "本号收益统计已清空。";
    }
}
