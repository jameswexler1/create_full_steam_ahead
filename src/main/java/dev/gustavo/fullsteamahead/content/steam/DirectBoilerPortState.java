package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.config.FullSteamConfig;

public final class DirectBoilerPortState {
    public int storedMb;
    public int productionMb;
    public int totalProductionMb;
    public int portCount;
    public int pushedMb;
    public int externallyDrainedSteam;
    public int boilerVolume = 1;
    public int temperatureK = (int) Math.round(FullSteamConfig.steamTemperatureBaseK());
    public boolean lit;
    public boolean venting;
    public double pressurePn;
    public boolean networkVenting;
    public boolean networkWarn;
    public int networkProductionMb;
    public int networkVolume;
    public int networkEngines;
    public int networkConsumedMb;
}
