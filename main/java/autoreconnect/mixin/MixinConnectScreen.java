package autoreconnect.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class MixinConnectScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoReconnecto");
    
    @Inject(method = "connect", at = @At("HEAD"))
    private void onConnectStart(CallbackInfo ci) {
        LOGGER.info("正在连接到服务器");
    }
    
    @Inject(method = "connect", at = @At("RETURN"))
    private void onConnectComplete(CallbackInfo ci) {
        LOGGER.info("连接请求已发起");
    }
}
