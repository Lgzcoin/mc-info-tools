package com.doubao.infotools;

import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 补货清单：设目标库存，算缺口，找最便宜店，按距离排一条采购顺序。 */
public final class Stock {
    private Stock() {
    }

    private static Path file() {
        return Files2.path("补货清单.txt");
    }

    private static Map<String, Integer> load() {
        Map<String, Integer> out = new LinkedHashMap<>();
        try {
            Path f = file();
            if (!Files.exists(f)) {
                return out;
            }
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", 2);
                if (p.length < 2) {
                    continue;
                }
                try {
                    out.put(p[0], Math.max(0, Integer.parseInt(p[1].trim())));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void save(Map<String, Integer> map) {
        try {
            StringBuilder sb = new StringBuilder("# 物品 id|目标数量\n");
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                sb.append(e.getKey()).append('|').append(e.getValue()).append('\n');
            }
            Files.writeString(file(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    public static String set(String itemText, int target) {
        String id = Market.resolveItemName(itemText);
        if (id == null) {
            return "认不出「" + itemText + "」。";
        }
        Map<String, Integer> map = load();
        if (target <= 0) {
            map.remove(id);
        } else {
            map.put(id, target);
        }
        save(map);
        return (target <= 0 ? "已移出补货清单：" : "已设置补货目标：") + Names.of(id)
                + (target > 0 ? " ×" + target : "");
    }

    public static String list(Minecraft mc) {
        Map<String, Integer> target = load();
        if (target.isEmpty()) {
            return "补货清单是空的。用 /info stock set 钻石 64 添加。";
        }
        Map<String, Integer> have = Worth.inventoryCounts(mc);
        StringBuilder sb = new StringBuilder("补货清单：");
        for (Map.Entry<String, Integer> e : target.entrySet()) {
            int n = have.getOrDefault(e.getKey(), 0);
            sb.append('\n').append(pad(Names.of(e.getKey()), 8)).append(" 有 ").append(n)
                    .append(" / 目标 ").append(e.getValue()).append(n < e.getValue() ? "  缺 " + (e.getValue() - n) : "  够");
        }
        return sb.toString();
    }

    public static String plan(Minecraft mc) {
        Map<String, Integer> target = load();
        if (target.isEmpty()) {
            return "补货清单是空的。先 /info stock set 钻石 64。";
        }
        Map<String, Integer> have = Worth.inventoryCounts(mc);
        List<Need> needs = new ArrayList<>();
        for (Map.Entry<String, Integer> e : target.entrySet()) {
            int gap = e.getValue() - have.getOrDefault(e.getKey(), 0);
            if (gap <= 0) {
                continue;
            }
            Need n = cheapest(mc, e.getKey(), gap);
            needs.add(n);
        }
        if (needs.isEmpty()) {
            return "补货清单全部够了，不用买。";
        }
        // 从玩家当前位置开始，每次选最近的下一家店，做一个贪心顺序。
        double cx = mc.player == null ? 0 : mc.player.getX();
        double cz = mc.player == null ? 0 : mc.player.getZ();
        List<Need> order = new ArrayList<>();
        while (!needs.isEmpty()) {
            Need best = null;
            double bestD = Double.MAX_VALUE;
            for (Need n : needs) {
                double d = n.shop == null ? 1e9 : Math.hypot(n.shop.x() + 0.5 - cx, n.shop.z() + 0.5 - cz);
                if (d < bestD) {
                    bestD = d;
                    best = n;
                }
            }
            if (best == null) {
                break;
            }
            needs.remove(best);
            order.add(best);
            if (best.shop != null) {
                cx = best.shop.x() + 0.5;
                cz = best.shop.z() + 0.5;
            }
        }
        double total = 0;
        for (Need n : order) {
            total += n.cost;
        }
        StringBuilder sb = new StringBuilder("采购顺序（缺口 " + order.size() + " 种，约 " + Worth.money(total) + "）：");
        int i = 1;
        for (Need n : order) {
            sb.append('\n').append(i++).append(". ").append(Names.of(n.item)).append(" 缺 ").append(n.gap);
            if (n.shop == null && n.unit <= 0) {
                sb.append("  没扫到卖店，也没聊天卖价");
            } else if (n.shop == null) {
                sb.append("  聊天价 ").append(fmt(n.unit)).append("/个 × ").append(n.gap)
                        .append(" = ").append(Worth.money(n.cost));
            } else {
                sb.append("  ").append(fmt(n.unit)).append("/个 × ").append(n.gap)
                        .append(" = ").append(Worth.money(n.cost))
                        .append("  ").append(n.shop.x()).append(',').append(n.shop.y()).append(',')
                        .append(n.shop.z()).append("  ").append(where(mc, n.shop));
            }
        }
        sb.append("\n按已扫到的告示牌店算，不含路费；没扫到店的会用聊天卖价参考。");
        return sb.toString();
    }

    private record Need(String item, int gap, SignShop.Shop shop, double unit, double cost) {
    }

    private static Need cheapest(Minecraft mc, String item, int gap) {
        SignShop.Shop best = null;
        for (SignShop.Shop s : SignShop.all()) {
            if (!s.item().equals(item) || s.sellPer() <= 0) {
                continue;
            }
            if (best == null || s.sellPer() < best.sellPer()) {
                best = s;
            }
        }
        double unit = best == null ? 0 : best.sellPer();
        if (best == null) {
            Market.ItemPrice p = Market.priceOf(item);
            if (p != null && p.sellMed() > 0) {
                unit = p.sellMed();
            }
        }
        return new Need(item, gap, best, unit, unit * gap);
    }

    private static String where(Minecraft mc, SignShop.Shop s) {
        if (mc.player == null || mc.level == null
                || !s.dim().equals(mc.level.dimension().identifier().toString())) {
            return "别的维度";
        }
        double dx = s.x() + 0.5 - mc.player.getX();
        double dz = s.z() + 0.5 - mc.player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String dir = Math.abs(dx) > Math.abs(dz) ? (dx >= 0 ? "东" : "西") : (dz >= 0 ? "南" : "北");
        return dist + "m 往" + dir;
    }

    public static String clear() {
        try {
            Files.deleteIfExists(file());
        } catch (Throwable ignored) {
        }
        return "补货清单已清空。";
    }

    public static int gapCount(Minecraft mc) {
        Map<String, Integer> target = load();
        Map<String, Integer> have = Worth.inventoryCounts(mc);
        int n = 0;
        for (Map.Entry<String, Integer> e : target.entrySet()) {
            if (have.getOrDefault(e.getKey(), 0) < e.getValue()) {
                n++;
            }
        }
        return n;
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && v < 1e9) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
