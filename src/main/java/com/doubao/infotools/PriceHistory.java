package com.doubao.infotools;

import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 行情日线：每天第一次进游戏时，把当前行情快照一行行写下来，用来查走势和 7 日均价。 */
public final class PriceHistory {
    private static int ticks = 0;
    private static String lastDate = "";
    private static String owner = "";

    private PriceHistory() {
    }

    private static Path file() {
        return Files2.path("行情日线.txt");
    }

    public static void tick(Minecraft mc) {
        ticks++;
        if (ticks % 600 != 0 || mc.player == null) {
            return;
        }
        try {
            String me = Files2.name();
            if (!me.equals(owner)) {
                owner = me;
                lastDate = "";
            }
            String today = LocalDate.now().toString();
            if (today.equals(lastDate)) {
                return;
            }
            if (hasDate(today)) {
                lastDate = today;
                return;
            }
            Map<String, Market.ItemPrice> prices = Market.allPrices();
            if (prices.isEmpty()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Market.ItemPrice> e : prices.entrySet()) {
                Market.ItemPrice p = e.getValue();
                if (p == null || !p.any()) {
                    continue;
                }
                sb.append(today).append('|').append(e.getKey()).append('|')
                        .append(p.sellMed()).append('|').append(p.buyMed()).append('|')
                        .append(p.sellN()).append('|').append(p.buyN()).append('\n');
            }
            if (sb.length() > 0) {
                Files.writeString(file(), sb.toString(), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            }
            lastDate = today;
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasDate(String date) {
        try {
            Path f = file();
            if (!Files.exists(f)) {
                return false;
            }
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (line.startsWith(date + "|")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static String query(String itemText) {
        String id = Market.resolveItemName(itemText == null ? "" : itemText);
        if (id == null) {
            return "认不出「" + itemText + "」，换个说法（钻石、铁、煤…）。";
        }
        List<Row> rows = read().stream().filter(r -> r.item.equals(id)).toList();
        if (rows.isEmpty()) {
            return Names.of(id) + "：还没有日线记录。每天第一次进游戏会自动记一笔，明天再来看。";
        }
        StringBuilder sb = new StringBuilder(Names.of(id)).append(" 行情日线（最近 ").append(rows.size()).append(" 天）：");
        double sum = 0;
        int n = 0;
        for (int i = Math.max(0, rows.size() - 7); i < rows.size(); i++) {
            Row r = rows.get(i);
            sb.append('\n').append(r.date).append("  卖 ").append(fmt(r.sell))
                    .append("  收 ").append(fmt(r.buy));
            if (r.sell > 0) {
                sum += r.sell;
                n++;
            }
        }
        if (n > 0) {
            double avg = sum / n;
            double latest = rows.get(rows.size() - 1).sell;
            sb.append("\n7 日均卖价：").append(fmt(avg));
            if (latest > 0 && avg > 0) {
                double diff = (latest - avg) / avg * 100;
                sb.append("，最近 ").append(diff >= 0 ? "高 " : "低 ")
                        .append(Math.abs(Math.round(diff))).append("%");
            }
        }
        return sb.toString();
    }

    private record Row(String date, String item, double sell, double buy, int sellN, int buyN) {
    }

    private static List<Row> read() {
        List<Row> out = new ArrayList<>();
        try {
            Path f = file();
            if (!Files.exists(f)) {
                return out;
            }
            Map<String, Row> last = new LinkedHashMap<>();
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String[] p = raw.trim().split("\\|", 6);
                if (p.length < 6) {
                    continue;
                }
                try {
                    Row r = new Row(p[0], p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                            Integer.parseInt(p[4]), Integer.parseInt(p[5]));
                    last.put(r.date + "|" + r.item, r);
                } catch (Throwable ignored) {
                }
            }
            out.addAll(last.values());
            out.sort((a, b) -> a.date.compareTo(b.date));
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static int historyCount() {
        return read().size();
    }

    public static String clear() {
        try {
            Files.deleteIfExists(file());
        } catch (Throwable ignored) {
        }
        return "本号行情日线已清空。";
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && v < 1e9) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
