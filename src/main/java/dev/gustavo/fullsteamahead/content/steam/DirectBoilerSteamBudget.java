package dev.gustavo.fullsteamahead.content.steam;

public record DirectBoilerSteamBudget(
        int totalProductionMb,
        int portCount,
        int boilerVolume,
        int temperatureK,
        boolean lit
) {
}
