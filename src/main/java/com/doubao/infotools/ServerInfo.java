package com.doubao.infotools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

/** 当前服务器：名字、延迟、在线人数。 */
public final class ServerInfo {
    private ServerInfo() {
    }

    public static String name() {
        try {
            ServerData d = Minecraft.getInstance().getCurrentServer();
            if (d == null) {
                return "单人";
            }
            if (d.name != null && !d.name.isBlank()) {
                return d.name;
            }
            return d.ip == null ? "服务器" : d.ip;
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 按服务器区分行情用：优先 ip，其次名字。 */
    public static String key() {
        try {
            ServerData d = Minecraft.getInstance().getCurrentServer();
            if (d == null) {
                return "单人";
            }
            return d.ip == null || d.ip.isBlank() ? name() : d.ip;
        } catch (Throwable t) {
            return "?";
        }
    }

    public static int online() {
        try {
            ClientPacketListener c = Minecraft.getInstance().getConnection();
            if (c == null) {
                return 0;
            }
            return c.getOnlinePlayers().size();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String onlineNames(int limit) {
        try {
            ClientPacketListener c = Minecraft.getInstance().getConnection();
            if (c == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (PlayerInfo info : c.getOnlinePlayers()) {
                if (n >= limit) {
                    break;
                }
                if (n > 0) {
                    sb.append('、');
                }
                sb.append(info.getProfile().name());
                n++;
            }
            int total = c.getOnlinePlayers().size();
            if (total > n) {
                sb.append(" …共 ").append(total).append(" 人");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public static int ping() {
        try {
            Minecraft mc = Minecraft.getInstance();
            ClientPacketListener c = mc.getConnection();
            if (c == null || mc.player == null) {
                return -1;
            }
            PlayerInfo info = c.getPlayerInfo(mc.player.getUUID());
            return info == null ? -1 : info.getLatency();
        } catch (Throwable t) {
            return -1;
        }
    }
}
