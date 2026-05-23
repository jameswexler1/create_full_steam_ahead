package dev.gustavo.fullsteamahead.content.cylinder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.crankshaft.CrankshaftBlockEntity;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CylinderConnectivity {
    private static final Comparator<BlockPos> ROOT_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(pos -> pos.getX())
            .thenComparingInt(pos -> pos.getZ());

    public static void refreshFrom(Level level, BlockPos changedPos) {
        refresh(level, changedPos, null);
    }

    public static void refreshFromRemoval(Level level, BlockPos removedPos) {
        refresh(level, removedPos, removedPos);
    }

    private static void refresh(Level level, BlockPos changedPos, BlockPos ignoredRingMemberPos) {
        if (level.isClientSide()) {
            return;
        }

        Set<BlockPos> candidates = candidateOrigins(changedPos);
        Set<BlockPos> validOrigins = new LinkedHashSet<>();
        Set<BlockPos> validRingPositions = new LinkedHashSet<>();

        for (BlockPos origin : candidates) {
            if (isValidRing(level, origin, ignoredRingMemberPos)) {
                validOrigins.add(origin);
                validRingPositions.addAll(ringPositions(origin));
            }
        }

        for (BlockPos origin : candidates) {
            if (!validOrigins.contains(origin)) {
                disassemble(level, ringPositions(origin), validRingPositions);
            }
        }

        for (BlockPos origin : validOrigins) {
            assemble(level, origin);
        }

        notifyCrankshafts(level, candidates);
    }

    private static Set<BlockPos> candidateOrigins(BlockPos pos) {
        Set<BlockPos> origins = new LinkedHashSet<>();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = 0; dx <= 2; dx++) {
                for (int dz = 0; dz <= 2; dz++) {
                    origins.add(pos.offset(-dx, -dy, -dz));
                }
            }
        }
        return origins;
    }

    private static boolean isValidRing(Level level, BlockPos origin, BlockPos ignoredRingMemberPos) {
        int inlets = 0;

        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.isLoaded(pos)) {
                        return false;
                    }

                    if (isCenter(x, z)) {
                        BlockState centerState = level.getBlockState(pos);
                        if (!centerState.isAir()
                                && !centerState.is(ModBlocks.PISTON.get())
                                && !centerState.is(ModBlocks.PISTON_HEAD.get())) {
                            return false;
                        }
                        continue;
                    }

                    RingMember member = getRingMemberAt(level, pos, ignoredRingMemberPos);
                    if (member == RingMember.NONE) {
                        return false;
                    }
                    if (member == RingMember.INLET && ++inlets > 1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static RingMember getRingMemberAt(Level level, BlockPos pos, BlockPos ignoredRingMemberPos) {
        if (pos.equals(ignoredRingMemberPos)) {
            return RingMember.NONE;
        }

        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.STEAM_CYLINDER.get())) {
            return RingMember.CYLINDER;
        }
        if (state.is(ModBlocks.STEAM_INLET.get())) {
            return RingMember.INLET;
        }
        return RingMember.NONE;
    }

    private static boolean isCenter(int x, int z) {
        return x == 1 && z == 1;
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

    private static void disassemble(Level level, List<BlockPos> positions, Set<BlockPos> protectedPositions) {
        for (BlockPos pos : positions) {
            if (protectedPositions.contains(pos) || !level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!isRingMember(state)) {
                continue;
            }

            setAssembled(level, pos, state, false);
            withCylinderBlockEntity(level, pos, SteamCylinderBlockEntity::clearRingState);
            withInletBlockEntity(level, pos, SteamInletBlockEntity::clearRingState);
        }
    }

    private static void assemble(Level level, BlockPos origin) {
        List<BlockPos> positions = ringPositions(origin);
        BlockPos root = positions.stream()
                .filter(pos -> level.getBlockState(pos).is(ModBlocks.STEAM_CYLINDER.get()))
                .min(ROOT_ORDER)
                .orElse(origin);
        BlockPos boilerPos = findBoiler(level, origin).orElse(null);
        BlockPos inletPos = findInlet(level, positions).orElse(null);

        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            setAssembled(level, pos, state, true);
            withCylinderBlockEntity(level, pos,
                    be -> be.applyRingState(origin, root, boilerPos, inletPos, pos.equals(root)));
            withInletBlockEntity(level, pos,
                    be -> be.applyRingState(origin, root, boilerPos));
        }
    }

    private static Optional<BlockPos> findInlet(Level level, List<BlockPos> positions) {
        return positions.stream()
                .filter(pos -> level.getBlockState(pos).is(ModBlocks.STEAM_INLET.get()))
                .findFirst();
    }

    private static Optional<BlockPos> findBoiler(Level level, BlockPos origin) {
        BlockPos linkedBoiler = null;

        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                if (isCenter(x, z)) {
                    continue;
                }

                BlockPos tankPos = origin.offset(x, -1, z);
                if (!level.isLoaded(tankPos)) {
                    return Optional.empty();
                }

                BlockEntity blockEntity = level.getBlockEntity(tankPos);
                if (!(blockEntity instanceof FluidTankBlockEntity tank)) {
                    return Optional.empty();
                }

                if (linkedBoiler == null) {
                    FluidTankBlockEntity controller = tank.getControllerBE();
                    linkedBoiler = controller == null ? tankPos : controller.getBlockPos();
                }
            }
        }

        return Optional.ofNullable(linkedBoiler);
    }

    private static void setAssembled(Level level, BlockPos pos, BlockState state, boolean assembled) {
        if (state.is(ModBlocks.STEAM_CYLINDER.get())) {
            setAssembled(level, pos, state, SteamCylinderBlock.ASSEMBLED, assembled);
        } else if (state.is(ModBlocks.STEAM_INLET.get())) {
            setAssembled(level, pos, state, SteamInletBlock.ASSEMBLED, assembled);
        }
    }

    private static void setAssembled(
            Level level,
            BlockPos pos,
            BlockState state,
            net.minecraft.world.level.block.state.properties.BooleanProperty property,
            boolean assembled
    ) {
        if (!state.hasProperty(property) || state.getValue(property) == assembled) {
            return;
        }

        level.setBlock(pos, state.setValue(property, assembled), Block.UPDATE_CLIENTS);
    }

    private static boolean isRingMember(BlockState state) {
        return state.is(ModBlocks.STEAM_CYLINDER.get()) || state.is(ModBlocks.STEAM_INLET.get());
    }

    private static void notifyCrankshafts(Level level, Set<BlockPos> candidateOrigins) {
        Set<BlockPos> notified = new LinkedHashSet<>();
        for (BlockPos origin : candidateOrigins) {
            BlockPos crankshaftPos = origin.offset(1, 3, 1);
            if (notified.add(crankshaftPos)) {
                CrankshaftBlockEntity.revalidateAt(level, crankshaftPos);
            }
        }
    }

    private static void withCylinderBlockEntity(
            Level level,
            BlockPos pos,
            java.util.function.Consumer<SteamCylinderBlockEntity> action
    ) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SteamCylinderBlockEntity cylinder) {
            action.accept(cylinder);
        }
    }

    private static void withInletBlockEntity(
            Level level,
            BlockPos pos,
            java.util.function.Consumer<SteamInletBlockEntity> action
    ) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SteamInletBlockEntity inlet) {
            action.accept(inlet);
        }
    }

    private enum RingMember {
        NONE,
        CYLINDER,
        INLET
    }

    private CylinderConnectivity() {
    }
}
