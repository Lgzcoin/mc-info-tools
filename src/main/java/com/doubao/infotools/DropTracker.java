package com.doubao.infotools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 掉落物倒计时：原版掉落物 5 分钟（6000 tick）后消失。
 * 客户端看不到服务器侧的出生时间，所以用实体自己的 tickCount 估算；
 * 刚进服就存在的旧掉落物会显示得偏久，这是正常的。
 */
public final class DropTracker {
    public static final int DESPAWN_TICKS = 6000;

    private static int ticks = 0;
    private static String scene = "";
    private static final Set<Integer> WARNED = new HashSet<>();

    private DropTracker() {
    }

    public static void tick(Minecraft mc) {
        ticks++;
        if (ticks % 20 != 0) {
            return;
        }
        try {
            LocalPlayer p = mc.player;
            ClientLevel level = mc.level;
            if (p == null || level == null) {
                WARNED.clear();
                return;
            }
            String nowScene = Files2.name() + "|" + level.dimension().identifier();
            if (!nowScene.equals(scene)) {
                scene = nowScene;
                WARNED.clear();
            }
            int warnTicks = Math.max(5, InfoTools.config().dropWarnSeconds) * 20;
            for (ItemEntity e : nearby(mc)) {
                int remain = remain(e);
                int id = e.getId();
                if (remain <= warnTicks && !WARNED.contains(id)) {
                    WARNED.add(id);
                    if (InfoTools.config().autoBroadcast) {
                        Notify.alert("掉落物快消失了：" + label(e) + "  剩 " + mmss(remain)
                                + "  (" + e.getX() + "," + e.getY() + "," + e.getZ() + ")");
                    }
                }
            }
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("掉落物扫描出错（已忽略）", t);
        }
    }

    private static List<ItemEntity> nearby(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) {
            return List.of();
        }
        try {
            return level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(32.0), e -> e != null);
        } catch (Throwable t) {
            return List.of();
        }
    }

    public static int remain(ItemEntity e) {
        int r = DESPAWN_TICKS - e.tickCount;
        return Math.max(0, Math.min(DESPAWN_TICKS, r));
    }

    public static String listText(Minecraft mc) {
        List<ItemEntity> items = nearby(mc);
        if (items.isEmpty()) {
            return "附近 32 格没有掉落物。";
        }
        List<ItemEntity> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(DropTracker::remain));
        LocalPlayer p = mc.player;
        StringBuilder sb = new StringBuilder("附近掉落物 " + sorted.size() + " 件：");
        int shown = Math.min(8, sorted.size());
        for (int i = 0; i < shown; i++) {
            ItemEntity e = sorted.get(i);
            int dist = 0;
            if (p != null) {
                dist = (int) Math.round(Math.sqrt(p.distanceToSqr(e)));
            }
            sb.append('\n').append(label(e)).append("  剩 ").append(mmss(remain(e)))
                    .append("  距 ").append(dist).append(" 格  (")
                    .append(e.getX()).append(',').append((int) e.getY()).append(',').append(e.getZ()).append(')');
        }
        if (sorted.size() > shown) {
            sb.append("\n…还有 ").append(sorted.size() - shown).append(" 件");
        }
        return sb.toString();
    }

    public static String line(Minecraft mc) {
        List<ItemEntity> items = nearby(mc);
        if (items.isEmpty()) {
            return "0 件";
        }
        int min = DESPAWN_TICKS;
        for (ItemEntity e : items) {
            min = Math.min(min, remain(e));
        }
        return items.size() + " 件，最近剩 " + mmss(min);
    }

    public static int count(Minecraft mc) {
        return nearby(mc).size();
    }

    private static String label(ItemEntity e) {
        try {
            ItemStack st = e.getItem();
            if (st == null || st.isEmpty()) {
                return "物品";
            }
            return Names.of(ItemIds.of(st))
                    + "×" + st.getCount();
        } catch (Throwable t) {
            return "物品";
        }
    }

    public static String mmss(int ticksLeft) {
        int sec = ticksLeft / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }
}
