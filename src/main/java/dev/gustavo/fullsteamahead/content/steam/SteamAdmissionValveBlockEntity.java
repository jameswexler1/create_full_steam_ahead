package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SteamAdmissionValveBlockEntity extends FluidPipeBlockEntity implements IHaveGoggleInformation {
    private static final String RECEIVED_SIGNAL_KEY = "ReceivedSignal";

    private LinkBehaviour link;
    private int receivedSignal;
    private final LerpedFloat clientAdmission = LerpedFloat.linear();

    public SteamAdmissionValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_ADMISSION_VALVE.get(), pos, state);
        clientAdmission.setValue(1.0F);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        link = LinkBehaviour.receiver(
                this,
                ValueBoxTransform.Dual.makeSlots(AdmissionFrequencySlot::new),
                this::setReceivedSignal
        );
        behaviours.add(link);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide()) {
            clientAdmission.chase(getAdmissionFraction(), 0.25F, LerpedFloat.Chaser.EXP);
            clientAdmission.tickChaser();
        }
    }

    public int getReceivedSignal() {
        return receivedSignal;
    }

    public int getAdmissionStrength() {
        return isFrequencyBypass() ? 15 : receivedSignal;
    }

    public float getAdmissionFraction() {
        return getAdmissionStrength() / 15.0F;
    }

    public float getRenderedAdmission(float partialTicks) {
        return clientAdmission.getValue(partialTicks);
    }

    public boolean isFrequencyBypass() {
        if (link == null) {
            return true;
        }
        Couple<com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency> frequencies =
                link.getNetworkKey();
        return frequencies.both(frequency -> frequency.getStack().isEmpty());
    }

    private void setReceivedSignal(int signal) {
        int clamped = Mth.clamp(signal, 0, 15);
        if (receivedSignal == clamped) {
            return;
        }
        receivedSignal = clamped;
        setChanged();
        sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("Steam Admission Valve").forGoggles(tooltip);
        if (isFrequencyBypass()) {
            CreateLang.text("Admission: Bypass (100%)").forGoggles(tooltip, 1);
        } else {
            int strength = getAdmissionStrength();
            int percent = Math.round(getAdmissionFraction() * 100.0F);
            CreateLang.text("Command: " + strength + "/15 (" + percent + "%)")
                    .forGoggles(tooltip, 1);
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt(RECEIVED_SIGNAL_KEY, receivedSignal);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        receivedSignal = Mth.clamp(tag.getInt(RECEIVED_SIGNAL_KEY), 0, 15);
        if (clientPacket) {
            clientAdmission.chase(getAdmissionFraction(), 0.25F, LerpedFloat.Chaser.EXP);
        } else {
            clientAdmission.setValue(getAdmissionFraction());
        }
    }

    private static final class AdmissionFrequencySlot extends ValueBoxTransform.Dual {
        private AdmissionFrequencySlot(boolean first) {
            super(first);
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            double x = isFirst() ? 5.5D : 10.5D;
            Vec3 offset = new Vec3(x / 16.0D, 14.51D / 16.0D, 0.5D);
            return rotateForFacing(offset, state.getValue(SteamAdmissionValveBlock.FACING));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack poseStack) {
            TransformStack.of(poseStack)
                    .rotateYDegrees(rotationDegrees(state.getValue(SteamAdmissionValveBlock.FACING)))
                    .rotateXDegrees(90.0F);
        }

        @Override
        public float getScale() {
            return 0.4975F;
        }

        private static Vec3 rotateForFacing(Vec3 offset, Direction facing) {
            double x = offset.x;
            double z = offset.z;
            return switch (facing) {
                case EAST -> new Vec3(1.0D - z, offset.y, x);
                case SOUTH -> new Vec3(1.0D - x, offset.y, 1.0D - z);
                case WEST -> new Vec3(z, offset.y, 1.0D - x);
                default -> offset;
            };
        }

        private static float rotationDegrees(Direction facing) {
            return switch (facing) {
                case EAST -> 90.0F;
                case SOUTH -> 180.0F;
                case WEST -> 270.0F;
                default -> 0.0F;
            };
        }
    }
}
