# Create: Full Steam Ahead

Create: Full Steam Ahead is a NeoForge 1.21.1 Create addon centered on large Create-style reciprocating steam engines for ships, aircraft, and industrial power plants.

The addon keeps Create's own Fluid Tank boiler as the boiler. Full Steam Ahead adds cylinder walls, pistons, piston heads, boiler outlets, relief valves, steam inlets, storable steam, and a hidden powered shaft that lets the assembled engine drive a normal Create shaft network.

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
- v1 engine shape: vertical `3x3x2` hollow cylinder ring, piston head, one to three piston bodies, one to three empty stroke spaces, and a horizontal Create shaft
- Engine orientation: upright engines support direct compact boiler and pipe-fed modes; upside-down engines are pipe-fed only and require an active `steam_inlet`
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

Active Create Fluid Tank boilers produce steam internally from Create's boiler data:

```text
total steam units = min(active burner units, water supply heat level) * boiler height
```

Normal Blaze Burners contribute `1` burner unit each. Blaze Cake burners contribute `2` each. Boiler height multiplies the burner footprint, so a `3x3x6` boiler with 9 normal active burners produces `54` steam units, enough for six full normal engines. With 9 Blaze Cake burners it produces `108` units, enough for twelve full pipe-fed engines.

A sealed active boiler still stores generated steam and builds pressure even when no outlet or pipe is attached. Pipe networks distribute usable steam evenly across reachable active `steam_inlet` blocks, capped at `90 mB/t` per active inlet from all sources combined, before filling passive storage tanks. If supply is short, every reachable engine receives the same share as closely as whole mB/t allows; SU scales from exact consumed mB/t. A cylinder ring may include one optional passive `steam_inlet` for visual symmetry; passive inlets connect visually but never request or consume steam.

A boiler can expose stored steam in two ways. A `boiler_outlet` is still the explicit Create-style port block. Active Create Fluid Tank boilers can also feed steam directly into Create Fluid Pipes attached to any top-layer tank face except the bottom face: the top face and all horizontal side faces are valid. Direct boiler ports and `boiler_outlet` blocks share the same boiler budget, so adding more ports splits output instead of multiplying it. Ordinary tanks that merely store `steam` do not gain this pressure source.

## Pressure Network

Boilers and pipe-fed steam networks use a per-network ideal-gas pressure model:

```text
pressure pN/m^2 = gasConstant * storedSteamMb * temperatureK / networkVolumeM3
```

- Rated pressure is `1.0 MpN/m²`; a full-flow engine at rated pressure reaches full SU and `64 RPM`.
- Engine output is gated by both pressure and delivered flow: `min(pressure factor, flow factor)`.
- Multiple boilers can feed one pipe network. Network temperature is weighted by contributed steam, not simply copied from the hottest boiler.
- A boiler with no connected steam ports is treated as its own sealed pressure network. Relief valves, goggles, Display Links, and burst logic still see its pressure.
- Multiple steam ports on one boiler, whether `boiler_outlet` blocks or direct pipe connections, split one shared boiler budget and cannot duplicate steam.
- Passive Create Fluid Tanks add pressure volume from their configured fluid capacity, so larger storage actually buffers pressure.
- Create fluid valves block steam pressure traversal. Closed valves isolate pressure instead of leaking or bypassing steam.
- Open pipe ends and unconnected outlets act as atmospheric relief. Broken pipe ends drain toward `steamPhysics.openPipeTargetPressure` (`0 pN/m²` by default); when smoothing is enabled, both stored steam and engine output fall along the same pressure curve instead of snapping off instantly.
- Boiler-mounted `steam_relief_valve` blocks protect the physical Create Fluid Tank boiler they attach to. They can mount on the top or horizontal sides of the boiler, auto-open at `2.2 MpN/m²`, close below `1.7 MpN/m²`, have a `720 mB/t` baseline vent rate, scale relief authority with active boiler production once open, and can be forced open with redstone.
- When Create Aeronautics is installed, powered `aeronautics:steam_vent` blocks consume FSA steam proportional to their live Aeronautics gas output. Vents can either sit on FSA-fed boilers or be pipe-fed by placing them on top of a Create fluid pipe carrying FSA `steam`. The default conversion is `5000 m³ -> 10 mB/t`, controlled by `aeronauticsCompat.steamVentMbPerM3`.
- Overpressure warns at `1.5 MpN/m²` and bursts at `2.5 MpN/m²`. A burst is deduped per physical boiler, depressurizes the whole connected steam network, uses `(12.0 + 0.45 * networkVolume) * 1.0` explosion power capped before scaling at `36.0`, and adds a client-side steam cloud, layered boom/hiss sounds within `200` blocks, and configurable screen shake within `150` blocks.
- Create Big Cannons projectiles that strike a Create Fluid Tank belonging to an active steam boiler force a boiler rupture. Pressure networks use current pressure and depressurize the connected steam network; active boilers without stored pressure still rupture at full burst pressure.
- On Sable/Aeronautics simulated contraptions, boiler bursts project the visual/world explosion to real-world coordinates and also apply local randomized damage to nearby sublevel blocks. Most destroyed sublevel blocks are vaporized; only sparse edge damage drops items.
- Direct compact engines still work, but remain a simplified compatibility mode. Full pressure storage, venting, and burst behavior belongs to pipe-fed networks.

Steam remains visible in Create tanks and pipes through a high-visibility tinted vanilla water render path. Unconnected boiler outlets, open pipe ends, running cylinder exhaust, and boiler bursts emit custom translucent steam particles, and leak clouds can scald entities.

## Display Link Readout

Create Display Links can read steam network data from `boiler_outlet` blocks and active Create Fluid Tank boiler controllers through the `Steam Network` source. The source supports Summary, Pressure, Safety, Flow, and Network modes from the normal Display Link configuration screen. Each mode writes one row, so multiple Display Links can target different rows on the same Display Board without overwriting each other.

## Planned Polish

- Optional volumetric steam cloud simulation for steam trapped in closed spaces.
