package com.doubao.infotools;

import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 每 5 秒把当前信息写成 信息状态.txt，别的工具/看一眼文件就知道情况。 */
public final class InfoStatusWriter {
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static int ticks = 0;
    private static String last = "";

    private InfoStatusWriter() {
    }

    public static void tick(Minecraft mc) {
        ticks++;
        if (ticks % 100 != 0) {
            return;
        }
        if (mc.player == null) {
            return;
        }
        try {
            String body = build(mc);
            if (body.equals(last)) {
                return;
            }
            last = body;
            Path f = Files2.path("信息状态.txt");
            Files.writeString(f, body, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static String build(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 信息工具 v").append(InfoTools.MOD_VERSION).append('\n');
        sb.append("时间=").append(LocalDateTime.now().format(FULL)).append('\n');
        sb.append("资产=").append(AssetLedger.line()).append('\n');
        sb.append("死亡点=").append(DeathTracker.line()).append('\n');
        sb.append("掉落物=").append(DropTracker.line(mc)).append('\n');
        sb.append("告警=").append(KeywordAlert.line()).append('\n');
        sb.append("行情=").append(Market.line()).append('\n');
        sb.append("商店=").append(ShopAd.line()).append('\n');
        sb.append("商店地图=").append(SignShop.count()).append(" 家\n");
        String worth = Worth.hudLine(mc);
        sb.append("背包=").append(worth == null ? "关" : worth).append('\n');
        sb.append("收购榜=").append(BuyBoard.hudLine()).append('\n');
        sb.append("补货缺口=").append(Stock.gapCount(mc)).append('\n');
        sb.append("今日收益=").append(IncomeStats.dayLine(mc)).append('\n');
        sb.append("开箱索引=").append(ChestIndex.count()).append(" 条\n");
        return sb.toString();
    }
}
