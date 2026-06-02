package dev.gustavo.fullsteamahead.content.cylinder;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

public class SteamCylinderBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final String ASSEMBLED_KEY = "Assembled";
    private static final String ROOT_KEY = "Root";
    private static final String ROOT_POS_KEY = "RootPos";
    private static final String RING_ORIGIN_KEY = "RingOrigin";
    private static final String BOILER_POS_KEY = "BoilerPos";
    private static final String INLET_POS_KEY = "InletPos";

    private boolean assembled;
    private boolean root;
    private BlockPos rootPos;
    private BlockPos ringOrigin;
    private BlockPos boilerPos;
    private BlockPos inletPos;

    public SteamCylinderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_CYLINDER.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide()) {
            CylinderConnectivity.refreshFrom(level, worldPosition);
        }
    }

    public void applyRingState(
            BlockPos ringOrigin,
            BlockPos rootPos,
            BlockPos boilerPos,
            BlockPos inletPos,
            boolean root
    ) {
        boolean changed = !assembled
                || this.root != root
                || !Objects.equals(this.rootPos, rootPos)
                || !Objects.equals(this.ringOrigin, ringOrigin)
                || !Objects.equals(this.boilerPos, boilerPos)
                || !Objects.equals(this.inletPos, inletPos);

        this.assembled = true;
        this.root = root;
        this.rootPos = rootPos;
        this.ringOrigin = ringOrigin;
        this.boilerPos = boilerPos;
        this.inletPos = inletPos;

        if (changed) {
            notifyUpdate();
        }
    }

    public void clearRingState() {
        if (!assembled && !root && rootPos == null && ringOrigin == null && boilerPos == null && inletPos == null) {
            return;
        }

        assembled = false;
        root = false;
        rootPos = null;
        ringOrigin = null;
        boilerPos = null;
        inletPos = null;
        notifyUpdate();
    }

    public boolean isCylinderAssembled() {
        return assembled;
    }

    public boolean isRoot() {
        return root;
    }

    public BlockPos getRootPos() {
        return rootPos;
    }

    public BlockPos getRingOrigin() {
        return ringOrigin;
    }

    public BlockPos getBoilerPos() {
        return boilerPos;
    }

    public BlockPos getInletPos() {
        return inletPos;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("Steam Cylinder").style(ChatFormatting.GRAY).forGoggles(tooltip);

        if (!assembled) {
            CreateLang.text("Incomplete cylinder ring").style(ChatFormatting.RED).forGoggles(tooltip, 1);
            return true;
        }

        CreateLang.text("Cylinder ring assembled").style(ChatFormatting.GREEN).forGoggles(tooltip, 1);
        CreateLang.text(root ? "Root cylinder block" : "Linked cylinder block")
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);

        FluidTankBlockEntity boiler = getBoiler();
        if (boiler == null || boiler.boiler == null) {
            if (inletPos == null) {
                CreateLang.text("No steam source").style(ChatFormatting.YELLOW).forGoggles(tooltip, 1);
            } else {
                CreateLang.text("Steam inlet linked").style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
            }
            return true;
        }

        CreateLang.text("Boiler linked").style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        boiler.boiler.addToGoggleTooltip(tooltip, isPlayerSneaking, boiler.getTotalTankSize());
        return true;
    }

    private FluidTankBlockEntity getBoiler() {
        if (level == null || boilerPos == null) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(boilerPos);
        if (blockEntity instanceof FluidTankBlockEntity tank) {
            return tank.getControllerBE() == null ? tank : tank.getControllerBE();
        }
        return null;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean(ASSEMBLED_KEY, assembled);
        tag.putBoolean(ROOT_KEY, root);
        writePos(tag, ROOT_POS_KEY, rootPos);
        writePos(tag, RING_ORIGIN_KEY, ringOrigin);
        writePos(tag, BOILER_POS_KEY, boilerPos);
        writePos(tag, INLET_POS_KEY, inletPos);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        assembled = tag.getBoolean(ASSEMBLED_KEY);
        root = tag.getBoolean(ROOT_KEY);
        rootPos = readPos(tag, ROOT_POS_KEY);
        ringOrigin = readPos(tag, RING_ORIGIN_KEY);
        boilerPos = readPos(tag, BOILER_POS_KEY);
        inletPos = readPos(tag, INLET_POS_KEY);
    }

    private static void writePos(CompoundTag tag, String key, BlockPos pos) {
        if (pos == null) {
            tag.remove(key);
            return;
        }
        tag.putLong(key, pos.asLong());
    }

    private static BlockPos readPos(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }
}
