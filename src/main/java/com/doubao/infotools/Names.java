package com.doubao.infotools;

/** 物品 id → 中文名，认不出来的就显示英文 id 尾段。 */
public final class Names {
    private Names() {
    }

    public static String of(String id) {
        String path = id;
        int i = path.indexOf(':');
        if (i >= 0) {
            path = path.substring(i + 1);
        }
        return switch (path) {
            case "diamond" -> "钻石";
            case "diamond_block" -> "钻石块";
            case "emerald" -> "绿宝石";
            case "emerald_block" -> "绿宝石块";
            case "iron_ingot" -> "铁锭";
            case "iron_block" -> "铁块";
            case "iron_ore", "deepslate_iron_ore" -> "铁矿石";
            case "raw_iron" -> "粗铁";
            case "gold_ingot" -> "金锭";
            case "gold_block" -> "金块";
            case "gold_ore", "deepslate_gold_ore" -> "金矿石";
            case "raw_gold" -> "粗金";
            case "copper_ingot" -> "铜锭";
            case "copper_block" -> "铜块";
            case "raw_copper" -> "粗铜";
            case "coal" -> "煤炭";
            case "coal_block" -> "煤炭块";
            case "charcoal" -> "木炭";
            case "redstone" -> "红石";
            case "redstone_block" -> "红石块";
            case "lapis_lazuli" -> "青金石";
            case "lapis_block" -> "青金石块";
            case "quartz" -> "下界石英";
            case "quartz_block" -> "石英块";
            case "amethyst_shard" -> "紫水晶碎片";
            case "netherite_scrap" -> "下界合金碎片";
            case "netherite_ingot" -> "下界合金锭";
            case "netherite_block" -> "下界合金块";
            case "netherite_sword" -> "下界合金剑";
            case "netherite_axe" -> "下界合金斧";
            case "netherite_shovel" -> "下界合金锹";
            case "netherite_hoe" -> "下界合金锄";
            case "netherite_helmet" -> "下界合金头盔";
            case "netherite_chestplate" -> "下界合金胸甲";
            case "netherite_leggings" -> "下界合金护腿";
            case "netherite_boots" -> "下界合金靴子";
            case "netherite_upgrade_smithing_template" -> "下界合金升级模板";
            case "gold_nugget" -> "金粒";
            case "iron_nugget" -> "铁粒";
            case "ancient_debris" -> "远古残骸";
            case "raw_diamond" -> "粗钻石";
            case "raw_emerald" -> "粗绿宝石";
            case "obsidian" -> "黑曜石";
            case "cobblestone" -> "圆石";
            case "stone" -> "石头";
            case "deepslate" -> "深板岩";
            case "cobbled_deepslate" -> "深板岩圆石";
            case "dirt" -> "泥土";
            case "gravel" -> "沙砾";
            case "sand" -> "沙子";
            case "oak_log" -> "橡木原木";
            case "oak_planks" -> "橡木木板";
            case "stick" -> "木棍";
            case "torch" -> "火把";
            case "arrow" -> "箭";
            case "bone" -> "骨头";
            case "string" -> "线";
            case "gunpowder" -> "火药";
            case "ender_pearl" -> "末影珍珠";
            case "blaze_rod" -> "烈焰棒";
            case "slime_ball" -> "黏液球";
            case "bread" -> "面包";
            case "cooked_beef" -> "牛排";
            case "cooked_porkchop" -> "熟猪排";
            case "cooked_chicken" -> "熟鸡肉";
            case "apple" -> "苹果";
            case "golden_apple" -> "金苹果";
            case "water_bucket" -> "水桶";
            case "lava_bucket" -> "岩浆桶";
            case "bucket" -> "桶";
            case "diamond_pickaxe" -> "钻石镐";
            case "netherite_pickaxe" -> "下界合金镐";
            case "iron_pickaxe" -> "铁镐";
            case "stone_pickaxe" -> "石镐";
            case "diamond_sword" -> "钻石剑";
            case "shield" -> "盾牌";
            case "elytra" -> "鞘翅";
            case "shulker_box" -> "潜影盒";
            case "chest" -> "箱子";
            default -> path;
        };
    }
}
