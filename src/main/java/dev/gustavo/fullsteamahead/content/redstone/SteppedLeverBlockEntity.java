package dev.gustavo.fullsteamahead.content.redstone;

import com.simibubi.create.AllSoundEvents;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Engine Order Telegraph. Telegraphs sharing a {@link #linkId} channel stay <b>synchronized</b>:
 * moving one handle sets every linked unit to the same position and rings the telegraph bell on each.
 */
public class SteppedLeverBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final String STATE_KEY = "State";
    private static final String CHANGE_TIMER_KEY = "ChangeTimer";
    private static final String LINK_ID_KEY = "LinkId";
    private static final int UPDATE_DELAY_TICKS = 15;
    /** Each level change rings Create's desk bell as a short repeated burst (telegraph "ding-ding"). */
    private static final int BELL_RING_COUNT = 3;
    private static final int BELL_RING_GAP_TICKS = 4;

    private int state;
    private int lastChange;
    /** Shared channel id keeping this telegraph synchronized with its partner(s); null when unlinked. */
    private UUID linkId;
    private boolean registered;
    private int pendingRings;
    private int ringGap;

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
        linkId = compound.hasUUID(LINK_ID_KEY) ? compound.getUUID(LINK_ID_KEY) : null;
        clientState.chase(state, 0.2F, LerpedFloat.Chaser.EXP);
        super.read(compound, registries, clientPacket);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt(STATE_KEY, state);
        compound.putInt(CHANGE_TIMER_KEY, lastChange);
        // Synced to clients too so the channel outliner can match telegraphs while the item is held.
        if (linkId != null) {
            compound.putUUID(LINK_ID_KEY, linkId);
        }
        super.write(compound, registries, clientPacket);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }

        // Join the link registry (both sides) after a world/chunk load; the client copy feeds the outliner.
        if (!registered && linkId != null) {
            TelegraphLinks.add(level, linkId, worldPosition);
            registered = true;
        }

        if (level.isClientSide) {
            clientState.tickChaser();
            return;
        }

        if (lastChange > 0) {
            lastChange--;
            if (lastChange == 0) {
                SteppedLeverBlock.updateNeighbors(getBlockState(), level, worldPosition);
            }
        }

        if (pendingRings > 0) {
            if (ringGap > 0) {
                ringGap--;
            } else {
                ringOnce();
                pendingRings--;
                ringGap = BELL_RING_GAP_TICKS;
            }
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void invalidate() {
        // setRemoved() is final in SmartBlockEntity and routes here; the link-registry lookup also
        // self-heals stale positions, so this is the cleanup path for break/unload.
        if (level != null && linkId != null) {
            TelegraphLinks.remove(level, linkId, worldPosition);
        }
        registered = false;
        super.invalidate();
    }

    /** Player moved this handle: synchronizes every linked partner to the new position and rings. */
    public void changeState(boolean decrease) {
        int previousState = state;
        state = Mth.clamp(state + (decrease ? -1 : 1), 0, 15);
        if (previousState == state) {
            return;
        }
        markChanged();
        if (level != null && !level.isClientSide && linkId != null) {
            for (SteppedLeverBlockEntity partner : TelegraphLinks.partners(level, linkId, worldPosition)) {
                partner.syncTo(state);
            }
            startBell();
        }
    }

    /** Adopt a synchronized position pushed by a partner (no re-propagation), ringing the bell. */
    private void syncTo(int newState) {
        if (level == null || level.isClientSide || state == newState) {
            return;
        }
        state = newState;
        markChanged();
        startBell();
    }

    private void markChanged() {
        lastChange = UPDATE_DELAY_TICKS;
        setChanged();
        sendData();
    }

    public UUID getLinkId() {
        return linkId;
    }

    /** Binds (or clears, when {@code null}) this telegraph's channel; a newcomer adopts the channel's position. */
    public void setLinkId(UUID id) {
        if (level == null || level.isClientSide || Objects.equals(linkId, id)) {
            return;
        }
        if (linkId != null) {
            TelegraphLinks.remove(level, linkId, worldPosition);
        }
        linkId = id;
        registered = false;
        if (linkId != null) {
            TelegraphLinks.add(level, linkId, worldPosition);
            registered = true;
            // Match the rest of the channel quietly so a freshly linked unit lines up immediately.
            for (SteppedLeverBlockEntity partner : TelegraphLinks.partners(level, linkId, worldPosition)) {
                state = partner.getState();
                break;
            }
        }
        markChanged();
    }

    /** Queue the repeated telegraph ding; the burst plays out over the next ticks in {@link #tick()}. */
    private void startBell() {
        pendingRings = BELL_RING_COUNT;
        ringGap = 0;
    }

    private void ringOnce() {
        if (level instanceof ServerLevel serverLevel) {
            AllSoundEvents.DESK_BELL_USE.playOnServer(serverLevel, worldPosition, 1.2F, 1.0F);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("tooltip.analogStrength", state).forGoggles(tooltip);
        if (linkId != null) {
            CreateLang.translate("tooltip.full_steam_ahead.telegraph_linked").forGoggles(tooltip);
        }
        return true;
    }

    public int getState() {
        return state;
    }

    public float getRenderedState(float partialTicks) {
        return clientState.getValue(partialTicks);
    }
}
