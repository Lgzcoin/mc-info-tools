package com.doubao.infotools;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 配置文件：游戏目录/信息工具配置.txt（UTF-8，改完 20 秒内自动生效，不用重启）。
 */
public final class InfoConfig {
    public boolean autoBroadcast = true;
    public boolean alertEnabled = true;
    public boolean priceEnabled = true;
    public boolean hudEnabled = true;
    public boolean quickCardEnabled = true;
    public boolean shopMapEnabled = true;
    public boolean worthHud = true;
    public int priceDiscountPercent = 80;
    public final List<String> shopKeywords = new ArrayList<>(Arrays.asList(
            "商店", "店铺", "摊位", "促销", "特价", "甩卖", "上新", "批发"));
    public final List<String> keywords = new ArrayList<>(Arrays.asList(
            "管理员", "清人", "重启", "封号", "封禁", "TPA", "交易", "收人"));
    public int assetSnapSeconds = 60;
    public int dropWarnSeconds = 60;

    private InfoConfig() {
    }

    public static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("信息工具配置.txt");
    }

    public static InfoConfig load() {
        InfoConfig c = new InfoConfig();
        try {
            Path f = file();
            if (!Files.exists(f)) {
                c.save();
                return c;
            }
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int i = line.indexOf('=');
                if (i < 0) {
                    i = line.indexOf('＝');
                }
                if (i < 0) {
                    continue;
                }
                String key = line.substring(0, i).trim();
                String val = line.substring(i + 1).trim();
                switch (key) {
                    case "自动播报" -> c.autoBroadcast = bool(val, true);
                    case "关键词告警" -> c.alertEnabled = bool(val, true);
                    case "比价" -> c.priceEnabled = bool(val, true);
                    case "悬浮窗" -> c.hudEnabled = bool(val, true);
                    case "速查卡" -> c.quickCardEnabled = bool(val, true);
                    case "商店地图" -> c.shopMapEnabled = bool(val, true);
                    case "背包市值" -> c.worthHud = bool(val, true);
                    case "比价便宜线" -> c.priceDiscountPercent = clamp(parseInt(val, 80), 30, 99);
                    case "商店关键词" -> {
                        c.shopKeywords.clear();
                        for (String s2 : val.split("[,，、]")) {
                            String k = s2.trim();
                            if (!k.isEmpty()) {
                                c.shopKeywords.add(k);
                            }
                        }
                    }
                    case "关键词" -> {
                        c.keywords.clear();
                        for (String s : val.split("[,，、]")) {
                            String k = s.trim();
                            if (!k.isEmpty()) {
                                c.keywords.add(k);
                            }
                        }
                    }
                    case "资产快照秒" -> c.assetSnapSeconds = clamp(parseInt(val, 60), 10, 3600);
                    case "掉落提醒秒" -> c.dropWarnSeconds = clamp(parseInt(val, 60), 5, 600);
                    default -> {
                    }
                }
            }
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("读 信息工具配置.txt 失败，用默认值", t);
        }
        return c;
    }

    public void save() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# 信息工具 v").append(InfoTools.MOD_VERSION).append('\n');
            sb.append("# 自动播报：开 / 关（死亡、掉落将消失、关键词命中时自动提示）\n");
            sb.append("# 关键词告警：开 / 关（聊天命中关键词时提示并记录）\n");
            sb.append("# 悬浮窗：开 / 关（屏幕左上角信息板，游戏里按 I 也能开关）\n");
            sb.append("# 速查卡：开 / 关（鼠标悬停物品时补行情和附近最低价）\n");
            sb.append("# 商店地图：开 / 关（走近告示牌商店自动记坐标和价格）\n");
            sb.append("# 背包市值：开 / 关（悬浮板显示背包总估值）\n");
            sb.append("# 比价：开 / 关（聊天报价自动记录，便宜货提醒）\n");
            sb.append("# 比价便宜线：卖价低于历史均价的百分之几就提醒（30~99）\n");
            sb.append("# 商店关键词：聊天里出现这些词就把整条记成商店广告（逗号分隔）\n");
            sb.append("# 关键词：用逗号分隔，聊天里出现就告警\n");
            sb.append("# 资产快照秒：多久记一次背包+仓库资产（10~3600）\n");
            sb.append("# 掉落提醒秒：掉落物还剩多少秒时提醒（5~600）\n\n");
            sb.append("自动播报 = ").append(autoBroadcast ? "开" : "关").append('\n');
            sb.append("关键词告警 = ").append(alertEnabled ? "开" : "关").append('\n');
            sb.append("悬浮窗 = ").append(hudEnabled ? "开" : "关").append('\n');
            sb.append("速查卡 = ").append(quickCardEnabled ? "开" : "关").append('\n');
            sb.append("商店地图 = ").append(shopMapEnabled ? "开" : "关").append('\n');
            sb.append("背包市值 = ").append(worthHud ? "开" : "关").append('\n');
            sb.append("比价 = ").append(priceEnabled ? "开" : "关").append('\n');
            sb.append("比价便宜线 = ").append(priceDiscountPercent).append('\n');
            sb.append("商店关键词 = ").append(String.join(",", shopKeywords)).append('\n');
            sb.append("关键词 = ").append(String.join(",", keywords)).append('\n');
            sb.append("资产快照秒 = ").append(assetSnapSeconds).append('\n');
            sb.append("掉落提醒秒 = ").append(dropWarnSeconds).append('\n');
            Files.writeString(file(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("写 信息工具配置.txt 失败", t);
        }
    }

    private static boolean bool(String v, boolean def) {
        String s = v.toLowerCase().trim();
        if (s.equals("开") || s.equals("true") || s.equals("1") || s.equals("on") || s.equals("是")) {
            return true;
        }
        if (s.equals("关") || s.equals("false") || s.equals("0") || s.equals("off") || s.equals("否")) {
            return false;
        }
        return def;
    }

    private static int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
