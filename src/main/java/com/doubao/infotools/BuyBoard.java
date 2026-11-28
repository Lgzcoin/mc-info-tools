package com.doubao.infotools;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 全服收购榜 + 搬砖套利雷达：把告示牌商店和聊天收购价合成一张表。 */
public final class BuyBoard {
    private BuyBoard() {
    }

    public record Offer(String item, double price, String where, long time, boolean shop) {
    }

    private static List<Offer> allOffers() {
        List<Offer> out = new ArrayList<>();
        try {
            for (SignShop.Shop s : SignShop.all()) {
                if (s.buyPer() > 0) {
                    out.add(new Offer(s.item(), s.buyPer(),
                            dimShort(s.dim()) + " " + s.x() + "," + s.y() + "," + s.z(), s.updated(), true));
                }
            }
            for (Market.QuoteView q : Market.quoteViews()) {
                if ("收".equals(q.dir())) {
                    out.add(new Offer(q.item(), q.perPiece(), "聊天报价", q.time(), false));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static List<Offer> allSellOffers() {
        List<Offer> out = new ArrayList<>();
        try {
            for (SignShop.Shop s : SignShop.all()) {
                if (s.sellPer() > 0) {
                    out.add(new Offer(s.item(), s.sellPer(),
                            dimShort(s.dim()) + " " + s.x() + "," + s.y() + "," + s.z(), s.updated(), true));
                }
            }
            for (Market.QuoteView q : Market.quoteViews()) {
                if ("卖".equals(q.dir())) {
                    out.add(new Offer(q.item(), q.perPiece(), "聊天报价", q.time(), false));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Map<String, List<Offer>> byItem() {
        Map<String, List<Offer>> map = new LinkedHashMap<>();
        for (Offer o : allOffers()) {
            map.computeIfAbsent(o.item(), k -> new ArrayList<>()).add(o);
        }
        return map;
    }

    public static String board() {
        Map<String, List<Offer>> map = byItem();
        if (map.isEmpty()) {
            return "还没有收购价：走近告示牌商店自动扫，聊天里的「收 xxx」也会记。";
        }
        List<Map.Entry<String, List<Offer>>> rows = new ArrayList<>(map.entrySet());
        rows.sort(Comparator.<Map.Entry<String, List<Offer>>>comparingDouble(e ->
                -e.getValue().stream().mapToDouble(Offer::price).max().orElse(0)));
        StringBuilder sb = new StringBuilder("全服收购榜（按最高收价）：");
        int n = 0;
        for (Map.Entry<String, List<Offer>> e : rows) {
            if (n++ >= 10) {
                break;
            }
            Offer best = e.getValue().stream().max(Comparator.comparingDouble(Offer::price)).orElse(null);
            if (best == null) {
                continue;
            }
            sb.append('\n').append(pad(Names.of(e.getKey()), 8)).append(" 最高收 ")
                    .append(fmt(best.price())).append("/个  ")
                    .append(e.getValue().size()).append(" 条  ")
                    .append(best.shop() ? best.where() : "聊天");
        }
        sb.append("\n单品：/info buy 钻石　套利：/info flip");
        return sb.toString();
    }

    public static String item(String text) {
        String id = Market.resolveItemName(text == null ? "" : text);
        if (id == null) {
            return "认不出「" + text + "」，换个说法（钻石、铁、煤、红石…）。";
        }
        List<Offer> list = byItem().getOrDefault(id, List.of());
        if (list.isEmpty()) {
            return Names.of(id) + "：还没收到收购价。";
        }
        List<Offer> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingDouble(Offer::price).reversed());
        StringBuilder sb = new StringBuilder(Names.of(id)).append("：").append(sorted.size()).append(" 条收购");
        int n = 0;
        for (Offer o : sorted) {
            if (n++ >= 10) {
                break;
            }
            sb.append('\n').append(fmt(o.price())).append("/个  ")
                    .append(o.shop() ? o.where() : "聊天").append("  ").append(ago(o.time()));
        }
        return sb.toString();
    }

    public static int count() {
        return byItem().size();
    }

    public static Offer bestOffer(String itemId) {
        List<Offer> list = byItem().get(itemId);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.stream().max(Comparator.comparingDouble(Offer::price)).orElse(null);
    }

    public static String hudLine() {
        Map<String, List<Offer>> map = byItem();
        if (map.isEmpty()) {
            return "收购榜 暂无";
        }
        String bestItem = null;
        double best = 0;
        for (Map.Entry<String, List<Offer>> e : map.entrySet()) {
            for (Offer o : e.getValue()) {
                if (o.price() > best) {
                    best = o.price();
                    bestItem = e.getKey();
                }
            }
        }
        return bestItem == null ? "收购榜 暂无"
                : "收购榜 " + Names.of(bestItem) + " " + fmt(best) + "/个";
    }

    /** 套利：从最便宜的店买，卖给最高收价（店或聊天），按利润率过滤。 */
    public static String flip() {
        Map<String, Offer> bestBuy = new LinkedHashMap<>();
        for (Offer o : allOffers()) {
            Offer old = bestBuy.get(o.item());
            if (old == null || o.price() > old.price()) {
                bestBuy.put(o.item(), o);
            }
        }
        record Flip(String item, double buyAt, String buyWhere, double sellAt, String sellWhere,
                    double profit, double margin) {
        }
        List<Flip> out = new ArrayList<>();
        for (Offer seller : allSellOffers()) {
            if (seller.price() <= 0) {
                continue;
            }
            Offer buyer = bestBuy.get(seller.item());
            if (buyer == null || buyer.price() <= seller.price()) {
                continue;
            }
            double profit = buyer.price() - seller.price();
            double margin = profit / seller.price() * 100;
            if (margin < InfoTools.config().arbitragePercent) {
                continue;
            }
            out.add(new Flip(seller.item(), seller.price(), seller.shop() ? seller.where() : "聊天",
                    buyer.price(), buyer.shop() ? buyer.where() : "聊天", profit, margin));
        }
        if (out.isEmpty()) {
            return "暂时没有达到 " + InfoTools.config().arbitragePercent
                    + "% 利润的搬砖路线（需要同时扫到卖店和收购价）。";
        }
        out.sort(Comparator.comparingDouble(Flip::margin).reversed());
        StringBuilder sb = new StringBuilder("搬砖套利（利润率 ≥ " + InfoTools.config().arbitragePercent + "%）：");
        int n = 0;
        for (Flip f : out) {
            if (n++ >= 8) {
                break;
            }
            sb.append('\n').append(Names.of(f.item())).append("：").append(fmt(f.buyAt()))
                    .append(" 买 → ").append(fmt(f.sellAt())).append(" 卖，每个赚 ")
                    .append(fmt(f.profit())).append("（").append(Math.round(f.margin())).append("%）")
                    .append("\n  买：").append(f.buyWhere()).append("　卖：").append(f.sellWhere());
        }
        sb.append("\n只算已扫到的告示牌店和聊天收价，不含路费和库存。");
        return sb.toString();
    }

    private static String dimShort(String dim) {
        if (dim == null) {
            return "?";
        }
        if (dim.contains("nether")) {
            return "下界";
        }
        if (dim.contains("end")) {
            return "末地";
        }
        return "主世界";
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        if (v >= 10000 && Math.abs(v - Math.round(v)) < 1e-6) {
            return String.format(java.util.Locale.ROOT, "%.1f万", v / 10000.0);
        }
        if (v == Math.floor(v) && v < 1e9) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
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
