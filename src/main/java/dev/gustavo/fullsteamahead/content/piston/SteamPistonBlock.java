package dev.gustavo.fullsteamahead.content.piston;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class SteamPistonBlock extends Block {
    public static final MapCodec<SteamPistonBlock> CODEC = simpleCodec(SteamPistonBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final EnumProperty<PistonSection> PISTON_SECTION =
            EnumProperty.create("piston_section", PistonSection.class);

    public SteamPistonBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ASSEMBLED, false)
                .setValue(PISTON_SECTION, PistonSection.INSIDE_LOW));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED, PISTON_SECTION);
    }
}
