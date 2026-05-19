package dev.gustavo.fullsteamahead;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(FullSteamAhead.MOD_ID)
public final class FullSteamAhead {
    public static final String MOD_ID = "full_steam_ahead";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FullSteamAhead(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing Create: Full Steam Ahead");
    }
}
