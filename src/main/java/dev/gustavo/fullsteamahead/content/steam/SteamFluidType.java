package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Consumer;

public class SteamFluidType extends FluidType {
    private static final ResourceLocation STILL_TEXTURE = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOWING_TEXTURE = ResourceLocation.withDefaultNamespace("block/water_flow");
    private static final int STEAM_TINT = 0xEED8E7EE;

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

            @Override
            public ResourceLocation getStillTexture(FluidStack stack) {
                return STILL_TEXTURE;
            }

            @Override
            public ResourceLocation getFlowingTexture(FluidStack stack) {
                return FLOWING_TEXTURE;
            }

            @Override
            public int getTintColor(FluidStack stack) {
                return STEAM_TINT;
            }

            @Override
            public ResourceLocation getStillTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                return STILL_TEXTURE;
            }

            @Override
            public ResourceLocation getFlowingTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                return FLOWING_TEXTURE;
            }

            @Override
            public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                return STEAM_TINT;
            }
        });
    }
}
