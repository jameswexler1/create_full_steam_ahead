package dev.gustavo.fullsteamahead.content.piston;

import dev.gustavo.fullsteamahead.compat.simulated.SableHeatMapLoadCompat;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class EngineLinkageContinuity {
    public static void install(Level level, EngineValidator.Result result) {
        if (result.valid()) {
            install(level, result.strokeDirection(), result.emptyStrokeSpaces());
        }
    }

    public static void restoreFromPoweredShaft(Level level, BlockPos pistonHeadPos, BlockPos shaftPos) {
        EngineValidator.pistonPositionsForShaft(level, pistonHeadPos, shaftPos)
                .ifPresent(positions -> install(level, strokeDirectionFor(positions), positions.emptyStrokeSpaces()));
    }

    public static void restoreDuringInitialLoad(Level level, BlockPos pistonHeadPos, BlockPos shaftPos) {
        if (!SableHeatMapLoadCompat.isInitialSublevelLoad(level, shaftPos)) {
            return;
        }

        EngineValidator.pistonPositionsForShaft(level, pistonHeadPos, shaftPos)
                .ifPresent(positions -> installDirectlyIntoLoadingChunk(
                        level,
                        strokeDirectionFor(positions),
                        positions.emptyStrokeSpaces()
                ));
    }

    public static void clear(Level level, BlockPos pistonHeadPos, int pistonBodyCount, int shaftGap) {
        if (level == null || level.isClientSide()) {
            return;
        }

        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            EngineValidator.PistonPositions positions = EngineValidator.pistonPositions(
                    pistonHeadPos,
                    direction,
                    pistonBodyCount,
                    shaftGap
            );
            for (BlockPos strokeSpace : positions.emptyStrokeSpaces()) {
                if (!level.isLoaded(strokeSpace)) {
                    continue;
                }
                BlockState state = level.getBlockState(strokeSpace);
                if (isForDirection(state, direction)) {
                    level.setBlock(strokeSpace, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public static boolean isForDirection(BlockState state, Direction direction) {
        return state.is(ModBlocks.ENGINE_LINKAGE.get())
                && state.hasProperty(EngineLinkageBlock.FACING)
                && state.getValue(EngineLinkageBlock.FACING) == direction;
    }

    private static void install(Level level, Direction direction, Iterable<BlockPos> strokeSpaces) {
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockState linkageState = ModBlocks.ENGINE_LINKAGE.get()
                .defaultBlockState()
                .setValue(EngineLinkageBlock.FACING, direction);
        for (BlockPos strokeSpace : strokeSpaces) {
            if (!level.isLoaded(strokeSpace)) {
                continue;
            }
            BlockState current = level.getBlockState(strokeSpace);
            if (current.isAir()) {
                level.setBlock(strokeSpace, linkageState, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void installDirectlyIntoLoadingChunk(
            Level level,
            Direction direction,
            Iterable<BlockPos> strokeSpaces
    ) {
        BlockState linkageState = ModBlocks.ENGINE_LINKAGE.get()
                .defaultBlockState()
                .setValue(EngineLinkageBlock.FACING, direction);
        for (BlockPos strokeSpace : strokeSpaces) {
            if (!level.isLoaded(strokeSpace)) {
                continue;
            }

            LevelChunk chunk = level.getChunkAt(strokeSpace);
            int sectionIndex = chunk.getSectionIndex(strokeSpace.getY());
            if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(sectionIndex);
            int localX = strokeSpace.getX() & 15;
            int localY = strokeSpace.getY() & 15;
            int localZ = strokeSpace.getZ() & 15;
            if (section.getBlockState(localX, localY, localZ).isAir()) {
                // Sable scans this palette immediately after block entities load. A normal setBlock
                // here would notify its heat map once now and a second time during that scan.
                section.setBlockState(localX, localY, localZ, linkageState, false);
                chunk.setUnsaved(true);
            }
        }
    }

    private static Direction strokeDirectionFor(EngineValidator.PistonPositions positions) {
        return positions.shaft().getY() < positions.pistonHead().getY() ? Direction.DOWN : Direction.UP;
    }

    private EngineLinkageContinuity() {
    }
}
