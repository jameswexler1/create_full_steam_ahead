package dev.gustavo.fullsteamahead.content.redstone;

import dev.gustavo.fullsteamahead.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Block item for the Engine Order Telegraph; surfaces its link status and the pairing hint. */
public class SteppedLeverItem extends BlockItem {
    public SteppedLeverItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (stack.has(ModDataComponents.TELEGRAPH_LINK.get())) {
            tooltip.add(Component.translatable("tooltip.full_steam_ahead.telegraph_linked")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.full_steam_ahead.telegraph_link_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
