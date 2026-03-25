package autoreconnect;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * AutoReconnect - 自动重连模组主类
 * 
 * 功能说明：
 * 1. 服务器断开后自动尝试重连
 * 2. 检测到登录提示后自动发送指令序列（如 /login）
 * 3. 支持在主界面暂停/恢复自动重连
 * 
 * @author AutoReconnect Team
 */
@Mod("autoreconnect")
public class AutoReconnectMod {
    /** 模组 ID */
    public static final String MOD_ID = "autoreconnect";
    
    /** 日志记录器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 获取翻译文本（支持多语言）
     * 
     * @param key 翻译键（格式：autoreconnect.message.xxx）
     * @param args 格式化参数
     * @return Component 对象，包含翻译后的文本
     */
    public static Component getTranslation(String key, Object... args) {
        return Component.translatable("autoreconnect.message." + key, args);
    }

    /**
     * 模组构造函数
     * NeoForge 会自动调用此方法初始化模组
     * 
     * @param modEventBus 模组事件总线
     * @param modContainer 模组容器
     */
    public AutoReconnectMod(IEventBus modEventBus, ModContainer modContainer) {
        // 注册公共设置事件监听器
        modEventBus.addListener(this::commonSetup);
        
        // 注册 NeoForge 全局事件总线
        NeoForge.EVENT_BUS.register(this);
        
        // 注册模组配置（类型：COMMON，通用配置）
        modContainer.registerConfig(
            net.neoforged.fml.config.ModConfig.Type.COMMON, 
            autoreconnect.ModConfig.SPEC
        );
    }

    /**
     * 公共设置阶段回调
     * 在客户端和服务端都会执行
     * 
     * @param event 公共设置事件
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("AutoReconnect mod loaded");
    }

    /**
     * 服务器启动事件监听器
     * 当服务器即将启动时触发
     * 
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}
