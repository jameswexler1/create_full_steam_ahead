package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FullSteamAhead.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.full_steam_ahead.main"))
                    .icon(() -> new ItemStack(ModBlocks.STEAM_CYLINDER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.STEAM_CYLINDER.get());
                        output.accept(ModBlocks.PISTON.get());
                        output.accept(ModBlocks.PISTON_HEAD.get());
                        output.accept(ModBlocks.BOILER_OUTLET.get());
                        output.accept(ModBlocks.STEAM_INLET.get());
                        output.accept(ModBlocks.ENGINE_TELEGRAPH.get());
                        output.accept(ModBlocks.STEPPED_LEVER.get());
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }

    private ModCreativeTabs() {
    }
}
