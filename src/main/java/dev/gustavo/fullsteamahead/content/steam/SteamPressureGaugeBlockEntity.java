package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SteamPressureGaugeBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final String SOURCE_OFFSET_KEY = "SourceOffset";
    private static final String LINK_FACING_KEY = "LinkFacing";
    private static final String PRESSURE_KEY = "PressurePn";
    private static final String BURST_PRESSURE_KEY = "BurstPressurePn";
    private static final String SOURCE_AVAILABLE_KEY = "SourceAvailable";
    private static final int SOURCE_POLL_TICKS = 4;
    private static final int HEARTBEAT_POLLS = 5;

    private final LerpedFloat clientPressure = LerpedFloat.linear();

    private BlockPos sourceOffset;
    private Direction linkFacing;
    private double pressurePn;
    private double burstPressurePn = 1.0D;
    private boolean sourceAvailable;
    private int pollCountdown;
    private int heartbeatCountdown;
    private float clientTarget;
    private boolean clientInitialized;

    public SteamPressureGaugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_PRESSURE_GAUGE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            reconcileMovingFacing();
            refreshSource(true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            clientPressure.chase(clientTarget, 0.18F, LerpedFloat.Chaser.EXP);
            clientPressure.tickChaser();
            return;
        }

        reconcileMovingFacing();
        if (pollCountdown > 0) {
            pollCountdown--;
            return;
        }
        pollCountdown = SOURCE_POLL_TICKS - 1;
        refreshSource(false);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        sourceOffset = compound.contains(SOURCE_OFFSET_KEY)
                ? BlockPos.of(compound.getLong(SOURCE_OFFSET_KEY))
                : null;
        linkFacing = Direction.byName(compound.getString(LINK_FACING_KEY));
        pressurePn = SteamPressure.zeroIfNegligible(compound.getDouble(PRESSURE_KEY));
        burstPressurePn = Math.max(1.0D, compound.getDouble(BURST_PRESSURE_KEY));
        sourceAvailable = compound.getBoolean(SOURCE_AVAILABLE_KEY);
        if (clientPacket) {
            clientTarget = normalizedPressure();
            if (!clientInitialized) {
                clientPressure.startWithValue(clientTarget);
                clientInitialized = true;
            }
        }
        super.read(compound, registries, clientPacket);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (sourceOffset != null) {
            compound.putLong(SOURCE_OFFSET_KEY, sourceOffset.asLong());
        }
        if (linkFacing != null) {
            compound.putString(LINK_FACING_KEY, linkFacing.getSerializedName());
        }
        compound.putDouble(PRESSURE_KEY, pressurePn);
        compound.putDouble(BURST_PRESSURE_KEY, burstPressurePn);
        compound.putBoolean(SOURCE_AVAILABLE_KEY, sourceAvailable);
        super.write(compound, registries, clientPacket);
    }

    public boolean bindToSource(GlobalPos source) {
        if (level == null || !level.dimension().equals(source.dimension())) {
            return false;
        }
        sourceOffset = source.pos().subtract(worldPosition);
        linkFacing = getGaugeFacing();
        pressurePn = 0.0D;
        sourceAvailable = false;
        refreshSource(true);
        return true;
    }

    public GlobalPos getLinkedSource() {
        if (level == null || sourceOffset == null) {
            return null;
        }
        return GlobalPos.of(level.dimension(), worldPosition.offset(sourceOffset));
    }

    /** A manual wrench rotates only the gauge; its already-linked world source must not rotate. */
    public void rebaseFacingAfterWrench() {
        linkFacing = getGaugeFacing();
        setChanged();
    }

    public float getRenderedPressure(float partialTicks) {
        return Mth.clamp(clientPressure.getValue(partialTicks), 0.0F, 1.0F);
    }

    public boolean isLinked() {
        return sourceOffset != null;
    }

    public boolean isSourceAvailable() {
        return sourceAvailable;
    }

    public double getPressurePn() {
        return pressurePn;
    }

    private void refreshSource(boolean forceSync) {
        if (level == null || level.isClientSide) {
            return;
        }

        double previousPressure = pressurePn;
        double previousBurstPressure = burstPressurePn;
        boolean previousAvailable = sourceAvailable;
        burstPressurePn = Math.max(1.0D, FullSteamConfig.steamBurstPressure());

        SourceReading reading = readSource();
        sourceAvailable = reading != null;
        pressurePn = reading == null ? 0.0D : SteamPressure.zeroIfNegligible(reading.pressurePn());

        heartbeatCountdown--;
        double syncThreshold = Math.max(1.0D, burstPressurePn / 2048.0D);
        boolean shouldSync = forceSync
                || previousAvailable != sourceAvailable
                || previousBurstPressure != burstPressurePn
                || Math.abs(previousPressure - pressurePn) >= syncThreshold
                || heartbeatCountdown <= 0;
        if (shouldSync) {
            heartbeatCountdown = HEARTBEAT_POLLS;
            notifyUpdate();
        }
    }

    private SourceReading readSource() {
        if (level == null || sourceOffset == null) {
            return null;
        }
        BlockPos sourcePos = worldPosition.offset(sourceOffset);
        if (!level.isLoaded(sourcePos)) {
            return null;
        }
        FluidTankBlockEntity controller = resolveTankController(level, sourcePos);
        if (!(controller instanceof SteamNetworkReadout readout)) {
            return null;
        }
        return new SourceReading(readout.getNetworkPressurePn());
    }

    public static FluidTankBlockEntity resolveTankController(Level level, BlockPos tankPos) {
        if (level == null || !level.isLoaded(tankPos)
                || !(level.getBlockEntity(tankPos) instanceof FluidTankBlockEntity tank)) {
            return null;
        }
        FluidTankBlockEntity controller = tank.getControllerBE();
        return controller == null ? tank : controller;
    }

    private void reconcileMovingFacing() {
        if (sourceOffset == null) {
            linkFacing = getGaugeFacing();
            return;
        }

        Direction currentFacing = getGaugeFacing();
        if (linkFacing == null) {
            linkFacing = currentFacing;
            setChanged();
            return;
        }
        if (linkFacing == currentFacing) {
            return;
        }

        int turns = clockwiseTurns(linkFacing, currentFacing);
        sourceOffset = rotateClockwise(sourceOffset, turns);
        linkFacing = currentFacing;
        setChanged();
    }

    private Direction getGaugeFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(SteamPressureGaugeBlock.FACING)
                ? state.getValue(SteamPressureGaugeBlock.FACING)
                : Direction.SOUTH;
    }

    private float normalizedPressure() {
        if (!sourceAvailable || burstPressurePn <= 0.0D) {
            return 0.0F;
        }
        return (float) Mth.clamp(pressurePn / burstPressurePn, 0.0D, 1.0D);
    }

    private static int clockwiseTurns(Direction from, Direction to) {
        Direction cursor = from;
        for (int turns = 0; turns < 4; turns++) {
            if (cursor == to) {
                return turns;
            }
            cursor = cursor.getClockWise();
        }
        return 0;
    }

    private static BlockPos rotateClockwise(BlockPos offset, int turns) {
        int x = offset.getX();
        int z = offset.getZ();
        for (int i = 0; i < turns; i++) {
            int previousX = x;
            x = -z;
            z = previousX;
        }
        return new BlockPos(x, offset.getY(), z);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("Steam Pressure Gauge").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if (!isLinked()) {
            CreateLang.text("Unlinked")
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
            CreateLang.text("Sneak-right-click a boiler with the item first")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
            return true;
        }
        if (!sourceAvailable) {
            CreateLang.text("Steam source unavailable")
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
            return true;
        }

        CreateLang.text("Boiler linked")
                .style(ChatFormatting.GREEN)
                .forGoggles(tooltip, 1);
        CreateLang.text("Pressure: " + SteamPressure.format(pressurePn))
                .style(pressurePn >= FullSteamConfig.steamWarnPressure()
                        ? ChatFormatting.RED
                        : pressurePn > 0.0D ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        if (isPlayerSneaking) {
            CreateLang.text("Burst pressure: " + SteamPressure.format(burstPressurePn))
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
            GlobalPos source = getLinkedSource();
            if (source != null) {
                BlockPos pos = source.pos();
                CreateLang.text("Source: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                        .style(ChatFormatting.DARK_GRAY)
                        .forGoggles(tooltip, 1);
            }
        }
        return true;
    }

    private record SourceReading(double pressurePn) {
    }
}
