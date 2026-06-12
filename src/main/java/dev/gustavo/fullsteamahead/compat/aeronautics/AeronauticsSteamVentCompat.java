package dev.gustavo.fullsteamahead.compat.aeronautics;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class AeronauticsSteamVentCompat {
    public static final ResourceLocation STEAM_VENT_ID =
            ResourceLocation.fromNamespaceAndPath("aeronautics", "steam_vent");
    private static final String STEAM_VENT_BE_CLASS =
            "dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity";

    private static Method canOutputGasMethod;
    private static Method getGasOutputMethod;
    private static boolean methodsResolved;
    private static boolean reflectionWarningLogged;

    public static int steamDemandMb(Level level, FluidTankBlockEntity boiler) {
        if (!FullSteamConfig.aeronauticsSteamVentConsumptionEnabled()
                || FullSteamConfig.aeronauticsSteamVentMbPerM3() <= 0.0D
                || level == null
                || boiler == null
                || boiler.isRemoved()) {
            return 0;
        }

        double totalGasOutput = 0.0D;
        BlockPos origin = boiler.getBlockPos();
        int width = Math.max(1, boiler.getWidth());
        int topY = origin.getY() + Math.max(1, boiler.getHeight());

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                BlockPos ventPos = new BlockPos(origin.getX() + x, topY, origin.getZ() + z);
                if (!level.isLoaded(ventPos) || !isSteamVent(level.getBlockState(ventPos))) {
                    continue;
                }

                totalGasOutput += gasOutput(level.getBlockEntity(ventPos));
            }
        }

        if (totalGasOutput <= 0.0D) {
            return 0;
        }

        double steamMb = totalGasOutput * FullSteamConfig.aeronauticsSteamVentMbPerM3();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, Math.round(steamMb)));
    }

    public static boolean isSteamVent(BlockState state) {
        return STEAM_VENT_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public static int steamMbForGasOutput(double gasOutput) {
        if (!FullSteamConfig.aeronauticsSteamVentConsumptionEnabled()
                || FullSteamConfig.aeronauticsSteamVentMbPerM3() <= 0.0D
                || gasOutput <= 0.0D) {
            return 0;
        }
        double steamMb = gasOutput * FullSteamConfig.aeronauticsSteamVentMbPerM3();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, Math.round(steamMb)));
    }

    private static double gasOutput(BlockEntity blockEntity) {
        if (blockEntity == null || !STEAM_VENT_BE_CLASS.equals(blockEntity.getClass().getName())) {
            return 0.0D;
        }
        if (!resolveMethods(blockEntity.getClass())) {
            return 0.0D;
        }

        try {
            if (!(Boolean) canOutputGasMethod.invoke(blockEntity)) {
                return 0.0D;
            }
            Object value = getGasOutputMethod.invoke(blockEntity);
            if (value instanceof Number number) {
                return Math.max(0.0D, number.doubleValue());
            }
        } catch (IllegalAccessException | InvocationTargetException | ClassCastException exception) {
            logReflectionWarning(exception);
        }
        return 0.0D;
    }

    private static boolean resolveMethods(Class<?> ventClass) {
        if (methodsResolved) {
            return canOutputGasMethod != null && getGasOutputMethod != null;
        }

        methodsResolved = true;
        try {
            canOutputGasMethod = ventClass.getMethod("canOutputGas");
            getGasOutputMethod = ventClass.getMethod("getGasOutput");
            return true;
        } catch (NoSuchMethodException exception) {
            logReflectionWarning(exception);
            return false;
        }
    }

    private static void logReflectionWarning(Exception exception) {
        if (reflectionWarningLogged) {
            return;
        }
        reflectionWarningLogged = true;
        FullSteamAhead.LOGGER.warn("Unable to read Create Aeronautics steam vent output", exception);
    }

    private AeronauticsSteamVentCompat() {
    }
}
