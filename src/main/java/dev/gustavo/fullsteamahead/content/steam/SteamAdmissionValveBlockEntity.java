package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.gustavo.fullsteamahead.content.redstone.TelegraphLinkable;
import dev.gustavo.fullsteamahead.content.redstone.TelegraphLinks;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SteamAdmissionValveBlockEntity extends FluidPipeBlockEntity
        implements IHaveGoggleInformation, TelegraphLinkable {
    private static final String RECEIVED_SIGNAL_KEY = "ReceivedSignal";
    private static final String MANUAL_STRENGTH_KEY = "ManualStrength";
    private static final String TELEGRAPH_LINK_KEY = "TelegraphLink";
    private static final String CONTROL_MODE_KEY = "ControlMode";

    private LinkBehaviour link;
    private int receivedSignal;
    private int manualStrength = 15;
    private SteamAdmissionControlMode controlMode = SteamAdmissionControlMode.MANUAL;
    private UUID telegraphLinkId;
    private boolean telegraphRegistered;
    private double networkPressurePn;
    private int requestedSteamMb;
    private int allocatedSteamMb;
    private int deliveredSteamMb;
    private long networkGameTime = Long.MIN_VALUE;
    private int lastAudibleAdmission = -1;
    private boolean clientLeverInitialized;

    private final LerpedFloat clientManualStrength = LerpedFloat.linear().startWithValue(15.0D);

    public SteamAdmissionValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_ADMISSION_VALVE.get(), pos, state);
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
        if (level == null) {
            return;
        }
        if (!telegraphRegistered && telegraphLinkId != null) {
            TelegraphLinks.add(level, telegraphLinkId, worldPosition);
            telegraphRegistered = true;
        }
        if (level.isClientSide) {
            clientManualStrength.tickChaser();
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            if (level.getGameTime() % 5L == 0L) {
                BlockState currentState = getBlockState();
                BlockState resolvedState = SteamAdmissionValveBlock.resolveTopology(
                        level,
                        worldPosition,
                        currentState
                );
                if (resolvedState.getValue(SteamAdmissionValveBlock.INVERTED)
                        != currentState.getValue(SteamAdmissionValveBlock.INVERTED)
                        && !SteamAdmissionValveController.canOccupy(level, worldPosition, resolvedState)) {
                    resolvedState = resolvedState.setValue(
                            SteamAdmissionValveBlock.INVERTED,
                            currentState.getValue(SteamAdmissionValveBlock.INVERTED));
                }
                if (resolvedState != currentState) {
                    level.setBlock(worldPosition, resolvedState, Block.UPDATE_ALL);
                }
                SteamAdmissionValveController.sync(
                        level,
                        worldPosition,
                        level.getBlockState(worldPosition)
                );
            }
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
        if (isManualMode()) {
            return manualStrength;
        }
        return isFrequencyBypass() ? 15 : receivedSignal;
    }

    public float getAdmissionFraction() {
        return getAdmissionStrength() / 15.0F;
    }

    public boolean isManualMode() {
        return controlMode == SteamAdmissionControlMode.MANUAL;
    }

    public SteamAdmissionControlMode getControlMode() {
        return controlMode;
    }

    public SteamAdmissionControlMode cycleControlMode() {
        controlMode = controlMode.next();
        setChanged();
        sendData();
        return controlMode;
    }

    public int getManualStrength() {
        return manualStrength;
    }

    public float getRenderedManualStrength(float partialTicks) {
        return clientManualStrength.getValue(partialTicks);
    }

    public boolean changeManualStrength(boolean decrease) {
        int next = Mth.clamp(manualStrength + (decrease ? -1 : 1), 0, 15);
        if (next == manualStrength) {
            return false;
        }
        manualStrength = next;
        lastAudibleAdmission = next;
        markManualChanged();
        if (level != null && !level.isClientSide && telegraphLinkId != null) {
            for (TelegraphLinkable device : TelegraphLinks.devices(level, telegraphLinkId, worldPosition)) {
                device.receiveTelegraphState(next);
            }
        }
        return true;
    }

    @Override
    public UUID getLinkId() {
        return telegraphLinkId;
    }

    @Override
    public int getTelegraphState() {
        return manualStrength;
    }

    @Override
    public void receiveTelegraphState(int state) {
        if (level == null || level.isClientSide) {
            return;
        }
        int clamped = Mth.clamp(state, 0, 15);
        if (manualStrength == clamped) {
            return;
        }
        manualStrength = clamped;
        markManualChanged();
    }

    public void setTelegraphLinkId(UUID id) {
        if (level == null || level.isClientSide || Objects.equals(telegraphLinkId, id)) {
            return;
        }
        if (telegraphLinkId != null) {
            TelegraphLinks.remove(level, telegraphLinkId, worldPosition);
        }
        telegraphLinkId = id;
        telegraphRegistered = false;
        if (telegraphLinkId != null) {
            TelegraphLinks.add(level, telegraphLinkId, worldPosition);
            telegraphRegistered = true;
            for (TelegraphLinkable device : TelegraphLinks.devices(level, telegraphLinkId, worldPosition)) {
                manualStrength = device.getTelegraphState();
                break;
            }
        }
        markManualChanged();
    }

    private void markManualChanged() {
        clientManualStrength.chase(manualStrength, 0.25D, LerpedFloat.Chaser.EXP);
        setChanged();
        sendData();
    }

    @Override
    public void invalidate() {
        if (level != null && telegraphLinkId != null) {
            TelegraphLinks.remove(level, telegraphLinkId, worldPosition);
        }
        telegraphRegistered = false;
        super.invalidate();
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
        if (isManualMode()) {
            CreateLang.text("Control: Manual / Telegraph").forGoggles(tooltip, 1);
            int percent = Math.round(getAdmissionFraction() * 100.0F);
            CreateLang.text("Admission: " + manualStrength + "/15 (" + percent + "%)")
                    .forGoggles(tooltip, 1);
            CreateLang.text(telegraphLinkId == null ? "Telegraph: Local" : "Telegraph: Linked")
                    .style(telegraphLinkId == null ? ChatFormatting.GRAY : ChatFormatting.GREEN)
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.text("Control: Redstone Link").forGoggles(tooltip, 1);
            if (isFrequencyBypass()) {
                CreateLang.text("Admission: Bypass (100%)").forGoggles(tooltip, 1);
            } else {
                int strength = getAdmissionStrength();
                int percent = Math.round(getAdmissionFraction() * 100.0F);
                CreateLang.text("Signal: " + receivedSignal + "/15")
                        .forGoggles(tooltip, 1);
                CreateLang.text("Admission: " + strength + "/15 (" + percent + "%)")
                        .forGoggles(tooltip, 1);
            }
        }
        String state = !isManualMode() && isFrequencyBypass()
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
        tag.putInt(MANUAL_STRENGTH_KEY, manualStrength);
        tag.putString(CONTROL_MODE_KEY, controlMode.getSerializedName());
        if (telegraphLinkId != null) {
            tag.putUUID(TELEGRAPH_LINK_KEY, telegraphLinkId);
        }
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
        UUID previousLink = telegraphLinkId;
        receivedSignal = Mth.clamp(tag.getInt(RECEIVED_SIGNAL_KEY), 0, 15);
        if (tag.contains(MANUAL_STRENGTH_KEY)) {
            manualStrength = Mth.clamp(tag.getInt(MANUAL_STRENGTH_KEY), 0, 15);
        }
        // Old Phase 17 block entities have no mode key and retain their receiver behavior.
        controlMode = tag.contains(CONTROL_MODE_KEY)
                ? SteamAdmissionControlMode.fromSerializedName(tag.getString(CONTROL_MODE_KEY))
                : SteamAdmissionControlMode.REDSTONE_LINK;
        telegraphLinkId = tag.hasUUID(TELEGRAPH_LINK_KEY) ? tag.getUUID(TELEGRAPH_LINK_KEY) : null;
        if (level != null && telegraphRegistered && !Objects.equals(previousLink, telegraphLinkId)) {
            TelegraphLinks.remove(level, previousLink, worldPosition);
            telegraphRegistered = false;
        }
        if (!clientLeverInitialized) {
            clientManualStrength.setValueNoUpdate(manualStrength);
            clientLeverInitialized = true;
        } else {
            clientManualStrength.chase(manualStrength, 0.25D, LerpedFloat.Chaser.EXP);
        }
        if (clientPacket) {
            networkPressurePn = SteamPressure.zeroIfNegligible(tag.getDouble("NetworkPressure"));
            requestedSteamMb = Math.max(0, tag.getInt("RequestedSteam"));
            allocatedSteamMb = Math.max(0, tag.getInt("AllocatedSteam"));
            deliveredSteamMb = Math.max(0, tag.getInt("DeliveredSteam"));
            networkGameTime = tag.getLong("NetworkGameTime");
        }
    }

    private static final class AdmissionFrequencySlot extends ValueBoxTransform.Dual {
        private AdmissionFrequencySlot(boolean first) {
            super(first);
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            if (!isRemoteMode(level, pos)) {
                return null;
            }
            double x = isFirst() ? 5.5D : 10.5D;
            Vec3 offset = new Vec3(x / 16.0D, 24.0D / 16.0D, 10.51D / 16.0D);
            if (state.getValue(SteamAdmissionValveBlock.INVERTED)) {
                offset = new Vec3(1.0D - offset.x, 1.0D - offset.y, offset.z);
            }
            return rotateForFacing(offset, state.getValue(SteamAdmissionValveBlock.FACING));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack poseStack) {
            TransformStack transform = TransformStack.of(poseStack);
            if (state.getValue(SteamAdmissionValveBlock.INVERTED)) {
                transform.rotateZDegrees(180.0F);
            }
            transform.rotateYDegrees(AngleHelper.horizontalAngle(
                    state.getValue(SteamAdmissionValveBlock.FACING)));
        }

        @Override
        public float getScale() {
            return 0.4975F;
        }

        @Override
        public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
            return isRemoteMode(level, pos) && super.shouldRender(level, pos, state);
        }

        private static boolean isRemoteMode(LevelAccessor level, BlockPos pos) {
            return level.getBlockEntity(pos) instanceof SteamAdmissionValveBlockEntity valve
                    && !valve.isManualMode();
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

    }
}
