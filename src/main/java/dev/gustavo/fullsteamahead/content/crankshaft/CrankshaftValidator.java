package dev.gustavo.fullsteamahead.content.crankshaft;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlock;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CrankshaftValidator {
    private static final Comparator<BlockPos> ROOT_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(pos -> pos.getX())
            .thenComparingInt(pos -> pos.getZ());

    public static Result validate(Level level, BlockPos crankshaftPos) {
        PistonPositions pistons = pistonPositions(crankshaftPos);

        if (!isPiston(level, pistons.protrudeHigh())) {
            return Result.invalid("Missing upper protruding piston");
        }
        if (!isPiston(level, pistons.protrudeLow())) {
            return Result.invalid("Missing lower protruding piston");
        }
        if (!isPiston(level, pistons.insideHigh())) {
            return Result.invalid("Missing upper internal piston");
        }
        if (!isPiston(level, pistons.insideLow())) {
            return Result.invalid("Missing lower internal piston");
        }

        BlockPos ringOrigin = crankshaftPos.offset(-1, -4, -1);
        List<BlockPos> ringPositions = ringPositions(ringOrigin);
        for (BlockPos pos : ringPositions) {
            if (!level.isLoaded(pos)) {
                return Result.invalid("Cylinder ring is unloaded");
            }

            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlocks.STEAM_CYLINDER.get())) {
                return Result.invalid("Missing steam cylinder ring");
            }
            if (!state.hasProperty(SteamCylinderBlock.ASSEMBLED)
                    || !state.getValue(SteamCylinderBlock.ASSEMBLED)) {
                return Result.invalid("Cylinder ring is not assembled");
            }
        }

        BlockPos boilerPos = null;
        for (BlockPos pos : boilerShellPositions(ringOrigin)) {
            if (!level.isLoaded(pos)) {
                return Result.invalid("Boiler layer is unloaded");
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof FluidTankBlockEntity tank)) {
                return Result.invalid("Missing Create fluid tank boiler");
            }

            if (boilerPos == null) {
                FluidTankBlockEntity controller = tank.getControllerBE();
                boilerPos = controller == null ? pos : controller.getBlockPos();
            }
        }

        BlockPos cylinderRoot = ringPositions.stream()
                .min(ROOT_ORDER)
                .orElse(ringOrigin);

        return new Result(
                true,
                "Structure assembled",
                ringOrigin,
                cylinderRoot,
                boilerPos,
                pistons.insideLow(),
                pistons.insideHigh(),
                pistons.protrudeLow(),
                pistons.protrudeHigh()
        );
    }

    public static PistonPositions pistonPositions(BlockPos crankshaftPos) {
        return new PistonPositions(
                crankshaftPos.below(4),
                crankshaftPos.below(3),
                crankshaftPos.below(2),
                crankshaftPos.below(1)
        );
    }

    public static List<BlockPos> candidateCrankshaftsNear(BlockPos changedPos) {
        List<BlockPos> candidates = new ArrayList<>(175);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 6; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    candidates.add(changedPos.offset(dx, dy, dz));
                }
            }
        }
        return candidates;
    }

    private static boolean isPiston(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.PISTON.get());
    }

    private static List<BlockPos> ringPositions(BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>(16);
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    if (!isCenter(x, z)) {
                        positions.add(origin.offset(x, y, z));
                    }
                }
            }
        }
        return positions;
    }

    private static List<BlockPos> boilerShellPositions(BlockPos ringOrigin) {
        List<BlockPos> positions = new ArrayList<>(8);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                if (!isCenter(x, z)) {
                    positions.add(ringOrigin.offset(x, -1, z));
                }
            }
        }
        return positions;
    }

    private static boolean isCenter(int x, int z) {
        return x == 1 && z == 1;
    }

    public record PistonPositions(
            BlockPos insideLow,
            BlockPos insideHigh,
            BlockPos protrudeLow,
            BlockPos protrudeHigh
    ) {
    }

    public record Result(
            boolean valid,
            String message,
            BlockPos ringOrigin,
            BlockPos cylinderRoot,
            BlockPos boilerPos,
            BlockPos insideLow,
            BlockPos insideHigh,
            BlockPos protrudeLow,
            BlockPos protrudeHigh
    ) {
        public static Result invalid(String message) {
            return new Result(false, message, null, null, null, null, null, null, null);
        }
    }

    private CrankshaftValidator() {
    }
}
