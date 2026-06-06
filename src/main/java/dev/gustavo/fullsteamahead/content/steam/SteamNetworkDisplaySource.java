package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
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
        BlockEntity source = context.getSourceBlockEntity();
        if (!(source instanceof BoilerOutletBlockEntity outlet)) {
            return DisplaySource.EMPTY_LINE;
        }

        return switch (getMode(context)) {
            case MODE_PRESSURE -> pressureLines(outlet);
            case MODE_SAFETY -> safetyLines(outlet);
            case MODE_FLOW -> flowLines(outlet);
            case MODE_NETWORK -> networkLines(outlet);
            default -> fullMonitorLines(outlet);
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

    private static MutableComponent fullMonitorLines(BoilerOutletBlockEntity outlet) {
        return line("P: " + SteamPressure.format(outlet.getNetworkPressurePn()) + " | ")
                .append(statusComponent(outlet));
    }

    private static MutableComponent pressureLines(BoilerOutletBlockEntity outlet) {
        return line("Pressure: " + SteamPressure.format(outlet.getNetworkPressurePn()));
    }

    private static MutableComponent safetyLines(BoilerOutletBlockEntity outlet) {
        return line("Safety: ")
                .append(statusComponent(outlet))
                .append(line(" | Burst: " + SteamPressure.format(FullSteamConfig.steamBurstPressure())));
    }

    private static MutableComponent flowLines(BoilerOutletBlockEntity outlet) {
        return line("Flow: " + outlet.getNetworkProductionRate()
                + " -> " + outlet.getNetworkConsumedRate() + " mB/t");
    }

    private static MutableComponent networkLines(BoilerOutletBlockEntity outlet) {
        return line("Network: " + outlet.getNetworkVolume()
                + " m³ | " + outlet.getNetworkEngineCount() + " engines");
    }

    private static MutableComponent line(String text) {
        return Component.literal(text);
    }

    private static MutableComponent statusComponent(BoilerOutletBlockEntity outlet) {
        return Component.translatable("full_steam_ahead.display_source.steam_network.status."
                + outlet.getSteamNetworkStatusKey());
    }

    private static MutableComponent modeLabel(String key) {
        return Component.translatable("full_steam_ahead.display_source.steam_network." + key);
    }
}
