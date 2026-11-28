package com.doubao.infotools;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 统一的物品 id 解析。
 *
 * 服务器经常用原版物品加自定义名字（例如把金锭显示成“下界合金锭”），
 * 这时只读注册表 id 会认成 gold_ingot。这里先看物品实际显示名，
 * 显示名能精确对上别名时优先用别名，再把注册表 id 作为兜底。
 */
public final class ItemIds {
    private ItemIds() {
    }

    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String registry = registryId(stack);
        String display = displayName(stack);
        if (display != null && !display.isBlank()) {
            String exact = PriceParser.resolveExact(display);
            if (exact != null && (registry == null || !exact.equals(registry))) {
                return exact;
            }
        }
        return registry;
    }

    public static String registryId(ItemStack stack) {
        try {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public static String displayName(ItemStack stack) {
        try {
            Component name = stack.getCustomName();
            if (name == null) {
                name = stack.getHoverName();
            }
            return name == null ? null : name.getString();
        } catch (Throwable t) {
            return null;
        }
    }
}
