package dev.gustavo.fullsteamahead.compat.create;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.crankshaft.CrankshaftValidator;
import dev.gustavo.fullsteamahead.content.steam.BoilerOutletBlock;
import dev.gustavo.fullsteamahead.content.steam.BoilerOutletBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
                BlockPos crankshaftPos = ringOrigin.offset(1, 3, 1);
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

        return crankshafts.size() + countAttachedOutlets(controller);
    }

    private static int countAttachedOutlets(FluidTankBlockEntity controller) {
        Level level = controller.getLevel();
        BlockPos controllerPos = controller.getBlockPos();
        int width = controller.getWidth();
        int height = controller.getHeight();

        Set<BlockPos> outlets = new HashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    BlockPos tankPos = controllerPos.offset(x, y, z);
                    for (Direction direction : Direction.values()) {
                        BlockPos outletPos = tankPos.relative(direction);
                        if (!level.isLoaded(outletPos)) {
                            continue;
                        }

                        BlockState state = level.getBlockState(outletPos);
                        if (!state.is(ModBlocks.BOILER_OUTLET.get())
                                || BoilerOutletBlock.getFacing(state) != direction) {
                            continue;
                        }

                        BlockEntity blockEntity = level.getBlockEntity(outletPos);
                        if (blockEntity instanceof BoilerOutletBlockEntity outlet
                                && outlet.isAttachedToBoiler(controller)) {
                            outlets.add(outletPos);
                        }
                    }
                }
            }
        }

        return outlets.size();
    }

    public static int compactBoilerHeatLimit(int tankSize) {
        return Math.min(MAX_COMPACT_HEAT_LEVEL, tankSize);
    }

    private FullSteamBoilerIntegration() {
    }
}
