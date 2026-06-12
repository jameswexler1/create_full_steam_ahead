package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class SteamNetworkDisplaySource extends SingleLineDisplaySource {
    private static final int LIVE_REFRESH_TICKS = 5;
    private static final String MODE_KEY = "Mode";
    private static final int MODE_FULL_MONITOR = 0;
    private static final int MODE_PRESSURE = 1;
    private static final int MODE_SAFETY = 2;
    private static final int MODE_FLOW = 3;
    private static final int MODE_NETWORK = 4;
    private static final int MODE_MAX = 4;

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        SteamNetworkReadout readout = readoutFrom(context.getSourceBlockEntity());
        if (readout == null) {
            return DisplaySource.EMPTY_LINE;
        }

        return switch (getMode(context)) {
            case MODE_PRESSURE -> pressureLines(readout);
            case MODE_SAFETY -> safetyLines(readout);
            case MODE_FLOW -> flowLines(readout);
            case MODE_NETWORK -> networkLines(readout);
            default -> fullMonitorLines(readout);
        };
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return LIVE_REFRESH_TICKS;
    }

    @Override
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isTarget) {
        super.initConfigurationWidgets(context, builder, isTarget);
        if (isTarget) {
            return;
        }

        builder.addSelectionScrollInput(0, 120, (input, label) -> input
                .forOptions(List.of(
                        modeLabel("full_monitor"),
                        modeLabel("pressure"),
                        modeLabel("safety"),
                        modeLabel("flow"),
                        modeLabel("network")
                ))
                .titled(Component.translatable("full_steam_ahead.display_source.steam_network.display")), MODE_KEY);
    }

    private int getMode(DisplayLinkContext context) {
        int mode = context.sourceConfig().getInt(MODE_KEY);
        if (mode < 0 || mode > MODE_MAX) {
            return MODE_FULL_MONITOR;
        }
        return mode;
    }

    private static SteamNetworkReadout readoutFrom(BlockEntity blockEntity) {
        if (blockEntity instanceof FluidTankBlockEntity tank) {
            FluidTankBlockEntity controller = tank.getControllerBE();
            if (controller instanceof SteamNetworkReadout readout) {
                return readout;
            }
            if (tank instanceof SteamNetworkReadout readout) {
                return readout;
            }
        }
        if (blockEntity instanceof SteamNetworkReadout readout) {
            return readout;
        }
        return null;
    }

    private static MutableComponent fullMonitorLines(SteamNetworkReadout readout) {
        return line("P: " + SteamPressure.format(readout.getNetworkPressurePn()) + " | ")
                .append(statusComponent(readout));
    }

    private static MutableComponent pressureLines(SteamNetworkReadout readout) {
        return line("Pressure: " + SteamPressure.format(readout.getNetworkPressurePn()));
    }

    private static MutableComponent safetyLines(SteamNetworkReadout readout) {
        return line("Safety: ")
                .append(statusComponent(readout))
                .append(line(" | Burst: " + SteamPressure.format(FullSteamConfig.steamBurstPressure())));
    }

    private static MutableComponent flowLines(SteamNetworkReadout readout) {
        return line("Flow: " + readout.getNetworkProductionRate()
                + " -> " + readout.getNetworkConsumedRate() + " mB/t");
    }

    private static MutableComponent networkLines(SteamNetworkReadout readout) {
        return line("Network: " + readout.getNetworkVolume()
                + " m³ | " + readout.getNetworkEngineCount() + " engines");
    }

    private static MutableComponent line(String text) {
        return Component.literal(text);
    }

    private static MutableComponent statusComponent(SteamNetworkReadout readout) {
        return Component.translatable("full_steam_ahead.display_source.steam_network.status."
                + readout.getSteamNetworkStatusKey());
    }

    private static MutableComponent modeLabel(String key) {
        return Component.translatable("full_steam_ahead.display_source.steam_network." + key);
    }
}
