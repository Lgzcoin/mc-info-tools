package com.doubao.infotools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 死亡点：死的一瞬间记下坐标，聊天里报一次，写进 死亡点.txt。
 */
public final class DeathTracker {
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static boolean recordedThisDeath = false;
    private static int lastX, lastY, lastZ;
    private static String lastDim = "";
    private static String lastTime = "";

    private DeathTracker() {
    }

    private static Path file() {
        return Files2.path("死亡点.txt");
    }

    private static boolean loaded = false;
    private static String owner = "";

    public static void load() {
        if (loaded || Files2.name().equals("未进世界")) {
            return;
        }
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) {
                return;
            }
            var lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|");
                if (p.length < 4) {
                    continue;
                }
                String[] xyz = p[2].split(",");
                if (xyz.length != 3) {
                    continue;
                }
                lastTime = p[0];
                lastDim = p[1];
                lastX = Integer.parseInt(xyz[0].trim());
                lastY = Integer.parseInt(xyz[1].trim());
                lastZ = Integer.parseInt(xyz[2].trim());
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            recordedThisDeath = false;
            return;
        }
        boolean dying;
        try {
            dying = p.isDeadOrDying();
        } catch (Throwable t) {
            return;
        }
        if (!dying) {
            recordedThisDeath = false;
        }
        String me = Files2.name();
        if (!me.equals(owner)) {
            owner = me;
            loaded = false;
            lastTime = "";
            lastDim = "";
        }
        if (!loaded) {
            load();
        }
        if (!dying) {
            return;
        }
        if (recordedThisDeath) {
            return;
        }
        recordedThisDeath = true;
        try {
            BlockPos pos = p.blockPosition();
            lastX = pos.getX();
            lastY = pos.getY();
            lastZ = pos.getZ();
            lastDim = p.level().dimension().identifier().toString();
            lastTime = LocalDateTime.now().format(FULL);
            String line = lastTime + "|" + lastDim + "|" + lastX + "," + lastY + "," + lastZ + "\n";
            Files.writeString(file(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Notify.alert("记录死亡点 " + lastX + "," + lastY + "," + lastZ
                    + "（掉落物 5 分钟后消失，快回去捡）");
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("记死亡点失败", t);
        }
    }

    public static String summary(Minecraft mc) {
        if (lastTime.isEmpty()) {
            return "还没有死亡记录（本机没死过，或 死亡点.txt 是空的）。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("上次死亡：").append(lastTime).append("  ")
                .append(lastX).append(',').append(lastY).append(',').append(lastZ)
                .append("  ").append(shortDim(lastDim));
        try {
            LocalPlayer p = mc.player;
            if (p != null) {
                double dx = lastX + 0.5 - p.getX();
                double dz = lastZ + 0.5 - p.getZ();
                int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                sb.append("\n离现在 ").append(dist).append(" 格，");
                sb.append(dir(dx, dz));
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    /** HUD 用：离死亡点多远、往哪走。 */
    public static String shortLine(Minecraft mc) {
        if (lastTime.isEmpty()) {
            return "无记录";
        }
        try {
            LocalPlayer p = mc.player;
            if (p == null) {
                return lastX + "," + lastY + "," + lastZ;
            }
            double dx = lastX + 0.5 - p.getX();
            double dz = lastZ + 0.5 - p.getZ();
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            return dist + " 格 " + dir(dx, dz);
        } catch (Throwable t) {
            return "记录异常";
        }
    }

    public static String line() {
        if (lastTime.isEmpty()) {
            return "无";
        }
        return lastX + "," + lastY + "," + lastZ + "（" + (lastTime.length() >= 19 ? lastTime.substring(11, 16) : lastTime) + "）";
    }

    private static String shortDim(String dim) {
        if (dim == null) {
            return "?";
        }
        int i = dim.indexOf(':');
        return i >= 0 ? dim.substring(i + 1) : dim;
    }

    private static String dir(double dx, double dz) {
        String ns = dz < 0 ? "北" : "南";
        String ew = dx < 0 ? "西" : "东";
        if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) {
            return "就在脚下";
        }
        if (Math.abs(dx) > Math.abs(dz) * 2) {
            return "往" + ew;
        }
        if (Math.abs(dz) > Math.abs(dx) * 2) {
            return "往" + ns;
        }
        return "往" + ns + ew;
    }
}
