package dev.gustavo.fullsteamahead.mixin.client;

import dev.gustavo.fullsteamahead.client.AdmissionValveTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void fullSteamAhead$pickAdmissionValveController(float partialTick, CallbackInfo ci) {
        Entity camera = minecraft.getCameraEntity();
        if (camera == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        Vec3 start = camera.getEyePosition(partialTick);
        Vec3 end = start.add(camera.getViewVector(partialTick)
                .scale(minecraft.player.blockInteractionRange()));
        BlockHitResult towerHit = AdmissionValveTargeting.findCloserTowerHit(
                minecraft.level,
                start,
                end,
                minecraft.hitResult
        );
        if (towerHit == null) {
            return;
        }

        minecraft.hitResult = towerHit;
        minecraft.crosshairPickEntity = null;
    }
}
