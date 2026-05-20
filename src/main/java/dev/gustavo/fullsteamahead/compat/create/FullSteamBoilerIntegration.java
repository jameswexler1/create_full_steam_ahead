package dev.gustavo.fullsteamahead.compat.create;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.crankshaft.CrankshaftValidator;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

public final class FullSteamBoilerIntegration {
    public static final int MIN_BOILER_TANKS = 9;
    public static final int MAX_COMPACT_HEAT_LEVEL = 18;

    public static int countAttachedEngines(FluidTankBlockEntity boiler) {
        FluidTankBlockEntity controller = boiler == null ? null : boiler.getControllerBE();
        if (controller == null) {
            return 0;
        }

        Level level = controller.getLevel();
        if (level == null) {
            return 0;
        }

        int width = controller.getWidth();
        int height = controller.getHeight();
        if (width < 3 || height < 1) {
            return 0;
        }

        BlockPos controllerPos = controller.getBlockPos();
        Set<BlockPos> crankshafts = new HashSet<>();
        for (int x = 0; x <= width - 3; x++) {
            for (int z = 0; z <= width - 3; z++) {
                BlockPos ringOrigin = controllerPos.offset(x, height, z);
                BlockPos crankshaftPos = ringOrigin.offset(1, 4, 1);
                if (!level.isLoaded(crankshaftPos)
                        || !level.getBlockState(crankshaftPos).is(ModBlocks.CRANKSHAFT.get())) {
                    continue;
                }

                CrankshaftValidator.Result result = CrankshaftValidator.validate(level, crankshaftPos);
                if (result.valid() && controllerPos.equals(result.boilerPos())) {
                    crankshafts.add(crankshaftPos);
                }
            }
        }

        return crankshafts.size();
    }

    public static int compactBoilerHeatLimit(int tankSize) {
        return Math.min(MAX_COMPACT_HEAT_LEVEL, tankSize);
    }

    private FullSteamBoilerIntegration() {
    }
}
