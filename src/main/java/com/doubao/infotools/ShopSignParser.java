package com.doubao.infotools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 告示牌商店解析（纯字符串，方便离线测）。
 * 支持：ChestShop 的 B/S 行、QuickShop 的 Buy/Sell 行、以及中文“收/卖 + 价格”。
 * 统一输出“每个多少钱”（per piece），买 = 商店收你的价，卖 = 商店卖给你的价。
 */
public final class ShopSignParser {
    public record Parsed(String itemId, double buyPer, double sellPer, String raw, int qty) {
    }

    private static final Pattern CHEST = Pattern.compile(
            "([BbSs])\\s*(\\d+)\\s*[:：]\\s*(\\d+(?:\\.\\d+)?)\\s*(万|[wWkK])?");
    private static final Pattern QUICK = Pattern.compile(
            "(?i)\\b(buy|sell)\\b\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)\\s*(万|[wWkK])?");
    private static final Pattern CN = Pattern.compile(
            "(收购|出售|收|卖|售)\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)\\s*(万|[wWkK])?");
    private static final Pattern MONEY = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(万|[wWkK千])?");

    private ShopSignParser() {
    }

    public static Parsed parse(List<String> rawLines, Function<String, String> fallbackItem) {
        if (rawLines == null || rawLines.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            String s = clean(raw);
            if (!s.isBlank()) {
                lines.add(s);
            }
        }
        if (lines.isEmpty()) {
            return null;
        }

        String item = null;
        double buy = 0;
        double sell = 0;
        int qty = 0;
        StringBuilder raw = new StringBuilder();

        for (String line : lines) {
            raw.append(line).append(" / ");
            PriceParser.Quote q = PriceParser.parse(line);
            if (q != null) {
                if (item == null) {
                    item = q.itemId();
                }
                if ("卖".equals(q.direction())) {
                    sell = Math.max(sell, q.perPiece());
                } else {
                    buy = Math.max(buy, q.perPiece());
                }
                continue;
            }

            Matcher cm = CHEST.matcher(line);
            boolean chest = false;
            while (cm.find()) {
                chest = true;
                double amount = money(cm.group(3), cm.group(4));
                int n = Math.max(1, parseInt(cm.group(2)));
                double per = amount / n;
                if (cm.group(1).equalsIgnoreCase("B")) {
                    buy = Math.max(buy, per);
                } else {
                    sell = Math.max(sell, per);
                }
                qty = Math.max(qty, n);
            }
            if (chest) {
                continue;
            }

            Matcher qm = QUICK.matcher(line);
            boolean quick = false;
            while (qm.find()) {
                quick = true;
                double per = money(qm.group(2), qm.group(3));
                if (qm.group(1).equalsIgnoreCase("buy")) {
                    sell = Math.max(sell, per);
                } else {
                    buy = Math.max(buy, per);
                }
            }
            if (quick) {
                continue;
            }

            Matcher cn = CN.matcher(line);
            boolean chinese = false;
            while (cn.find()) {
                chinese = true;
                double per = money(cn.group(2), cn.group(3));
                String d = cn.group(1);
                if (d.equals("收购") || d.equals("收")) {
                    buy = Math.max(buy, per);
                } else {
                    sell = Math.max(sell, per);
                }
            }
            if (chinese) {
                continue;
            }

            if (item == null && fallbackItem != null) {
                item = fallbackItem.apply(line);
            }
        }

        if (item == null) {
            for (String line : lines) {
                if (fallbackItem != null) {
                    item = fallbackItem.apply(line);
                    if (item != null) {
                        break;
                    }
                }
            }
        }
        if (item == null || (buy <= 0 && sell <= 0)) {
            return null;
        }
        if (buy > 1_000_000 || sell > 1_000_000) {
            return null;
        }
        return new Parsed(item, buy, sell, raw.toString(), qty);
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("§.", "").replace('\u00a0', ' ').trim();
    }

    private static double money(String num, String unit) {
        double v = parseDouble(num);
        if (unit != null) {
            if (unit.equals("万")) {
                v *= 10000;
            } else if (unit.equalsIgnoreCase("w")) {
                v *= 10000;
            } else if (unit.equals("千") || unit.equalsIgnoreCase("k")) {
                v *= 1000;
            }
        }
        return v;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 给离线测试用：从一段文本里抓所有价格，方便核对。 */
    public static double firstMoney(String line) {
        Matcher m = MONEY.matcher(line == null ? "" : line);
        if (!m.find()) {
            return 0;
        }
        return money(m.group(1), m.group(2));
    }
}
