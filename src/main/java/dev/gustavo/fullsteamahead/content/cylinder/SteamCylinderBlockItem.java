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
        LayerScan referenceLayer = scanLayer(level, referenceOrigin, true);
        if (referenceLayer == null) {
            return null;
        }
        LayerScan targetLayer = scanLayer(level, targetOrigin, false);
        if (targetLayer == null) {
            return null;
        }
        if (referenceLayer.inletCount() + targetLayer.inletCount() > 1) {
            return null;
        }

        SharedStrip referenceSharedStrip = sharedStrip(referenceLayer);
        if (!referenceLayer.sharedOffsets().isEmpty() && referenceSharedStrip == null) {
            return null;
        }
        SharedStrip targetSharedStrip = sharedStrip(targetLayer);
        if (!targetLayer.sharedOffsets().isEmpty() && targetSharedStrip == null) {
            return null;
        }
        if (referenceSharedStrip != null && targetSharedStrip != null && !referenceSharedStrip.equals(targetSharedStrip)) {
            return null;
        }

        SharedStrip sharedStrip = referenceSharedStrip == null ? targetSharedStrip : referenceSharedStrip;
        if (sharedStrip != null && !isSharedTargetStripReady(level, targetOrigin, sharedStrip)) {
            return null;
        }

        return new LayerCandidate(targetLayer.missingPositions());
    }

    private LayerScan scanLayer(Level level, BlockPos origin, boolean requireComplete) {
        int inletCount = 0;
        List<ShellOffset> sharedOffsets = new ArrayList<>();
        List<BlockPos> missingPositions = new ArrayList<>();
        for (int[] offset : SHELL_OFFSETS) {
            BlockPos pos = origin.offset(offset[0], 0, offset[1]);
            BlockState state = level.getBlockState(pos);
            if (isShellBlock(state)) {
                if (state.is(ModBlocks.STEAM_INLET.get())) {
                    inletCount++;
                }
                if (isSharedShellBlock(level, pos, state)) {
                    sharedOffsets.add(new ShellOffset(offset[0], offset[1]));
                }
                continue;
            }
            if (requireComplete || !state.canBeReplaced()) {
                return null;
            }
            missingPositions.add(pos);
        }

        return new LayerScan(missingPositions, sharedOffsets, inletCount);
    }

    private boolean isShellBlock(BlockState state) {
        return state.is(ModBlocks.STEAM_CYLINDER.get()) || state.is(ModBlocks.STEAM_INLET.get());
    }

    private boolean isSharedShellBlock(Level level, BlockPos pos, BlockState state) {
        if (!state.is(ModBlocks.STEAM_CYLINDER.get())) {
            return false;
        }
        if (state.getValue(SteamCylinderBlock.SHARED_WALL) != CylinderSharedWall.NONE) {
            return true;
        }
        CylinderWallShape shape = state.getValue(SteamCylinderBlock.WALL_SHAPE);
        if (shape == CylinderWallShape.SHARED_STRIP_X || shape == CylinderWallShape.SHARED_STRIP_Z) {
            return true;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof SteamCylinderBlockEntity cylinder && cylinder.getRingOrigins().size() > 1;
    }

    private SharedStrip sharedStrip(LayerScan layer) {
        if (layer.sharedOffsets().isEmpty()) {
            return null;
        }

        for (int x : new int[]{0, 2}) {
            boolean matches = true;
            for (ShellOffset offset : layer.sharedOffsets()) {
                if (offset.x() != x) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return new SharedStrip(Direction.Axis.Z, x);
            }
        }

        for (int z : new int[]{0, 2}) {
            boolean matches = true;
            for (ShellOffset offset : layer.sharedOffsets()) {
                if (offset.z() != z) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return new SharedStrip(Direction.Axis.X, z);
            }
        }

        return null;
    }

    private boolean isSharedTargetStripReady(Level level, BlockPos targetOrigin, SharedStrip sharedStrip) {
        for (int offset = 0; offset <= 2; offset++) {
            BlockPos pos = sharedStrip.axis() == Direction.Axis.Z
                    ? targetOrigin.offset(sharedStrip.fixedOffset(), 0, offset)
                    : targetOrigin.offset(offset, 0, sharedStrip.fixedOffset());
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlocks.STEAM_CYLINDER.get())) {
                return false;
            }
        }
        return true;
    }

    private record LayerCandidate(List<BlockPos> missingPositions) {
    }

    private record LayerScan(List<BlockPos> missingPositions, List<ShellOffset> sharedOffsets, int inletCount) {
    }

    private record ShellOffset(int x, int z) {
    }

    private record SharedStrip(Direction.Axis axis, int fixedOffset) {
    }
}
