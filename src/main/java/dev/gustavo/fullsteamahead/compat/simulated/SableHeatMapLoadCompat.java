package dev.gustavo.fullsteamahead.compat.simulated;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Detects Sable's one-time sublevel heat-map scan without adding a compile-time Sable dependency.
 */
public final class SableHeatMapLoadCompat {
    private static final String SABLE_CLASS = "dev.ryanhcode.sable.Sable";
    private static boolean resolved;
    private static boolean available;
    private static boolean warned;
    private static Object helper;
    private static Method getContaining;
    private static Method getHeatMapManager;
    private static Field heatMapInitialized;

    public static boolean isInitialSublevelLoad(Level level, BlockPos pos) {
        if (level == null || level.isClientSide() || !ModList.get().isLoaded("sable")) {
            return false;
        }
        resolve();
        if (!available) {
            return false;
        }

        try {
            Object sublevel = getContaining.invoke(helper, level, pos);
            if (sublevel == null) {
                return false;
            }
            Object heatMap = getHeatMapManager.invoke(sublevel);
            return heatMap != null && !heatMapInitialized.getBoolean(heatMap);
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
            return false;
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;

        try {
            Class<?> sableClass = Class.forName(SABLE_CLASS);
            helper = sableClass.getField("HELPER").get(null);
            getContaining = helper.getClass().getMethod("getContaining", Level.class, Vec3i.class);

            Class<?> serverSublevelClass = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            getHeatMapManager = serverSublevelClass.getMethod("getHeatMapManager");
            Class<?> heatMapClass = Class.forName(
                    "dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager"
            );
            heatMapInitialized = heatMapClass.getDeclaredField("initialized");
            heatMapInitialized.setAccessible(true);
            available = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    private static void disable(Throwable exception) {
        if (!warned) {
            warned = true;
            FullSteamAhead.LOGGER.warn(
                    "Unable to inspect Sable's initial heat-map load; existing engines will use normal tick recovery",
                    exception
            );
        }
        available = false;
    }

    private SableHeatMapLoadCompat() {
    }
}
