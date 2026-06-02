package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlock;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlock;
import dev.gustavo.fullsteamahead.content.piston.SteamPistonBlock;
import dev.gustavo.fullsteamahead.content.redstone.SteppedLeverBlock;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlock;
import dev.gustavo.fullsteamahead.content.steam.BoilerOutletBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlock;
import dev.gustavo.fullsteamahead.content.telegraph.EngineTelegraphBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FullSteamAhead.MOD_ID);

    public static final DeferredBlock<SteamCylinderBlock> STEAM_CYLINDER =
            registerBlock("steam_cylinder", SteamCylinderBlock::new, cylinderProperties().noOcclusion());
    public static final DeferredBlock<SteamPistonBlock> PISTON =
            registerBlock("piston", SteamPistonBlock::new, metalProperties().noOcclusion());
    public static final DeferredBlock<PistonHeadBlock> PISTON_HEAD =
            registerBlock("piston_head", PistonHeadBlock::new, metalProperties().noOcclusion());
    public static final DeferredBlock<FullSteamPoweredShaftBlock> POWERED_SHAFT =
            registerBlockOnly("powered_shaft", FullSteamPoweredShaftBlock::new, metalProperties().noOcclusion());
    public static final DeferredBlock<BoilerOutletBlock> BOILER_OUTLET =
            registerBlock("boiler_outlet", BoilerOutletBlock::new, copperProperties());
    public static final DeferredBlock<SteamInletBlock> STEAM_INLET =
            registerBlock("steam_inlet", SteamInletBlock::new, copperProperties().noOcclusion());
    public static final DeferredBlock<EngineTelegraphBlock> ENGINE_TELEGRAPH =
            registerBlock("engine_telegraph", EngineTelegraphBlock::new,
                    BlockBehaviour.Properties.of()
                            .strength(3.0f, 6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
            );
    public static final DeferredBlock<SteppedLeverBlock> STEPPED_LEVER =
            registerBlock("stepped_lever", SteppedLeverBlock::new,
                    BlockBehaviour.Properties.of()
                            .strength(0.5F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
            );

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name,
            Function<BlockBehaviour.Properties, T> factory,
            BlockBehaviour.Properties properties
    ) {
        DeferredBlock<T> block = BLOCKS.registerBlock(name, factory, properties);
        ModItems.ITEMS.registerSimpleBlockItem(name, block);
        return block;
    }

    private static <T extends Block> DeferredBlock<T> registerBlockOnly(
            String name,
            Function<BlockBehaviour.Properties, T> factory,
            BlockBehaviour.Properties properties
    ) {
        return BLOCKS.registerBlock(name, factory, properties);
    }

    // Cylinder wall, inlet, outlet, and telegraph break like a copper block: hardness 3.0 and a
    // stone-or-better pickaxe to drop (minecraft:mineable/pickaxe + minecraft:needs_stone_tool).
    private static BlockBehaviour.Properties cylinderProperties() {
        return copperProperties();
    }

    private static BlockBehaviour.Properties copperProperties() {
        return BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.COPPER);
    }

    // Piston head and body break like a Create shaft (andesite-based, SharedProperties.stone):
    // hardness 1.5 and any pickaxe drops them (in mineable/pickaxe, no needs_*_tool tag).
    private static BlockBehaviour.Properties metalProperties() {
        return BlockBehaviour.Properties.of()
                .strength(1.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private ModBlocks() {
    }
}
