package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class SteamNetworkDisplaySource extends DisplaySource {
    private static final int LIVE_REFRESH_TICKS = 10;
    private static final String MODE_KEY = "Mode";
    private static final int MODE_FULL_MONITOR = 0;
    private static final int MODE_PRESSURE = 1;
    private static final int MODE_SAFETY = 2;
    private static final int MODE_FLOW = 3;
    private static final int MODE_NETWORK = 4;
    private static final int MODE_MAX = 4;

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity source = context.getSourceBlockEntity();
        if (!(source instanceof BoilerOutletBlockEntity outlet)) {
            return EMPTY;
        }

        List<MutableComponent> lines = switch (getMode(context)) {
            case MODE_PRESSURE -> pressureLines(outlet);
            case MODE_SAFETY -> safetyLines(outlet);
            case MODE_FLOW -> flowLines(outlet);
            case MODE_NETWORK -> networkLines(outlet);
            default -> fullMonitorLines(outlet);
        };
        return fitRows(lines, stats);
    }

    @Override
    public int getPassiveRefreshTicks() {
        return LIVE_REFRESH_TICKS;
    }

    @Override
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isTarget) {
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

    private static List<MutableComponent> fullMonitorLines(BoilerOutletBlockEntity outlet) {
        return List.of(
                line("Pressure: " + SteamPressure.format(outlet.getNetworkPressurePn())),
                statusLine(outlet),
                line("Flow: " + outlet.getNetworkProductionRate() + " -> " + outlet.getNetworkConsumedRate() + " mB/t"),
                line("Engines: " + outlet.getNetworkEngineCount() + " connected")
        );
    }

    private static List<MutableComponent> pressureLines(BoilerOutletBlockEntity outlet) {
        return List.of(line("Pressure: " + SteamPressure.format(outlet.getNetworkPressurePn())));
    }

    private static List<MutableComponent> safetyLines(BoilerOutletBlockEntity outlet) {
        return List.of(
                statusLine(outlet),
                line("Burst at: " + SteamPressure.format(FullSteamConfig.steamBurstPressure()))
        );
    }

    private static List<MutableComponent> flowLines(BoilerOutletBlockEntity outlet) {
        return List.of(
                line("Produced: " + outlet.getNetworkProductionRate() + " mB/t"),
                line("Consumed: " + outlet.getNetworkConsumedRate() + " mB/t")
        );
    }

    private static List<MutableComponent> networkLines(BoilerOutletBlockEntity outlet) {
        return List.of(
                line("Volume: " + outlet.getNetworkVolume() + " m³"),
                line("Engines: " + outlet.getNetworkEngineCount())
        );
    }

    private static List<MutableComponent> fitRows(List<MutableComponent> lines, DisplayTargetStats stats) {
        int rows = Math.max(1, stats.maxRows());
        if (lines.size() <= rows) {
            return lines;
        }
        return new ArrayList<>(lines.subList(0, rows));
    }

    private static MutableComponent line(String text) {
        return Component.literal(text);
    }

    private static MutableComponent statusLine(BoilerOutletBlockEntity outlet) {
        return Component.literal("Status: ")
                .append(Component.translatable("full_steam_ahead.display_source.steam_network.status."
                        + outlet.getSteamNetworkStatusKey()));
    }

    private static MutableComponent modeLabel(String key) {
        return Component.translatable("full_steam_ahead.display_source.steam_network." + key);
    }
}
