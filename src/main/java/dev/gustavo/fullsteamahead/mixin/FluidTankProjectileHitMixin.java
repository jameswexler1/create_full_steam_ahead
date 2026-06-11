package dev.gustavo.fullsteamahead.mixin;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.steam.SteamNetworkManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.class)
public abstract class FluidTankProjectileHitMixin {
    @Inject(method = "onProjectileHit", at = @At("HEAD"))
    private void fullSteamAhead$rupturePressurizedBoilerOnCannonHit(
            Level level,
            BlockState state,
            BlockHitResult hit,
            Projectile projectile,
            CallbackInfo ci
    ) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(state.getBlock() instanceof FluidTankBlock)
                || !(level.getBlockEntity(hit.getBlockPos()) instanceof FluidTankBlockEntity tank)
                || !fullSteamAhead$isCreateBigCannonsProjectile(projectile)) {
            return;
        }

        SteamNetworkManager.ruptureBoilerFromProjectile(serverLevel, tank);
    }

    private static boolean fullSteamAhead$isCreateBigCannonsProjectile(Projectile projectile) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        if (entityId != null && "createbigcannons".equals(entityId.getNamespace())) {
            return true;
        }
        return projectile.getClass().getName().startsWith("rbasamoyai.createbigcannons.");
    }
}
