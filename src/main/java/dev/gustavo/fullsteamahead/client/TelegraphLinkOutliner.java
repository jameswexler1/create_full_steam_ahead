package dev.gustavo.fullsteamahead.client;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.content.redstone.TelegraphLinks;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import dev.gustavo.fullsteamahead.registry.ModDataComponents;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.UUID;

/**
 * Highlights every telegraph on a channel while the player holds a telegraph item tuned to that
 * channel — mirroring Create's stock-link outline (catnip {@link Outliner}).
 */
@EventBusSubscriber(modid = FullSteamAhead.MOD_ID, value = Dist.CLIENT)
public final class TelegraphLinkOutliner {
    private static final int OUTLINE_COLOR = 0xC9974C; // brass, matching the dial bezel
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

        for (BlockPos pos : TelegraphLinks.loadedPositions(level, linkId)) {
            AABB box = new AABB(pos).inflate(0.02D);
            Outliner.getInstance().showAABB(pos, box, OUTLINE_TTL_TICKS)
                    .lineWidth(1 / 16.0F)
                    .disableLineNormals()
                    .colored(OUTLINE_COLOR);
        }
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
