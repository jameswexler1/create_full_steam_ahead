# Display Link Steam Network Readout

## Create API Findings

Create 6.0.10 exposes `DisplaySource` through `CreateBuiltInRegistries.DISPLAY_SOURCE`.
Display sources are associated with source blocks or block entities through `DisplaySource.BY_BLOCK`
and `DisplaySource.BY_BLOCK_ENTITY`.

Create sources can add their own configuration controls by overriding:

```java
initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isTarget)
```

The selected values are stored in `DisplayLinkContext.sourceConfig()`. Create's own
`KineticStressDisplaySource` uses this pattern with a `Mode` selection scroll input, so Full Steam
Ahead uses the same approach rather than adding custom wrench or block cycling behavior.

## Implemented Source

Source id: `full_steam_ahead:steam_network`

Source name: `Steam Network`

Associated block entity: `boiler_outlet`

The source is read-only. It reads cached values from `BoilerOutletBlockEntity` and does not tick,
recalculate, mutate, vent, or move steam.

## Modes

The Display Link source configuration stores `Mode`:

- `0` — Full Monitor
- `1` — Pressure
- `2` — Safety
- `3` — Flow
- `4` — Network

Displayed lines:

```text
Full Monitor:
Pressure: 1.02 MpN/m²
Status: Stable
Flow: 540 -> 540 mB/t
Engines: 6 connected

Pressure:
Pressure: 1.02 MpN/m²

Safety:
Status: Stable
Burst at: 2.50 MpN/m²

Flow:
Produced: 540 mB/t
Consumed: 540 mB/t

Network:
Volume: 18 m³
Engines: 6
```

If a target has fewer rows than the selected mode provides, only the first rows are sent.

## Manual Verification

1. Place a valid Create boiler, `boiler_outlet`, pipe network, `steam_inlet`, and running engine.
2. Place a Create Display Link on the outlet and link it to a Display Board.
3. Confirm `Steam Network` appears as a source.
4. Cycle the source mode through all five options.
5. Confirm pressure, flow, volume, engine count, and status update as the network changes.
6. Confirm reads do not affect pressure, steam amount, venting, or engine output.
7. Reload the world and confirm the selected mode remains.
