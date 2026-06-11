package dev.gustavo.fullsteamahead.compat.cbc;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.content.steam.SteamNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class CbcProjectileDamageCompat {
    private static final String PROJECTILE_DAMAGE_EVENT = "rbasamoyai.createbigcannons.events.ProjectileDamageEvent";

    public static void registerIfPresent(IEventBus eventBus) {
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Class<Event> eventType = (Class) Class.forName(PROJECTILE_DAMAGE_EVENT).asSubclass(Event.class);
            Consumer<Event> listener = CbcProjectileDamageCompat::onProjectileDamage;
            eventBus.addListener(EventPriority.HIGHEST, false, eventType, listener);
            FullSteamAhead.LOGGER.info("Registered Full Steam Ahead optional Create Big Cannons projectile compatibility");
        } catch (ClassNotFoundException ignored) {
            // Create Big Cannons is optional.
        } catch (RuntimeException exception) {
            FullSteamAhead.LOGGER.warn("Unable to register optional Create Big Cannons projectile compatibility", exception);
        }
    }

    private static void onProjectileDamage(Event event) {
        try {
            Method getLevel = event.getClass().getMethod("getLevel");
            Method getPos = event.getClass().getMethod("getPos");
            Object levelObject = getLevel.invoke(event);
            Object posObject = getPos.invoke(event);
            if (!(levelObject instanceof ServerLevel serverLevel) || !(posObject instanceof BlockPos pos)) {
                return;
            }

            ruptureIfTank(serverLevel, pos);
        } catch (ReflectiveOperationException exception) {
            FullSteamAhead.LOGGER.warn("Unable to inspect Create Big Cannons projectile damage event", exception);
        }
    }

    private static void ruptureIfTank(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof FluidTankBlockEntity tank && level instanceof ServerLevel serverLevel) {
            SteamNetworkManager.ruptureBoilerFromProjectile(serverLevel, tank);
        }
    }

    private CbcProjectileDamageCompat() {
    }
}
