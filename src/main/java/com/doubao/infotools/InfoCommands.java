package com.doubao.infotools;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 客户端命令 /info */
public final class InfoCommands {
    private InfoCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(buildTree()));
    }

    /** 单独抽出来，方便离线检查命令树有没有重名/漏节点。 */
    public static LiteralArgumentBuilder<FabricClientCommandSource> buildTree() {
        return ClientCommands.literal("info")
                        .executes(InfoCommands::status)
                        .then(ClientCommands.literal("status").executes(InfoCommands::status))
                        .then(ClientCommands.literal("assets")
                                .executes(ctx -> {
                                    reply(ctx, AssetLedger.summary());
                                    return 1;
                                })
                                .then(ClientCommands.literal("snap").executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player == null) {
                                        reply(ctx, "还没进世界。");
                                        return 1;
                                    }
                                    AssetLedger.snapshotNow(mc);
                                    return 1;
                                }))
                                .then(ClientCommands.literal("log").executes(ctx -> {
                                    reply(ctx, AssetLedger.logTail());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("reset").executes(ctx -> {
                                    AssetLedger.resetToday();
                                    reply(ctx, "今日增减已清零（总量不动，继续从当前值算）。");
                                    return 1;
                                })))
                        .then(ClientCommands.literal("death").executes(ctx -> {
                            reply(ctx, DeathTracker.summary(Minecraft.getInstance()));
                            return 1;
                        }))
                        .then(ClientCommands.literal("drops").executes(ctx -> {
                            reply(ctx, DropTracker.listText(Minecraft.getInstance()));
                            return 1;
                        }))
                        .then(ClientCommands.literal("alert")
                                .executes(ctx -> {
                                    reply(ctx, KeywordAlert.listText());
                                    return 1;
                                })
                                .then(ClientCommands.literal("on").executes(ctx -> {
                                    KeywordAlert.setEnabled(true);
                                    reply(ctx, "关键词告警已开。");
                                    return 1;
                                }))
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    KeywordAlert.setEnabled(false);
                                    reply(ctx, "关键词告警已关。");
                                    return 1;
                                }))
                                .then(ClientCommands.literal("list").executes(ctx -> {
                                    reply(ctx, KeywordAlert.listText());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("add")
                                        .then(ClientCommands.argument("词", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    reply(ctx, KeywordAlert.add(StringArgumentType.getString(ctx, "词")));
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("del")
                                        .then(ClientCommands.argument("词", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    reply(ctx, KeywordAlert.remove(StringArgumentType.getString(ctx, "词")));
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("test")
                                        .then(ClientCommands.argument("一段话", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    reply(ctx, KeywordAlert.test(StringArgumentType.getString(ctx, "一段话")));
                                                    return 1;
                                                })))
                        )
                        .then(ClientCommands.literal("reload").executes(ctx -> {
                            InfoTools.reloadConfig();
                            reply(ctx, "配置已重读（信息工具配置.txt）。");
                            return 1;
                        }))
                        .then(ClientCommands.literal("help").executes(ctx -> {
                            reply(ctx, helpText());
                            return 1;
                        }))
                        .then(ClientCommands.literal("配置").executes(ctx -> {
                            reply(ctx, "配置文件：" + InfoConfig.file().toAbsolutePath());
                            return 1;
                        }))
                        .then(ClientCommands.literal("file").executes(ctx -> {
                            reply(ctx, "配置文件：" + InfoConfig.file().toAbsolutePath());
                            return 1;
                        }));
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            reply(ctx, "还没进世界。");
            return 1;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(AssetLedger.summary()).append('\n');
        sb.append("掉落物：").append(DropTracker.line(mc)).append('\n');
        sb.append(DeathTracker.summary(mc)).append('\n');
        sb.append("关键词告警：").append(KeywordAlert.line());
        reply(ctx, sb.toString());
        return 1;
    }

    private static String helpText() {
        return String.join("\n",
                "/info                 一眼看全部（资产 / 掉落 / 死亡点 / 告警）",
                "/info assets          今日收支前几名",
                "/info assets snap     立刻记一笔资产",
                "/info assets log      最近 12 条变动",
                "/info assets reset    今日增减清零",
                "/info drops           附近掉落物还剩多久",
                "/info death           上次死亡点，离你多远、往哪走",
                "/info alert           看关键词告警状态",
                "/info alert add 词     加一个关键词",
                "/info alert del 词     删一个关键词",
                "/info alert test 话    试一句话，看会不会响",
                "/info reload          重读 信息工具配置.txt");
    }

    private static void reply(CommandContext<FabricClientCommandSource> ctx, String text) {
        try {
            ctx.getSource().sendFeedback(Component.literal(text));
        } catch (Throwable t) {
            InfoTools.LOGGER.warn("命令回显失败", t);
        }
    }
}
