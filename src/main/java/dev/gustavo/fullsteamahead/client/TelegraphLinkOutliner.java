package dev.gustavo.fullsteamahead.client;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.content.redstone.TelegraphLinks;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import dev.gustavo.fullsteamahead.registry.ModDataComponents;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.UUID;

/**
 * Highlights every telegraph on a channel while the player holds a telegraph item tuned to that
 * channel — mirroring Create's stock-link outline (catnip {@link Outliner}): the same pulsing blue
 * and per-shape boxes that hug the block's actual hitbox.
 */
@EventBusSubscriber(modid = FullSteamAhead.MOD_ID, value = Dist.CLIENT)
public final class TelegraphLinkOutliner {
    // Create's logistics-link outline pulses between these two blues.
    private static final int COLOR_A = 0x708DAD;
    private static final int COLOR_B = 0x90ADCD;
    private static final int OUTLINE_TTL_TICKS = 5;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }

        UUID linkId = tunedChannel(player.getMainHandItem());
        if (linkId == null) {
            linkId = tunedChannel(player.getOffhandItem());
        }
        if (linkId == null) {
            return;
        }

        float pulse = (Mth.sin(AnimationTickHolder.getTicks() * 0.15F) + 1.0F) * 0.5F;
        int color = mix(COLOR_A, COLOR_B, pulse);

        for (BlockPos pos : TelegraphLinks.loadedPositions(level, linkId)) {
            BlockState state = level.getBlockState(pos);
            List<AABB> boxes = state.getShape(level, pos).toAabbs();
            for (int i = 0; i < boxes.size(); i++) {
                AABB box = boxes.get(i).inflate(-0.0078125D).move(pos.getX(), pos.getY(), pos.getZ());
                Outliner.getInstance().showAABB(Pair.of(pos, i), box, OUTLINE_TTL_TICKS)
                        .lineWidth(1 / 32.0F)
                        .disableLineNormals()
                        .colored(color);
            }
        }
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static UUID tunedChannel(ItemStack stack) {
        if (stack.is(ModBlocks.STEPPED_LEVER.get().asItem())) {
            return stack.get(ModDataComponents.TELEGRAPH_LINK.get());
        }
        return null;
    }

    private TelegraphLinkOutliner() {
    }
}
