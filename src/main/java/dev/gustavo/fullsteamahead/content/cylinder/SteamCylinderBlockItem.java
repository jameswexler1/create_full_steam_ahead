package dev.gustavo.fullsteamahead.content.cylinder;

import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class SteamCylinderBlockItem extends BlockItem {
    private static final int[][] SHELL_OFFSETS = {
            {0, 0}, {1, 0}, {2, 0},
            {0, 1},         {2, 1},
            {0, 2}, {1, 2}, {2, 2}
    };

    public SteamCylinderBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (!result.consumesAction()) {
            return result;
        }

        tryCompleteLayer(context);
        return result;
    }

    private void tryCompleteLayer(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        if (SymmetryWandItem.presentInHotbar(player)) {
            return;
        }

        Direction clickedFace = context.getClickedFace();
        if (!clickedFace.getAxis().isVertical()) {
            return;
        }

        Level level = context.getLevel();
        BlockPos placedPos = context.getClickedPos();
        List<LayerCandidate> candidates = new ArrayList<>();
        for (int[] offset : SHELL_OFFSETS) {
            BlockPos targetOrigin = placedPos.offset(-offset[0], 0, -offset[1]);
            LayerCandidate candidate = evaluateCandidate(level, targetOrigin, clickedFace);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        if (candidates.size() != 1) {
            return;
        }

        LayerCandidate candidate = candidates.getFirst();
        int missing = candidate.missingPositions().size();
        if (missing == 0) {
            return;
        }

        ItemStack stack = context.getItemInHand();
        if (!player.isCreative() && stack.getCount() < missing) {
            return;
        }

        boolean placedAny = false;
        for (BlockPos pos : candidate.missingPositions()) {
            InteractionResult result = super.place(BlockPlaceContext.at(context, pos, clickedFace));
            if (!result.consumesAction()) {
                break;
            }
            placedAny = true;
        }

        if (placedAny) {
            CylinderConnectivity.refreshFrom(level, placedPos);
        }
    }

    private LayerCandidate evaluateCandidate(Level level, BlockPos targetOrigin, Direction clickedFace) {
        BlockPos referenceOrigin = targetOrigin.relative(clickedFace.getOpposite());
        int inletCount = 0;

        for (int[] offset : SHELL_OFFSETS) {
            BlockPos pos = referenceOrigin.offset(offset[0], 0, offset[1]);
            BlockState state = level.getBlockState(pos);
            if (!isStandaloneShellBlock(level, pos, state)) {
                return null;
            }
            if (state.is(ModBlocks.STEAM_INLET.get())) {
                inletCount++;
            }
        }

        List<BlockPos> missingPositions = new ArrayList<>();
        for (int[] offset : SHELL_OFFSETS) {
            BlockPos pos = targetOrigin.offset(offset[0], 0, offset[1]);
            BlockState state = level.getBlockState(pos);
            if (isStandaloneShellBlock(level, pos, state)) {
                if (state.is(ModBlocks.STEAM_INLET.get())) {
                    inletCount++;
                }
                continue;
            }
            if (!state.canBeReplaced()) {
                return null;
            }
            missingPositions.add(pos);
        }

        if (inletCount > 1) {
            return null;
        }
        return new LayerCandidate(missingPositions);
    }

    private boolean isStandaloneShellBlock(Level level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.STEAM_INLET.get())) {
            return true;
        }
        if (!state.is(ModBlocks.STEAM_CYLINDER.get())) {
            return false;
        }
        if (state.getValue(SteamCylinderBlock.SHARED_WALL) != CylinderSharedWall.NONE) {
            return false;
        }
        CylinderWallShape shape = state.getValue(SteamCylinderBlock.WALL_SHAPE);
        if (shape == CylinderWallShape.SHARED_STRIP_X || shape == CylinderWallShape.SHARED_STRIP_Z) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        return !(blockEntity instanceof SteamCylinderBlockEntity cylinder && cylinder.getRingOrigins().size() > 1);
    }

    private record LayerCandidate(List<BlockPos> missingPositions) {
    }
}
