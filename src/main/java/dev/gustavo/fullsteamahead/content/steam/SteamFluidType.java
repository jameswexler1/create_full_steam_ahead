package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Consumer;

public class SteamFluidType extends FluidType {
    private static final ResourceLocation STILL_TEXTURE = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOWING_TEXTURE = ResourceLocation.withDefaultNamespace("block/water_flow");
    private static final int STEAM_TINT = 0x99D8E7EE;

    public SteamFluidType() {
        super(Properties.create()
                .descriptionId("fluid_type." + FullSteamAhead.MOD_ID + ".steam")
                .density(-100)
                .temperature(450)
                .viscosity(200)
                .canDrown(false)
                .canSwim(false)
                .canPushEntity(false));
    }

    @Override
    @SuppressWarnings("removal")
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return STILL_TEXTURE;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOWING_TEXTURE;
            }

            @Override
            public int getTintColor() {
                return STEAM_TINT;
            }
        });
    }
}
