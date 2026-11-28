package com.doubao.infotools;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 箱子内容索引：客户端不能凭空读到没开的箱子，所以只记你亲手打开过的箱子/木桶/潜影盒。
 * 打开时把容器格里的东西写进文件，以后 /info chest 钻石 就能查上次在哪个箱子看到。
 */
public final class ChestIndex {
    private ChestIndex() {
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            try {
                if (!InfoTools.config().chestIndexEnabled) {
                    return;
                }
                if (!(screen instanceof ContainerScreen) && !(screen instanceof ShulkerBoxScreen)) {
                    return;
                }
                record(client, screen);
            } catch (Throwable ignored) {
            }
        });
    }

    private static void record(Minecraft mc, Screen screen) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        String title = screen.getTitle() == null ? "容器" : screen.getTitle().getString();
        BlockPos pos = null;
        if (mc.hitResult instanceof BlockHitResult hit) {
            pos = hit.getBlockPos();
        }
        if (pos == null) {
            pos = mc.player.blockPosition();
            title = title + "（坐标近似）";
        }
        Map<String, Integer> contents = new LinkedHashMap<>();
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return;
        }
        for (Slot slot : container.getMenu().slots) {
            try {
                if (slot.container instanceof Inventory) {
                    continue;
                }
                ItemStack stack = slot.getItem();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                String id = QuickCard.idOf(stack);
                if (id != null) {
                    contents.merge(id, stack.getCount(), Integer::sum);
                }
            } catch (Throwable ignored) {
            }
        }
        if (contents.isEmpty()) {
            return;
        }
        try {
            String when = Long.toString(System.currentTimeMillis());
            String dim = mc.level.dimension().identifier().toString();
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : contents.entrySet()) {
                sb.append(when).append('|').append(ServerInfo.key()).append('|').append(dim).append('|')
                        .append(pos.getX()).append('|').append(pos.getY()).append('|').append(pos.getZ()).append('|')
                        .append(clean(title)).append('|').append(e.getKey()).append('|').append(e.getValue()).append('\n');
            }
            Files.writeString(file(), sb.toString(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }

    private static Path file() {
        return Files2.path("箱子索引.txt");
    }

    public static String query(String itemText, Minecraft mc) {
        String id = Market.resolveItemName(itemText == null ? "" : itemText);
        if (id == null) {
            return "认不出「" + itemText + "」，换个说法（钻石、铁、煤…）。";
        }
        List<Row> rows = read().stream().filter(r -> r.item.equals(id)).toList();
        if (rows.isEmpty()) {
            return Names.of(id) + "：还没有开箱记录。打开箱子、木桶或潜影盒后会自动记。";
        }
        Map<String, Row> latest = new LinkedHashMap<>();
        for (Row r : rows) {
            String key = r.dim + "|" + r.x + "," + r.y + "," + r.z;
            Row old = latest.get(key);
            if (old == null || r.time >= old.time) {
                latest.put(key, r);
            }
        }
        List<Row> list = new ArrayList<>(latest.values());
        list.sort(Comparator.comparingLong((Row r) -> r.time).reversed());
        StringBuilder sb = new StringBuilder(Names.of(id)).append("：在 ").append(list.size()).append(" 个箱子见过");
        int n = 0;
        for (Row r : list) {
            if (n++ >= 10) {
                break;
            }
            sb.append('\n').append(r.count).append(" 个  ").append(dimShort(r.dim)).append(' ')
                    .append(r.x).append(',').append(r.y).append(',').append(r.z).append("  ")
                    .append(r.title).append("  ").append(ago(r.time));
        }
        return sb.toString();
    }

    public static String list() {
        List<Row> rows = read();
        if (rows.isEmpty()) {
            return "还没有开箱记录。打开箱子/木桶/潜影盒后自动记。";
        }
        Map<String, Row> latest = new LinkedHashMap<>();
        for (Row r : rows) {
            String key = r.dim + "|" + r.x + "," + r.y + "," + r.z + "|" + r.item;
            Row old = latest.get(key);
            if (old == null || r.time >= old.time) {
                latest.put(key, r);
            }
        }
        List<Row> list = new ArrayList<>(latest.values());
        list.sort(Comparator.comparingLong((Row r) -> r.time).reversed());
        StringBuilder sb = new StringBuilder("开箱索引 ").append(list.size()).append(" 条：");
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            Row r = list.get(i);
            sb.append('\n').append(Names.of(r.item)).append(" ×").append(r.count).append("  ")
                    .append(r.x).append(',').append(r.y).append(',').append(r.z).append("  ")
                    .append(r.title).append("  ").append(ago(r.time));
        }
        return sb.toString();
    }

    private record Row(long time, String server, String dim, int x, int y, int z,
                      String title, String item, int count) {
    }

    private static List<Row> read() {
        List<Row> out = new ArrayList<>();
        try {
            Path f = file();
            if (!Files.exists(f)) {
                return out;
            }
            String want = ServerInfo.key();
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String[] p = raw.trim().split("\\|", 9);
                if (p.length < 9) {
                    continue;
                }
                try {
                    Row r = new Row(Long.parseLong(p[0]), p[1], p[2], Integer.parseInt(p[3]),
                            Integer.parseInt(p[4]), Integer.parseInt(p[5]), p[6], p[7], Integer.parseInt(p[8]));
                    if (want.isEmpty() || r.server.equals(want)) {
                        out.add(r);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static String clear() {
        try {
            Files.deleteIfExists(file());
        } catch (Throwable ignored) {
        }
        return "本号开箱索引已清空。";
    }

    public static int count() {
        return read().size();
    }

    private static String clean(String s) {
        return s.replace("|", "/").replace("\n", " ").replace("\r", " ");
    }

    private static String dimShort(String dim) {
        return dim.contains("nether") ? "下界" : dim.contains("end") ? "末地" : "主世界";
    }

    private static String ago(long time) {
        long sec = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (sec < 60) {
            return sec + "秒前";
        }
        if (sec < 3600) {
            return sec / 60 + "分前";
        }
        if (sec < 86400) {
            return sec / 3600 + "小时前";
        }
        return sec / 86400 + "天前";
    }
}
