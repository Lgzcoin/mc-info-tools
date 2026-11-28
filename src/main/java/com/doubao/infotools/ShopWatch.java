package com.doubao.infotools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 店主订阅：关注某个店主，他说话/上新/降价时提醒。 */
public final class ShopWatch {
    private static final Map<String, Long> LAST = new HashMap<>();
    private static final Map<String, Double> LAST_PRICE = new HashMap<>();
    private static boolean priceLoaded = false;

    private ShopWatch() {
    }

    private static Path priceFile() {
        return Files2.path("店主价格.txt");
    }

    public static void onMessage(String text, String sender) {
        try {
            if (!InfoTools.config().shopWatchEnabled || text == null || text.isBlank()
                    || sender == null || sender.isBlank() || "系统".equals(sender)) {
                return;
            }
            if (!InfoTools.config().watchedShops.contains(sender)) {
                return;
            }
            long now = System.currentTimeMillis();
            String key = sender + "|" + text;
            Long last = LAST.get(key);
            if (last != null && now - last < 60_000) {
                return;
            }
            LAST.put(key, now);
            if (LAST.size() > 200) {
                LAST.entrySet().removeIf(e -> now - e.getValue() > 600_000);
            }
            StringBuilder msg = new StringBuilder("关注店主 ").append(sender).append("：").append(shorten(text));
            PriceParser.Quote q = PriceParser.parse(text);
            if (q != null) {
                loadPrices();
                String pk = sender + "|" + q.itemId() + "|" + q.direction();
                Double old = LAST_PRICE.get(pk);
                if (old != null && old > 0 && q.perPiece() < old * 0.98) {
                    double off = (1 - q.perPiece() / old) * 100;
                    msg.append("\n降价：").append(Names.of(q.itemId())).append(' ')
                            .append(fmt(old)).append(" → ").append(fmt(q.perPiece()))
                            .append("/个（低 ").append(Math.round(off)).append("%）");
                }
                LAST_PRICE.put(pk, q.perPiece());
                savePrice(pk, q.perPiece());
            } else if (text.contains("上新") || text.contains("新货") || text.contains("开张")
                    || text.contains("开业") || text.contains("补货")) {
                msg.append("\n可能有上新/补货，去看一眼。");
            }
            Notify.alert(msg.toString());
        } catch (Throwable ignored) {
        }
    }

    public static String listText() {
        return "店主订阅：" + (InfoTools.config().shopWatchEnabled ? "开" : "关")
                + "\n关注：" + (InfoTools.config().watchedShops.isEmpty() ? "（空）"
                : String.join("、", InfoTools.config().watchedShops))
                + "\n/info watch add 名字　del 名字　on/off";
    }

    public static String add(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return "名字不能为空。";
        }
        if (!InfoTools.config().watchedShops.contains(n)) {
            InfoTools.config().watchedShops.add(n);
            InfoTools.saveConfig();
        }
        return "已关注店主：" + n;
    }

    public static String remove(String name) {
        boolean removed = InfoTools.config().watchedShops.remove(name == null ? "" : name.trim());
        InfoTools.saveConfig();
        return removed ? "已取消关注：" + name : "没关注过：" + name;
    }

    public static String setEnabled(boolean on) {
        InfoTools.config().shopWatchEnabled = on;
        InfoTools.saveConfig();
        return "店主订阅已" + (on ? "开" : "关") + "。";
    }

    private static void loadPrices() {
        if (priceLoaded) {
            return;
        }
        priceLoaded = true;
        try {
            Path f = priceFile();
            if (!Files.exists(f)) {
                return;
            }
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String[] p = raw.trim().split("\\|", 4);
                if (p.length >= 4) {
                    LAST_PRICE.put(p[0] + "|" + p[1] + "|" + p[2], Double.parseDouble(p[3]));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void savePrice(String key, double price) {
        try {
            Map<String, Double> all = new LinkedHashMap<>(LAST_PRICE);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Double> e : all.entrySet()) {
                sb.append(e.getKey()).append('|').append(e.getValue()).append('\n');
            }
            Files.writeString(priceFile(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && v < 1e9) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String shorten(String s) {
        return s.length() > 70 ? s.substring(0, 70) + "…" : s;
    }
}
