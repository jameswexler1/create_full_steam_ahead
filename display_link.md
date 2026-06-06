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

Display Board targets treat normal `DisplaySource` instances as multi-row writers. If a source
returns fewer rows than before, Create clears the following rows for that link. Therefore the steam
readout extends Create's `SingleLineDisplaySource`: one Display Link owns exactly one board row and
multiple configured lines can update independently without erasing each other.

## Implemented Source

Source id: `full_steam_ahead:steam_network`

Source name: `Steam Network`

Associated block entity: `boiler_outlet`

The source is read-only. It reads cached values from `BoilerOutletBlockEntity` and does not tick,
recalculate, mutate, vent, or move steam.

The source overrides Create's default passive refresh interval from 100 ticks to 5 ticks, so
pressure and flow telemetry updates roughly four times per second.

## Modes

The Display Link source configuration stores `Mode`:

- `0` — Summary
- `1` — Pressure
- `2` — Safety
- `3` — Flow
- `4` — Network

Each mode emits one line:

```text
Summary:
P: 1.02 MpN/m² | Stable

Pressure:
Pressure: 1.02 MpN/m²

Safety:
Safety: Stable | Burst: 2.50 MpN/m²

Flow:
Flow: 540 -> 540 mB/t

Network:
Network: 18 m³ | 6 engines
```

To build a multi-line display board, place multiple Display Links, target different display rows,
and configure each link to a different mode.

## Manual Verification

1. Place a valid Create boiler, `boiler_outlet`, pipe network, `steam_inlet`, and running engine.
2. Place a Create Display Link on the outlet and link it to a Display Board.
3. Confirm `Steam Network` appears as a source.
4. Cycle the source mode through all five options.
5. Link several Display Links to different rows of one Display Board and configure different modes.
6. Confirm pressure, flow, volume, engine count, and status update as the network changes.
7. Confirm updating one row does not clear or overwrite other rows.
8. Confirm target-side labels still work for rows that need a prefix.
9. Confirm reads do not affect pressure, steam amount, venting, or engine output.
10. Reload the world and confirm the selected mode remains.
