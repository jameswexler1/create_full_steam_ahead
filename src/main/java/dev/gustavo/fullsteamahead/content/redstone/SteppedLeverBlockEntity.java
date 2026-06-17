package dev.gustavo.fullsteamahead.content.redstone;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModSoundEvents;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SteppedLeverBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /**
     * Flip to {@code true} to ring Create's confirm "ding" instead of the custom telegraph bell —
     * useful before {@code sounds/telegraph/telegraph_bell.ogg} has been added to the resources.
     */
    public static final boolean USE_CONFIRM_FALLBACK = false;

    private static final String STATE_KEY = "State";
    private static final String CHANGE_TIMER_KEY = "ChangeTimer";
    private static final String LINK_ID_KEY = "LinkId";
    private static final String ORDER_KEY = "Order";
    private static final String RINGING_KEY = "Ringing";
    private static final int UPDATE_DELAY_TICKS = 15;
    /** Cadence of the reminder bell while an order sits unanswered (3s). */
    private static final int BELL_REMINDER_TICKS = 60;

    private int state;
    private int lastChange;
    /** Shared channel id pairing this telegraph with its partner(s); null when unlinked. */
    private UUID linkId;
    /** The partner's commanded position shown as the "order" needle; -1 when no order is present. */
    private int orderState = -1;
    /** True while a received order differs from this unit's handle (bell rings until answered). */
    private boolean ringing;
    private int ringTimer;
    private boolean registered;

    private final LerpedFloat clientState = LerpedFloat.linear();
    private final LerpedFloat clientOrder = LerpedFloat.linear();

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
        orderState = compound.contains(ORDER_KEY) ? compound.getInt(ORDER_KEY) : -1;
        ringing = compound.getBoolean(RINGING_KEY);
        linkId = compound.hasUUID(LINK_ID_KEY) ? compound.getUUID(LINK_ID_KEY) : null;
        clientState.chase(state, 0.2F, LerpedFloat.Chaser.EXP);
        clientOrder.chase(orderState < 0 ? state : orderState, 0.2F, LerpedFloat.Chaser.EXP);
        super.read(compound, registries, clientPacket);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt(STATE_KEY, state);
        compound.putInt(CHANGE_TIMER_KEY, lastChange);
        compound.putInt(ORDER_KEY, orderState);
        compound.putBoolean(RINGING_KEY, ringing);
        // The link id is only needed by the server-side registry; no reason to ship it to clients.
        if (linkId != null && !clientPacket) {
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

        if (level.isClientSide) {
            clientState.tickChaser();
            clientOrder.tickChaser();
            return;
        }

        // Re-join the link registry after a world/chunk load (the id was read from NBT).
        if (!registered && linkId != null) {
            TelegraphLinks.add(level, linkId, worldPosition);
            registered = true;
            refreshFromPartners();
        }

        if (lastChange > 0) {
            lastChange--;
            if (lastChange == 0) {
                SteppedLeverBlock.updateNeighbors(getBlockState(), level, worldPosition);
            }
        }

        if (ringing) {
            if (ringTimer > 0) {
                ringTimer--;
            }
            if (ringTimer <= 0) {
                playBell();
                ringTimer = BELL_REMINDER_TICKS;
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
        if (level != null && !level.isClientSide && linkId != null) {
            TelegraphLinks.remove(level, linkId, worldPosition);
        }
        registered = false;
        super.invalidate();
    }

    /** Player moved this handle: pushes the new order to partners and answers any pending bell. */
    public void changeState(boolean decrease) {
        int previousState = state;
        state = Mth.clamp(state + (decrease ? -1 : 1), 0, 15);
        if (previousState != state) {
            lastChange = UPDATE_DELAY_TICKS;
            if (level != null && !level.isClientSide && linkId != null) {
                for (SteppedLeverBlockEntity partner : TelegraphLinks.partners(level, linkId, worldPosition)) {
                    partner.receiveOrder(state);
                }
            }
            updateRinging();
            setChanged();
            sendData();
        }
    }

    /** A partner's handle moved: record it as our order and (re)start the bell if it differs. */
    public void receiveOrder(int order) {
        if (level == null || level.isClientSide || orderState == order) {
            return;
        }
        orderState = order;
        updateRinging();
        setChanged();
        sendData();
    }

    public UUID getLinkId() {
        return linkId;
    }

    /** Binds (or clears, when {@code null}) this telegraph's channel and syncs with partners. */
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
            refreshFromPartners();
        } else {
            orderState = -1;
            updateRinging();
            setChanged();
            sendData();
        }
    }

    /** Pulls the partner's position into our order needle and pushes ours onto theirs. */
    private void refreshFromPartners() {
        if (level == null || level.isClientSide || linkId == null) {
            return;
        }
        int newOrder = -1;
        for (SteppedLeverBlockEntity partner : TelegraphLinks.partners(level, linkId, worldPosition)) {
            newOrder = partner.getState();
            partner.receiveOrder(state);
        }
        orderState = newOrder;
        updateRinging();
        setChanged();
        sendData();
    }

    private void updateRinging() {
        boolean shouldRing = linkId != null && orderState >= 0 && orderState != state;
        if (shouldRing && !ringing) {
            ringTimer = 0; // ring on the next tick when a fresh order arrives
        }
        ringing = shouldRing;
    }

    private void playBell() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (USE_CONFIRM_FALLBACK) {
            AllSoundEvents.CONFIRM.playOnServer(serverLevel, worldPosition, 1.2F, 1.0F);
            return;
        }
        serverLevel.playSound(null, worldPosition, ModSoundEvents.TELEGRAPH_BELL.get(),
                SoundSource.BLOCKS, 1.2F, 1.0F);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("tooltip.analogStrength", state).forGoggles(tooltip);
        if (orderState >= 0) {
            CreateLang.translate("tooltip.full_steam_ahead.telegraph_order", orderState).forGoggles(tooltip);
            if (ringing) {
                CreateLang.translate("tooltip.full_steam_ahead.telegraph_awaiting").forGoggles(tooltip);
            }
        }
        return true;
    }

    public int getState() {
        return state;
    }

    public boolean isLinked() {
        return orderState >= 0;
    }

    public boolean isRinging() {
        return ringing;
    }

    public float getRenderedState(float partialTicks) {
        return clientState.getValue(partialTicks);
    }

    public float getRenderedOrder(float partialTicks) {
        return clientOrder.getValue(partialTicks);
    }
}
