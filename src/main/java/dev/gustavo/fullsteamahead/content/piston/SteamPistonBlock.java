package dev.gustavo.fullsteamahead.content.piston;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SteamPistonBlock extends Block {
    public static final MapCodec<SteamPistonBlock> CODEC = simpleCodec(SteamPistonBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final EnumProperty<PistonSection> PISTON_SECTION =
            EnumProperty.create("piston_section", PistonSection.class);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5, 0, 5, 11, 12, 11),
            Block.box(5, 12, 5, 11, 16, 7),
            Block.box(5, 12, 9, 11, 16, 11),
            Block.box(7, 13, 3, 9, 15, 5),
            Block.box(7, 13, 11, 9, 15, 13)
    );

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
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            PistonHeadBlockEntity.revalidateNearbyEngines(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            PistonHeadBlockEntity.invalidateNearbyEngines(level, pos, "Piston column changed", pos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED, PISTON_SECTION);
    }
}
