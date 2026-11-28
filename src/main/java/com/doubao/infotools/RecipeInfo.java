package com.doubao.infotools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 配方/用途速查：用客户端配方书查“怎么合成”和“能做什么”。 */
public final class RecipeInfo {
    private RecipeInfo() {
    }

    public static List<String> cardLines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        try {
            if (stack == null || stack.isEmpty()) {
                return out;
            }
            Item target = stack.getItem();
            List<String> recipes = recipesFor(target, 2);
            if (recipes.isEmpty()) {
                out.add("§7合成 §8配方书里没找到");
            } else {
                out.add("§7合成 §f" + String.join(" ｜ ", recipes));
            }
            List<String> uses = usedFor(target, 2);
            if (!uses.isEmpty()) {
                out.add("§7用于 §f" + String.join("、", uses));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static String summary(String itemText) {
        String id = Market.resolveItemName(itemText == null ? "" : itemText);
        if (id == null) {
            return "认不出「" + itemText + "」，换个说法（钻石、铁、木板…）。";
        }
        Minecraft mc = Minecraft.getInstance();
        Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
        if (item == null) {
            return Names.of(id) + "：注册表里没有这个物品。";
        }
        ItemStack stack = new ItemStack(item);
        List<String> recipes = recipesFor(item, 3);
        List<String> uses = usedFor(item, 4);
        StringBuilder sb = new StringBuilder(Names.of(id)).append("：");
        if (recipes.isEmpty()) {
            sb.append("\n合成：配方书里没找到（可能是掉落物/交易/烧炼，或配方书没同步）。");
        } else {
            sb.append("\n合成：");
            for (String r : recipes) {
                sb.append('\n').append(r);
            }
        }
        if (uses.isEmpty()) {
            sb.append("\n用途：没在配方书里找到它作为材料。");
        } else {
            sb.append("\n用途：").append(String.join("、", uses));
        }
        sb.append("\n配方来自客户端配方书，只覆盖你已解锁的配方。");
        return sb.toString();
    }

    private static List<String> recipesFor(Item target, int limit) {
        List<String> out = new ArrayList<>();
        try {
            for (RecipeCollection c : collections()) {
                for (RecipeDisplayEntry entry : c.getRecipes()) {
                    ItemStack result = resultOf(entry.display().result());
                    if (result.isEmpty() || result.getItem() != target) {
                        continue;
                    }
                    String text = ingredientText(entry);
                    if (!text.isEmpty()) {
                        out.add(text + " → " + (result.getCount() > 1 ? result.getCount() + "×" : "")
                                + Names.of(idOf(result)));
                    }
                    if (out.size() >= limit) {
                        return out;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static List<String> usedFor(Item target, int limit) {
        List<String> out = new ArrayList<>();
        ItemStack probe = new ItemStack(target);
        try {
            for (RecipeCollection c : collections()) {
                for (RecipeDisplayEntry entry : c.getRecipes()) {
                    List<Ingredient> req = entry.craftingRequirements().orElse(List.of());
                    boolean uses = false;
                    for (Ingredient ing : req) {
                        if (ing != null && ing.test(probe)) {
                            uses = true;
                            break;
                        }
                    }
                    if (!uses) {
                        continue;
                    }
                    ItemStack result = resultOf(entry.display().result());
                    if (!result.isEmpty() && result.getItem() != target) {
                        String name = Names.of(idOf(result));
                        if (!out.contains(name)) {
                            out.add(name);
                        }
                    }
                    if (out.size() >= limit) {
                        return out;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String ingredientText(RecipeDisplayEntry entry) {
        try {
            List<Ingredient> req = entry.craftingRequirements().orElse(List.of());
            if (req.isEmpty()) {
                return "";
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Ingredient ing : req) {
                if (ing == null || ing.isEmpty()) {
                    continue;
                }
                ItemStack first = ing.items().findFirst().map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
                if (!first.isEmpty()) {
                    counts.merge(Names.of(idOf(first)), 1, Integer::sum);
                }
            }
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (n++ >= 5) {
                    sb.append("…");
                    break;
                }
                if (n > 1) {
                    sb.append('+');
                }
                sb.append(e.getKey());
                if (e.getValue() > 1) {
                    sb.append('×').append(e.getValue());
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static List<RecipeCollection> collections() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return List.of();
        }
        try {
            return mc.player.getRecipeBook().getCollections();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static ItemStack resultOf(SlotDisplay display) {
        try {
            if (display instanceof SlotDisplay.ItemStackSlotDisplay s) {
                return s.stack().create();
            }
            if (display instanceof SlotDisplay.ItemSlotDisplay i) {
                return new ItemStack(i.item().value());
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static String idOf(ItemStack stack) {
        return QuickCard.idOf(stack);
    }
}
