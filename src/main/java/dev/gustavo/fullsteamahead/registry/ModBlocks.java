package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.content.engine.block.AxialEnginePartBlock;
import dev.gustavo.fullsteamahead.content.engine.block.EnginePartBlock;
import dev.gustavo.fullsteamahead.content.engine.block.HorizontalEnginePartBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FullSteamAhead.MOD_ID);

    public static final DeferredBlock<HorizontalEnginePartBlock> LARGE_STEAM_ENGINE_CONTROLLER =
            registerBlock("large_steam_engine_controller", HorizontalEnginePartBlock::new, copperProperties());
    public static final DeferredBlock<EnginePartBlock> LARGE_ENGINE_CASING =
            registerBlock("large_engine_casing", EnginePartBlock::new, casingProperties());
    public static final DeferredBlock<AxialEnginePartBlock> BOILER_DRUM =
            registerBlock("boiler_drum", AxialEnginePartBlock::new, copperProperties());
    public static final DeferredBlock<HorizontalEnginePartBlock> FIREBOX =
            registerBlock("firebox", HorizontalEnginePartBlock::new, metalProperties());
    public static final DeferredBlock<HorizontalEnginePartBlock> STEAM_CYLINDER =
            registerBlock("steam_cylinder", HorizontalEnginePartBlock::new, copperProperties());
    public static final DeferredBlock<AxialEnginePartBlock> PISTON_ROD =
            registerBlock("piston_rod", AxialEnginePartBlock::new, metalProperties());
    public static final DeferredBlock<AxialEnginePartBlock> FLYWHEEL =
            registerBlock("flywheel", AxialEnginePartBlock::new, metalProperties());
    public static final DeferredBlock<AxialEnginePartBlock> OUTPUT_COUPLING =
            registerBlock("output_coupling", AxialEnginePartBlock::new, metalProperties());
    public static final DeferredBlock<HorizontalEnginePartBlock> GOVERNOR =
            registerBlock("governor", HorizontalEnginePartBlock::new, copperProperties());

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name,
            Function<BlockBehaviour.Properties, T> factory,
            BlockBehaviour.Properties properties
    ) {
        DeferredBlock<T> block = BLOCKS.registerBlock(name, factory, properties);
        ModItems.ITEMS.registerSimpleBlockItem(name, block);
        return block;
    }

    private static BlockBehaviour.Properties casingProperties() {
        return BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties copperProperties() {
        return BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.COPPER);
    }

    private static BlockBehaviour.Properties metalProperties() {
        return BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private ModBlocks() {
    }
}
