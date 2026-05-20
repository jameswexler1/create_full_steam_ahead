package dev.gustavo.fullsteamahead.content.cylinder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
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

    private static void refresh(Level level, BlockPos changedPos, BlockPos ignoredCylinderPos) {
        if (level.isClientSide()) {
            return;
        }

        Set<BlockPos> candidates = candidateOrigins(changedPos);
        Set<BlockPos> validOrigins = new LinkedHashSet<>();
        Set<BlockPos> validRingPositions = new LinkedHashSet<>();

        for (BlockPos origin : candidates) {
            if (isValidRing(level, origin, ignoredCylinderPos)) {
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

    private static boolean isValidRing(Level level, BlockPos origin, BlockPos ignoredCylinderPos) {
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.isLoaded(pos)) {
                        return false;
                    }

                    if (isCenter(x, z)) {
                        BlockState centerState = level.getBlockState(pos);
                        if (!centerState.isAir() && !centerState.is(ModBlocks.PISTON.get())) {
                            return false;
                        }
                        continue;
                    }

                    if (!isCylinderAt(level, pos, ignoredCylinderPos)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isCylinderAt(Level level, BlockPos pos, BlockPos ignoredCylinderPos) {
        if (pos.equals(ignoredCylinderPos)) {
            return false;
        }
        return level.getBlockState(pos).is(ModBlocks.STEAM_CYLINDER.get());
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
            if (!state.is(ModBlocks.STEAM_CYLINDER.get())) {
                continue;
            }

            setAssembled(level, pos, state, false);
            withCylinderBlockEntity(level, pos, SteamCylinderBlockEntity::clearRingState);
        }
    }

    private static void assemble(Level level, BlockPos origin) {
        List<BlockPos> positions = ringPositions(origin);
        BlockPos root = positions.stream()
                .min(ROOT_ORDER)
                .orElse(origin);
        BlockPos boilerPos = findBoiler(level, origin).orElse(null);

        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            setAssembled(level, pos, state, true);
            withCylinderBlockEntity(level, pos,
                    be -> be.applyRingState(origin, root, boilerPos, pos.equals(root)));
        }
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
        if (!state.hasProperty(SteamCylinderBlock.ASSEMBLED)
                || state.getValue(SteamCylinderBlock.ASSEMBLED) == assembled) {
            return;
        }

        level.setBlock(pos, state.setValue(SteamCylinderBlock.ASSEMBLED, assembled), Block.UPDATE_ALL);
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

    private CylinderConnectivity() {
    }
}
