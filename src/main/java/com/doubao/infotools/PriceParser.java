package com.doubao.infotools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从聊天里抠「谁在收/卖什么、多少钱、按组还是按个」。
 * 纯字符串逻辑，不碰 Minecraft，方便离线跑用例。
 */
public final class PriceParser {
    public record Quote(String itemId, String direction, double rawPrice, String unit,
                        double perPiece, String raw) {
        public boolean sell() {
            return "卖".equals(direction);
        }
    }

    /** 中文别名 → 统一 id；按长度倒序匹配，避免“石”抢“钻石”。 */
    private static final Map<String, String> ALIAS = new LinkedHashMap<>();
    private static final List<String> ALIAS_ORDER = new ArrayList<>();

    static {
        alias("钻石", "minecraft:diamond");
        alias("钻石块", "minecraft:diamond_block");
        alias("绿宝石块", "minecraft:emerald_block");
        alias("绿宝石", "minecraft:emerald");
        alias("铁锭", "minecraft:iron_ingot");
        alias("铁块", "minecraft:iron_block");
        alias("粗铁", "minecraft:raw_iron");
        alias("铁", "minecraft:iron_ingot");
        alias("金锭", "minecraft:gold_ingot");
        alias("金块", "minecraft:gold_block");
        alias("粗金", "minecraft:raw_gold");
        alias("金", "minecraft:gold_ingot");
        alias("煤炭块", "minecraft:coal_block");
        alias("煤炭", "minecraft:coal");
        alias("煤", "minecraft:coal");
        alias("木炭", "minecraft:charcoal");
        alias("红石块", "minecraft:redstone_block");
        alias("红石", "minecraft:redstone");
        alias("青金石", "minecraft:lapis_lazuli");
        alias("下界石英", "minecraft:quartz");
        alias("石英", "minecraft:quartz");
        alias("紫水晶", "minecraft:amethyst_shard");
        alias("下界合金锭", "minecraft:netherite_ingot");
        alias("下界合金块", "minecraft:netherite_block");
        alias("下界合金碎片", "minecraft:netherite_scrap");
        alias("下界合金剑", "minecraft:netherite_sword");
        alias("下界合金斧", "minecraft:netherite_axe");
        alias("下界合金锹", "minecraft:netherite_shovel");
        alias("下界合金铲", "minecraft:netherite_shovel");
        alias("下界合金锄", "minecraft:netherite_hoe");
        alias("下界合金头盔", "minecraft:netherite_helmet");
        alias("下界合金胸甲", "minecraft:netherite_chestplate");
        alias("下界合金护腿", "minecraft:netherite_leggings");
        alias("下界合金靴子", "minecraft:netherite_boots");
        alias("下界合金升级模板", "minecraft:netherite_upgrade_smithing_template");
        alias("下界合金", "minecraft:netherite_ingot");
        alias("远古残骸", "minecraft:ancient_debris");
        alias("金粒", "minecraft:gold_nugget");
        alias("铁粒", "minecraft:iron_nugget");
        alias("netherite ingot", "minecraft:netherite_ingot");
        alias("netherite scrap", "minecraft:netherite_scrap");
        alias("netherite block", "minecraft:netherite_block");
        alias("ancient debris", "minecraft:ancient_debris");
        alias("gold ingot", "minecraft:gold_ingot");
        alias("iron ingot", "minecraft:iron_ingot");
        alias("copper ingot", "minecraft:copper_ingot");
        alias("铜锭", "minecraft:copper_ingot");
        alias("粗铜", "minecraft:raw_copper");
        alias("铜", "minecraft:copper_ingot");
        alias("黑曜石", "minecraft:obsidian");
        alias("圆石", "minecraft:cobblestone");
        alias("深板岩", "minecraft:deepslate");
        alias("石头", "minecraft:stone");
        alias("泥土", "minecraft:dirt");
        alias("沙砾", "minecraft:gravel");
        alias("沙子", "minecraft:sand");
        alias("玻璃", "minecraft:glass");
        alias("原木", "minecraft:oak_log");
        alias("木头", "minecraft:oak_log");
        alias("木板", "minecraft:oak_planks");
        alias("木棍", "minecraft:stick");
        alias("火把", "minecraft:torch");
        alias("箭", "minecraft:arrow");
        alias("骨头", "minecraft:bone");
        alias("线", "minecraft:string");
        alias("火药", "minecraft:gunpowder");
        alias("末影珍珠", "minecraft:ender_pearl");
        alias("烈焰棒", "minecraft:blaze_rod");
        alias("粘液球", "minecraft:slime_ball");
        alias("苹果", "minecraft:apple");
        alias("金苹果", "minecraft:golden_apple");
        alias("面包", "minecraft:bread");
        alias("牛排", "minecraft:cooked_beef");
        alias("熟猪排", "minecraft:cooked_porkchop");
        alias("熟鸡肉", "minecraft:cooked_chicken");
        alias("潜影盒", "minecraft:shulker_box");
        alias("鞘翅", "minecraft:elytra");
        alias("钻石镐", "minecraft:diamond_pickaxe");
        alias("下界合金镐", "minecraft:netherite_pickaxe");
        alias("铁镐", "minecraft:iron_pickaxe");
        alias("钻石剑", "minecraft:diamond_sword");
        alias("盾牌", "minecraft:shield");
        alias("附魔书", "minecraft:enchanted_book");
    }

    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(万|[wW]|千|[kK])?");

    private PriceParser() {
    }

    private record Tok(double value, String unit, boolean x, boolean qtyLike) {
    }

    private static void alias(String cn, String id) {
        ALIAS.put(cn, id);
        ALIAS_ORDER.add(cn);
        ALIAS_ORDER.sort(Comparator.comparingInt(String::length).reversed());
    }

    public static Quote parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("[信息]") || s.startsWith("[告警]")) {
            return null;
        }

        int buyAt = firstIndex(s, "求购", "收购", "收");
        int sellAt = firstIndex(s, "出售", "甩卖", "卖", "出");
        if (buyAt < 0 && sellAt < 0) {
            return null;
        }
        String dir;
        if (buyAt < 0) {
            dir = "卖";
        } else if (sellAt < 0) {
            dir = "收";
        } else {
            dir = buyAt < sellAt ? "收" : "卖";
        }

        String item = null;
        int itemAt = Integer.MAX_VALUE;
        for (String cn : ALIAS_ORDER) {
            int i = s.indexOf(cn);
            if (i >= 0) {
                item = ALIAS.get(cn);
                itemAt = i;
                break;
            }
        }
        if (item == null) {
            return null;
        }

        double price = -1;
        double qtyVal = 0;
        String priceUnit = null;
        List<Tok> toks = new ArrayList<>();
        Matcher m = NUMBER.matcher(s);
        while (m.find()) {
            double v = safeDouble(m.group(1));
            if (v <= 0) {
                continue;
            }
            double mult = 1;
            String suffix = m.group(2);
            if (suffix != null) {
                mult = (suffix.equals("万") || suffix.equalsIgnoreCase("w")) ? 10000 : 1000;
            }
            int endAt = m.end();
            char next = endAt < s.length() ? s.charAt(endAt) : '\0';
            char next2 = endAt + 1 < s.length() ? s.charAt(endAt + 1) : '\0';
            boolean direct = next != '/' && next != '每';
            char u = direct ? next : next2;
            String unit = null;
            boolean qtyLike = false;
            if (u == '个' || u == '颗' || u == '块' || u == '份') {
                unit = "个";
                qtyLike = direct;
            } else if (u == '组') {
                unit = "组";
            } else if (u == '盒') {
                unit = "盒";
            }
            boolean beforeX = m.start() > 0 && (s.charAt(m.start() - 1) == '×' || s.charAt(m.start() - 1) == 'x'
                    || s.charAt(m.start() - 1) == 'X');
            toks.add(new Tok(v * mult, unit, beforeX, qtyLike));
        }

        for (int i = 0; i < toks.size(); i++) {
            Tok t = toks.get(i);
            boolean qty = t.x || t.qtyLike || (("组".equals(t.unit) || "盒".equals(t.unit)) && i < toks.size() - 1);
            if (qty) {
                double qv = t.value;
                if ("组".equals(t.unit)) {
                    qv = t.value * 64;
                } else if ("盒".equals(t.unit)) {
                    qv = t.value * 1728;
                }
                qtyVal = Math.max(qtyVal, qv);
                continue;
            }
            if (price < 0) {
                price = t.value;
                priceUnit = t.unit;
            }
        }
        if (price <= 0) {
            return null;
        }

        double divisor = 1;
        String unit = "个";
        if ("组".equals(priceUnit)) {
            divisor = 64;
            unit = "组";
        } else if ("盒".equals(priceUnit)) {
            divisor = 1728;
            unit = "盒";
        } else if (qtyVal > 1) {
            divisor = qtyVal;
            unit = "份" + (int) qtyVal;
        } else if (s.contains("组")) {
            divisor = 64;
            unit = "组";
        } else if (s.contains("盒") && !s.contains("潜影盒")) {
            divisor = 1728;
            unit = "盒";
        }

        double perPiece = price / divisor;
        if (perPiece <= 0.01 || perPiece > 1_000_000) {
            return null;
        }
        perPiece = Math.round(perPiece * 100.0) / 100.0;
        return new Quote(item, dir, price, unit, perPiece, s);
    }

    /** 只认“整段就是某个别名”，用于物品显示名，避免把“下界合金锭”当成“金锭”。 */
    public static String resolveExact(String text) {
        String n = normalize(text);
        if (n.isEmpty()) {
            return null;
        }
        for (String cn : ALIAS_ORDER) {
            if (normalize(cn).equalsIgnoreCase(n)) {
                return ALIAS.get(cn);
            }
        }
        return null;
    }

    /** 命令里输入的物品名 → 统一 id；也接受 diamond / minecraft:diamond 这种写法。 */
    public static String resolveItem(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String n = normalize(text);
        String exact = resolveExact(n);
        if (exact != null) {
            return exact;
        }
        String low = n.toLowerCase();
        String best = null;
        int bestLen = -1;
        for (String cn : ALIAS_ORDER) {
            String key = normalize(cn).toLowerCase();
            if (!key.isEmpty() && low.contains(key) && key.length() > bestLen) {
                best = ALIAS.get(cn);
                bestLen = key.length();
            }
        }
        if (best != null) {
            return best;
        }
        if (low.startsWith("minecraft:")) {
            return low;
        }
        if (low.matches("[a-z0-9_]+")) {
            return "minecraft:" + low;
        }
        return null;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("§.", "")
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", "")
                .trim();
    }

    private static int firstIndex(String s, String... words) {
        int best = -1;
        for (String w : words) {
            int i = s.indexOf(w);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    private static double safeDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static String describe(Quote q) {
        return q.direction() + " " + Names.of(q.itemId()) + " " + fmt(q.perPiece()) + "/个"
                + "（原 " + fmt(q.rawPrice()) + "/" + q.unit() + "）";
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && v < 1e9) {
            return Long.toString((long) v);
        }
        return String.format("%.1f", v);
    }
}
