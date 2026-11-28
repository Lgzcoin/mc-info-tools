package com.doubao.infotools;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 背包市值：按行情卖中位 / 已扫商店最高收价，算总价值和最值钱的几样。 */
public final class Worth {
    private Worth() {
    }

    public static String summary(Minecraft mc) {
        if (mc.player == null) {
            return "还没进世界。";
        }
        Map<String, Integer> counts = counts(mc);
        if (counts.isEmpty()) {
            return "背包是空的。";
        }
        int slots = mc.player.getInventory().getNonEquipmentItems().size();
        int used = 0;
        int pieces = 0;
        for (int c : counts.values()) {
            pieces += c;
        }
        for (ItemStack s : mc.player.getInventory().getNonEquipmentItems()) {
            if (s != null && !s.isEmpty()) {
                used++;
            }
        }

        double market = 0;
        double shop = 0;
        int priced = 0;
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String id = e.getKey();
            int n = e.getValue();
            Market.ItemPrice p = Market.priceOf(id);
            double unit = p == null ? 0 : p.reference();
            double shopUnit = SignShop.maxBuy(id);
            double mv = unit * n;
            double sv = shopUnit * n;
            if (p != null && p.any()) {
                priced++;
            }
            market += mv;
            shop += sv;
            lines.add(new Line(id, n, unit, mv));
        }
        lines.sort(Comparator.comparingDouble(Line::value).reversed());

        StringBuilder sb = new StringBuilder();
        sb.append("背包 ").append(used).append('/').append(slots).append(" 格，共 ").append(pieces).append(" 件");
        sb.append("\n行情参考（按卖中位）：").append(priced == 0 ? "没有报价" : "约 " + money(market));
        if (priced > 0) {
            sb.append("（").append(priced).append(" 种有报价）");
        }
        if (shop > 0) {
            sb.append("\n已扫商店最高收：约 ").append(money(shop));
        } else {
            sb.append("\n已扫商店最高收：暂无（走近告示牌商店会自动记）");
        }
        if (priced > 0) {
            sb.append("\n最值钱：");
            int shown = 0;
            for (Line l : lines) {
                if (l.value() <= 0 || shown >= 5) {
                    continue;
                }
                sb.append(shown == 0 ? "" : "；").append(Names.of(l.id())).append(' ').append(l.count())
                        .append(" 个 × ").append(fmt(l.unit())).append(" = ").append(money(l.value()));
                shown++;
            }
        } else {
            sb.append("\n行情里还没有这些物品的报价。聊天里有人喊价后，或用 /info price 物品 看。");
        }
        return sb.toString();
    }

    public static String hudLine(Minecraft mc) {
        if (mc.player == null || !InfoTools.config().worthHud) {
            return null;
        }
        Map<String, Integer> counts = counts(mc);
        if (counts.isEmpty()) {
            return "背包 空";
        }
        int used = 0;
        double value = 0;
        int priced = 0;
        for (ItemStack s : mc.player.getInventory().getNonEquipmentItems()) {
            if (s != null && !s.isEmpty()) {
                used++;
            }
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Market.ItemPrice p = Market.priceOf(e.getKey());
            if (p == null || !p.any()) {
                continue;
            }
            priced++;
            value += p.reference() * e.getValue();
        }
        if (priced == 0) {
            return "背包 " + used + " 格（暂无行情）";
        }
        return "背包 " + used + " 格 市值 " + money(value);
    }

    /** 背包物品计数（不含装备栏），给补货清单/收益统计用。 */
    public static Map<String, Integer> inventoryCounts(Minecraft mc) {
        return new LinkedHashMap<>(counts(mc));
    }

    /** 按行情卖中位估背包总价值。 */
    public static double marketValue(Minecraft mc) {
        double sum = 0;
        for (Map.Entry<String, Integer> e : counts(mc).entrySet()) {
            Market.ItemPrice p = Market.priceOf(e.getKey());
            if (p != null && p.any()) {
                sum += p.reference() * e.getValue();
            }
        }
        return sum;
    }

    public static String topValueText(Minecraft mc, int limit) {
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts(mc).entrySet()) {
            Market.ItemPrice p = Market.priceOf(e.getKey());
            double unit = p == null ? 0 : p.reference();
            lines.add(new Line(e.getKey(), e.getValue(), unit, unit * e.getValue()));
        }
        lines.sort(Comparator.comparingDouble(Line::value).reversed());
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Line l : lines) {
            if (l.value() <= 0 || shown >= limit) {
                continue;
            }
            if (shown > 0) {
                sb.append("、");
            }
            sb.append(Names.of(l.id())).append('×').append(l.count());
            shown++;
        }
        return sb.toString();
    }

    private static Map<String, Integer> counts(Minecraft mc) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (mc.player == null) {
            return out;
        }
        for (ItemStack s : mc.player.getInventory().getNonEquipmentItems()) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            String id = QuickCard.idOf(s);
            if (id == null) {
                continue;
            }
            out.merge(id, s.getCount(), Integer::sum);
        }
        return out;
    }

    private record Line(String id, int count, double unit, double value) {
    }

    public static String money(double v) {
        if (v >= 10000) {
            return String.format(java.util.Locale.ROOT, "%.1f万", v / 10000.0);
        }
        if (v >= 100) {
            return Long.toString(Math.round(v));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && v < 1e9) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
