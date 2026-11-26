package com.doubao.infotools;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告示牌商店地图：扫已加载区块里的告示牌，识别商店价格，按坐标存下来。
 * 只读，不改方块、不发包；等价于你路过时“看一眼记下来”。
 */
public final class SignShop {
    private static final int RADIUS_CHUNKS = 5;
    private static final int SCAN_INTERVAL = 100; // 5 秒
    private static final int MOVE_SCAN = 12;
    private static final long CACHE_MS = 5000L;

    public record Shop(String server, String dim, int x, int y, int z, String item,
                       double buyPer, double sellPer, long updated, String raw) {
    }

    private static final Map<String, Shop> OWN = new LinkedHashMap<>();
    private static final Map<String, String> LAST_TEXT = new HashMap<>();
    private static boolean loaded = false;
    private static String loadedName = "";
    private static int ticks = 0;
    private static int lastScanTick = -1000;
    private static Vec3 lastScanPos = null;
    private static String lastDim = "";

    private static List<Shop> cache = null;
    private static long cacheAt = 0;

    private SignShop() {
    }

    public static boolean enabled() {
        return InfoTools.config().shopMapEnabled;
    }

    private static String key(String server, String dim, BlockPos p) {
        return server + "|" + dim + "|" + p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static String key(Shop s) {
        return s.server() + "|" + s.dim() + "|" + s.x() + "," + s.y() + "," + s.z();
    }

    private static Path ownFile() {
        return Files2.path("商店地图.txt");
    }

    private static void loadOwn() {
        String name = Files2.name();
        if (loaded && name.equals(loadedName)) {
            return;
        }
        loaded = true;
        loadedName = name;
        OWN.clear();
        LAST_TEXT.clear();
        try {
            Path f = ownFile();
            if (!Files.exists(f)) {
                return;
            }
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                Shop s = parseLine(raw);
                if (s != null) {
                    OWN.put(key(s), s);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Shop parseLine(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        String[] p = line.split("\\|", 10);
        if (p.length < 9) {
            return null;
        }
        try {
            return new Shop(p[1], p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]),
                    p[6], Double.parseDouble(p[7]), Double.parseDouble(p[8]),
                    p.length >= 10 ? Long.parseLong(p[0]) : 0L, p.length >= 10 ? p[9] : "");
        } catch (Throwable t) {
            return null;
        }
    }

    private static String toLine(Shop s) {
        String raw = s.raw() == null ? "" : s.raw().replace("|", "/").replace('\n', ' ');
        return s.updated() + "|" + s.server() + "|" + s.dim() + "|" + s.x() + "|" + s.y() + "|" + s.z()
                + "|" + s.item() + "|" + s.buyPer() + "|" + s.sellPer() + "|" + raw;
    }

    private static void saveOwn() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# 信息工具 商店地图（告示牌扫描，只读）\n");
            for (Shop s : OWN.values()) {
                sb.append(toLine(s)).append('\n');
            }
            Files.writeString(ownFile(), sb.toString(), StandardCharsets.UTF_8);
            cacheAt = 0;
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("保存商店地图失败", t);
        }
    }

    public static void tick(Minecraft mc) {
        if (!enabled() || mc.player == null || mc.level == null) {
            return;
        }
        loadOwn();
        ticks++;
        Vec3 now = mc.player.position();
        String dim = mc.level.dimension().identifier().toString();
        boolean moved = lastScanPos == null || lastScanPos.distanceToSqr(now) > MOVE_SCAN * MOVE_SCAN;
        boolean periodic = ticks % SCAN_INTERVAL == 0;
        boolean dimChanged = !dim.equals(lastDim);
        if (!periodic && !dimChanged && !(moved && ticks - lastScanTick >= 40)) {
            return;
        }
        lastScanPos = now;
        lastDim = dim;
        lastScanTick = ticks;
        scan(mc);
    }

    public static void forceScan(Minecraft mc) {
        loadOwn();
        scan(mc);
    }

    private static void scan(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        boolean changed = false;
        String dim = level.dimension().identifier().toString();
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        for (int cx = pcx - RADIUS_CHUNKS; cx <= pcx + RADIUS_CHUNKS; cx++) {
            for (int cz = pcz - RADIUS_CHUNKS; cz <= pcz + RADIUS_CHUNKS; cz++) {
                LevelChunk chunk;
                try {
                    chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                } catch (Throwable t) {
                    continue;
                }
                if (chunk == null) {
                    continue;
                }
                List<BlockEntity> list;
                try {
                    list = new ArrayList<>(chunk.getBlockEntities().values());
                } catch (Throwable t) {
                    continue;
                }
                for (BlockEntity be : list) {
                    if (!(be instanceof SignBlockEntity sign)) {
                        continue;
                    }
                    try {
                        List<String> lines = new ArrayList<>();
                        for (var c : sign.getFrontText().getMessages(false)) {
                            lines.add(c == null ? "" : c.getString());
                        }
                        String textKey = String.join("\n", lines);
                        BlockPos pos = sign.getBlockPos();
                        String k = key(ServerInfo.key(), dim, pos);
                        if (textKey.equals(LAST_TEXT.get(k))) {
                            continue;
                        }
                        LAST_TEXT.put(k, textKey);
                        if (!looksLikeShop(lines)) {
                            if (OWN.remove(k) != null) {
                                changed = true;
                            }
                            continue;
                        }
                        ShopSignParser.Parsed parsed = ShopSignParser.parse(sanitizeFirst(lines), SignShop::resolveItem);
                        if (parsed == null) {
                            if (OWN.remove(k) != null) {
                                changed = true;
                            }
                            continue;
                        }
                        Shop old = OWN.get(k);
                        if (old != null && old.item().equals(parsed.itemId())
                                && Math.abs(old.buyPer() - parsed.buyPer()) < 0.001
                                && Math.abs(old.sellPer() - parsed.sellPer()) < 0.001) {
                            continue;
                        }
                        OWN.put(k, new Shop(ServerInfo.key(), dim, pos.getX(), pos.getY(), pos.getZ(),
                                parsed.itemId(), parsed.buyPer(), parsed.sellPer(), System.currentTimeMillis(),
                                parsed.raw()));
                        changed = true;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        if (changed) {
            saveOwn();
            cacheAt = 0;
        }
    }

    /** 第一行若是店名/广告词，先清掉，避免“钻石商店”被当成钻石。 */
    private static List<String> sanitizeFirst(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!out.isEmpty() && !looksTrading(out.get(0))) {
            out.set(0, "");
        }
        return out;
    }

    private static boolean looksLikeShop(List<String> lines) {
        for (String s : lines) {
            String l = s == null ? "" : s.toLowerCase();
            if (l.contains("shop") || l.contains("商店") || l.contains("收购") || l.contains("出售")
                    || l.contains("buy") || l.contains("sell") || l.contains("收") || l.contains("卖")
                    || l.contains("售")
                    || l.matches(".*[bs]\\s*\\d+\\s*[:：]\\s*\\d+.*")) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksTrading(String s) {
        String l = s == null ? "" : s.toLowerCase();
        return l.contains("收") || l.contains("卖") || l.contains("售") || l.contains("buy")
                || l.contains("sell") || l.contains("b ") || l.contains("s ")
                || l.matches(".*[bs]\\s*\\d+\\s*[:：]\\s*\\d+.*");
    }

    /** 告示牌上的物品名 → minecraft id：先中文别名，再英文注册名。 */
    private static String resolveItem(String line) {
        String id = PriceParser.resolveItem(line);
        if (id != null) {
            return id;
        }
        String low = line == null ? "" : line.toLowerCase();
        if (low.startsWith("minecraft:") && low.length() > 10) {
            try {
                Identifier loc = Identifier.parse(low);
                if (BuiltInRegistries.ITEM.containsKey(loc)) {
                    return loc.toString();
                }
            } catch (Throwable ignored) {
            }
        }
        for (String tok : low.split("[^a-z0-9_]+")) {
            if (tok.length() < 3) {
                continue;
            }
            try {
                Identifier loc = Identifier.fromNamespaceAndPath("minecraft", tok);
                if (BuiltInRegistries.ITEM.containsKey(loc)) {
                    return loc.toString();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 多账号扫到的店合并，只取当前服务器。 */
    public static List<Shop> all() {
        long now = System.currentTimeMillis();
        if (cache != null && now - cacheAt < CACHE_MS) {
            return cache;
        }
        Map<String, Shop> merged = new LinkedHashMap<>();
        try {
            Path root = FabricLoader.getInstance().getGameDir().resolve("信息工具");
            if (Files.exists(root)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                    for (Path dir : ds) {
                        Path f = dir.resolve("商店地图.txt");
                        if (!Files.isRegularFile(f)) {
                            continue;
                        }
                        for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                            Shop s = parseLine(raw);
                            if (s == null || !s.server().equals(ServerInfo.key())) {
                                continue;
                            }
                            String k = s.dim() + "|" + s.x() + "," + s.y() + "," + s.z();
                            Shop old = merged.get(k);
                            if (old == null || s.updated() >= old.updated()) {
                                merged.put(k, s);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        cache = new ArrayList<>(merged.values());
        cacheAt = now;
        return cache;
    }

    public static int count() {
        return all().size();
    }

    public static String search(String itemText) {
        String id = resolveItem(itemText == null ? "" : itemText);
        if (id == null) {
            return "认不出「" + itemText + "」，换个说法（钻石、铁、煤、红石…或 diamond）。";
        }
        List<Shop> shops = all().stream().filter(s -> s.item().equals(id)).toList();
        if (shops.isEmpty()) {
            return Names.of(id) + "：附近还没扫到商店。走到商店附近（加载区块）会自动记，或 /info sign scan。";
        }
        Minecraft mc = Minecraft.getInstance();
        List<Shop> sorted = new ArrayList<>(shops);
        sorted.sort(Comparator.comparingDouble((Shop s) -> s.sellPer() > 0 ? s.sellPer() : 1e9)
                .thenComparingDouble(s -> s.buyPer() > 0 ? -s.buyPer() : 1e9));
        StringBuilder sb = new StringBuilder(Names.of(id)).append("：附近 ").append(shops.size()).append(" 家店");
        int n = 0;
        for (Shop s : sorted) {
            if (n++ >= 8) {
                break;
            }
            sb.append('\n').append(posLine(s, mc));
            if (s.sellPer() > 0) {
                sb.append(" 卖 ").append(fmt(s.sellPer())).append("/个");
            }
            if (s.buyPer() > 0) {
                sb.append(" 收 ").append(fmt(s.buyPer())).append("/个");
            }
        }
        if (shops.size() > 8) {
            sb.append("\n…还有 ").append(shops.size() - 8).append(" 家");
        }
        return sb.toString();
    }

    public static String list() {
        List<Shop> shops = all();
        if (shops.isEmpty()) {
            return "还没扫到商店。走到有告示牌商店的地方（区块加载后）会自动记；也可以 /info sign scan。";
        }
        Map<String, Integer> byItem = new LinkedHashMap<>();
        for (Shop s : shops) {
            byItem.merge(s.item(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder("已记 ").append(shops.size()).append(" 家店，")
                .append(byItem.size()).append(" 种物品");
        Minecraft mc = Minecraft.getInstance();
        List<Shop> near = new ArrayList<>(shops);
        near.sort(Comparator.comparingDouble(s -> {
            int d = dist(s, mc);
            return d < 0 ? 1e9 : d;
        }));
        sb.append("\n最近的：");
        for (int i = 0; i < Math.min(5, near.size()); i++) {
            sb.append('\n').append(posLine(near.get(i), mc));
        }
        sb.append("\n查某个物品：/info sign 钻石");
        return sb.toString();
    }

    public static String near() {
        List<Shop> shops = all();
        Minecraft mc = Minecraft.getInstance();
        List<Shop> near = shops.stream().filter(s -> dist(s, mc) <= 64)
                .sorted(Comparator.comparingDouble(s -> dist(s, mc))).toList();
        if (near.isEmpty()) {
            return "64 格内没扫到商店（已记 " + shops.size() + " 家）。";
        }
        StringBuilder sb = new StringBuilder("64 格内 ").append(near.size()).append(" 家：");
        for (int i = 0; i < Math.min(8, near.size()); i++) {
            Shop s = near.get(i);
            sb.append('\n').append(Names.of(s.item())).append("  ");
            if (s.sellPer() > 0) {
                sb.append("卖 ").append(fmt(s.sellPer())).append("  ");
            }
            if (s.buyPer() > 0) {
                sb.append("收 ").append(fmt(s.buyPer())).append("  ");
            }
            sb.append(dist(s, mc)).append(" 格 ").append(dir(s, mc));
        }
        return sb.toString();
    }

    public static String clear() {
        OWN.clear();
        LAST_TEXT.clear();
        cache = null;
        cacheAt = 0;
        try {
            Files.deleteIfExists(ownFile());
        } catch (Throwable ignored) {
        }
        return "本号的商店地图已清空（其他号的记录还在）。";
    }

    /** 当前手持物品的最低价店。 */
    public static Shop bestSell(String itemId) {
        Shop best = null;
        Minecraft mc = Minecraft.getInstance();
        for (Shop s : all()) {
            if (!s.item().equals(itemId) || s.sellPer() <= 0) {
                continue;
            }
            if (dist(s, mc) < 0) {
                continue;
            }
            if (best == null || s.sellPer() < best.sellPer()) {
                best = s;
            }
        }
        return best;
    }

    /** 当前服务器里，已扫到的最高“商店收价”。 */
    public static double maxBuy(String itemId) {
        double best = 0;
        for (Shop s : all()) {
            if (s.item().equals(itemId) && s.buyPer() > best) {
                best = s.buyPer();
            }
        }
        return best;
    }

    public static String hudLine(Minecraft mc) {
        if (!enabled()) {
            return null;
        }
        List<Shop> shops = all();
        if (shops.isEmpty()) {
            return "商店 未扫到（走近告示牌自动记）";
        }
        String held = QuickCard.idOf(mc.player == null ? null : mc.player.getMainHandItem());
        Shop best = held == null ? null : bestSell(held);
        if (best != null) {
            return "商店 " + shops.size() + " 家｜" + Names.of(held) + " 最低卖 "
                    + fmt(best.sellPer()) + "/个 " + dist(best, mc) + "m " + dir(best, mc);
        }
        Shop near = shops.stream().min(Comparator.comparingDouble(s -> dist(s, mc))).orElse(null);
        if (near == null) {
            return "商店 " + shops.size() + " 家";
        }
        return "商店 " + shops.size() + " 家｜最近 " + Names.of(near.item()) + " "
                + (near.sellPer() > 0 ? "卖" + fmt(near.sellPer()) : "收" + fmt(near.buyPer()))
                + " " + dist(near, mc) + "m " + dir(near, mc);
    }

    private static int dist(Shop s, Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return -1;
        }
        if (!s.dim().equals(mc.level.dimension().identifier().toString())) {
            return -1;
        }
        double dx = s.x() + 0.5 - mc.player.getX();
        double dz = s.z() + 0.5 - mc.player.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    private static String dir(Shop s, Minecraft mc) {
        if (mc.player == null || mc.level == null || !s.dim().equals(mc.level.dimension().identifier().toString())) {
            return "别的维度";
        }
        double dx = s.x() + 0.5 - mc.player.getX();
        double dz = s.z() + 0.5 - mc.player.getZ();
        String ns = dz < 0 ? "北" : "南";
        String ew = dx < 0 ? "西" : "东";
        if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) {
            return "脚下";
        }
        if (Math.abs(dx) > Math.abs(dz) * 2) {
            return "往" + ew;
        }
        if (Math.abs(dz) > Math.abs(dx) * 2) {
            return "往" + ns;
        }
        return "往" + ns + ew;
    }

    private static String posLine(Shop s, Minecraft mc) {
        int d = dist(s, mc);
        String dim = s.dim().contains("nether") ? "下界" : s.dim().contains("end") ? "末地" : "主世界";
        return (d >= 0 ? d + "m " + dir(s, mc) : "别的维度") + " " + dim + " " + s.x() + "," + s.y() + "," + s.z();
    }

    private static String fmt(double v) {
        if (v >= 10000 && Math.abs(v - Math.round(v)) < 1e-6) {
            return String.format(java.util.Locale.ROOT, "%.1f万", v / 10000.0);
        }
        if (v == Math.floor(v) && v < 1e9) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
