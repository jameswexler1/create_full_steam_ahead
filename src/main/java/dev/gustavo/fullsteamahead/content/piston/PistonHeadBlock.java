package dev.gustavo.fullsteamahead.content.piston;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PistonHeadBlock extends Block implements IBE<PistonHeadBlockEntity> {
    public static final MapCodec<PistonHeadBlock> CODEC = simpleCodec(PistonHeadBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(2, 3, 2, 14, 5, 14),
            Block.box(4, 5, 4, 12, 7, 12),
            Block.box(5, 7, 5, 11, 16, 11)
    );

    public PistonHeadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ASSEMBLED, false));
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
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            withBlockEntityDo(level, pos, PistonHeadBlockEntity::revalidateStructure);
            PistonHeadBlockEntity.revalidateNearbyEngines(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                withBlockEntityDo(level, pos, PistonHeadBlockEntity::clearAssembly);
            }
            IBE.onRemove(state, level, pos, newState);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED);
    }

    @Override
    public Class<PistonHeadBlockEntity> getBlockEntityClass() {
        return PistonHeadBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PistonHeadBlockEntity> getBlockEntityType() {
        return ModBlockEntities.PISTON_HEAD.get();
    }
}
