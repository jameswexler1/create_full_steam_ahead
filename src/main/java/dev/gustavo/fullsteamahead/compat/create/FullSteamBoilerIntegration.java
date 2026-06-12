package dev.gustavo.fullsteamahead.compat.create;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import dev.gustavo.fullsteamahead.content.piston.EngineValidator;
import dev.gustavo.fullsteamahead.content.steam.BoilerOutletBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamPipeUtil;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FullSteamBoilerIntegration {
    public static final int MAX_COMPACT_HEAT_LEVEL = 18;

    /**
     * Usable boiler heat units = min(burner heat under the footprint, water-limited heat). A normal lit
     * blaze burner counts 1, a blaze-cake (seething) burner counts 2; unfired burners count 0.
     */
    public static int usableHeatUnits(FluidTankBlockEntity controller) {
        if (controller == null) {
            return 0;
        }
        Level level = controller.getLevel();
        if (level == null || controller.boiler == null) {
            return 0;
        }

        BlockPos origin = controller.getBlockPos();
        int width = Math.max(1, controller.getWidth());
        int burnerHeat = 0;
        int y = origin.getY() - 1;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                BlockPos burnerPos = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
                if (!level.isLoaded(burnerPos)) {
                    continue;
                }
                BlazeBurnerBlock.HeatLevel heat = BlazeBurnerBlock.getHeatLevelOf(level.getBlockState(burnerPos));
                if (!heat.isAtLeast(BlazeBurnerBlock.HeatLevel.FADING)) {
                    continue;
                }
                burnerHeat += heat.isAtLeast(BlazeBurnerBlock.HeatLevel.SEETHING) ? 2 : 1;
            }
        }

        int waterCap = controller.boiler.getMaxHeatLevelForWaterSupply();
        return Math.max(0, Math.min(burnerHeat, waterCap));
    }

    public static int countAttachedEngines(FluidTankBlockEntity boiler) {
        FluidTankBlockEntity controller = resolveController(boiler);
        if (controller == null) {
            return 0;
        }

        Level level = controller.getLevel();
        if (level == null) {
            return 0;
        }

        return countAttachedDirectEngines(controller) + countAttachedSteamPorts(controller);
    }

    private static int countAttachedDirectEngines(FluidTankBlockEntity controller) {
        Level level = controller.getLevel();
        int width = controller.getWidth();
        int height = controller.getHeight();
        if (width < 3 || height < 1) {
            return 0;
        }

        BlockPos controllerPos = controller.getBlockPos();
        Set<BlockPos> engines = new HashSet<>();
        for (int x = 0; x <= width - 3; x++) {
            for (int z = 0; z <= width - 3; z++) {
                BlockPos ringOrigin = controllerPos.offset(x, height, z);
                BlockPos enginePos = ringOrigin.offset(1, 0, 1);
                if (!level.isLoaded(enginePos)
                        || !level.getBlockState(enginePos).is(ModBlocks.PISTON_HEAD.get())) {
                    continue;
                }

                EngineValidator.Result result = EngineValidator.validate(level, enginePos);
                if (result.valid() && controllerPos.equals(result.boilerPos())) {
                    engines.add(enginePos);
                }
            }
        }

        return engines.size();
    }

    public static int countAttachedOutlets(FluidTankBlockEntity controller) {
        return attachedOutletPositions(controller).size();
    }

    public static int countAttachedSteamPorts(FluidTankBlockEntity controller) {
        return attachedSteamPorts(controller).size();
    }

    public static int steamUnitsForOutlet(FluidTankBlockEntity controller, BlockPos outletPos, int totalSteamUnits) {
        return amountForOutlet(controller, outletPos, totalSteamUnits);
    }

    public static int amountForOutlet(FluidTankBlockEntity controller, BlockPos outletPos, int totalAmount) {
        if (totalAmount <= 0) {
            return 0;
        }

        List<BoilerSteamPort> ports = attachedSteamPorts(controller);
        BoilerSteamPort outlet = ports.stream()
                .filter(port -> port.type() == BoilerSteamPort.Type.PHYSICAL_OUTLET && port.pos().equals(outletPos))
                .findFirst()
                .orElse(null);
        if (outlet == null) {
            return 0;
        }
        return amountForPort(ports, outlet, totalAmount);
    }

    public static int amountForPort(FluidTankBlockEntity controller, BoilerSteamPort port, int totalAmount) {
        if (totalAmount <= 0) {
            return 0;
        }

        List<BoilerSteamPort> ports = attachedSteamPorts(controller);
        if (ports.isEmpty()) {
            return 0;
        }

        int index = ports.indexOf(port);
        if (index < 0) {
            return 0;
        }

        return amountForPort(ports, index, totalAmount);
    }

    private static int amountForPort(List<BoilerSteamPort> ports, BoilerSteamPort port, int totalAmount) {
        int index = ports.indexOf(port);
        if (index < 0) {
            return 0;
        }
        return amountForPort(ports, index, totalAmount);
    }

    private static int amountForPort(List<BoilerSteamPort> ports, int index, int totalAmount) {
        int baseShare = totalAmount / ports.size();
        int remainder = totalAmount % ports.size();
        return baseShare + (index < remainder ? 1 : 0);
    }

    public static List<BoilerSteamPort> attachedSteamPorts(FluidTankBlockEntity controller) {
        List<BoilerSteamPort> ports = new ArrayList<>();
        ports.addAll(attachedOutletPorts(controller));
        ports.addAll(attachedDirectPipePorts(controller));
        ports.sort(Comparator.naturalOrder());
        return ports;
    }

    public static List<BoilerSteamPort> attachedOutletPorts(FluidTankBlockEntity controller) {
        controller = resolveController(controller);
        if (controller == null) {
            return List.of();
        }

        Level level = controller.getLevel();
        if (level == null) {
            return List.of();
        }

        List<BoilerSteamPort> ports = new ArrayList<>();
        for (BlockPos outletPos : attachedOutletPositions(controller)) {
            if (!level.isLoaded(outletPos)) {
                continue;
            }
            BlockState state = level.getBlockState(outletPos);
            if (state.is(ModBlocks.BOILER_OUTLET.get())) {
                ports.add(BoilerSteamPort.outlet(outletPos, BoilerOutletBlock.getFacing(state)));
            }
        }
        ports.sort(Comparator.naturalOrder());
        return ports;
    }

    public static List<BoilerSteamPort> attachedDirectPipePorts(FluidTankBlockEntity controller) {
        controller = resolveController(controller);
        if (controller == null || !FullSteamConfig.directBoilerPipeOutputEnabled()) {
            return List.of();
        }

        Level level = controller.getLevel();
        if (level == null || controller.boiler == null) {
            return List.of();
        }

        BlockPos controllerPos = controller.getBlockPos();
        int width = Math.max(1, controller.getWidth());
        int height = Math.max(1, controller.getHeight());
        int y = controllerPos.getY() + height - 1;
        Set<BoilerSteamPort> ports = new HashSet<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                BlockPos tankPos = new BlockPos(controllerPos.getX() + x, y, controllerPos.getZ() + z);
                for (Direction direction : Direction.values()) {
                    if (!isDirectSteamFace(direction)) {
                        continue;
                    }
                    BoilerSteamPort port = directPipePortAt(level, tankPos, direction);
                    if (port != null && controllerPos.equals(resolveDirectPortController(level, port))) {
                        ports.add(port);
                    }
                }
            }
        }

        List<BoilerSteamPort> sorted = new ArrayList<>(ports);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    public static BoilerSteamPort directPipePortAt(Level level, BlockPos tankPos, Direction outputDirection) {
        if (level == null
                || !FullSteamConfig.directBoilerPipeOutputEnabled()
                || outputDirection == null
                || !isDirectSteamFace(outputDirection)
                || !level.isLoaded(tankPos)) {
            return null;
        }
        if (!(level.getBlockEntity(tankPos) instanceof FluidTankBlockEntity tank)) {
            return null;
        }

        FluidTankBlockEntity controller = resolveController(tank);
        if (controller == null || controller.boiler == null || !isTopLayerTank(controller, tankPos)) {
            return null;
        }

        BlockPos pipePos = tankPos.relative(outputDirection);
        if (!level.isLoaded(pipePos)) {
            return null;
        }

        FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pipePos);
        if (pipe == null) {
            return null;
        }
        BlockState pipeState = level.getBlockState(pipePos);
        if (!SteamPipeUtil.canSteamPassThrough(pipe, pipeState, outputDirection.getOpposite())) {
            return null;
        }
        return BoilerSteamPort.directPipe(tankPos, outputDirection);
    }

    public static BlockPos resolveDirectPortController(Level level, BoilerSteamPort port) {
        if (level == null || port == null || port.type() != BoilerSteamPort.Type.DIRECT_PIPE
                || !level.isLoaded(port.pos())) {
            return null;
        }
        if (!(level.getBlockEntity(port.pos()) instanceof FluidTankBlockEntity tank)) {
            return null;
        }
        FluidTankBlockEntity controller = resolveController(tank);
        return controller == null ? null : controller.getBlockPos();
    }

    public static List<BlockPos> attachedOutletPositions(FluidTankBlockEntity controller) {
        controller = resolveController(controller);
        if (controller == null) {
            return List.of();
        }

        Level level = controller.getLevel();
        if (level == null) {
            return List.of();
        }

        BlockPos controllerPos = controller.getBlockPos();
        int width = controller.getWidth();
        int height = controller.getHeight();

        Set<BlockPos> outlets = new HashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    BlockPos tankPos = controllerPos.offset(x, y, z);
                    for (Direction direction : Direction.values()) {
                        BlockPos outletPos = tankPos.relative(direction);
                        if (!level.isLoaded(outletPos)) {
                            continue;
                        }

                        BlockState state = level.getBlockState(outletPos);
                        if (!state.is(ModBlocks.BOILER_OUTLET.get())
                                || BoilerOutletBlock.getFacing(state) != direction) {
                            continue;
                        }

                        // Boiler visuals must not depend on the outlet block entity having refreshed
                        // its cached controller yet. Create's own boiler scan is geometric, so match
                        // that behaviour: a correctly facing outlet adjacent to this tank footprint
                        // counts as an attached boiler device immediately.
                        outlets.add(outletPos);
                    }
                }
            }
        }

        List<BlockPos> sorted = new ArrayList<>(outlets);
        sorted.sort(Comparator
                .comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(pos -> pos.getX())
                .thenComparingInt(pos -> pos.getZ()));
        return sorted;
    }

    private static boolean isDirectSteamFace(Direction direction) {
        return direction == Direction.UP || direction.getAxis().isHorizontal();
    }

    private static boolean isTopLayerTank(FluidTankBlockEntity controller, BlockPos tankPos) {
        BlockPos controllerPos = controller.getBlockPos();
        int width = Math.max(1, controller.getWidth());
        int height = Math.max(1, controller.getHeight());
        return tankPos.getY() == controllerPos.getY() + height - 1
                && tankPos.getX() >= controllerPos.getX()
                && tankPos.getX() < controllerPos.getX() + width
                && tankPos.getZ() >= controllerPos.getZ()
                && tankPos.getZ() < controllerPos.getZ() + width;
    }

    public static int compactBoilerHeatLimit(int tankSize) {
        return Math.min(MAX_COMPACT_HEAT_LEVEL, tankSize);
    }

    private static FluidTankBlockEntity resolveController(FluidTankBlockEntity tank) {
        if (tank == null) {
            return null;
        }
        FluidTankBlockEntity controller = tank.getControllerBE();
        return controller == null ? tank : controller;
    }

    private FullSteamBoilerIntegration() {
    }
}
