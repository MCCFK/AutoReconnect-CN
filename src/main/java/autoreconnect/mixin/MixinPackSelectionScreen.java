package autoreconnect.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;

/**
 * 根据 Minecraft 官方协议文档实现资源包自动拒绝
 * https://wiki.vg/Protocol#Resource_Pack_Send
 * 
 * 当 PackSelectionScreen 创建时，自动关闭屏幕
 */
@Mixin(PackSelectionScreen.class)
public class MixinPackSelectionScreen {
    
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        System.out.println("[AutoReconnecto] 检测到资源包选择界面，执行自动拒绝...");
        
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try {
                // 直接关闭资源包选择界面
                if (mc.screen instanceof PackSelectionScreen) {
                    mc.screen.onClose();
                    System.out.println("[AutoReconnecto] ✓ 已关闭资源包选择界面");
                }
                
            } catch (Exception e) {
                System.err.println("[AutoReconnecto] ✗ 处理资源包请求失败：" + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
