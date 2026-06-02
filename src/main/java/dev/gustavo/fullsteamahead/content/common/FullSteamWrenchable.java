package dev.gustavo.fullsteamahead.content.common;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared Create-style wrench behaviour for Full Steam Ahead blocks.
 *
 * <p>Implementing this makes a block respond to the Create wrench: a normal wrench click
 * re-orients it via {@link #getRotatedBlockState(BlockState, Direction)} and a sneaking wrench
 * click removes it (the default {@link IWrenchable#onSneakWrenched}). Unlike Create's default
 * {@link IWrenchable#onWrenched}, this writes the rotated state with a plain {@code setBlock} so
 * it works with our non-kinetic block entities, and exposes {@link #onAfterWrench} so multiblock
 * parts can re-validate their structure afterwards.</p>
 */
public interface FullSteamWrenchable extends IWrenchable {

    @Override
    default InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState rotated = getRotatedBlockState(state, context.getClickedFace());
        if (rotated == state || !rotated.is(state.getBlock())) {
            return InteractionResult.PASS;
        }
        if (!rotated.canSurvive(level, pos)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        level.setBlock(pos, rotated, Block.UPDATE_ALL);
        IWrenchable.playRotateSound(level, pos);
        onAfterWrench(level, pos);
        return InteractionResult.SUCCESS;
    }

    /**
     * Hook fired after a successful server-side wrench re-orient. Multiblock parts override this
     * to re-validate the structure so it rebuilds or cleanly disassembles for the new orientation.
     */
    default void onAfterWrench(Level level, BlockPos pos) {
    }

    static boolean isPlacingShifted(BlockPlaceContext context) {
        Player player = context.getPlayer();
        return player != null && player.isShiftKeyDown();
    }

    /** Flips {@code facing} to its opposite when the placing player is sneaking, matching Create. */
    static Direction flipIfShifted(BlockPlaceContext context, Direction facing) {
        return isPlacingShifted(context) ? facing.getOpposite() : facing;
    }
}
