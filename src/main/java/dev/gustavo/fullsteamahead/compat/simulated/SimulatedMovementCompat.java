package dev.gustavo.fullsteamahead.compat.simulated;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.compat.movement.FullSteamMovementRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

public final class SimulatedMovementCompat {
    private static final String SIMULATED_MOVEMENT_CHECKS =
            "dev.simulated_team.simulated.index.SimBlockMovementChecks";
    private static boolean registered;

    public static void registerIfPresent() {
        if (registered) {
            return;
        }

        registered = true;
        if (!isSimulatedStackPresent()) {
            return;
        }

        try {
            Class<?> checksClass = Class.forName(SIMULATED_MOVEMENT_CHECKS);
            registerAttachedCheck(checksClass);
            registerAdditionalBlocks(checksClass);
            FullSteamAhead.LOGGER.info("Registered Full Steam Ahead optional Simulated movement checks");
        } catch (ClassNotFoundException exception) {
            FullSteamAhead.LOGGER.info("Simulated movement API is not present; skipping optional movement checks");
        } catch (ReflectiveOperationException | LinkageError exception) {
            FullSteamAhead.LOGGER.warn("Unable to register Full Steam Ahead Simulated movement checks", exception);
        }
    }

    private static void registerAttachedCheck(Class<?> checksClass) throws ReflectiveOperationException {
        Class<?> attachedCheckClass = Class.forName(SIMULATED_MOVEMENT_CHECKS + "$AttachedCheck");
        Object proxy = Proxy.newProxyInstance(
                attachedCheckClass.getClassLoader(),
                new Class<?>[]{attachedCheckClass},
                (ignoredProxy, method, args) -> {
                    Object objectResult = handleObjectMethod(ignoredProxy, method, args);
                    if (objectResult != null) {
                        return objectResult;
                    }

                    if ("isBlockAttachedTowards".equals(method.getName()) && args.length == 4) {
                        return FullSteamMovementRules.isBlockAttachedTowards(
                                (BlockState) args[0],
                                (Level) args[1],
                                (BlockPos) args[2],
                                (BlockPos) args[3]
                        );
                    }

                    return BlockMovementChecks.CheckResult.PASS;
                }
        );

        Method register = checksClass.getMethod("registerAttachedCheck", attachedCheckClass);
        register.invoke(null, proxy);
    }

    private static void registerAdditionalBlocks(Class<?> checksClass) throws ReflectiveOperationException {
        Class<?> additionalBlocksClass = Class.forName(SIMULATED_MOVEMENT_CHECKS + "$AdditionalBlocks");
        Object proxy = Proxy.newProxyInstance(
                additionalBlocksClass.getClassLoader(),
                new Class<?>[]{additionalBlocksClass},
                (ignoredProxy, method, args) -> {
                    Object objectResult = handleObjectMethod(ignoredProxy, method, args);
                    if (objectResult != null) {
                        return objectResult;
                    }

                    if ("addAdditionalBlocks".equals(method.getName()) && args.length == 4) {
                        return FullSteamMovementRules.addAdditionalBlocks(
                                (BlockState) args[0],
                                (Level) args[1],
                                (BlockPos) args[2],
                                castVisited(args[3])
                        );
                    }

                    return Set.of();
                }
        );

        Method register = checksClass.getMethod("registerAdditionalBlocks", additionalBlocksClass);
        register.invoke(null, proxy);
    }

    @SuppressWarnings("unchecked")
    private static Set<BlockPos> castVisited(Object visited) {
        return (Set<BlockPos>) visited;
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() != Object.class) {
            return null;
        }

        return switch (method.getName()) {
            case "toString" -> "FullSteamAheadSimulatedMovementCheck";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        };
    }

    private static boolean isSimulatedStackPresent() {
        ModList modList = ModList.get();
        return modList.isLoaded("simulated") || modList.isLoaded("aeronautics") || modList.isLoaded("sable");
    }

    private SimulatedMovementCompat() {
    }
}
