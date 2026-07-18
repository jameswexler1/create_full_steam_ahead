package dev.gustavo.fullsteamahead.content.piston;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderConnectivity;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlock;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlockEntity;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlock;
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
import java.util.Optional;

public final class EngineValidator {
    public static final int MIN_PISTON_BODIES = 1;
    public static final int MAX_PISTON_BODIES = 3;
    public static final int MIN_SHAFT_GAP = 1;
    public static final int MAX_SHAFT_GAP = 3;

    private static final Comparator<BlockPos> ROOT_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(pos -> pos.getX())
            .thenComparingInt(pos -> pos.getZ());

    public static Result validate(Level level, BlockPos pistonHeadPos) {
        if (!level.isLoaded(pistonHeadPos)) {
            return Result.pending("Piston head is loading");
        }
        if (!isPistonHead(level, pistonHeadPos)) {
            return Result.invalid("Missing piston head");
        }

        Direction preferredDirection = pistonHeadFacing(level.getBlockState(pistonHeadPos));
        Result preferred = validate(level, pistonHeadPos, preferredDirection);
        if (preferred.valid()) {
            return preferred;
        }

        Result opposite = validate(level, pistonHeadPos, preferredDirection.getOpposite());
        if (opposite.valid()) {
            return opposite;
        }
        if (preferred.pending()) {
            return preferred;
        }
        return opposite.pending() ? opposite : preferred;
    }

    private static Result validate(Level level, BlockPos pistonHeadPos, Direction strokeDirection) {
        for (int offset = 1; offset <= MAX_PISTON_BODIES + 1; offset++) {
            if (!level.isLoaded(pistonHeadPos.relative(strokeDirection, offset))) {
                return Result.pending("Piston column is loading");
            }
        }

        PistonPositions pistons = pistonPositions(level, pistonHeadPos, strokeDirection);
        if (pistons.pistonBodyCount() < MIN_PISTON_BODIES) {
            return Result.invalid("Missing piston body");
        }
        if (pistons.pistonBodyCount() > MAX_PISTON_BODIES) {
            return Result.invalid("Too many piston bodies");
        }
        for (BlockPos strokeSpace : pistons.emptyStrokeSpaces()) {
            if (!level.isLoaded(strokeSpace)) {
                return Result.pending("Stroke space is loading");
            }
            if (!isStrokeSpaceAvailable(level, strokeSpace, strokeDirection)) {
                return Result.invalid("Stroke space must be empty");
            }
        }
        if (!level.isLoaded(pistons.shaft())) {
            return Result.pending("Shaft is loading");
        }
        if (!isValidShaft(level, pistons.shaft())) {
            return Result.invalid("Missing horizontal Create shaft");
        }

        BlockPos ringOrigin = ringOriginFor(pistonHeadPos, strokeDirection);
        List<BlockPos> ringPositions = ringPositions(ringOrigin);
        List<BlockPos> cylinderPositions = new ArrayList<>(16);
        List<BlockPos> inletPositions = new ArrayList<>(CylinderConnectivity.MAX_INLETS_PER_RING);
        for (BlockPos pos : ringPositions) {
            if (!level.isLoaded(pos)) {
                return Result.pending("Cylinder ring is loading");
            }

            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.STEAM_CYLINDER.get())) {
                if (!isAssembled(state, SteamCylinderBlock.ASSEMBLED)) {
                    return Result.invalid("Cylinder ring is not assembled");
                }
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (!(blockEntity instanceof SteamCylinderBlockEntity cylinder)) {
                    return Result.pending("Cylinder block entities are loading");
                }
                if (!cylinder.belongsToRingOrigin(ringOrigin)) {
                    return Result.pending("Cylinder ring ownership is synchronizing");
                }
                cylinderPositions.add(pos);
                continue;
            }

            if (state.is(ModBlocks.STEAM_INLET.get())) {
                if (inletPositions.size() >= CylinderConnectivity.MAX_INLETS_PER_RING) {
                    return Result.invalid("Too many steam inlets");
                }
                if (!isAssembled(state, SteamInletBlock.ASSEMBLED)) {
                    return Result.invalid("Steam inlet is not assembled");
                }
                inletPositions.add(pos);
                continue;
            }

            return Result.invalid("Missing steam cylinder ring");
        }

        BlockPos inletPos = CylinderConnectivity.selectActiveInlet(level, ringOrigin, ringPositions).orElse(null);
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
                false,
                "Structure assembled",
                ringOrigin,
                cylinderRoot,
                boiler.boilerPos(),
                inletPos,
                strokeDirection,
                pistons.pistonHead(),
                pistons.pistons(),
                pistons.pistonBodyCount(),
                pistons.shaftGap(),
                pistons.emptyStrokeSpaces(),
                pistons.shaft()
        );
    }

    public static PistonPositions pistonPositions(BlockPos pistonHeadPos) {
        return pistonPositions(pistonHeadPos, Direction.UP);
    }

    public static PistonPositions pistonPositions(BlockPos pistonHeadPos, Direction strokeDirection) {
        return pistonPositions(pistonHeadPos, strokeDirection, MIN_PISTON_BODIES);
    }

    public static PistonPositions pistonPositions(BlockPos pistonHeadPos, Direction strokeDirection, int pistonBodyCount) {
        return pistonPositions(
                pistonHeadPos,
                strokeDirection,
                pistonBodyCount,
                defaultShaftGapForPistonBodies(pistonBodyCount)
        );
    }

    public static PistonPositions pistonPositions(
            BlockPos pistonHeadPos,
            Direction strokeDirection,
            int pistonBodyCount,
            int shaftGap
    ) {
        int count = Math.max(0, pistonBodyCount);
        int gap = clampShaftGap(shaftGap);
        List<BlockPos> pistons = new ArrayList<>(count);
        for (int offset = 1; offset <= count; offset++) {
            pistons.add(pistonHeadPos.relative(strokeDirection, offset));
        }
        List<BlockPos> strokeSpaces = strokeSpacePositions(pistonHeadPos, strokeDirection, count, gap);
        return new PistonPositions(
                pistonHeadPos,
                List.copyOf(pistons),
                count,
                gap,
                strokeSpaces,
                pistonHeadPos.relative(strokeDirection, shaftDistanceForPistonBodies(count, gap))
        );
    }

    private static PistonPositions pistonPositions(Level level, BlockPos pistonHeadPos, Direction strokeDirection) {
        int count = scanPistonBodyCount(level, pistonHeadPos, strokeDirection);
        int gap = selectExistingShaftGap(level, pistonHeadPos, strokeDirection, count);
        return pistonPositions(pistonHeadPos, strokeDirection, count, gap);
    }

    public static int shaftDistanceForPistonBodies(int pistonBodyCount) {
        return shaftDistanceForPistonBodies(pistonBodyCount, defaultShaftGapForPistonBodies(pistonBodyCount));
    }

    public static int shaftDistanceForPistonBodies(int pistonBodyCount, int shaftGap) {
        return Math.max(0, pistonBodyCount) + clampShaftGap(shaftGap) + 1;
    }

    public static int defaultShaftGapForPistonBodies(int pistonBodyCount) {
        return Math.max(MIN_SHAFT_GAP, Math.min(MAX_SHAFT_GAP, pistonBodyCount));
    }

    public static int clampShaftGap(int shaftGap) {
        return Math.max(MIN_SHAFT_GAP, Math.min(MAX_SHAFT_GAP, shaftGap));
    }

    private static List<BlockPos> strokeSpacePositions(
            BlockPos pistonHeadPos,
            Direction strokeDirection,
            int pistonBodyCount,
            int shaftGap
    ) {
        int count = Math.max(0, pistonBodyCount);
        int gap = clampShaftGap(shaftGap);
        List<BlockPos> strokeSpaces = new ArrayList<>(gap);
        for (int offset = count + 1; offset <= count + gap; offset++) {
            strokeSpaces.add(pistonHeadPos.relative(strokeDirection, offset));
        }
        return List.copyOf(strokeSpaces);
    }

    public static PistonPositions pistonPositionsFromBody(BlockPos pistonPos) {
        return pistonPositions(pistonPos.below());
    }

    public static PistonPositions pistonPositionsFromBody(Level level, BlockPos pistonPos) {
        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            for (int bodyIndex = MIN_PISTON_BODIES; bodyIndex <= MAX_PISTON_BODIES; bodyIndex++) {
                BlockPos headPos = pistonPos.relative(direction.getOpposite(), bodyIndex);
                if (!isPistonHead(level, headPos)) {
                    continue;
                }

                PistonPositions positions = pistonPositions(level, headPos, direction);
                if (positions.pistons().contains(pistonPos)) {
                    return positions;
                }
            }
        }

        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            BlockPos headPos = pistonPos.relative(direction.getOpposite());
            if (isPistonHead(level, headPos)) {
                return pistonPositions(level, headPos, direction);
            }
        }

        Direction strokeDirection = Direction.UP;
        if (level.isLoaded(pistonPos)) {
            strokeDirection = pistonFacing(level.getBlockState(pistonPos));
        }
        return pistonPositions(pistonPos.relative(strokeDirection.getOpposite()), strokeDirection);
    }

    public static boolean isReadyForShaftPlacement(Level level, BlockPos pistonPos) {
        return shaftPlacementPositionsFromBody(level, pistonPos).isPresent();
    }

    public static Optional<PistonPositions> shaftPlacementPositionsFromBody(Level level, BlockPos pistonPos) {
        PistonPositions pistons = pistonPositionsFromBody(level, pistonPos);
        if (!isPistonHead(level, pistons.pistonHead())
                || pistons.pistonBodyCount() < MIN_PISTON_BODIES
                || pistons.pistonBodyCount() > MAX_PISTON_BODIES) {
            return Optional.empty();
        }
        for (BlockPos piston : pistons.pistons()) {
            if (!isPiston(level, piston)) {
                return Optional.empty();
            }
        }

        Direction strokeDirection = strokeDirectionFor(pistons);
        BlockPos ringOrigin = ringOriginFor(pistons.pistonHead(), strokeDirection);
        if (!isRingReady(level, ringOrigin, strokeDirection)) {
            return Optional.empty();
        }

        for (int gap : preferredShaftGaps(pistons.pistonBodyCount())) {
            PistonPositions candidate = pistonPositions(
                    pistons.pistonHead(),
                    strokeDirection,
                    pistons.pistonBodyCount(),
                    gap
            );
            if (!hasEmptyStrokeSpaces(level, candidate)) {
                continue;
            }
            if (level.isLoaded(candidate.shaft()) && level.getBlockState(candidate.shaft()).canBeReplaced()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static List<BlockPos> candidatePistonHeadsNear(BlockPos changedPos) {
        List<BlockPos> candidates = new ArrayList<>(175);
        int maxVerticalDistance = shaftDistanceForPistonBodies(MAX_PISTON_BODIES, MAX_SHAFT_GAP);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -maxVerticalDistance; dy <= maxVerticalDistance; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    candidates.add(changedPos.offset(dx, dy, dz));
                }
            }
        }
        return candidates;
    }

    public static boolean matchesShaftGeometry(Level level, BlockPos pistonHeadPos, BlockPos shaftPos) {
        return pistonPositionsForShaft(level, pistonHeadPos, shaftPos).isPresent();
    }

    public static Optional<PistonPositions> pistonPositionsForShaft(
            Level level,
            BlockPos pistonHeadPos,
            BlockPos shaftPos
    ) {
        if (pistonHeadPos == null
                || shaftPos == null
                || pistonHeadPos.getX() != shaftPos.getX()
                || pistonHeadPos.getZ() != shaftPos.getZ()
                || !level.isLoaded(pistonHeadPos)
                || !level.isLoaded(shaftPos)
                || !isPistonHead(level, pistonHeadPos)
                || !isValidShaft(level, shaftPos)) {
            return Optional.empty();
        }

        int verticalOffset = shaftPos.getY() - pistonHeadPos.getY();
        if (verticalOffset == 0) {
            return Optional.empty();
        }
        Direction strokeDirection = verticalOffset > 0 ? Direction.UP : Direction.DOWN;
        if (pistonHeadFacing(level.getBlockState(pistonHeadPos)) != strokeDirection) {
            return Optional.empty();
        }

        int shaftDistance = Math.abs(verticalOffset);
        for (int bodyCount = MIN_PISTON_BODIES; bodyCount <= MAX_PISTON_BODIES; bodyCount++) {
            int shaftGap = shaftDistance - bodyCount - 1;
            if (shaftGap < MIN_SHAFT_GAP || shaftGap > MAX_SHAFT_GAP) {
                continue;
            }

            PistonPositions candidate = pistonPositions(
                    pistonHeadPos,
                    strokeDirection,
                    bodyCount,
                    shaftGap
            );
            boolean pistonsMatch = candidate.pistons().stream().allMatch(pos -> isPiston(level, pos));
            BlockPos nextBodyPos = pistonHeadPos.relative(strokeDirection, bodyCount + 1);
            if (!pistonsMatch || isPiston(level, nextBodyPos) || !hasEmptyStrokeSpaces(level, candidate)) {
                continue;
            }

            if (candidate.shaft().equals(shaftPos)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean isPiston(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.PISTON.get());
    }

    private static boolean isPistonHead(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.PISTON_HEAD.get());
    }

    private static boolean isStrokeSpaceAvailable(Level level, BlockPos pos, Direction strokeDirection) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || EngineLinkageContinuity.isForDirection(state, strokeDirection);
    }

    private static boolean isPoweredShaft(Level level, BlockPos pos) {
        return level.isLoaded(pos) && FullSteamPoweredShaftBlock.isPoweredShaft(level.getBlockState(pos));
    }

    public static boolean isValidShaft(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!FullSteamPoweredShaftBlock.isRecognizedShaft(state)) {
            return false;
        }

        return FullSteamPoweredShaftBlock.axisOf(state).isHorizontal();
    }

    public static Direction.Axis shaftAxis(Level level, BlockPos shaftPos) {
        return shaftAxis(level.getBlockState(shaftPos));
    }

    public static Direction.Axis shaftAxis(BlockState state) {
        return FullSteamPoweredShaftBlock.axisOf(state);
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
        return pistons.shaft().getY() < pistons.pistonHead().getY() ? Direction.DOWN : Direction.UP;
    }

    private static int scanPistonBodyCount(Level level, BlockPos pistonHeadPos, Direction strokeDirection) {
        int count = 0;
        for (int offset = 1; offset <= MAX_PISTON_BODIES + 1; offset++) {
            BlockPos pistonPos = pistonHeadPos.relative(strokeDirection, offset);
            if (!isPiston(level, pistonPos)) {
                break;
            }
            count++;
        }
        return count;
    }

    private static int selectExistingShaftGap(
            Level level,
            BlockPos pistonHeadPos,
            Direction strokeDirection,
            int pistonBodyCount
    ) {
        for (int gap : preferredShaftGaps(pistonBodyCount)) {
            BlockPos shaft = pistonHeadPos.relative(
                    strokeDirection,
                    shaftDistanceForPistonBodies(pistonBodyCount, gap)
            );
            if (isPoweredShaft(level, shaft)) {
                return gap;
            }
        }
        for (int gap : preferredShaftGaps(pistonBodyCount)) {
            BlockPos shaft = pistonHeadPos.relative(
                    strokeDirection,
                    shaftDistanceForPistonBodies(pistonBodyCount, gap)
            );
            if (isValidShaft(level, shaft)) {
                return gap;
            }
        }
        return defaultShaftGapForPistonBodies(pistonBodyCount);
    }

    private static boolean hasEmptyStrokeSpaces(Level level, PistonPositions positions) {
        Direction strokeDirection = strokeDirectionFor(positions);
        for (BlockPos strokeSpace : positions.emptyStrokeSpaces()) {
            if (!isStrokeSpaceAvailable(level, strokeSpace, strokeDirection)) {
                return false;
            }
        }
        return true;
    }

    private static List<Integer> preferredShaftGaps(int pistonBodyCount) {
        int preferred = defaultShaftGapForPistonBodies(pistonBodyCount);
        List<Integer> gaps = new ArrayList<>(MAX_SHAFT_GAP);
        gaps.add(preferred);
        for (int gap = MIN_SHAFT_GAP; gap <= MAX_SHAFT_GAP; gap++) {
            if (gap != preferred) {
                gaps.add(gap);
            }
        }
        return gaps;
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
                    && ++inlets <= CylinderConnectivity.MAX_INLETS_PER_RING) {
                continue;
            }

            return false;
        }
        return strokeDirection == Direction.UP || inlets >= 1;
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
            List<BlockPos> pistons,
            int pistonBodyCount,
            int shaftGap,
            List<BlockPos> emptyStrokeSpaces,
            BlockPos shaft
    ) {
    }

    public record Result(
            boolean valid,
            boolean pending,
            String message,
            BlockPos ringOrigin,
            BlockPos cylinderRoot,
            BlockPos boilerPos,
            BlockPos inletPos,
            Direction strokeDirection,
            BlockPos pistonHead,
            List<BlockPos> pistons,
            int pistonBodyCount,
            int shaftGap,
            List<BlockPos> emptyStrokeSpaces,
            BlockPos shaft
    ) {
        public static Result invalid(String message) {
            return new Result(false, false, message, null, null, null, null,
                    Direction.UP, null, List.of(), 0, MIN_SHAFT_GAP, List.of(), null);
        }

        public static Result pending(String message) {
            return new Result(false, true, message, null, null, null, null,
                    Direction.UP, null, List.of(), 0, MIN_SHAFT_GAP, List.of(), null);
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
