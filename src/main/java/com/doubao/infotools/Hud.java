package com.doubao.infotools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * 屏幕左上角悬浮信息板：不用敲命令，按 I 显示/隐藏。
 * 数据每 2 秒刷新一次缓存，渲染只读缓存，不卡帧。
 */
public final class Hud {
    private static final int X = 6;
    private static final int Y = 6;
    private static final int LINE = 10;

    private static boolean visible = true;
    private static int ticks = 0;
    private static List<String> lines = new ArrayList<>();

    private Hud() {
    }

    public static void init() {
        visible = InfoTools.config().hudEnabled;
        refresh();
    }

    public static boolean visible() {
        return visible;
    }

    public static void setVisible(boolean on) {
        visible = on;
        InfoTools.config().hudEnabled = on;
        InfoTools.saveConfig();
        refresh();
    }

    public static void toggle() {
        setVisible(!visible);
    }

    public static void tick(Minecraft mc) {
        ticks++;
        if (ticks % 40 == 0) {
            refresh();
        }
    }

    public static void refresh() {
        List<String> out = new ArrayList<>();
        out.add("信息工具 v" + InfoTools.MOD_VERSION + "   按 I 隐藏");
        try {
            int ping = ServerInfo.ping();
            out.add("服务器 " + ServerInfo.name() + (ping >= 0 ? "   延迟 " + ping + "ms" : "")
                    + "   在线 " + ServerInfo.online() + " 人");
        } catch (Throwable ignored) {
        }
        out.add(AssetLedger.line());
        out.add("掉落 " + DropTracker.line(Minecraft.getInstance()) + "   死亡点 "
                + DeathTracker.shortLine(Minecraft.getInstance()));
        out.add("行情 " + Market.hudLine());
        out.add("便宜货 " + Market.cheapCount() + " 个   商店广告 " + ShopAd.count() + " 条");
        String worth = Worth.hudLine(Minecraft.getInstance());
        if (worth != null) {
            out.add(worth);
        }
        String shop = SignShop.hudLine(Minecraft.getInstance());
        if (shop != null) {
            out.add(shop);
        }
        out.add(BuyBoard.hudLine());
        int gap = Stock.gapCount(Minecraft.getInstance());
        if (gap > 0) {
            out.add("补货 缺 " + gap + " 种　" + IncomeStats.dayLine(Minecraft.getInstance()));
        } else {
            out.add(IncomeStats.dayLine(Minecraft.getInstance()));
        }
        lines = out;
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!visible) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null || lines.isEmpty()) {
            return;
        }
        try {
            int w = 0;
            for (String s : lines) {
                w = Math.max(w, mc.font.width(s));
            }
            int pad = 4;
            int x2 = X + w + pad * 2;
            int y2 = Y + lines.size() * LINE + pad * 2 - 2;
            g.fill(X, Y, x2, y2, 0x90000000);
            for (int i = 0; i < lines.size(); i++) {
                int color = i == 0 ? 0xFFFFD24A : 0xFFEEEEEE;
                g.text(mc.font, lines.get(i), X + pad, Y + pad + i * LINE, color);
            }
        } catch (Throwable ignored) {
        }
    }
}
