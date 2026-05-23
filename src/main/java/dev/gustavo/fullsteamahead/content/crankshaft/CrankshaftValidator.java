package dev.gustavo.fullsteamahead.content.crankshaft;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlock;
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

        if (!isCenterClear(level, pistons.lowerBore())) {
            return Result.invalid("Lower cylinder bore must be empty");
        }
        if (!isPistonHead(level, pistons.pistonHead())) {
            return Result.invalid("Missing piston head");
        }
        if (!isPiston(level, pistons.upperPiston())) {
            return Result.invalid("Missing upper protruding piston");
        }
        if (!isPiston(level, pistons.lowerPiston())) {
            return Result.invalid("Missing lower protruding piston");
        }

        BlockPos ringOrigin = crankshaftPos.offset(-1, -4, -1);
        List<BlockPos> ringPositions = ringPositions(ringOrigin);
        List<BlockPos> cylinderPositions = new ArrayList<>(16);
        BlockPos inletPos = null;
        for (BlockPos pos : ringPositions) {
            if (!level.isLoaded(pos)) {
                return Result.invalid("Cylinder ring is unloaded");
            }

            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.STEAM_CYLINDER.get())) {
                if (!isAssembled(state, SteamCylinderBlock.ASSEMBLED)) {
                    return Result.invalid("Cylinder ring is not assembled");
                }
                cylinderPositions.add(pos);
                continue;
            }

            if (state.is(ModBlocks.STEAM_INLET.get())) {
                if (inletPos != null) {
                    return Result.invalid("Too many steam inlets");
                }
                if (!isAssembled(state, SteamInletBlock.ASSEMBLED)) {
                    return Result.invalid("Steam inlet is not assembled");
                }
                inletPos = pos;
                continue;
            }

            return Result.invalid("Missing steam cylinder ring");
        }

        BoilerScan boiler = findDirectBoiler(level, ringOrigin);
        if (!boiler.valid() && inletPos == null) {
            return Result.invalid(boiler.message());
        }

        BlockPos cylinderRoot = cylinderPositions.stream()
                .min(ROOT_ORDER)
                .orElse(ringOrigin);

        return new Result(
                true,
                "Structure assembled",
                ringOrigin,
                cylinderRoot,
                boiler.boilerPos(),
                inletPos,
                pistons.pistonHead(),
                pistons.lowerPiston(),
                pistons.upperPiston()
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

    private static boolean isPistonHead(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.PISTON_HEAD.get());
    }

    private static boolean isCenterClear(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).isAir();
    }

    private static boolean isAssembled(
            BlockState state,
            net.minecraft.world.level.block.state.properties.BooleanProperty property
    ) {
        return state.hasProperty(property) && state.getValue(property);
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

    private static BoilerScan findDirectBoiler(Level level, BlockPos ringOrigin) {
        BlockPos boilerPos = null;
        for (BlockPos pos : boilerShellPositions(ringOrigin)) {
            if (!level.isLoaded(pos)) {
                return BoilerScan.invalid("Boiler layer is unloaded");
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof FluidTankBlockEntity tank)) {
                return BoilerScan.invalid("Missing Create fluid tank boiler");
            }

            if (boilerPos == null) {
                FluidTankBlockEntity controller = tank.getControllerBE();
                boilerPos = controller == null ? pos : controller.getBlockPos();
            }
        }

        return BoilerScan.valid(boilerPos);
    }

    private static boolean isCenter(int x, int z) {
        return x == 1 && z == 1;
    }

    public record PistonPositions(
            BlockPos lowerBore,
            BlockPos pistonHead,
            BlockPos lowerPiston,
            BlockPos upperPiston
    ) {
    }

    public record Result(
            boolean valid,
            String message,
            BlockPos ringOrigin,
            BlockPos cylinderRoot,
            BlockPos boilerPos,
            BlockPos inletPos,
            BlockPos pistonHead,
            BlockPos lowerPiston,
            BlockPos upperPiston
    ) {
        public static Result invalid(String message) {
            return new Result(false, message, null, null, null, null, null, null, null);
        }
    }

    private record BoilerScan(boolean valid, String message, BlockPos boilerPos) {
        private static BoilerScan valid(BlockPos boilerPos) {
            return new BoilerScan(true, "Direct boiler linked", boilerPos);
        }

        private static BoilerScan invalid(String message) {
            return new BoilerScan(false, message, null);
        }
    }

    private CrankshaftValidator() {
    }
}
