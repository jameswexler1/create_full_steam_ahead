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
- Engine orientation: upright engines support direct compact boiler and pipe-fed modes; upside-down engines are pipe-fed only and require a `steam_inlet`
- Inline engine banks: adjacent same-orientation cylinder rings may share ordinary `steam_cylinder` wall blocks; shared walls are a blockstate/visual form, not a separate block
- Cylinder construction visuals: straight-only wall runs stay fence-like; once a local corner implies a ring, the existing assembled partial section models are assigned from that stable corner origin
- Visual direction: close to base Create's copper, andesite, brass, shafts, gauges, and steam engine style
- Engine RPM tiers: `16`, `32`, `48`, `64`

## Steam Power Model

Steam is a real fluid. Production still uses the readable Create-style unit scale:

- `1 steam unit = 10 mB/t steam`
- `1 steam unit consumed = 16,384 SU`
- One pipe-fed engine consumes up to `9 steam units` or `90 mB/t`
- A full normal engine output is `9 units = 90 mB/t = 147,456 SU` at `64 RPM`

Boiler outlets produce steam from the attached Create Fluid Tank boiler:

```text
total steam units = min(active burner units, water supply heat level) * boiler height
```

Normal Blaze Burners contribute `1` burner unit each. Blaze Cake burners contribute `2` each. Boiler height multiplies the burner footprint, so a `3x3x6` boiler with 9 normal active burners produces `54` steam units, enough for six full normal engines. With 9 Blaze Cake burners it produces `108` units, enough for twelve full pipe-fed engines.

Pipe networks distribute usable steam evenly across reachable assembled `steam_inlet` blocks, capped at `90 mB/t` per inlet from all sources combined, before filling passive storage tanks. If supply is short, every reachable engine receives the same share as closely as whole mB/t allows; SU scales from exact consumed mB/t.

## Pressure Network

Pipe-fed steam networks use a per-network ideal-gas pressure model:

```text
pressure pN/m^2 = gasConstant * storedSteamMb * temperatureK / networkVolumeM3
```

- Rated pressure is `1.0 MpN/m²`; a full-flow engine at rated pressure reaches full SU and `64 RPM`.
- Engine output is gated by both pressure and delivered flow: `min(pressure factor, flow factor)`.
- Multiple boilers can feed one pipe network. Network temperature is weighted by contributed steam, not simply copied from the hottest boiler.
- Multiple `boiler_outlet` blocks on one boiler split one shared boiler budget and cannot duplicate steam.
- Passive Create Fluid Tanks add pressure volume from their configured fluid capacity, so larger storage actually buffers pressure.
- Create fluid valves block steam pressure traversal. Closed valves isolate pressure instead of leaking or bypassing steam.
- Open pipe ends and unconnected outlets act as atmospheric relief. Broken pipe ends drain toward `steamPhysics.openPipeTargetPressure` (`0 pN/m²` by default); when smoothing is enabled, both stored steam and engine output fall along the same pressure curve instead of snapping off instantly.
- Overpressure warns at `1.5 MpN/m²` and bursts at `2.5 MpN/m²`. A burst is deduped per physical boiler, depressurizes the whole connected steam network, and uses `12.0 + 0.45 * networkVolume` explosion power capped at `36.0`.
- Direct compact engines still work, but remain a simplified compatibility mode. Full pressure storage, venting, and burst behavior belongs to pipe-fed networks.

Steam remains visible in Create tanks and pipes through a high-visibility tinted vanilla water render path. Unconnected boiler outlets, open pipe ends, and running cylinder exhaust emit custom translucent steam particles, and leak clouds can scald entities.

## Display Link Readout

Create Display Links can read `boiler_outlet` steam network data through the `Steam Network` source. The source supports Summary, Pressure, Safety, Flow, and Network modes from the normal Display Link configuration screen. Each mode writes one row, so multiple Display Links can target different rows on the same Display Board without overwriting each other.

## Planned Polish

- Future direct pipe-to-boiler support so active Create Fluid Tank boilers can expose steam without a dedicated `boiler_outlet`.
- Optional volumetric steam cloud simulation for steam trapped in closed spaces.
