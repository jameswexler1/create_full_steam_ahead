package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllSoundEvents;
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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
    private double networkPressurePn;
    private int requestedSteamMb;
    private int allocatedSteamMb;
    private int deliveredSteamMb;
    private long networkGameTime = Long.MIN_VALUE;
    private int lastAudibleAdmission = -1;

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
        } else if (level instanceof ServerLevel serverLevel) {
            int admission = getAdmissionStrength();
            if (lastAudibleAdmission >= 0 && lastAudibleAdmission != admission) {
                float pitch = 0.85F + admission / 15.0F * 0.3F;
                AllSoundEvents.WRENCH_ROTATE.playOnServer(serverLevel, worldPosition, 0.35F, pitch);
            }
            lastAudibleAdmission = admission;
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

    public SteamInletBlockEntity getControlledInlet() {
        if (level == null) {
            return null;
        }

        SteamInletBlockEntity candidate = null;
        int candidates = 0;
        BlockState valveState = getBlockState();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!valveState.getValue(SteamAdmissionValveBlock.PROPERTY_BY_DIRECTION.get(direction))) {
                continue;
            }
            BlockPos inletPos = worldPosition.relative(direction);
            BlockState inletState = level.getBlockState(inletPos);
            if (!(level.getBlockEntity(inletPos) instanceof SteamInletBlockEntity inlet)
                    || !inlet.isActiveInlet()
                    || SteamInletBlock.getPortFacing(inletState) != direction.getOpposite()) {
                continue;
            }
            candidate = inlet;
            candidates++;
        }
        return candidates == 1 ? candidate : null;
    }

    public boolean controls(SteamInletBlockEntity inlet) {
        return inlet != null && getControlledInlet() == inlet;
    }

    public void applyNetworkState(
            double pressurePn,
            int requestedSteamMb,
            int allocatedSteamMb,
            int deliveredSteamMb
    ) {
        this.networkPressurePn = SteamPressure.zeroIfNegligible(pressurePn);
        this.requestedSteamMb = Math.max(0, requestedSteamMb);
        this.allocatedSteamMb = Math.max(0, allocatedSteamMb);
        this.deliveredSteamMb = Math.max(0, deliveredSteamMb);
        this.networkGameTime = level == null ? 0L : level.getGameTime();
        if (level != null && level.getGameTime() % 5L == 0L) {
            notifyUpdate();
        }
    }

    private boolean isNetworkFresh() {
        return level != null
                && networkGameTime != Long.MIN_VALUE
                && level.getGameTime() - networkGameTime <= 10L;
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
        SteamInletBlockEntity inlet = getControlledInlet();
        CreateLang.text(inlet == null ? "No active inlet linked" : "Active inlet linked")
                .style(inlet == null ? ChatFormatting.YELLOW : ChatFormatting.GREEN)
                .forGoggles(tooltip, 1);
        if (isFrequencyBypass()) {
            CreateLang.text("Admission: Bypass (100%)").forGoggles(tooltip, 1);
        } else {
            int strength = getAdmissionStrength();
            int percent = Math.round(getAdmissionFraction() * 100.0F);
            CreateLang.text("Command: " + strength + "/15 (" + percent + "%)")
                    .forGoggles(tooltip, 1);
        }
        String state = isFrequencyBypass()
                ? "Bypass"
                : getAdmissionStrength() == 0
                        ? "Closed"
                        : getAdmissionStrength() == 15 ? "Open" : "Throttled";
        CreateLang.text("State: " + state).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        double pressure = isNetworkFresh() ? networkPressurePn : 0.0D;
        CreateLang.text("Supply pressure: " + SteamPressure.format(pressure))
                .style(pressure > 0.0D ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        int requested = isNetworkFresh() ? requestedSteamMb : 0;
        int delivered = isNetworkFresh() ? deliveredSteamMb : 0;
        CreateLang.text("Requested: " + requested + " mB/t  Delivered: " + delivered + " mB/t")
                .style(delivered > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt(RECEIVED_SIGNAL_KEY, receivedSignal);
        if (clientPacket) {
            tag.putDouble("NetworkPressure", networkPressurePn);
            tag.putInt("RequestedSteam", requestedSteamMb);
            tag.putInt("AllocatedSteam", allocatedSteamMb);
            tag.putInt("DeliveredSteam", deliveredSteamMb);
            tag.putLong("NetworkGameTime", networkGameTime);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        receivedSignal = Mth.clamp(tag.getInt(RECEIVED_SIGNAL_KEY), 0, 15);
        if (clientPacket) {
            networkPressurePn = SteamPressure.zeroIfNegligible(tag.getDouble("NetworkPressure"));
            requestedSteamMb = Math.max(0, tag.getInt("RequestedSteam"));
            allocatedSteamMb = Math.max(0, tag.getInt("AllocatedSteam"));
            deliveredSteamMb = Math.max(0, tag.getInt("DeliveredSteam"));
            networkGameTime = tag.getLong("NetworkGameTime");
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
            return 0.44F;
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
