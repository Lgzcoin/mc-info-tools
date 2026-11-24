package com.doubao.infotools;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 多开时几个号共用一个游戏目录，文件必须按账号分开，否则互相覆盖。
 * 统一放 游戏目录/信息工具/<玩家名>/ 下面。
 */
public final class Files2 {
    private Files2() {
    }

    public static String name() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String n = mc.player.getName().getString();
                if (n != null && !n.isBlank()) {
                    return sanitize(n);
                }
            }
        } catch (Throwable ignored) {
        }
        return "未进世界";
    }

    public static Path path(String fileName) {
        Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                .resolve("信息工具").resolve(name());
        try {
            Files.createDirectories(dir);
        } catch (Throwable ignored) {
        }
        return dir.resolve(fileName);
    }

    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
