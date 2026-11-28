package com.doubao.infotools;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
                        .then(ClientCommands.literal("price")
                                .executes(ctx -> {
                                    reply(ctx, Market.overview());
                                    return 1;
                                })
                                .then(ClientCommands.literal("list").executes(ctx -> {
                                    reply(ctx, Market.overview());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("cheap").executes(ctx -> {
                                    reply(ctx, Market.cheap());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    Market.clear();
                                    reply(ctx, "本号行情已清空（其他号的记录还在）。");
                                    return 1;
                                }))
                                .then(ClientCommands.literal("test")
                                        .then(ClientCommands.argument("一句话", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    reply(ctx, Market.test(StringArgumentType.getString(ctx, "一句话")));
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("alert")
                                        .executes(ctx -> {
                                            reply(ctx, Market.line());
                                            return 1;
                                        })
                                        .then(ClientCommands.literal("on").executes(ctx -> {
                                            InfoTools.config().priceEnabled = true;
                                            InfoTools.saveConfig();
                                            reply(ctx, "比价告警已开（便宜货会提醒）。");
                                            return 1;
                                        }))
                                        .then(ClientCommands.literal("off").executes(ctx -> {
                                            InfoTools.config().priceEnabled = false;
                                            InfoTools.saveConfig();
                                            reply(ctx, "比价告警已关（仍会继续记录行情）。");
                                            return 1;
                                        }))
                                        .then(ClientCommands.argument("折扣", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String v = StringArgumentType.getString(ctx, "折扣");
                                                    try {
                                                        int pct = Math.max(30, Math.min(99, Integer.parseInt(v)));
                                                        InfoTools.config().priceDiscountPercent = pct;
                                                        InfoTools.saveConfig();
                                                        reply(ctx, "已设置：卖价低于历史均价 " + pct + "% 就提醒。");
                                                    } catch (NumberFormatException e) {
                                                        reply(ctx, "折扣要写数字，比如 /info price alert 75");
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommands.argument("物品", StringArgumentType.word())
                                        .executes(ctx -> {
                                            reply(ctx, Market.stats(StringArgumentType.getString(ctx, "物品")));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("shop")
                                .executes(ctx -> {
                                    reply(ctx, ShopAd.recent());
                                    return 1;
                                })
                                .then(ClientCommands.literal("list").executes(ctx -> {
                                    reply(ctx, ShopAd.recent());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("words").executes(ctx -> {
                                    reply(ctx, ShopAd.words());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    ShopAd.clear();
                                    reply(ctx, "本号商店广告已清空（其他号的记录还在）。");
                                    return 1;
                                }))
                                .then(ClientCommands.literal("add")
                                        .then(ClientCommands.argument("词", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    reply(ctx, ShopAd.addKeyword(StringArgumentType.getString(ctx, "词")));
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("del")
                                        .then(ClientCommands.argument("词", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    reply(ctx, ShopAd.removeKeyword(StringArgumentType.getString(ctx, "词")));
                                                    return 1;
                                                })))
                                .then(ClientCommands.argument("搜索", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            reply(ctx, ShopAd.search(StringArgumentType.getString(ctx, "搜索")));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("hud")
                                .executes(ctx -> {
                                    reply(ctx, "悬浮窗：" + (Hud.visible() ? "开" : "关") + "（游戏里按 I 开关）");
                                    return 1;
                                })
                                .then(ClientCommands.literal("on").executes(ctx -> {
                                    Hud.setVisible(true);
                                    reply(ctx, "悬浮窗已显示（按 I 可隐藏）。");
                                    return 1;
                                }))
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    Hud.setVisible(false);
                                    reply(ctx, "悬浮窗已隐藏（按 I 可再打开）。");
                                    return 1;
                                })))
                        .then(ClientCommands.literal("server").executes(ctx -> {
                            int ping = ServerInfo.ping();
                            String names = ServerInfo.onlineNames(12);
                            reply(ctx, "服务器：" + ServerInfo.name()
                            + "\n在线：" + ServerInfo.online() + " 人"
                            + (ping >= 0 ? "\n你的延迟：" + ping + "ms" : "")
                            + (names.isEmpty() ? "" : "\n玩家：" + names));
                            return 1;
                        }))
                        .then(ClientCommands.literal("reload").executes(ctx -> {
                            InfoTools.reloadConfig();
                            reply(ctx, "配置已重读（信息工具配置.txt）。");
                            return 1;
                        }))
                        .then(ClientCommands.literal("sign")
                                .executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player == null) {
                                        reply(ctx, "还没进世界。");
                                        return 1;
                                    }
                                    String held = QuickCard.idOf(mc.player.getMainHandItem());
                                    reply(ctx, held == null ? SignShop.list() : SignShop.search(held));
                                    return 1;
                                })
                                .then(ClientCommands.literal("list").executes(ctx -> {
                                    reply(ctx, SignShop.list());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("near").executes(ctx -> {
                                    reply(ctx, SignShop.near());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("scan").executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player == null) {
                                        reply(ctx, "还没进世界。");
                                        return 1;
                                    }
                                    SignShop.forceScan(mc);
                                    reply(ctx, "已扫一遍周围区块，当前共 " + SignShop.count() + " 家店。");
                                    return 1;
                                }))
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    reply(ctx, SignShop.clear());
                                    return 1;
                                }))
                                .then(ClientCommands.argument("物品", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            reply(ctx, SignShop.search(StringArgumentType.getString(ctx, "物品")));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("worth").executes(ctx -> {
                            reply(ctx, Worth.summary(Minecraft.getInstance()));
                            return 1;
                        }))
                        .then(ClientCommands.literal("buy")
                                .executes(ctx -> {
                                    reply(ctx, BuyBoard.board());
                                    return 1;
                                })
                                .then(ClientCommands.argument("物品", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            reply(ctx, BuyBoard.item(StringArgumentType.getString(ctx, "物品")));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("flip").executes(ctx -> {
                            reply(ctx, BuyBoard.flip());
                            return 1;
                        }))
                        .then(ClientCommands.literal("stock")
                                .executes(ctx -> {
                                    reply(ctx, Stock.list(Minecraft.getInstance()));
                                    return 1;
                                })
                                .then(ClientCommands.literal("list").executes(ctx -> {
                                    reply(ctx, Stock.list(Minecraft.getInstance()));
                                    return 1;
                                }))
                                .then(ClientCommands.literal("plan").executes(ctx -> {
                                    reply(ctx, Stock.plan(Minecraft.getInstance()));
                                    return 1;
                                }))
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    reply(ctx, Stock.clear());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("set")
                                        .then(ClientCommands.argument("物品", StringArgumentType.word())
                                                .then(ClientCommands.argument("数量", IntegerArgumentType.integer(0, 999999))
                                                        .executes(ctx -> {
                                                            reply(ctx, Stock.set(StringArgumentType.getString(ctx, "物品"),
                                                                    IntegerArgumentType.getInteger(ctx, "数量")));
                                                            return 1;
                                                        }))))
                                .then(ClientCommands.literal("del")
                                        .then(ClientCommands.argument("物品", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    reply(ctx, Stock.set(StringArgumentType.getString(ctx, "物品"), 0));
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("chest")
                                .executes(ctx -> {
                                    reply(ctx, ChestIndex.list());
                                    return 1;
                                })
                                .then(ClientCommands.literal("list").executes(ctx -> {
                                    reply(ctx, ChestIndex.list());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    reply(ctx, ChestIndex.clear());
                                    return 1;
                                }))
                                .then(ClientCommands.argument("物品", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            reply(ctx, ChestIndex.query(StringArgumentType.getString(ctx, "物品"),
                                                    Minecraft.getInstance()));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("history")
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    reply(ctx, PriceHistory.clear());
                                    return 1;
                                }))
                                .then(ClientCommands.argument("物品", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            reply(ctx, PriceHistory.query(StringArgumentType.getString(ctx, "物品")));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("loss")
                                .executes(ctx -> {
                                    reply(ctx, DeathLoss.summary());
                                    return 1;
                                })
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    reply(ctx, DeathLoss.clear());
                                    return 1;
                                })))
                        .then(ClientCommands.literal("income")
                                .executes(ctx -> {
                                    reply(ctx, IncomeStats.summary());
                                    return 1;
                                })
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    reply(ctx, IncomeStats.clear());
                                    return 1;
                                })))
                        .then(ClientCommands.literal("watch")
                                .executes(ctx -> {
                                    reply(ctx, ShopWatch.listText());
                                    return 1;
                                })
                                .then(ClientCommands.literal("list").executes(ctx -> {
                                    reply(ctx, ShopWatch.listText());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("on").executes(ctx -> {
                                    reply(ctx, ShopWatch.setEnabled(true));
                                    return 1;
                                }))
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    reply(ctx, ShopWatch.setEnabled(false));
                                    return 1;
                                }))
                                .then(ClientCommands.literal("add")
                                        .then(ClientCommands.argument("名字", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    reply(ctx, ShopWatch.add(StringArgumentType.getString(ctx, "名字")));
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("del")
                                        .then(ClientCommands.argument("名字", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    reply(ctx, ShopWatch.remove(StringArgumentType.getString(ctx, "名字")));
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("recipe")
                                .then(ClientCommands.argument("物品", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            reply(ctx, RecipeInfo.summary(StringArgumentType.getString(ctx, "物品")));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("use")
                                .then(ClientCommands.argument("物品", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            reply(ctx, RecipeInfo.summary(StringArgumentType.getString(ctx, "物品")));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("quote")
                                .executes(ctx -> {
                                    reply(ctx, QuoteActions.lastText());
                                    return 1;
                                })
                                .then(ClientCommands.literal("copy").executes(ctx -> {
                                    reply(ctx, QuoteActions.copyLast());
                                    return 1;
                                }))
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    reply(ctx, QuoteActions.clear());
                                    return 1;
                                })))
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
        sb.append("关键词告警：").append(KeywordAlert.line()).append('\n');
        sb.append("行情：").append(Market.line()).append('\n');
        sb.append("商店广告：").append(ShopAd.line()).append('\n');
        sb.append("商店地图：").append(SignShop.count()).append(" 家店").append('\n');
        sb.append("收购榜：").append(BuyBoard.count()).append(" 种　补货缺口：")
                .append(Stock.gapCount(mc)).append(" 种\n");
        sb.append(IncomeStats.dayLine(mc)).append('\n');
        sb.append(Worth.summary(mc));
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
                "/info price           行情总览",
                "/info price 钻石       某物品的卖价/收价、便宜线",
                "/info price test 话    试一句报价，看能不能识别",
                "/info price alert on|off|75   便宜货提醒开关 / 阈值",
                "/info price cheap      现在有哪些便宜货",
                "/info price clear      清空本号行情",
                "/info shop            最近的商店广告",
                "/info shop 钻石        搜索广告内容（也能搜发广告的人）",
                "/info shop add 词      加一个广告触发词",
                "/info shop del 词      删触发词；/info shop words 看全部",
                "/info shop clear       清空本号广告",
                "/info sign            手持物品的附近商店比价；空手=总览",
                "/info sign 钻石        查某物品的所有已记商店",
                "/info sign near       64 格内的店",
                "/info sign scan       立刻扫一遍周围区块",
                "/info sign clear      清空本号商店地图",
                "/info worth           背包总市值 + 最值钱的 5 样",
                "/info buy             全服收购榜；/info buy 钻石 看单品",
                "/info flip            搬砖套利（买店→最高收价）",
                "/info stock           补货清单；set 钻石 64 / plan 排采购顺序",
                "/info chest 钻石       上次在哪个箱子见过（只记开过的箱子）",
                "/info history 钻石     行情日线 + 7 日均价",
                "/info loss            上次死亡损失估值",
                "/info income          今日背包净增/经验",
                "/info watch add 名字   订阅店主，上新/降价提醒",
                "/info recipe 钻石      合成配方 + 能做什么",
                "/info quote           上次报价的 /msg 回复；copy 复制",
                "/info server           服务器名、在线人数、你的延迟",
                "/info hud on|off       左上角悬浮板（游戏里按 I）",
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
