package autoreconnect.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import autoreconnect.ClientEventHandler;

@Mixin(TitleScreen.class)
public class MixinTitleScreenMouseClick {
    
    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void onMouseClicked(MouseButtonEvent event, boolean isPaused, CallbackInfoReturnable<Boolean> cir) {
        int button = event.button();
        // 只检测左右键
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (Minecraft.getInstance().getConnection() == null) {
                System.out.println("[AutoReconnecto] ✗ 检测到主界面鼠标点击 (按钮：" + button + ")，已暂停自动重连");
                ClientEventHandler.pauseReconnectFromInput();
            }
        }
    }
}
