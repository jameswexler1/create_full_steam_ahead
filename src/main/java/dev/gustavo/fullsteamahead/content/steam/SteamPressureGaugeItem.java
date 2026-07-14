package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.compat.create.FullSteamBoilerIntegration;
import dev.gustavo.fullsteamahead.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** A placeable gauge that captures a boiler source before placement. */
public class SteamPressureGaugeItem extends BlockItem {
    public SteamPressureGaugeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return super.useOn(context);
        }

        Level level = context.getLevel();
        var controller = SteamPressureGaugeBlockEntity.resolveTankController(level, context.getClickedPos());
        if (controller == null) {
            return super.useOn(context);
        }

        ItemStack stack = context.getItemInHand();
        if (!FullSteamBoilerIntegration.isBoilerSteamSource(controller)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.full_steam_ahead.steam_gauge_not_boiler"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            stack.set(
                    ModDataComponents.STEAM_GAUGE_SOURCE.get(),
                    GlobalPos.of(level.dimension(), context.getClickedPos().immutable())
            );
            player.displayClientMessage(
                    Component.translatable("message.full_steam_ahead.steam_gauge_selected"), true);
        }
        level.playSound(
                player,
                context.getClickedPos(),
                SoundEvents.ITEM_FRAME_ADD_ITEM,
                SoundSource.PLAYERS,
                0.65F,
                1.1F
        );
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(ModDataComponents.STEAM_GAUGE_SOURCE.get())) {
            if (!level.isClientSide) {
                stack.remove(ModDataComponents.STEAM_GAUGE_SOURCE.get());
                player.displayClientMessage(
                        Component.translatable("message.full_steam_ahead.steam_gauge_unlinked"), true);
            }
            level.playSound(
                    player,
                    player.blockPosition(),
                    SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.PLAYERS,
                    0.65F,
                    1.1F
            );
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.STEAM_GAUGE_SOURCE.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        GlobalPos source = stack.get(ModDataComponents.STEAM_GAUGE_SOURCE.get());
        if (source == null) {
            tooltip.add(Component.translatable("tooltip.full_steam_ahead.steam_gauge_link_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.translatable("tooltip.full_steam_ahead.steam_gauge_linked")
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable(
                        "tooltip.full_steam_ahead.steam_gauge_source",
                        source.pos().getX(),
                        source.pos().getY(),
                        source.pos().getZ()
                )
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.full_steam_ahead.steam_gauge_unlink_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
