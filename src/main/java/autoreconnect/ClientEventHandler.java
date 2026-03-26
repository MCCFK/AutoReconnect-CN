package autoreconnect;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;

@Mod("autoreconnect")
@EventBusSubscriber(modid = "autoreconnect", value = Dist.CLIENT)
public class ClientEventHandler {
    // 保存最后一次连接的服务器信息（参考 Fabric AutoReconnect 实现）
    private static net.minecraft.client.multiplayer.ServerData lastServerData = null;
    private static String lastServerIp = null;
    private static boolean wasInServer = false; // 标记是否曾经在服务器内
    private static boolean reconnectPaused = false; // 标记是否暂停自动重连
    private static boolean playerQuitting = false; // 标记玩家是否主动退出
    private static boolean isReconnecting = false; // 标记是否正在重连中
    private static Thread reconnectThread = null; // 重连线程
    
    public ClientEventHandler(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AutoReconnectMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        AutoReconnectMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
    
    @SubscribeEvent
    static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        // 玩家成功加入服务器时，保存服务器信息并设置标志
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.getConnection().getServerData() != null) {
            lastServerData = mc.getConnection().getServerData();
            lastServerIp = mc.getConnection().getServerData().ip;
            System.out.println("[AutoReconnecto] ✓ 已保存服务器信息：" + lastServerIp);
        }
        wasInServer = true;
        reconnectPaused = false; // 加入服务器时恢复自动重连
        playerQuitting = false; // 加入服务器时重置退出标志（防止误判）
        
        // 如果之前在重连，现在成功了，停止重连线程
        stopReconnecting();
        
        AutoReconnectMod.LOGGER.info("玩家已加入服务器，恢复自动重连功能");
    }
    
    /**
     * 启动循环重连机制（每 10 秒尝试一次）
     */
    private static void startReconnecting() {
        isReconnecting = true;
        
        reconnectThread = new Thread(() -> {
            Minecraft mc = Minecraft.getInstance();
            int delaySeconds = ModConfig.RECONNECT_DELAY.get();
            
            System.out.println("[AutoReconnecto] 已启动循环重连机制（每 10 秒尝试一次）");
            
            while (isReconnecting) {
                try {
                    // 第一次等待初始延迟，之后每次间隔 10 秒
                    long waitTime = (delaySeconds > 0) ? delaySeconds * 1000L : 10000L;
                    
                    System.out.println("[AutoReconnecto] 等待 " + (waitTime / 1000) + " 秒后尝试重连...");
                    
                    // 等待期间持续检查暂停状态
                    long startTime = System.currentTimeMillis();
                    while (System.currentTimeMillis() - startTime < waitTime) {
                        Thread.sleep(200L);
                        if (reconnectPaused) {
                            System.out.println("[AutoReconnecto] ✗ 重连等待期间检测到玩家输入操作，已取消重连");
                            isReconnecting = false;
                            return;
                        }
                    }
                    
                    // 检查是否有保存的服务器数据
                    if (lastServerData == null || lastServerIp == null) {
                        System.err.println("[AutoReconnecto] ✗ 没有保存的服务器信息，无法重连");
                        System.err.println("[AutoReconnecto] 请先手动加入一次服务器");
                        isReconnecting = false;
                        return;
                    }
                    
                    // 在主线程中发起连接
                    final boolean[] connectionStarted = {false};
                    
                    mc.execute(() -> {
                        try {
                            System.out.println("[AutoReconnecto] 正在使用保存的服务器信息重连...");
                            System.out.println("[AutoReconnecto] ✓ 使用保存的服务器：" + lastServerData.name + " (" + lastServerIp + ")");
                            
                            // 确保 serverIp 是干净的（去除可能的后缀）
                            String cleanIp = lastServerIp;
                            if (cleanIp.contains("/")) {
                                cleanIp = cleanIp.split("/")[0];
                            }
                            String finalServerIp = cleanIp;
                            
                            net.minecraft.client.multiplayer.resolver.ServerAddress address = 
                                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(finalServerIp);
                            
                            // 调用 ConnectScreen.connect() 发起连接
                            java.lang.reflect.Method connectMethod = net.minecraft.client.gui.screens.ConnectScreen.class.getDeclaredMethod(
                                "connect",
                                Minecraft.class,
                                net.minecraft.client.multiplayer.resolver.ServerAddress.class,
                                net.minecraft.client.multiplayer.ServerData.class,
                                net.minecraft.client.multiplayer.TransferState.class
                            );
                            connectMethod.setAccessible(true);
                            
                            int modifiers = connectMethod.getModifiers();
                            boolean isStatic = java.lang.reflect.Modifier.isStatic(modifiers);
                            
                            if (isStatic) {
                                connectMethod.invoke(null, mc, address, lastServerData, null);
                            } else {
                                // 创建 ConnectScreen 实例
                                java.lang.reflect.Constructor<?> constructor = net.minecraft.client.gui.screens.ConnectScreen.class.getDeclaredConstructor(
                                    net.minecraft.client.gui.screens.Screen.class,
                                    net.minecraft.network.chat.Component.class
                                );
                                constructor.setAccessible(true);
                                Object screen = constructor.newInstance(
                                    mc.screen,
                                    net.minecraft.network.chat.Component.literal("Connecting...")
                                );
                                connectMethod.invoke(screen, mc, address, lastServerData, null);
                            }
                            
                            connectionStarted[0] = true;
                            System.out.println("[AutoReconnecto] ✓ 已发起重连请求");
                            wasInServer = false;
                            
                        } catch (Exception e) {
                            System.err.println("[AutoReconnecto] ✗ 重连失败：" + e.getMessage());
                            e.printStackTrace();
                            connectionStarted[0] = false;
                        }
                    });
                    
                    // 等待连接结果（最多等待 5 秒）
                    Thread.sleep(5000L);
                    
                    // 如果连接成功（玩家已在服务器中），停止重连
                    if (mc.getConnection() != null && mc.player != null) {
                        System.out.println("[AutoReconnecto] ✓ 重连成功，已加入服务器");
                        isReconnecting = false;
                        return;
                    }
                    
                    // 如果连接失败，继续下一次重试
                    System.out.println("[AutoReconnecto] ✗ 重连未成功，将在 10 秒后再次尝试...");
                    
                } catch (InterruptedException e) {
                    System.out.println("[AutoReconnecto] 重连线程被中断");
                    break;
                } catch (Exception e) {
                    System.err.println("[AutoReconnecto] 重连过程出错：" + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
            
            System.out.println("[AutoReconnecto] 循环重连已停止");
        });
        
        reconnectThread.setName("AutoReconnecto-ReconnectLoop");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }
    
    /**
     * 停止循环重连
     */
    private static void stopReconnecting() {
        if (isReconnecting) {
            isReconnecting = false;
            if (reconnectThread != null) {
                reconnectThread.interrupt();
                reconnectThread = null;
            }
            System.out.println("[AutoReconnecto] 已停止重连任务");
        }
    }
    
    /**
     * 公开方法：用于 Mixin 从输入事件调用以暂停自动重连
     * 只在标题屏幕检测到鼠标点击或键盘按键时触发
     */
    public static void pauseReconnectFromInput() {
        if (!reconnectPaused) {
            reconnectPaused = true;
            // 如果正在重连，也停止重连线程
            stopReconnecting();
            System.out.println("[AutoReconnecto] ✗ 检测到主界面输入操作，已暂停自动重连");
        }
    }
    
    /**
     * 设置玩家主动退出标志
     * @param quitting true=玩家主动退出，false=非自愿断开
     */
    public static void setPlayerQuitting(boolean quitting) {
        playerQuitting = quitting;
    }
    @SubscribeEvent
    static void a7b8c9d0e1f23456(ClientPlayerNetworkEvent.LoggingOut event) {
        // 检查功能开关和暂停状态
        if (!ModConfig.AUTO_RECONNECT_ENABLED.get() || reconnectPaused) {
            AutoReconnectMod.LOGGER.info("自动重连已禁用或已暂停，跳过重连");
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        
        // 如果当前在暂停屏幕，说明玩家可能是主动退出
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            AutoReconnectMod.LOGGER.info("检测到在暂停屏幕中断开，可能是玩家主动退出，不触发重连");
            playerQuitting = false; // 重置标志
            return;
        }
        
        // 如果玩家是主动退出（点击了暂停菜单的返回标题屏幕按钮），不触发重连
        if (playerQuitting) {
            AutoReconnectMod.LOGGER.info("检测到玩家主动退出，不触发自动重连");
            playerQuitting = false; // 重置标志
            return;
        }
        
        // 只有在服务器内才执行重连
        if (!wasInServer) {
            AutoReconnectMod.LOGGER.info("未在服务器内，跳过重连");
            return;
        }
        
        // 停止之前的重连线程（如果有）
        stopReconnecting();
        
        AutoReconnectMod.LOGGER.info("检测到服务器断开，准备自动重连...");
        
        // 启动循环重连机制
        startReconnecting();
    }

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(Commands.literal("#debug-kick")
            .executes(ClientEventHandler::executeDebugKick));
        
        // 查看自动重连状态指令（保留向后兼容）
        dispatcher.register(Commands.literal("#autoreconnect")
            .then(Commands.literal("info")
                .executes(ClientEventHandler::executeReconnectStatus)));
        
        // 配置管理指令:/#config <ID> [值]
        // 查看所有配置:/#config info
        dispatcher.register(Commands.literal("#config")
            .then(Commands.literal("info")
                .executes(ClientEventHandler::executeAllConfigInfo))
            .then(Commands.argument("id", IntegerArgumentType.integer())
                .suggests((context, builder) -> {
                    // 提供配置 ID 提示（带配置名称）
                    builder.suggest("1 自动重连开关(发送时仅可使用前面的数字，<null>重置为默认值)");
                    builder.suggest("2 重连延迟(发送时仅可使用前面的数字，<null>重置为默认值)");
                    builder.suggest("3 指令延迟(发送时仅可使用前面的数字，<null>重置为默认值)");
                    builder.suggest("4 指令1(发送时仅可使用前面的数字，<null>重置为默认值)");
                    builder.suggest("5 指令2(发送时仅可使用前面的数字，<null>重置为默认值)");
                    builder.suggest("6 指令3(发送时仅可使用前面的数字，<null>重置为默认值)");
                    builder.suggest("7 指令4(发送时仅可使用前面的数字，<null>重置为默认值)");
                    builder.suggest("8 指令5(发送时仅可使用前面的数字，<null>重置为默认值)");
                    builder.suggest("9 指令6(发送时仅可使用前面的数字，<null>重置为默认值)");
                    return builder.buildFuture();
                })
                .executes(ClientEventHandler::executeConfigGet)
                .then(Commands.argument("value", StringArgumentType.greedyString())
                    .suggests((context, builder) -> {
                        // 根据配置 ID 提供可设置值提示
                        int configId = IntegerArgumentType.getInteger(context, "id");
                        switch (configId) {
                            case 1: // 自动重连开关
                                builder.suggest("true");
                                builder.suggest("false");
                                break;
                            case 2: // 重连延迟 (2-10)
                                for (int i = 2; i <= 10; i++) {
                                    builder.suggest(String.valueOf(i));
                                }
                                break;
                            case 3: // 指令延迟 (0-60)
                                for (int i = 0; i <= 60; i++) {
                                    builder.suggest(String.valueOf(i));
                                }
                                break;
                            case 4: // 指令 1 - 示例提示
                                builder.suggest("/login 您的密码");
                                break;
                            case 5: // 指令 2 - 示例提示
                                builder.suggest("/server game");
                                break;
                            case 6: // 指令 3 - 示例提示
                                builder.suggest("/server game");
                                break;
                            case 7: // 指令 4 - 示例提示
                                builder.suggest("");
                                break;
                            case 8: // 指令 5 - 示例提示
                                builder.suggest("");
                                break;
                            case 9: // 指令 6 - 示例提示
                                builder.suggest("");
                                break;
                        }
                        return builder.buildFuture();
                    })
                    .executes(ClientEventHandler::executeConfigSet))));
    }

    private static int executeDebugKick(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        AutoReconnectMod.LOGGER.info("Debug-kick command executed, disconnecting player from server...");
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.player.connection.disconnect(Component.translatable("Debugkick")
                .withStyle(net.minecraft.ChatFormatting.RED)
                .withStyle(net.minecraft.ChatFormatting.BOLD));
        }
        
        return 1;
    }
    
    // 查看自动重连状态指令
    private static int executeReconnectStatus(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        boolean enabled = ModConfig.AUTO_RECONNECT_ENABLED.get();
        String status = reconnectPaused ? "§c暂停" : "§a运行中";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("autoreconnect.message.status_" + (reconnectPaused ? "paused" : "running")), false);
            mc.player.displayClientMessage(Component.translatable(enabled ? "autoreconnect.message.status_enabled" : "autoreconnect.message.status_disabled"), false);
            if (!reconnectPaused && enabled) {
                mc.player.displayClientMessage(Component.translatable("autoreconnect.message.reconnect_delay_info", ModConfig.RECONNECT_DELAY.get()), false);
            }
        }
        return 1;
    }
    
    // 显示所有配置信息
    private static int executeAllConfigInfo(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.all_config_header"), false);
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_line", 1, "自动重连开关", ModConfig.AUTO_RECONNECT_ENABLED.get()), false);
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_line", 2, "重连延迟", ModConfig.RECONNECT_DELAY.get() + " 秒"), false);
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_line", 3, "指令延迟", ModConfig.COMMAND_DELAY.get() + " 秒"), false);
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_line", 4, "指令 1", ModConfig.COMMAND_1.get()), false);
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_line", 5, "指令 2", ModConfig.COMMAND_2.get()), false);
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_line", 6, "指令 3", ModConfig.COMMAND_3.get()), false);
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_line", 7, "指令 4", ModConfig.COMMAND_4.get()), false);
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_line", 8, "指令 5", ModConfig.COMMAND_5.get()), false);
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_line", 9, "指令 6", ModConfig.COMMAND_6.get()), false);
        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.all_config_footer"), false);
        
        AutoReconnectMod.LOGGER.info("玩家查看了所有配置信息");
        return 1;
    }
    
    // 获取单个配置值
    private static int executeConfigGet(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        int configId = IntegerArgumentType.getInteger(context, "id");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        
        String configName = getConfigName(configId);
        String configValue = getConfigValue(configId);
        String configHint = getConfigHint(configId);
        
        if (configValue != null) {
            // 显示当前值
            mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_get", configId, configName, configValue), false);
            
            // 显示提示信息（单独一行）
            if (configHint != null && !configHint.isEmpty()) {
                mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_hint", configHint), false);
            }
        } else {
            mc.player.displayClientMessage(Component.translatable("autoreconnect.message.invalid_config_id", configId), false);
        }
        
        AutoReconnectMod.LOGGER.info("玩家查看了配置 {} 的值：{}", configId, configValue);
        return 1;
    }
    
    // 获取配置值
    private static String getConfigValue(int id) {
        switch (id) {
            case 1: return String.valueOf(ModConfig.AUTO_RECONNECT_ENABLED.get());
            case 2: return String.valueOf(ModConfig.RECONNECT_DELAY.get());
            case 3: return String.valueOf(ModConfig.COMMAND_DELAY.get());
            case 4: return ModConfig.COMMAND_1.get();
            case 5: return ModConfig.COMMAND_2.get();
            case 6: return ModConfig.COMMAND_3.get();
            case 7: return ModConfig.COMMAND_4.get();
            case 8: return ModConfig.COMMAND_5.get();
            case 9: return ModConfig.COMMAND_6.get();
            default: return null;
        }
    }
    
    // 获取配置提示信息
    private static String getConfigHint(int id) {
        switch (id) {
            case 1: return "可设置值：true (启用) 或 false (禁用)。示例：/#config 1 true";
            case 2: return "可设置值：2-10 之间的整数（秒）。示例：/#config 2 5";
            case 3: return "可设置值：0-60 之间的整数（秒）。示例：/#config 3 10";
            case 4: return "可设置值：任意指令（不含斜杠）。示例：/#config 4 /login 123456";
            case 5: return "可设置值：任意指令（不含斜杠）。示例：/#config 5 /server game";
            case 6: return "可设置值：任意指令（不含斜杠）。示例：/#config 6 /spawn";
            case 7: return "可设置值：任意指令（不含斜杠）或留空。示例：/#config 7 /kit beginner";
            case 8: return "可设置值：任意指令（不含斜杠）或留空。示例：/#config 8 （空字符串）";
            case 9: return "可设置值：任意指令（不含斜杠）或留空。示例：/#config 9 （空字符串）";
            default: return null;
        }
    }
    
    // 修改配置指令
    private static int executeConfigSet(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        int configId = IntegerArgumentType.getInteger(context, "id");
        String value = StringArgumentType.getString(context, "value");
        Minecraft mc = Minecraft.getInstance();
        
        try {
            // 检查是否使用 <null> 重置为默认值
            boolean isReset = value.equalsIgnoreCase("<null>");
            
            boolean success;
            if (isReset) {
                success = resetConfigToDefault(configId);
                if (success) {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_reset", configId), false);
                    }
                    AutoReconnectMod.LOGGER.info("配置 {} 已重置为默认值", configId);
                    return 1;
                }
            } else {
                success = setConfigValue(configId, value);
                if (success) {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_updated", configId, value), false);
                    }
                    AutoReconnectMod.LOGGER.info("配置 {} 已更新为：{}", configId, value);
                }
            }
            
            if (!success) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_update_failed"), false);
                }
                AutoReconnectMod.LOGGER.warn("配置 {} 更新失败：{}", configId, value);
            }
        } catch (Exception e) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("autoreconnect.message.config_update_error", e.getMessage()), false);
            }
            AutoReconnectMod.LOGGER.error("配置更新出错", e);
        }
        
        return 1;
    }
    
    // 获取配置名称
    private static String getConfigName(int id) {
        switch (id) {
            case 1: return "自动重连开关";
            case 2: return "重连延迟";
            case 3: return "指令延迟";
            case 4: return "指令 1";
            case 5: return "指令 2";
            case 6: return "指令 3";
            case 7: return "指令 4";
            case 8: return "指令 5";
            case 9: return "指令 6";
            default: return "未知配置";
        }
    }
    
    // 设置配置值
    private static boolean setConfigValue(int id, String value) {
        switch (id) {
            case 1: // 自动重连开关
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    ModConfig.AUTO_RECONNECT_ENABLED.set(Boolean.parseBoolean(value));
                    return true;
                }
                return false;
                
            case 2: // 重连延迟 (2-10)
                try {
                    int delay = Integer.parseInt(value);
                    if (delay >= 2 && delay <= 10) {
                        ModConfig.RECONNECT_DELAY.set(delay);
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // 忽略
                }
                return false;
                
            case 3: // 指令延迟 (0-60)
                try {
                    int delay = Integer.parseInt(value);
                    if (delay >= 0 && delay <= 60) {
                        ModConfig.COMMAND_DELAY.set(delay);
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // 忽略
                }
                return false;
                
            case 4: // 指令 1
                ModConfig.COMMAND_1.set(value);
                return true;
                
            case 5: // 指令 2
                ModConfig.COMMAND_2.set(value);
                return true;
                
            case 6: // 指令 3
                ModConfig.COMMAND_3.set(value);
                return true;
                
            case 7: // 指令 4
                ModConfig.COMMAND_4.set(value);
                return true;
                
            case 8: // 指令 5
                ModConfig.COMMAND_5.set(value);
                return true;
                
            case 9: // 指令 6
                ModConfig.COMMAND_6.set(value);
                return true;
                
            default:
                return false;
        }
    }
    
    // 重置配置为默认值
    private static boolean resetConfigToDefault(int id) {
        switch (id) {
            case 1: // 自动重连开关 - 默认值：true
                ModConfig.AUTO_RECONNECT_ENABLED.set(true);
                return true;
                
            case 2: // 重连延迟 - 默认值：2
                ModConfig.RECONNECT_DELAY.set(2);
                return true;
                
            case 3: // 指令延迟 - 默认值：0
                ModConfig.COMMAND_DELAY.set(0);
                return true;
                
            case 4: // 指令 1 - 默认值：/login 
                ModConfig.COMMAND_1.set("/login ");
                return true;
                
            case 5: // 指令 2 - 默认值：/server game
                ModConfig.COMMAND_2.set("/server game");
                return true;
                
            case 6: // 指令 3 - 默认值：/server game
                ModConfig.COMMAND_3.set("/server game");
                return true;
                
            case 7: // 指令 4 - 默认值：空字符串
                ModConfig.COMMAND_4.set("");
                return true;
                
            case 8: // 指令 5 - 默认值：空字符串
                ModConfig.COMMAND_5.set("");
                return true;
                
            case 9: // 指令 6 - 默认值：空字符串
                ModConfig.COMMAND_6.set("");
                return true;
                
            default:
                return false;
        }
    }
}
