package autoreconnect.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import autoreconnect.ClientEventHandler;

@Mixin(PauseScreen.class)
public class MixinPauseScreenQuitButton {
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // 暂停界面初始化时不设置标志，避免误判
        // ClientEventHandler.setPlayerQuitting(false);
    }
}
