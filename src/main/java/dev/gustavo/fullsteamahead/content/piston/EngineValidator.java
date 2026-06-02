package dev.gustavo.fullsteamahead.content.piston;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlock;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlockEntity;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlock;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EngineValidator {
    private static final Comparator<BlockPos> ROOT_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(pos -> pos.getX())
            .thenComparingInt(pos -> pos.getZ());

    public static Result validate(Level level, BlockPos pistonHeadPos) {
        if (!isPistonHead(level, pistonHeadPos)) {
            return Result.invalid("Missing piston head");
        }

        Direction preferredDirection = pistonHeadFacing(level.getBlockState(pistonHeadPos));
        Result preferred = validate(level, pistonHeadPos, preferredDirection);
        if (preferred.valid()) {
            return preferred;
        }

        Result opposite = validate(level, pistonHeadPos, preferredDirection.getOpposite());
        return opposite.valid() ? opposite : preferred;
    }

    private static Result validate(Level level, BlockPos pistonHeadPos, Direction strokeDirection) {
        PistonPositions pistons = pistonPositions(pistonHeadPos, strokeDirection);
        if (!isPiston(level, pistons.piston())) {
            return Result.invalid("Missing piston body");
        }
        if (!isEmpty(level, pistons.emptyStroke())) {
            return Result.invalid("Stroke space must be empty");
        }
        if (!isValidShaft(level, pistons.shaft())) {
            return Result.invalid("Missing horizontal Create shaft");
        }

        BlockPos ringOrigin = ringOriginFor(pistonHeadPos, strokeDirection);
        List<BlockPos> ringPositions = ringPositions(ringOrigin);
        List<BlockPos> cylinderPositions = new ArrayList<>(16);
        BlockPos inletPos = null;
        for (BlockPos pos : ringPositions) {
            if (!level.isLoaded(pos)) {
                return Result.invalid("Cylinder ring is unloaded");
            }

            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.STEAM_CYLINDER.get())) {
                if (!isCylinderAssembledFor(level, pos, state, ringOrigin)) {
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

        if (strokeDirection == Direction.DOWN && inletPos == null) {
            return Result.invalid("Upside-down engines need a steam inlet");
        }

        BoilerScan boiler = strokeDirection == Direction.UP
                ? findDirectBoiler(level, ringOrigin)
                : BoilerScan.invalid("Upside-down engines are pipe-fed only");

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
                strokeDirection,
                pistons.pistonHead(),
                pistons.piston(),
                pistons.emptyStroke(),
                pistons.shaft()
        );
    }

    public static PistonPositions pistonPositions(BlockPos pistonHeadPos) {
        return pistonPositions(pistonHeadPos, Direction.UP);
    }

    public static PistonPositions pistonPositions(BlockPos pistonHeadPos, Direction strokeDirection) {
        return new PistonPositions(
                pistonHeadPos,
                pistonHeadPos.relative(strokeDirection),
                pistonHeadPos.relative(strokeDirection, 2),
                pistonHeadPos.relative(strokeDirection, 3)
        );
    }

    public static PistonPositions pistonPositionsFromBody(BlockPos pistonPos) {
        return pistonPositions(pistonPos.below());
    }

    public static PistonPositions pistonPositionsFromBody(Level level, BlockPos pistonPos) {
        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            BlockPos headPos = pistonPos.relative(direction.getOpposite());
            if (isPistonHead(level, headPos)) {
                return pistonPositions(headPos, direction);
            }
        }

        Direction strokeDirection = Direction.UP;
        if (level.isLoaded(pistonPos)) {
            strokeDirection = pistonFacing(level.getBlockState(pistonPos));
        }
        return pistonPositions(pistonPos.relative(strokeDirection.getOpposite()), strokeDirection);
    }

    public static boolean isReadyForShaftPlacement(Level level, BlockPos pistonPos) {
        PistonPositions pistons = pistonPositionsFromBody(level, pistonPos);
        if (!isPistonHead(level, pistons.pistonHead()) || !isPiston(level, pistons.piston())) {
            return false;
        }
        if (!isEmpty(level, pistons.emptyStroke())) {
            return false;
        }
        if (!level.isLoaded(pistons.shaft()) || !level.getBlockState(pistons.shaft()).canBeReplaced()) {
            return false;
        }

        Direction strokeDirection = strokeDirectionFor(pistons);
        BlockPos ringOrigin = ringOriginFor(pistons.pistonHead(), strokeDirection);
        return isRingReady(level, ringOrigin, strokeDirection);
    }

    public static List<BlockPos> candidatePistonHeadsNear(BlockPos changedPos) {
        List<BlockPos> candidates = new ArrayList<>(175);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
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

    private static boolean isEmpty(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).isAir();
    }

    public static boolean isValidShaft(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!AllBlocks.SHAFT.has(state) && !state.is(ModBlocks.POWERED_SHAFT.get())) {
            return false;
        }

        if (!(state.getBlock() instanceof AbstractShaftBlock shaft)) {
            return false;
        }

        return shaft.getRotationAxis(state).isHorizontal();
    }

    public static Direction.Axis shaftAxis(Level level, BlockPos shaftPos) {
        BlockState state = level.getBlockState(shaftPos);
        if (state.getBlock() instanceof AbstractShaftBlock shaft) {
            return shaft.getRotationAxis(state);
        }
        return Direction.Axis.X;
    }

    public static Direction pistonHeadFacing(BlockState state) {
        return state.hasProperty(PistonHeadBlock.FACING) ? state.getValue(PistonHeadBlock.FACING) : Direction.UP;
    }

    public static Direction pistonFacing(BlockState state) {
        return state.hasProperty(SteamPistonBlock.FACING) ? state.getValue(SteamPistonBlock.FACING) : Direction.UP;
    }

    public static BlockPos ringOriginFor(BlockPos pistonHeadPos, Direction strokeDirection) {
        return pistonHeadPos.offset(-1, strokeDirection == Direction.DOWN ? -1 : 0, -1);
    }

    private static Direction strokeDirectionFor(PistonPositions pistons) {
        return pistons.piston().getY() < pistons.pistonHead().getY() ? Direction.DOWN : Direction.UP;
    }

    private static boolean isAssembled(
            BlockState state,
            net.minecraft.world.level.block.state.properties.BooleanProperty property
    ) {
        return state.hasProperty(property) && state.getValue(property);
    }

    private static boolean isCylinderAssembledFor(Level level, BlockPos pos, BlockState state, BlockPos ringOrigin) {
        if (!isAssembled(state, SteamCylinderBlock.ASSEMBLED)) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SteamCylinderBlockEntity cylinder) {
            return cylinder.belongsToRingOrigin(ringOrigin);
        }

        return false;
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

    private static boolean isRingReady(Level level, BlockPos origin, Direction strokeDirection) {
        int inlets = 0;
        for (BlockPos pos : ringPositions(origin)) {
            if (!level.isLoaded(pos)) {
                return false;
            }

            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.STEAM_CYLINDER.get())
                    && isCylinderAssembledFor(level, pos, state, origin)) {
                continue;
            }

            if (state.is(ModBlocks.STEAM_INLET.get())
                    && isAssembled(state, SteamInletBlock.ASSEMBLED)
                    && ++inlets <= 1) {
                continue;
            }

            return false;
        }
        return strokeDirection == Direction.UP || inlets == 1;
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
            BlockPos pistonHead,
            BlockPos piston,
            BlockPos emptyStroke,
            BlockPos shaft
    ) {
    }

    public record Result(
            boolean valid,
            String message,
            BlockPos ringOrigin,
            BlockPos cylinderRoot,
            BlockPos boilerPos,
            BlockPos inletPos,
            Direction strokeDirection,
            BlockPos pistonHead,
            BlockPos piston,
            BlockPos emptyStroke,
            BlockPos shaft
    ) {
        public static Result invalid(String message) {
            return new Result(false, message, null, null, null, null, Direction.UP, null, null, null, null);
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

    private EngineValidator() {
    }
}
