package autoreconnect;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置类
 * 
 * 定义所有可配置的选项，包括：
 * - 自动重连功能开关
 * - 重连延迟时间
 * - 指令发送延迟
 * - 6 个可自定义的指令
 * 
 * 配置文件位置：run/config/autoreconnect-common.toml
 */
public class ModConfig {
    /** 配置构建器 */
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ==================== 基础配置 ====================
    
    /**
     * 自动重连功能开关
     * true = 启用自动重连，false = 禁用
     */
    public static final ModConfigSpec.BooleanValue AUTO_RECONNECT_ENABLED = BUILDER
            .comment("启用或禁用自动重连功能")
            .translation("configuration.autoReconnecto.autoReconnectEnabled")
            .define("autoReconnectEnabled", true);

    /**
     * 重连等待时间（秒）
     * 服务器断开后等待多久开始重连
     * 范围：2-10 秒
     */
    public static final ModConfigSpec.IntValue RECONNECT_DELAY = BUILDER
            .comment("断开连接后等待多少秒开始重连（0-10 秒）")
            .translation("configuration.autoReconnecto.reconnectDelay")
            .defineInRange("reconnectDelay", 2, 2, 10);

    // ==================== 指令配置 ====================
    
    /**
     * 指令发送延迟（秒）
     * 加入服务器后延迟多久发送指令
     * 范围：0-60 秒
     */
    public static final ModConfigSpec.IntValue COMMAND_DELAY = BUILDER
            .comment("加入服务器后延迟发送指令的时间（秒）")
            .translation("configuration.autoReconnecto.commandDelay")
            .defineInRange("commandDelay", 0, 0, 60);

    /**
     * 六条可配置的指令
     * 玩家可在配置文件中自定义要发送的指令
     * 例如：/login <密码>, /server game 等
     */
    
    /** 第一条指令（默认：登录指令） */
    public static final ModConfigSpec.ConfigValue<String> COMMAND_1 = BUILDER
            .comment("在服务器聊天后自动发送的第一条指令")
            .translation("configuration.autoReconnecto.command1")
            .define("command1", "/login ");

    /** 第二条指令（默认：切换到游戏服） */
    public static final ModConfigSpec.ConfigValue<String> COMMAND_2 = BUILDER
            .comment("在服务器聊天后自动发送的第二条指令")
            .translation("configuration.autoReconnecto.command2")
            .define("command2", "/server game");

    /** 第三条指令（默认备用） */
    public static final ModConfigSpec.ConfigValue<String> COMMAND_3 = BUILDER
            .comment("在服务器聊天后自动发送的第三条指令")
            .translation("configuration.autoReconnecto.command3")
            .define("command3", "/server game");

    /** 第四条指令（默认空） */
    public static final ModConfigSpec.ConfigValue<String> COMMAND_4 = BUILDER
            .comment("在服务器聊天后自动发送的第四条指令")
            .translation("configuration.autoReconnecto.command4")
            .define("command4", "");

    /** 第五条指令（默认空） */
    public static final ModConfigSpec.ConfigValue<String> COMMAND_5 = BUILDER
            .comment("在服务器聊天后自动发送的第五条指令")
            .translation("configuration.autoReconnecto.command5")
            .define("command5", "");

    /** 第六条指令（默认空） */
    public static final ModConfigSpec.ConfigValue<String> COMMAND_6 = BUILDER
            .comment("在服务器聊天后自动发送的第六条指令")
            .translation("configuration.autoReconnecto.command6")
            .define("command6", "");

    /**
     * 配置规范对象
     * NeoForge 使用此对象验证和管理配置
     */
    public static final ModConfigSpec SPEC = BUILDER.build();
}
