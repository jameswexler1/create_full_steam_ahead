package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlockEntity;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.content.redstone.SteppedLeverBlockEntity;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;
import dev.gustavo.fullsteamahead.content.steam.BoilerOutletBlockEntity;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PistonHeadBlockEntity>> PISTON_HEAD =
            BLOCK_ENTITY_TYPES.register("piston_head",
                    () -> BlockEntityType.Builder.of(
                            PistonHeadBlockEntity::new,
                            ModBlocks.PISTON_HEAD.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FullSteamPoweredShaftBlockEntity>> POWERED_SHAFT =
            BLOCK_ENTITY_TYPES.register("powered_shaft",
                    () -> BlockEntityType.Builder.of(
                            FullSteamPoweredShaftBlockEntity::new,
                            ModBlocks.POWERED_SHAFT.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerOutletBlockEntity>> BOILER_OUTLET =
            BLOCK_ENTITY_TYPES.register("boiler_outlet",
                    () -> BlockEntityType.Builder.of(
                            BoilerOutletBlockEntity::new,
                            ModBlocks.BOILER_OUTLET.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamInletBlockEntity>> STEAM_INLET =
            BLOCK_ENTITY_TYPES.register("steam_inlet",
                    () -> BlockEntityType.Builder.of(
                            SteamInletBlockEntity::new,
                            ModBlocks.STEAM_INLET.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteppedLeverBlockEntity>> STEPPED_LEVER =
            BLOCK_ENTITY_TYPES.register("stepped_lever",
                    () -> BlockEntityType.Builder.of(
                            SteppedLeverBlockEntity::new,
                            ModBlocks.STEPPED_LEVER.get()
                    ).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }

    private ModBlockEntities() {
    }
}
