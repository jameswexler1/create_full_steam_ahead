package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

public final class SteamOpenPipeEffectHandler implements OpenPipeEffectHandler {
    public static final SteamOpenPipeEffectHandler INSTANCE = new SteamOpenPipeEffectHandler();

    public static void register() {
        OpenPipeEffectHandler.REGISTRY.register(ModFluids.STEAM.get(), INSTANCE);
    }

    @Override
    public void apply(Level level, AABB area, FluidStack stack) {
        if (!(level instanceof ServerLevel serverLevel) || stack.isEmpty()) {
            return;
        }

        SteamCloudEffects.emitOpenPipe(serverLevel, area, stack.getAmount());
    }

    private SteamOpenPipeEffectHandler() {
    }
}
