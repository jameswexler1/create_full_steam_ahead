# Create: Full Steam Ahead

Create: Full Steam Ahead is a NeoForge 1.21.1 Create addon planned around large 3x3x3 multiblock steam engines for ships and aircraft.

Phase one is a buildable project scaffold only. Gameplay blocks, multiblock validation, kinetic output, and Aeronautics/Sable compatibility logic start in later phases.

## Locked Targets

- Mod id: `full_steam_ahead`
- Java package: `dev.gustavo.fullsteamahead`
- Minecraft: `1.21.1`
- NeoForge: `21.1.230`
- Create: `6.0.10-280`
- Java: `21`
- License: `MIT`

## Build

```sh
./gradlew build
```

Use a Java 21 runtime. The first build downloads and prepares the NeoForge Minecraft artifacts, so it can take several minutes.

## Design Defaults

- Required runtime mod: Create
- Optional runtime compatibility targets: Sable and Create Aeronautics
- v1 multiblock cap: `3x3x3`, maximum `27` blocks
- Visual direction: close to base Create's copper, andesite, brass, shafts, gauges, and steam engine style
- Initial RPM options: `16`, `32`, `48`, `64`; default `32`
