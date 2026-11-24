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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天市场比价：听聊天里的报价 → 统一成“每个多少钱” → 攒行情。
 * 卖价明显低于历史中位数时提醒（便宜货）。
 * 多个号的行情会合并统计（都写在 信息工具/<玩家名>/行情.txt）。
 */
public final class Market {
    private static final Map<String, Long> LAST_ALERT = new HashMap<>();
    private static final Map<String, Long> LAST_SEEN = new HashMap<>();
    private static final int MAX_RECORDS = 2500;
    private static int appends = 0;
    private static long countAt = 0;
    private static String cachedLine = "";

    private Market() {
    }

    private record Rec(long time, String dir, String item, double perPiece, double raw, String unit, String text) {
    }

    public static boolean enabled() {
        return InfoTools.config().priceEnabled;
    }

    public static void onMessage(String text) {
        try {
            long now0 = System.currentTimeMillis();
            Long seen = LAST_SEEN.get(text);
            if (seen != null && now0 - seen < 2000) {
                return;
            }
            LAST_SEEN.put(text, now0);
            if (LAST_SEEN.size() > 200) {
                LAST_SEEN.entrySet().removeIf(e -> now0 - e.getValue() > 30_000);
            }
            PriceParser.Quote q = PriceParser.parse(text);
            if (q == null) {
                return;
            }
            if (q.sell() && enabled()) {
                cheapAlert(q);
            }
            append(q);
            appends++;
            if (appends % 200 == 0) {
                trimIfNeeded();
            }
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("报价解析出错（已忽略）", t);
        }
    }

    private static void cheapAlert(PriceParser.Quote q) {
        List<Rec> history = all().stream()
                .filter(r -> r.item.equals(q.itemId()) && "卖".equals(r.dir))
                .sorted(Comparator.comparingLong(Rec::time))
                .toList();
        if (history.size() < 4) {
            return;
        }
        double median = median(history.stream().map(Rec::perPiece).toList());
        if (median <= 0) {
            return;
        }
        int pct = Math.max(30, Math.min(99, InfoTools.config().priceDiscountPercent));
        double line = median * pct / 100.0;
        if (q.perPiece() > line) {
            return;
        }
        Long last = LAST_ALERT.get(q.itemId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < 60_000) {
            return;
        }
        LAST_ALERT.put(q.itemId(), now);
        int off = (int) Math.round((1 - q.perPiece() / median) * 100);
        Notify.alert("便宜货：" + Names.of(q.itemId()) + " " + fmt(q.perPiece()) + "/个，之前均价 "
                + fmt(median) + "（低 " + off + "%）｜" + shorten(q.raw()));
    }

    private static void append(PriceParser.Quote q) {
        try {
            String text = q.raw().replace("|", "/").replace("\n", " ").replace("\r", " ");
            String line = System.currentTimeMillis() + "|" + q.direction() + "|" + q.itemId() + "|"
                    + q.perPiece() + "|" + q.rawPrice() + "|" + q.unit() + "|" + text
                    + "\n";
            Files.writeString(file(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }

    private static void trimIfNeeded() {
        try {
            Path f = file();
            if (!Files.exists(f)) {
                return;
            }
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            if (lines.size() <= MAX_RECORDS) {
                return;
            }
            List<String> keep = new ArrayList<>(lines.subList(lines.size() - MAX_RECORDS, lines.size()));
            Files.writeString(f, String.join("\n", keep) + "\n", StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static Path file() {
        return Files2.path("行情.txt");
    }

    /** 读所有账号的行情，合并统计。 */
    private static List<Rec> all() {
        List<Rec> out = new ArrayList<>();
        try {
            Path root = FabricLoader.getInstance().getGameDir().resolve("信息工具");
            if (!Files.exists(root)) {
                return out;
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                for (Path dir : ds) {
                    Path f = dir.resolve("行情.txt");
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
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        String[] p = line.split("\\|", 7);
        if (p.length < 6) {
            return null;
        }
        try {
            return new Rec(Long.parseLong(p[0]), p[1], p[2], Double.parseDouble(p[3]),
                    Double.parseDouble(p[4]), p[5], p.length > 6 ? p[6] : "");
        } catch (Throwable t) {
            return null;
        }
    }

    public static String overview() {
        List<Rec> all = all();
        if (all.isEmpty()) {
            return "还没有报价记录。聊天里出现「收钻石 5000一组」这类会自己记；\n也可以 /info price test 收钻石 5000一组 试一下。";
        }
        Map<String, List<Rec>> byItem = new LinkedHashMap<>();
        for (Rec r : all) {
            byItem.computeIfAbsent(r.item(), k -> new ArrayList<>()).add(r);
        }
        List<Rec> last = new ArrayList<>(all);
        last.sort(Comparator.comparingLong(Rec::time).reversed());
        StringBuilder sb = new StringBuilder("行情 ").append(all.size()).append(" 条 / ")
                .append(byItem.size()).append(" 种，最近 ").append(ago(last.get(0).time()));
        List<Map.Entry<String, List<Rec>>> top = new ArrayList<>(byItem.entrySet());
        top.sort(Comparator.<Map.Entry<String, List<Rec>>>comparingInt(e -> -e.getValue().size()));
        for (int i = 0; i < Math.min(7, top.size()); i++) {
            String id = top.get(i).getKey();
            List<Double> sell = new ArrayList<>();
            List<Double> buy = new ArrayList<>();
            for (Rec r : top.get(i).getValue()) {
                if ("卖".equals(r.dir)) {
                    sell.add(r.perPiece());
                } else {
                    buy.add(r.perPiece());
                }
            }
            sb.append('\n').append(pad(Names.of(id), 8));
            if (!sell.isEmpty()) {
                sb.append(" 卖 ").append(fmt(median(sell))).append("（").append(sell.size()).append("）");
            }
            if (!buy.isEmpty()) {
                sb.append(" 收 ").append(fmt(median(buy))).append("（").append(buy.size()).append("）");
            }
        }
        if (top.size() > 7) {
            sb.append("\n…还有 ").append(top.size() - 7).append(" 种，用 /info price 物品名 查");
        }
        sb.append("\n最近：").append(last.get(0).dir()).append(' ').append(Names.of(last.get(0).item()))
                .append(' ').append(fmt(last.get(0).perPiece())).append("/个");
        sb.append("\n单品：/info price 钻石　便宜货：/info price cheap");
        return sb.toString();
    }

    public static String stats(String itemText) {
        String id = resolve(itemText);
        if (id == null) {
            return "认不出「" + itemText + "」，换个说法（钻石、铁、煤、红石…）。";
        }
        List<Rec> list = all().stream().filter(r -> r.item.equals(id))
                .sorted(Comparator.comparingLong(Rec::time)).toList();
        if (list.isEmpty()) {
            return Names.of(id) + "：还没有报价记录。";
        }
        List<Double> sellP = new ArrayList<>();
        List<Double> buyP = new ArrayList<>();
        Rec lastSell = null;
        Rec lastBuy = null;
        for (Rec r : list) {
            if ("卖".equals(r.dir)) {
                sellP.add(r.perPiece());
                lastSell = r;
            } else {
                buyP.add(r.perPiece());
                lastBuy = r;
            }
        }
        StringBuilder sb = new StringBuilder(Names.of(id)).append("：共 ").append(list.size())
                .append(" 条，最近 ").append(ago(list.get(list.size() - 1).time()));
        if (!sellP.isEmpty()) {
            double med = median(sellP);
            sb.append("\n卖：").append(sellP.size()).append(" 条，中位 ").append(fmt(med))
                    .append("，最低 ").append(fmt(min(sellP))).append("，最高 ").append(fmt(max(sellP)));
            if (lastSell != null) {
                double diff = (lastSell.perPiece() - med) / med * 100;
                sb.append("\n最近卖价 ").append(fmt(lastSell.perPiece())).append("，");
                if (Math.abs(diff) < 5) {
                    sb.append("跟中位差不多");
                } else {
                    sb.append(diff > 0 ? "比中位高 " : "比中位低 ").append(Math.abs((int) Math.round(diff))).append("%");
                }
            }
        }
        if (!buyP.isEmpty()) {
            sb.append("\n收：").append(buyP.size()).append(" 条，中位 ").append(fmt(median(buyP)))
                    .append("，最高 ").append(fmt(max(buyP)));
            if (lastBuy != null) {
                sb.append("，最近收价 ").append(fmt(lastBuy.perPiece()));
            }
        }
        if (!sellP.isEmpty() && !buyP.isEmpty()) {
            double sm = median(sellP);
            double bm = median(buyP);
            double spread = (sm - bm) / sm * 100;
            sb.append("\n价差：收 ").append(fmt(bm)).append(" / 卖 ").append(fmt(sm))
                    .append("，差 ").append(Math.round(spread)).append("%");
            if (spread >= 15) {
                sb.append("（低价收、高价卖有得赚）");
            }
        }
        sb.append("\n最近 5 条：");
        List<Rec> recent = new ArrayList<>(list);
        recent.sort(Comparator.comparingLong(Rec::time).reversed());
        for (int i = 0; i < Math.min(5, recent.size()); i++) {
            Rec r = recent.get(i);
            sb.append('\n').append(ago(r.time())).append(' ').append(r.dir()).append(' ')
                    .append(fmt(r.perPiece())).append("/个（").append(fmt(r.raw())).append('/')
                    .append(r.unit()).append("）");
        }
        int pct = Math.max(30, Math.min(99, InfoTools.config().priceDiscountPercent));
        if (sellP.size() >= 3) {
            double med = median(sellP);
            sb.append("\n便宜线：卖价 ≤ ").append(fmt(med * pct / 100.0)).append("/个 时提醒（中位 ")
                    .append(pct).append("%）");
        }
        return sb.toString();
    }

    /** 现在就能捡的便宜货：每个物品最近一次卖价 ≤ 中位 × 折扣线。 */
    public static String cheap() {
        List<Rec> all = all();
        if (all.isEmpty()) {
            return "还没有报价记录。";
        }
        int pct = Math.max(30, Math.min(99, InfoTools.config().priceDiscountPercent));
        Map<String, List<Rec>> byItem = new LinkedHashMap<>();
        for (Rec r : all) {
            if ("卖".equals(r.dir)) {
                byItem.computeIfAbsent(r.item(), k -> new ArrayList<>()).add(r);
            }
        }
        List<String> lines = new ArrayList<>();
        List<Double> offs = new ArrayList<>();
        for (Map.Entry<String, List<Rec>> e : byItem.entrySet()) {
            List<Rec> list = e.getValue();
            if (list.size() < 4) {
                continue;
            }
            list.sort(Comparator.comparingLong(Rec::time));
            double med = median(list.stream().map(Rec::perPiece).toList());
            Rec latest = list.get(list.size() - 1);
            if (med <= 0 || latest.perPiece() > med * pct / 100.0) {
                continue;
            }
            int off = (int) Math.round((1 - latest.perPiece() / med) * 100);
            lines.add(Names.of(e.getKey()) + " 现在 " + fmt(latest.perPiece()) + "/个，中位 "
                    + fmt(med) + "（低 " + off + "%）" + ago(latest.time()));
            offs.add((double) off);
        }
        if (lines.isEmpty()) {
            return "现在没有低于 " + pct + "% 的卖价（共 " + all.size() + " 条行情）。等有人喊再说。";
        }
        StringBuilder sb = new StringBuilder("当前便宜货（便宜线 " + pct + "%）：");
        for (int i = 0; i < Math.min(6, lines.size()); i++) {
            sb.append('\n').append(lines.get(i));
        }
        if (lines.size() > 6) {
            sb.append("\n…还有 ").append(lines.size() - 6).append(" 种");
        }
        return sb.toString();
    }

    public static String test(String text) {
        PriceParser.Quote q = PriceParser.parse(text);
        if (q == null) {
            return "这段话没识别出报价。要同时有「收/卖」+ 物品名 + 价格，比如：收钻石 5000一组。";
        }
        return "识别结果：" + PriceParser.describe(q)
                + "\n（只是测试，已经记进行情：/info price " + Names.of(q.itemId()) + "）";
    }

    public static void clear() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Files.delete(f);
            }
        } catch (Throwable ignored) {
        }
    }

    public static String line() {
        long now = System.currentTimeMillis();
        if (now - countAt > 10_000) {
            countAt = now;
            cachedLine = all().size() + " 条，告警" + (enabled() ? "开" : "关") + "，便宜线 "
                    + InfoTools.config().priceDiscountPercent + "%";
        }
        return cachedLine;
    }

    private static String resolve(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String id = PriceParser.resolveItem(text);
        return id;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> v = new ArrayList<>(values);
        v.sort(Double::compare);
        int n = v.size();
        return n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2.0;
    }

    private static double min(List<Double> v) {
        return v.stream().mapToDouble(Double::doubleValue).min().orElse(0);
    }

    private static double max(List<Double> v) {
        return v.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
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

    private static String shorten(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && v < 1e9) {
            return Long.toString((long) v);
        }
        return String.format("%.1f", v);
    }
}
