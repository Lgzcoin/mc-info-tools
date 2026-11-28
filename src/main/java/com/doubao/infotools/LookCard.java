package com.doubao.infotools;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 按键速查卡：准星对着物品/物品展示框，或在背包、箱子里用鼠标指着一格，按 V
 * 就在屏幕上弹出这张卡（物品 ID、耐久、行情、附近最低价、这格参考价），8 秒后自动消失。
 * 不是鼠标悬停 tooltip，必须按键才显示。
 */
public final class LookCard {
    private static final long SHOW_MS = 8000L;
    private static final int LINE = 10;
    private static final int PAD = 5;

    private static ItemStack item = ItemStack.EMPTY;
    private static List<String> lines = new ArrayList<>();
    private static long until = 0L;
    private static Field hoveredSlotField;
    private static boolean hoveredSlotLooked;

    private LookCard() {
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                ScreenKeyboardEvents.afterKeyPress(screen).register(LookCard::onScreenKey);
            }
            ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, tickDelta) -> render(g));
        });
    }

    private static void onScreenKey(Screen screen, KeyEvent event) {
        try {
            if (!(screen instanceof AbstractContainerScreen<?>)) {
                return;
            }
            if (InfoKeys.look == null || !InfoKeys.look.matches(event)) {
                return;
            }
            if (hasFocusedTextBox(screen)) {
                return;
            }
            trigger(Minecraft.getInstance(), screen);
        } catch (Throwable ignored) {
        }
    }

    /** 主入口：世界里的 V 键和界面里的 V 键都走这里。 */
    public static void trigger(Minecraft mc, Screen screen) {
        try {
            if (!InfoTools.config().quickCardEnabled) {
                return;
            }
            ItemStack stack = findItem(mc, screen);
            if (stack == null || stack.isEmpty()) {
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(Component.literal(
                            "§7[速查] 把准星对着物品/展示框，或在背包、箱子里指着物品再按 V"));
                }
                return;
            }
            item = stack.copy();
            item.setCount(stack.getCount());
            lines = buildLines(mc, item);
            until = System.currentTimeMillis() + SHOW_MS;
        } catch (Throwable ignored) {
        }
    }

    private static ItemStack findItem(Minecraft mc, Screen screen) {
        // 1) 背包 / 箱子 / 商店界面：鼠标指着的格子
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            // 界面开着就只认鼠标指着的格子，避免误显示界面后面的方块
            ItemStack hovered = hoveredSlot(containerScreen);
            return hovered == null ? ItemStack.EMPTY : hovered;
        }
        // 2) 世界：准星对着物品展示框或掉落物
        Entity picked = mc.crosshairPickEntity;
        if (picked instanceof ItemFrame frame) {
            return frame.getItem();
        }
        if (picked instanceof ItemEntity drop) {
            return drop.getItem();
        }
        // 3) 准星对着方块：按方块的物品形态显示（矿石方块、箱子等也能查）
        if (mc.hitResult instanceof BlockHitResult hit && mc.level != null) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            ItemStack blockItem = state.getBlock().asItem().getDefaultInstance();
            if (!blockItem.isEmpty()) {
                return blockItem;
            }
        }
        // 4) 什么都没对着：查手上拿的物品，至少不弹空
        return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
    }

    private static ItemStack hoveredSlot(AbstractContainerScreen<?> screen) {
        try {
            if (!hoveredSlotLooked) {
                hoveredSlotLooked = true;
                hoveredSlotField = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
                hoveredSlotField.setAccessible(true);
            }
            if (hoveredSlotField == null) {
                return ItemStack.EMPTY;
            }
            Object value = hoveredSlotField.get(screen);
            if (value instanceof Slot slot) {
                return slot.getItem();
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasFocusedTextBox(Screen screen) {
        try {
            for (var child : screen.children()) {
                if (child instanceof EditBox box && box.isFocused()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static List<String> buildLines(Minecraft mc, ItemStack stack) {
        List<String> out = new ArrayList<>();
        String id = idOf(stack);
        if (id == null) {
            id = "unknown";
        }
        String name = Names.of(id);
        out.add("§e" + name + "  §7" + id);
        out.add("§7数量 " + stack.getCount() + "/" + stack.getMaxStackSize());

        if (stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            out.add("§7耐久 §f" + (max - stack.getDamageValue()) + "§7/§f" + max);
        }

        Market.ItemPrice price = id == null ? null : Market.priceOf(id);
        if (price != null && price.any()) {
            StringBuilder sb = new StringBuilder("§7行情 ");
            if (price.sellN() > 0) {
                sb.append("§a卖 ").append(num(price.sellMed())).append("/个");
            }
            if (price.buyN() > 0) {
                sb.append(price.sellN() > 0 ? "  " : "").append("§b收 ").append(num(price.buyMed())).append("/个");
            }
            sb.append(" §8(").append(price.sellN() + price.buyN()).append(" 条)");
            out.add(sb.toString());
        } else {
            out.add("§7行情 §8暂无（聊天里还没抓到这物的报价）");
        }

        SignShop.Shop shop = id == null ? null : SignShop.bestSell(id);
        if (shop != null) {
            out.add("§7附近最低卖 §f" + num(shop.sellPer()) + "/个 §8" + where(mc, shop));
        } else {
            out.add("§7附近 §8暂未扫到卖这物的告示牌店");
        }

        if (price != null && price.any()) {
            out.add("§7这格参考 §f" + Worth.money(price.reference() * stack.getCount()));
        }

        if (id != null) {
            BuyBoard.Offer buyer = BuyBoard.bestOffer(id);
            if (buyer != null) {
                out.add("§7最高收 §f" + num(buyer.price()) + "/个 §8"
                        + (buyer.shop() ? buyer.where() : "聊天"));
            }
        }
        out.addAll(RecipeInfo.cardLines(stack));
        return out;
    }

    private static String where(Minecraft mc, SignShop.Shop shop) {
        double dx = shop.x() + 0.5 - (mc.player == null ? 0 : mc.player.getX());
        double dz = shop.z() + 0.5 - (mc.player == null ? 0 : mc.player.getZ());
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (mc.player != null && mc.player.level() != null
                && shop.dim() != null
                && !shop.dim().equals(mc.player.level().dimension().identifier().toString())) {
            return "别的维度";
        }
        String dir = Math.abs(dx) > Math.abs(dz)
                ? (dx >= 0 ? "往东" : "往西")
                : (dz >= 0 ? "往南" : "往北");
        return dist + "m " + dir;
    }

    public static void render(GuiGraphicsExtractor g) {
        try {
            if (System.currentTimeMillis() >= until || lines.isEmpty()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.font == null) {
                return;
            }
            int sw = mc.getWindow().getGuiScaledWidth();
            int width = 0;
            for (String line : lines) {
                width = Math.max(width, mc.font.width(strip(line)));
            }
            int w = Math.min(Math.max(width + PAD * 2, 210), sw - 16);
            int x = sw - w - 8;
            int y = 26;
            int h = lines.size() * LINE + PAD * 2 - 2;
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF3A3A3A);
            g.fill(x, y, x + w, y + h, 0xD0000000);
            int textX = x + PAD;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int dx = textX;
                int color = 0xFFFFFFFF;
                for (String part : line.split("§")) {
                    if (part.isEmpty()) {
                        continue;
                    }
                    char code = part.charAt(0);
                    String text = part.substring(1);
                    if (text.isEmpty()) {
                        continue;
                    }
                    color = codeColor(code);
                    g.text(mc.font, text, dx, y + PAD + i * LINE, color);
                    dx += mc.font.width(text);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int codeColor(char code) {
        return switch (code) {
            case '0' -> 0xFF000000;
            case '1' -> 0xFF0000AA;
            case '2' -> 0xFF00AA00;
            case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000;
            case '5' -> 0xFFAA00AA;
            case '6' -> 0xFFFFAA00;
            case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555;
            case '9' -> 0xFF5555FF;
            case 'a' -> 0xFF55FF55;
            case 'b' -> 0xFF55FFFF;
            case 'c' -> 0xFFFF5555;
            case 'd' -> 0xFFFF55FF;
            case 'e' -> 0xFFFFFF55;
            case 'f' -> 0xFFFFFFFF;
            default -> 0xFFFFFFFF;
        };
    }

    private static String strip(String s) {
        return s.replaceAll("§.", "");
    }

    private static String idOf(ItemStack stack) {
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
