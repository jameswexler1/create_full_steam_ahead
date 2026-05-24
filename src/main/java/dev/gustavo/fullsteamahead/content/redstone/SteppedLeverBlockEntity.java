package dev.gustavo.fullsteamahead.content.redstone;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SteppedLeverBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final String STATE_KEY = "State";
    private static final String CHANGE_TIMER_KEY = "ChangeTimer";
    private static final int UPDATE_DELAY_TICKS = 15;

    private int state;
    private int lastChange;
    private final LerpedFloat clientState = LerpedFloat.linear();

    public SteppedLeverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEPPED_LEVER.get(), pos, state);
    }

    public SteppedLeverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        state = compound.getInt(STATE_KEY);
        lastChange = compound.getInt(CHANGE_TIMER_KEY);
        clientState.chase(state, 0.2F, LerpedFloat.Chaser.EXP);
        super.read(compound, registries, clientPacket);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt(STATE_KEY, state);
        compound.putInt(CHANGE_TIMER_KEY, lastChange);
        super.write(compound, registries, clientPacket);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }

        if (lastChange > 0) {
            lastChange--;
            if (lastChange == 0) {
                SteppedLeverBlock.updateNeighbors(getBlockState(), level, worldPosition);
            }
        }

        if (level.isClientSide) {
            clientState.tickChaser();
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void changeState(boolean decrease) {
        int previousState = state;
        state = Mth.clamp(state + (decrease ? -1 : 1), 0, 15);
        if (previousState != state) {
            lastChange = UPDATE_DELAY_TICKS;
            setChanged();
            sendData();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("tooltip.analogStrength", state).forGoggles(tooltip);
        return true;
    }

    public int getState() {
        return state;
    }

    public float getRenderedState(float partialTicks) {
        return clientState.getValue(partialTicks);
    }
}
