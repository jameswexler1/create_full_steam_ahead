package dev.gustavo.fullsteamahead.content.redstone;

import dev.gustavo.fullsteamahead.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Block item for the Engine Order Telegraph: glows when tuned and clears its channel on sneak-use. */
public class SteppedLeverItem extends BlockItem {
    public SteppedLeverItem(Block block, Properties properties) {
        super(block, properties);
    }

    private static boolean isTuned(ItemStack stack) {
        return stack.has(ModDataComponents.TELEGRAPH_LINK.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Tuned telegraphs shimmer like enchanted items, matching Create's stock links.
        return isTuned(stack) || super.isFoil(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Sneak + use in the air clears the captured channel so the item places unlinked again.
        if (player.isShiftKeyDown() && isTuned(stack)) {
            if (!level.isClientSide) {
                stack.remove(ModDataComponents.TELEGRAPH_LINK.get());
                player.displayClientMessage(
                        Component.translatable("message.full_steam_ahead.telegraph_unlinked"), true);
            }
            level.playSound(player, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.PLAYERS, 0.7F, 1.1F);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (isTuned(stack)) {
            tooltip.add(Component.translatable("tooltip.full_steam_ahead.telegraph_linked")
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("tooltip.full_steam_ahead.telegraph_unlink_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.full_steam_ahead.telegraph_link_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
