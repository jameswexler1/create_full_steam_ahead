package dev.gustavo.fullsteamahead.client.ponder;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.minecraft.resources.ResourceLocation;

public class FullSteamPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return FullSteamAhead.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // Pipe-fed steam engine scene on the cylinder wall, using the testing_ponder_v2 structure.
        helper.addStoryBoard(ModBlocks.STEAM_CYLINDER.getId(), "testing_ponder_v2", FullSteamPonderScenes::cylinder);
    }
}
