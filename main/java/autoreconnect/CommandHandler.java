package autoreconnect;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 指令处理器
 * 
 * 功能说明：
 * 1. 监听服务器聊天消息
 * 2. 检测到登录提示时自动发送预配置的指令序列
 * 3. 每条指令间隔 1 秒发送，避免刷屏
 * 
 * 使用场景：
 * - 原版服务器登录（/login）
 * - AuthMe 插件认证
 * - BungeeCord 子服切换
 */
@EventBusSubscriber(modid = "autoreconnect", value = Dist.CLIENT)
public class CommandHandler {
    /** 指令执行标记，防止重复执行 */
    private static boolean hasExecutedOnce = false;
    
    /** 定时任务标记 */
    private static boolean isRepeatingCommands = false;
    
    /** 定时任务线程 */
    private static Thread repeatThread = null;
    
    /** 登录指令冷却时间标记（毫秒），防止循环触发 */
    private static long lastLoginCommandTime = 0;
    private static final int LOGIN_COMMAND_COOLDOWN_MS = 5000; // 5 秒冷却时间
    
    /** 多世界插件世界切换检测关键词 */
    private static final String[] WORLD_SWITCH_KEYWORDS = {
        "未知或不完整的命令。错误见下\\nlogin<--[此处]"
    };
    
    /**
     * 玩家加入服务器事件监听器
     * 每次玩家加入服务器时重置执行标记，确保下次可以重新执行指令
     * 同时检查指令 1 的配置是否有效
     * 
     * @param event 玩家加入服务器事件
     */
    @SubscribeEvent
    public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        hasExecutedOnce = false; // 重置执行标记
        // 如果之前在循环执行，先停止
        if (isRepeatingCommands) {
            stopRepeatingCommands();
        }
        AutoReconnectMod.LOGGER.info("玩家加入服务器，重置指令发送标记");
    }
    
    /**
     * 玩家离开服务器事件监听器
     * 停止所有正在执行的定时任务
     * 
     * @param event 玩家离开服务器事件
     */
    @SubscribeEvent
    public static void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        // 停止循环执行
        if (isRepeatingCommands) {
            stopRepeatingCommands();
        }
        // 重置登录指令冷却时间
        lastLoginCommandTime = 0;
        AutoReconnectMod.LOGGER.info("玩家离开服务器，重置登录指令冷却时间");
    }
    
    /**
     * 开始循环执行指令（每 10 秒一次）
     */
    private static void startRepeatingCommands() {
        isRepeatingCommands = true;
        
        repeatThread = new Thread(() -> {
            Minecraft mc = Minecraft.getInstance();
            int initialDelaySeconds = ModConfig.COMMAND_DELAY.get();
            
            AutoReconnectMod.LOGGER.info("已启动指令循环执行任务（初始延迟 {} 秒，之后每 10 秒一次）", initialDelaySeconds);
            
            // 第一次执行需要延迟
            boolean isFirstExecution = true;
            
            while (isRepeatingCommands) {
                try {
                    // 第一次等待配置的延迟时间，之后每次间隔 10 秒
                    long waitTime = isFirstExecution ? (initialDelaySeconds * 1000L) : 10000L;
                    
                    AutoReconnectMod.LOGGER.info("等待 {} 秒后{}执行指令", 
                        (waitTime / 1000), 
                        isFirstExecution ? "延迟" : "开始循环");
                    
                    Thread.sleep(waitTime);
                    
                    // 检查是否仍在服务器中
                    if (mc.player == null || mc.getConnection() == null) {
                        AutoReconnectMod.LOGGER.warn("玩家已离开服务器，停止指令循环");
                        isRepeatingCommands = false;
                        break;
                    }
                    
                    // 在主线程中调用 scheduleCommands()
                    mc.execute(() -> {
                        scheduleCommands();
                        AutoReconnectMod.LOGGER.info("[循环执行] 已触发 scheduleCommands()");
                    });
                    
                    // 第一次执行后标记为 false，后续不再延迟
                    isFirstExecution = false;
                    
                } catch (InterruptedException e) {
                    AutoReconnectMod.LOGGER.warn("指令循环被中断");
                    break;
                } catch (Exception e) {
                    AutoReconnectMod.LOGGER.error("指令循环执行出错", e);
                    break;
                }
            }
            
            AutoReconnectMod.LOGGER.info("指令循环执行任务已停止");
        });
        
        repeatThread.setName("AutoReconnecto-RepeatCommand");
        repeatThread.setDaemon(true);
        repeatThread.start();
    }
    
    /**
     * 停止循环执行指令
     */
    private static void stopRepeatingCommands() {
        isRepeatingCommands = false;
        if (repeatThread != null) {
            repeatThread.interrupt();
            repeatThread = null;
        }
        AutoReconnectMod.LOGGER.info("已停止指令循环执行任务");
    }
    
    /**
     * 客户端聊天消息接收事件监听器
     * 检测服务器发送的登录相关消息或连接问题，触发指令序列或循环执行
     * 
     * @param event 聊天消息接收事件
     */
    @SubscribeEvent
    public static void onClientChat(ClientChatReceivedEvent event) {
        // 获取聊天消息内容
        String chatText = event.getMessage().getString();
        
        // 打印所有收到的消息用于调试
        AutoReconnectMod.LOGGER.info("收到聊天消息：'{}'", chatText);
        
        // 检测是否包含世界切换关键词（停止循环）
        if (isRepeatingCommands) {
            for (String keyword : WORLD_SWITCH_KEYWORDS) {
                if (chatText.contains(keyword)) {
                    AutoReconnectMod.LOGGER.info("检测到世界切换消息，停止指令循环执行");
                    stopRepeatingCommands();
                    return;
                }
            }
        }
        
        // 检测是否包含连接问题的关键词
        boolean hasConnectionIssue = chatText.contains("玩家系统") ||
                                     chatText.contains("无法连接至 game") ||
                                     chatText.contains("无法连接到 game");
        
        // 如果检测到连接问题且当前未在循环执行，则开始循环
        if (hasConnectionIssue && !isRepeatingCommands) {
            AutoReconnectMod.LOGGER.info("检测到连接问题，开始每 10 秒执行指令");
            startRepeatingCommands();
            return;
        }
        
        // 如果正在循环执行中，不再处理登录消息（避免重复触发）
        if (isRepeatingCommands) {
            return;
        }
        
        // 检测是否包含登录相关的关键词（支持中英文和多种格式）
        boolean containsLogin = chatText.contains("/login") ||           // 原版登录提示
                                chatText.toLowerCase().contains("login") ||  // 英文登录
                                chatText.contains("自动登录");      // 中文登录
        
        // 如果不包含登录关键词，直接返回
        if (!containsLogin) {
            return;
        }
        
        AutoReconnectMod.LOGGER.info("检测到登录相关消息！");
        
        // 检查是否在冷却时间内，防止循环触发
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLoginCommandTime < LOGIN_COMMAND_COOLDOWN_MS) {
            AutoReconnectMod.LOGGER.warn("登录指令正在冷却时间内，跳过触发（剩余{}毫秒）", 
                LOGIN_COMMAND_COOLDOWN_MS - (int)(currentTime - lastLoginCommandTime));
            return;
        }
        
        // 如果已经执行过，直接返回（防止重复发送）
        if (hasExecutedOnce) {
            AutoReconnectMod.LOGGER.warn("指令已执行过，跳过");
            return;
        }
        
        // 开始发送指令序列
        AutoReconnectMod.LOGGER.info("检测到登录提示，开始发送指令序列");
        // 立即设置冷却时间，防止服务器响应消息再次触发
        lastLoginCommandTime = System.currentTimeMillis();
        scheduleCommands();
    }
    
    /**
     * 安排指令发送任务
     * 在独立线程中延迟执行，避免阻塞主线程
     * 
     * @param skipDelay 是否跳过延迟（用于循环执行时）
     */
    private static void scheduleCommands(boolean skipDelay) {
        // 双重检查执行标记（循环执行时跳过此检查）
        if (hasExecutedOnce && !isRepeatingCommands) {
            return;
        }
        
        // 获取 Minecraft 实例
        Minecraft mc = Minecraft.getInstance();
        // 获取配置的延迟时间（秒）
        int delaySeconds = ModConfig.COMMAND_DELAY.get();
        
        // 从配置中读取 6 条指令
        String[] commands = {
            ModConfig.COMMAND_1.get(),
            ModConfig.COMMAND_2.get(),
            ModConfig.COMMAND_3.get(),
            ModConfig.COMMAND_4.get(),
            ModConfig.COMMAND_5.get(),
            ModConfig.COMMAND_6.get()
        };
        
        if (!skipDelay) {
            AutoReconnectMod.LOGGER.info("监听到服务器消息，将在 {} 秒后开始发送指令", delaySeconds);
        } else {
            AutoReconnectMod.LOGGER.info("循环执行：立即发送指令");
        }
        
        // 启动独立线程处理延迟，避免阻塞游戏主线程
        new Thread(() -> {
            try {
                // 如果需要延迟，则等待配置的延迟时间
                if (!skipDelay) {
                    Thread.sleep(delaySeconds * 1000L);
                }
                
                // 切换到主线程发送指令（必须在主线程执行）
                mc.execute(() -> {
                    // 发送指令序列
                    sendCommandSequence(mc, commands, 0);
                    
                    // 标记已执行过（循环执行时不设置此标记）
                    if (!isRepeatingCommands) {
                        hasExecutedOnce = true;
                        AutoReconnectMod.LOGGER.info("指令序列已执行完毕，不再重复执行");
                    } else {
                        AutoReconnectMod.LOGGER.info("[循环] 指令序列已执行，将在 10 秒后再次执行");
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * 安排指令发送任务（带延迟）
     * 在独立线程中延迟执行，避免阻塞主线程
     */
    private static void scheduleCommands() {
        scheduleCommands(false); // 默认不跳过延迟
    }

    /**
     * 递归发送指令序列
     * 每条约隔 1 秒发送，模拟真实玩家操作
     * 
     * @param mc Minecraft 实例
     * @param commands 指令数组
     * @param index 当前发送的指令索引
     */
    private static void sendCommandSequence(Minecraft mc, String[] commands, int index) {
        // 玩家连接检查
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        
        // 边界检查：如果索引超出范围，说明所有指令已发送完成
        if (index >= commands.length) {
            // 所有指令发送完成，检查是否有指令位为默认值
            checkCommandDefault(mc, commands);
            return;
        }
        
        String command = commands[index];
        
        // 如果指令非空，发送它
        if (command != null && !command.isEmpty()) {
            // 移除开头的斜杠（如果需要）
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            
            // 发送指令到服务器
            mc.player.connection.sendCommand(cmd);
            AutoReconnectMod.LOGGER.info("已发送指令：{}", command);
            
            // 如果还有下一条指令，安排 1 秒后发送
            if (index < commands.length - 1) {
                final int nextIndex = index + 1;
                // 在新线程中等待，避免阻塞
                new Thread(() -> {
                    try {
                        Thread.sleep(1000L); // 等待 1 秒
                        // 切回主线程发送下一条
                        mc.execute(() -> sendCommandSequence(mc, commands, nextIndex));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            } else {
                // 已经是最后一条指令，标记为完成
                mc.execute(() -> checkCommandDefault(mc, commands));
            }
        } else {
            // 空指令跳过，继续下一条
            if (index < commands.length - 1) {
                sendCommandSequence(mc, commands, index + 1);
            } else {
                // 最后一条是空指令，也标记为完成
                checkCommandDefault(mc, commands);
            }
        }
    }
    
    /**
     * 检查指令 1-6 是否有默认值（/login），如果有则显示警告消息
     * 
     * @param mc Minecraft 实例
     * @param commands 指令数组
     */
    private static void checkCommandDefault(Minecraft mc, String[] commands) {
        // 收集所有默认值的指令位
        java.util.List<Integer> defaultSlots = new java.util.ArrayList<>();
        
        for (int i = 0; i < commands.length && i < 6; i++) {
            String command = commands[i];
            // 检查指令是否为 "/login"、"/login " 或 trim 后等于 "/login"
            if (command != null && (command.equals("/login") || command.equals("/login ") || 
                                    command.trim().equals("/login"))) {
                defaultSlots.add(i + 1); // 指令位从 1 开始计数
            }
        }
        
        // 如果有默认值的指令位，显示警告
        if (!defaultSlots.isEmpty()) {
            // 构建指令位列表字符串
            StringBuilder slotsBuilder = new StringBuilder();
            for (int i = 0; i < defaultSlots.size(); i++) {
                if (i > 0) {
                    slotsBuilder.append("§c、§b");
                }
                slotsBuilder.append(defaultSlots.get(i));
            }
            String slotsText = slotsBuilder.toString();
            int count = defaultSlots.size();
            
            // 获取翻译后的消息（参数会自动替换到翻译文本中）
            net.minecraft.network.chat.Component warningMsg = 
                net.minecraft.network.chat.Component.translatable(
                    "autoreconnect.message.command_default_warning", 
                    slotsText,
                    count
                );
            // 显示给玩家
            if (mc.player != null) {
                mc.player.displayClientMessage(warningMsg, false);
                // 播放警告音效（使用村民交易失败音效，非常明显的"哈？"声）
                mc.player.playNotifySound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                AutoReconnectMod.LOGGER.warn("检测到指令位 {} 为默认值，已显示警告消息并播放音效", defaultSlots);
            }
        }
    }
}
