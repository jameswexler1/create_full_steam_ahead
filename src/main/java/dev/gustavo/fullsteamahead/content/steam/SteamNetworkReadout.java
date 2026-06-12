package dev.gustavo.fullsteamahead.content.steam;

public interface SteamNetworkReadout {
    double getNetworkPressurePn();

    int getNetworkProductionRate();

    int getNetworkConsumedRate();

    int getNetworkVolume();

    int getNetworkEngineCount();

    String getSteamNetworkStatusKey();
}
