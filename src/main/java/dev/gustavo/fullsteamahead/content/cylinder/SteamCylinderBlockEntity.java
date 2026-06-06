package dev.gustavo.fullsteamahead.content.cylinder;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.content.steam.SteamPressure;
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
import java.util.Set;

public class SteamCylinderBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final String ASSEMBLED_KEY = "Assembled";
    private static final String ROOT_KEY = "Root";
    private static final String ROOT_POS_KEY = "RootPos";
    private static final String RING_ORIGIN_KEY = "RingOrigin";
    private static final String SECONDARY_RING_ORIGIN_KEY = "SecondaryRingOrigin";
    private static final String BOILER_POS_KEY = "BoilerPos";
    private static final String INLET_POS_KEY = "InletPos";

    private boolean assembled;
    private boolean root;
    private BlockPos rootPos;
    private BlockPos ringOrigin;
    private BlockPos secondaryRingOrigin;
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
        applyRingState(ringOrigin, null, rootPos, boilerPos, inletPos, root);
    }

    public void applyRingState(
            BlockPos ringOrigin,
            BlockPos secondaryRingOrigin,
            BlockPos rootPos,
            BlockPos boilerPos,
            BlockPos inletPos,
            boolean root
    ) {
        boolean changed = !assembled
                || this.root != root
                || !Objects.equals(this.rootPos, rootPos)
                || !Objects.equals(this.ringOrigin, ringOrigin)
                || !Objects.equals(this.secondaryRingOrigin, secondaryRingOrigin)
                || !Objects.equals(this.boilerPos, boilerPos)
                || !Objects.equals(this.inletPos, inletPos);

        this.assembled = true;
        this.root = root;
        this.rootPos = rootPos;
        this.ringOrigin = ringOrigin;
        this.secondaryRingOrigin = secondaryRingOrigin;
        this.boilerPos = boilerPos;
        this.inletPos = inletPos;

        if (changed) {
            notifyUpdate();
        }
    }

    public void clearRingState() {
        if (!assembled
                && !root
                && rootPos == null
                && ringOrigin == null
                && secondaryRingOrigin == null
                && boilerPos == null
                && inletPos == null) {
            return;
        }

        assembled = false;
        root = false;
        rootPos = null;
        ringOrigin = null;
        secondaryRingOrigin = null;
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

    public BlockPos getSecondaryRingOrigin() {
        return secondaryRingOrigin;
    }

    public Set<BlockPos> getRingOrigins() {
        if (ringOrigin == null) {
            return Set.of();
        }
        if (secondaryRingOrigin == null || secondaryRingOrigin.equals(ringOrigin)) {
            return Set.of(ringOrigin);
        }
        return Set.of(ringOrigin, secondaryRingOrigin);
    }

    public boolean belongsToRingOrigin(BlockPos origin) {
        return origin != null && (origin.equals(ringOrigin) || origin.equals(secondaryRingOrigin));
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
        if (secondaryRingOrigin != null) {
            CreateLang.text("Shared cylinder wall").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        }

        appendEngineReadout(tooltip);

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

    /** Mirrors the linked piston head's pressure/RPM/SU onto the (far more accessible) ring block. */
    private void appendEngineReadout(List<Component> tooltip) {
        boolean shownAny = false;
        for (BlockPos origin : getRingOrigins()) {
            PistonHeadBlockEntity engine = findEngine(origin);
            if (engine == null) {
                continue;
            }
            if (!shownAny) {
                CreateLang.text("Engine").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
                shownAny = true;
            }
            CreateLang.text("Pressure: " + SteamPressure.format(engine.getPressurePn()))
                    .style(engine.getPressurePn() > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                    .forGoggles(tooltip, 2);
            CreateLang.text("RPM: " + Math.round(engine.getGeneratedSpeed()))
                    .style(engine.getGeneratedSpeed() != 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                    .forGoggles(tooltip, 2);
            CreateLang.text("Capacity: " + Math.round(engine.getGeneratedCapacitySu()) + " SU")
                    .style(engine.getGeneratedCapacitySu() > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                    .forGoggles(tooltip, 2);
        }
    }

    private PistonHeadBlockEntity findEngine(BlockPos origin) {
        if (level == null || origin == null) {
            return null;
        }
        for (BlockPos candidate : new BlockPos[]{origin.offset(1, 0, 1), origin.offset(1, 1, 1)}) {
            if (level.isLoaded(candidate)
                    && level.getBlockEntity(candidate) instanceof PistonHeadBlockEntity engine
                    && engine.isEngineAssembled()) {
                return engine;
            }
        }
        return null;
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
        writePos(tag, SECONDARY_RING_ORIGIN_KEY, secondaryRingOrigin);
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
        secondaryRingOrigin = readPos(tag, SECONDARY_RING_ORIGIN_KEY);
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
