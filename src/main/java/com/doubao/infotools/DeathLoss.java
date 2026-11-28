package com.doubao.infotools;

import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 死亡损失账单：死的一瞬间按背包行情估值，记一笔并提示值不值得回去捡。 */
public final class DeathLoss {
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DeathLoss() {
    }

    private static Path file() {
        return Files2.path("死亡损失.txt");
    }

    public static void record(Minecraft mc) {
        try {
            if (mc.player == null) {
                return;
            }
            double value = Worth.marketValue(mc);
            String top = Worth.topValueText(mc, 3);
            if (value <= 0 && top.isEmpty()) {
                return;
            }
            String line = LocalDateTime.now().format(FULL) + "|" + value + "|" + clean(top) + "\n";
            Files.writeString(file(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Notify.alert("死亡损失约 " + Worth.money(value) + (top.isEmpty() ? "" : "（最贵：" + top + "）")
                    + "；掉落物 5 分钟消失");
        } catch (Throwable ignored) {
        }
    }

    public static String summary() {
        try {
            Path f = file();
            if (!Files.exists(f)) {
                return "还没有死亡损失记录。";
            }
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            String last = null;
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (!lines.get(i).trim().isEmpty()) {
                    last = lines.get(i).trim();
                    break;
                }
            }
            if (last == null) {
                return "还没有死亡损失记录。";
            }
            String[] p = last.split("\\|", 3);
            if (p.length < 2) {
                return "死亡损失记录格式不对。";
            }
            double value = Double.parseDouble(p[1]);
            String top = p.length >= 3 ? p[2] : "";
            return "上次死亡损失约 " + Worth.money(value)
                    + (top.isEmpty() ? "" : "\n最贵：" + top)
                    + "\n死亡点：/info death";
        } catch (Throwable t) {
            return "读死亡损失记录失败。";
        }
    }

    public static String clear() {
        try {
            Files.deleteIfExists(file());
        } catch (Throwable ignored) {
        }
        return "本号死亡损失记录已清空。";
    }

    private static String clean(String s) {
        return s.replace("|", "/").replace("\n", " ");
    }
}
