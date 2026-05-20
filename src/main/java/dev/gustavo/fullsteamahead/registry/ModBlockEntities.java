package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FullSteamAhead.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamCylinderBlockEntity>> STEAM_CYLINDER =
            BLOCK_ENTITY_TYPES.register("steam_cylinder",
                    () -> BlockEntityType.Builder.of(
                            SteamCylinderBlockEntity::new,
                            ModBlocks.STEAM_CYLINDER.get()
                    ).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }

    private ModBlockEntities() {
    }
}
