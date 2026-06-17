package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, FullSteamAhead.MOD_ID);

    /**
     * Engine-order-telegraph annunciator bell. Backed by
     * {@code assets/full_steam_ahead/sounds/telegraph/telegraph_bell.ogg} (mapped in sounds.json).
     * Supply that .ogg before release; until then set
     * {@link dev.gustavo.fullsteamahead.content.redstone.SteppedLeverBlockEntity#USE_CONFIRM_FALLBACK}
     * to play Create's confirm "ding" instead.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH_BELL =
            SOUND_EVENTS.register("telegraph.bell", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FullSteamAhead.MOD_ID, "telegraph.bell")));

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    private ModSoundEvents() {
    }
}
