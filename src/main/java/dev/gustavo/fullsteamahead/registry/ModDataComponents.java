package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, FullSteamAhead.MOD_ID);

    /** Link id captured onto a stepped-lever item so a placed telegraph joins an existing channel. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> TELEGRAPH_LINK =
            DATA_COMPONENTS.register("telegraph_link", () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }

    private ModDataComponents() {
    }
}
