package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.content.crankshaft.CrankshaftBlock;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlock;
import dev.gustavo.fullsteamahead.content.flywheel.FlywheelBlock;
import dev.gustavo.fullsteamahead.content.governor.GovernorBlock;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlock;
import dev.gustavo.fullsteamahead.content.piston.SteamPistonBlock;
import dev.gustavo.fullsteamahead.content.steam.BoilerOutletBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlock;
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
            registerBlock("steam_cylinder", SteamCylinderBlock::new, cylinderProperties());
    public static final DeferredBlock<SteamPistonBlock> PISTON =
            registerBlock("piston", SteamPistonBlock::new, metalProperties());
    public static final DeferredBlock<PistonHeadBlock> PISTON_HEAD =
            registerBlock("piston_head", PistonHeadBlock::new, metalProperties());
    public static final DeferredBlock<CrankshaftBlock> CRANKSHAFT =
            registerBlock("crankshaft", CrankshaftBlock::new, metalProperties());
    public static final DeferredBlock<FlywheelBlock> FLYWHEEL =
            registerBlock("flywheel", FlywheelBlock::new, metalProperties());
    public static final DeferredBlock<GovernorBlock> GOVERNOR =
            registerBlock("governor", GovernorBlock::new, copperProperties());
    public static final DeferredBlock<BoilerOutletBlock> BOILER_OUTLET =
            registerBlock("boiler_outlet", BoilerOutletBlock::new, copperProperties());
    public static final DeferredBlock<SteamInletBlock> STEAM_INLET =
            registerBlock("steam_inlet", SteamInletBlock::new, copperProperties());

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name,
            Function<BlockBehaviour.Properties, T> factory,
            BlockBehaviour.Properties properties
    ) {
        DeferredBlock<T> block = BLOCKS.registerBlock(name, factory, properties);
        ModItems.ITEMS.registerSimpleBlockItem(name, block);
        return block;
    }

    private static BlockBehaviour.Properties cylinderProperties() {
        return BlockBehaviour.Properties.of()
                .strength(4.0F, 8.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.COPPER);
    }

    private static BlockBehaviour.Properties copperProperties() {
        return BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.COPPER);
    }

    private static BlockBehaviour.Properties metalProperties() {
        return BlockBehaviour.Properties.of()
                .strength(3.5F, 7.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private ModBlocks() {
    }
}
