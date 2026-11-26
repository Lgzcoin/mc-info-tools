package com.doubao.infotools;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** 鼠标悬停物品时，在 tooltip 末尾补两行：行情 + 附近最低价店。 */
public final class QuickCard {
    private QuickCard() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            try {
                if (!InfoTools.config().quickCardEnabled || stack == null || stack.isEmpty() || lines == null) {
                    return;
                }
                String id = idOf(stack);
                if (id == null) {
                    return;
                }
                Market.ItemPrice p = Market.priceOf(id);
                SignShop.Shop shop = SignShop.bestSell(id);
                if ((p == null || !p.any()) && shop == null) {
                    return;
                }
                if (p != null && p.any()) {
                    StringBuilder sb = new StringBuilder("§8[速查] §7行情 ");
                    if (p.sellN() > 0) {
                        sb.append("卖 ").append(num(p.sellMed())).append("/个");
                    }
                    if (p.buyN() > 0) {
                        sb.append(p.sellN() > 0 ? "  " : "").append("收 ").append(num(p.buyMed())).append("/个");
                    }
                    sb.append(" §8(").append(p.sellN() + p.buyN()).append(" 条)");
                    lines.add(Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY));
                }
                if (shop != null) {
                    Minecraft mc = Minecraft.getInstance();
                    double dx = shop.x() + 0.5 - (mc.player == null ? 0 : mc.player.getX());
                    double dz = shop.z() + 0.5 - (mc.player == null ? 0 : mc.player.getZ());
                    int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                    lines.add(Component.literal("§8[速查] §7附近最低卖 " + num(shop.sellPer())
                            + "/个 §8" + (dist >= 0 ? dist + "m" : "别的维度")));
                }
                if (p != null && p.any()) {
                    lines.add(Component.literal("§8[速查] §7这格 " + stack.getCount() + " 个 · 参考 "
                            + Worth.money(p.reference() * stack.getCount())).withStyle(ChatFormatting.GRAY));
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String num(double v) {
        if (v == Math.floor(v) && v < 1e9) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
