package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.AllSoundEvents;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

public class SteamReliefValveBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final String BOILER_POS_KEY = "BoilerPos";
    private static final String AUTO_OPEN_KEY = "AutoOpen";
    private static final String PRESSURE_KEY = "Pressure";
    private static final String OPEN_KEY = "Open";
    private static final String VENTING_KEY = "Venting";
    private static final String FORCED_OPEN_KEY = "ForcedOpen";
    private static final String VENTED_LAST_TICK_KEY = "VentedLastTick";
    private static final String MAX_PRESSURE_KEY = "MaxPressure";
    private static final float SOUND_VOLUME_SCALE = 0.1F;

    private final LerpedFloat clientOpen = LerpedFloat.linear();

    private BlockPos boilerPos;
    private boolean autoOpen;
    private boolean open;
    private boolean venting;
    private boolean forcedOpen;
    private double networkPressurePn;
    private double maxObservedPressurePn;
    private int ventedLastTick;

    public SteamReliefValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_RELIEF_VALVE.get(), pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide()) {
            SteamNetworkManager.registerReliefValve(level, worldPosition);
            refreshBoilerState();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide()) {
            refreshBoilerState();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide()) {
            clientOpen.chase(open ? 1.0F : 0.0F, 0.22F, LerpedFloat.Chaser.EXP);
            clientOpen.tickChaser();
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        boilerPos = compound.contains(BOILER_POS_KEY) ? BlockPos.of(compound.getLong(BOILER_POS_KEY)) : null;
        autoOpen = compound.getBoolean(AUTO_OPEN_KEY);
        networkPressurePn = SteamPressure.zeroIfNegligible(compound.getDouble(PRESSURE_KEY));
        open = compound.getBoolean(OPEN_KEY);
        venting = compound.getBoolean(VENTING_KEY);
        forcedOpen = compound.getBoolean(FORCED_OPEN_KEY);
        ventedLastTick = compound.getInt(VENTED_LAST_TICK_KEY);
        maxObservedPressurePn = compound.getDouble(MAX_PRESSURE_KEY);
        clientOpen.chase(open ? 1.0F : 0.0F, 0.22F, LerpedFloat.Chaser.EXP);
        super.read(compound, registries, clientPacket);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (boilerPos != null) {
            compound.putLong(BOILER_POS_KEY, boilerPos.asLong());
        }
        compound.putBoolean(AUTO_OPEN_KEY, autoOpen);
        compound.putDouble(PRESSURE_KEY, networkPressurePn);
        compound.putBoolean(OPEN_KEY, open);
        compound.putBoolean(VENTING_KEY, venting);
        compound.putBoolean(FORCED_OPEN_KEY, forcedOpen);
        compound.putInt(VENTED_LAST_TICK_KEY, ventedLastTick);
        compound.putDouble(MAX_PRESSURE_KEY, maxObservedPressurePn);
        super.write(compound, registries, clientPacket);
    }

    public void refreshBoilerState() {
        BlockPos previous = boilerPos;
        FluidTankBlockEntity boiler = getBoiler();
        boilerPos = boiler == null ? null : boiler.getBlockPos();
        if (boilerPos == null) {
            autoOpen = false;
            open = false;
            venting = false;
            forcedOpen = false;
            networkPressurePn = 0.0D;
            ventedLastTick = 0;
        }
        if (!Objects.equals(previous, boilerPos)) {
            notifyUpdate();
        }
    }

    public void clearBoilerState() {
        boilerPos = null;
        autoOpen = false;
        open = false;
        venting = false;
        forcedOpen = false;
        networkPressurePn = 0.0D;
        ventedLastTick = 0;
        notifyUpdate();
    }

    public FluidTankBlockEntity getBoiler() {
        if (level == null) {
            return null;
        }

        BlockPos tankPos = SteamReliefValveBlock.getAttachedTankPos(worldPosition, getBlockState());
        if (!level.isLoaded(tankPos)) {
            return null;
        }

        if (level.getBlockEntity(tankPos) instanceof FluidTankBlockEntity tank) {
            FluidTankBlockEntity controller = tank.getControllerBE();
            return controller == null ? tank : controller;
        }
        return null;
    }

    public BlockPos getBoilerControllerPos() {
        return boilerPos;
    }

    public boolean isForcedOpen() {
        return getBlockState().hasProperty(SteamReliefValveBlock.POWERED)
                && getBlockState().getValue(SteamReliefValveBlock.POWERED);
    }

    public boolean isAutoOpen() {
        return autoOpen;
    }

    public boolean wouldOpenAt(double pressurePn) {
        if (isForcedOpen()) {
            return true;
        }
        if (pressurePn >= FullSteamConfig.reliefValveOpenPressure()) {
            return true;
        }
        return autoOpen && pressurePn > FullSteamConfig.reliefValveClosePressure();
    }

    public void applyNetworkState(double pressurePn, boolean open, boolean venting, int ventedMb) {
        pressurePn = SteamPressure.zeroIfNegligible(pressurePn);
        boolean previousOpen = this.open;
        boolean previousVenting = this.venting;
        boolean previousForced = this.forcedOpen;
        int previousVented = this.ventedLastTick;
        double previousPressure = this.networkPressurePn;
        double previousMax = this.maxObservedPressurePn;

        this.networkPressurePn = pressurePn;
        this.maxObservedPressurePn = Math.max(maxObservedPressurePn, pressurePn);
        this.forcedOpen = isForcedOpen();
        if (forcedOpen || pressurePn >= FullSteamConfig.reliefValveOpenPressure()) {
            autoOpen = true;
        } else if (pressurePn <= FullSteamConfig.reliefValveClosePressure()) {
            autoOpen = false;
        }
        this.open = forcedOpen || open || autoOpen;
        this.venting = venting;
        this.ventedLastTick = ventedMb;

        int audibleAmount = Math.max(ventedMb, FullSteamConfig.reliefValveVentRateMb() / 4);
        if (venting && ventedMb > 0) {
            emitSteam(ventedMb, true);
        } else if (this.open && pressurePn >= FullSteamConfig.reliefValveClosePressure()) {
            emitSteam(audibleAmount, false);
        }

        if (previousOpen != this.open
                || previousVenting != this.venting
                || previousForced != this.forcedOpen
                || previousVented != this.ventedLastTick
                || previousPressure != this.networkPressurePn
                || previousMax != this.maxObservedPressurePn) {
            notifyUpdate();
        }
    }

    private void emitSteam(int amount, boolean particles) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState state = getBlockState();
        Direction attachedFace = SteamReliefValveBlock.getAttachedFace(state);
        Direction facing = SteamReliefValveBlock.getFacing(state);
        Vec3 normal = Vec3.atLowerCornerOf(attachedFace.getNormal());
        Vec3 origin = Vec3.atCenterOf(worldPosition).add(normal.scale(0.28D));
        if (particles && level.getGameTime() % 4L == 0L) {
            SteamCloudEffects.emitReliefValve(serverLevel, origin, attachedFace, facing, amount);
        }
        if (level.getGameTime() % 8L == 0L) {
            float intensity = (float) Math.min(1.0D, amount / (double) Math.max(1, FullSteamConfig.reliefValveVentRateMb()));
            float volume = (0.55F + 0.35F * intensity) * SOUND_VOLUME_SCALE;
            AllSoundEvents.STEAM.playAt(level, origin, volume, 0.68F, false);
        }
    }

    public float getRenderedOpen(float partialTicks) {
        return clientOpen.getValue(partialTicks);
    }

    public float getWheelAngle(float partialTicks) {
        if (level == null) {
            return 0.0F;
        }
        float base = getRenderedOpen(partialTicks) * (float) (Math.PI * 2.0D);
        if (!venting) {
            return base;
        }
        return base + (level.getGameTime() + partialTicks) * 0.35F;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isVenting() {
        return venting;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("Steam Relief Valve").style(ChatFormatting.GRAY).forGoggles(tooltip);
        CreateLang.text(boilerPos == null ? "No boiler attached" : "Boiler linked")
                .style(boilerPos == null ? ChatFormatting.RED : ChatFormatting.GREEN)
                .forGoggles(tooltip, 1);
        CreateLang.text(forcedOpen ? "Forced open" : open ? "Valve open" : "Valve closed")
                .style(forcedOpen ? ChatFormatting.GOLD : open ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Pressure: " + SteamPressure.format(networkPressurePn))
                .style(networkPressurePn >= FullSteamConfig.reliefValveOpenPressure()
                        ? ChatFormatting.RED
                        : networkPressurePn > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Opens: " + SteamPressure.format(FullSteamConfig.reliefValveOpenPressure()))
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Vented: " + ventedLastTick + " mB/t")
                .style(ventedLastTick > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        if (isPlayerSneaking) {
            CreateLang.text("Peak: " + SteamPressure.format(maxObservedPressurePn))
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        }
        return true;
    }
}
