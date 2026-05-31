# Create: Full Steam Ahead

Create: Full Steam Ahead is a NeoForge 1.21.1 Create addon centered on large Create-style reciprocating steam engines for ships, aircraft, and industrial power plants.

The addon keeps Create's own Fluid Tank boiler as the boiler. Full Steam Ahead adds cylinder walls, pistons, piston heads, boiler outlets, steam inlets, storable steam, and a hidden powered shaft that lets the assembled engine drive a normal Create shaft network.

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
- v1 engine shape: vertical `3x3x2` hollow cylinder ring, piston head, piston body, empty stroke space, and a horizontal Create shaft
- Visual direction: close to base Create's copper, andesite, brass, shafts, gauges, and steam engine style
- Engine RPM tiers: `16`, `32`, `48`, `64`

## Steam Power Model

Steam is a real fluid and uses a simple unit model:

- `1 steam unit = 10 mB/t steam`
- `1 steam unit consumed = 16,384 SU`
- One pipe-fed engine consumes up to `9 steam units` or `90 mB/t`
- A full normal engine output is `9 units = 90 mB/t = 147,456 SU`
- In pipe-fed mode, surplus steam powers additional engines instead of making one generic steam cylinder exceed normal full output

Boiler outlets produce steam from the attached Create Fluid Tank boiler:

```text
total steam units = min(active burner units, water supply heat level) * boiler height
```

Normal Blaze Burners contribute `1` burner unit each. Blaze Cake burners contribute `2` each. Boiler height multiplies the burner footprint, so a `3x3x6` boiler with 9 normal active burners produces `54` steam units, enough for six full normal engines. With 9 Blaze Cake burners it produces `108` units, enough for twelve full pipe-fed engines. Direct compact engines still use the direct burner table, including Blaze Cake SU doubling.

Multiple `boiler_outlet` blocks on the same boiler split the same total steam budget deterministically. They do not duplicate steam output.
