# Steam Pressure Gauge Verification

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `git diff --check`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew runClient` completed mod initialization, model baking, texture-atlas creation, and shader loading with no Full Steam Ahead model/resource errors; the smoke client was then stopped intentionally.

Verified on 2026-07-14. Compilation, generated resources, JSON syntax, and the assembled mod jar pass; in-world rendering and interaction checks remain below.

## Implementation Contract

- [x] A gauge item captures the exact clicked tank block only when it belongs to an active or residually pressurized boiler.
- [x] The item stores a dimension-aware source and the placed block stores a relative offset plus link orientation.
- [x] Each reading resolves the selected tank's current controller and uses the existing `SteamNetworkReadout` pressure.
- [x] Source chunks are never force-loaded; unavailable sources render toward zero.
- [x] Client interpolation is independent of the server polling cadence.
- [x] The needle's full 270-degree sweep represents zero through configured burst pressure.
- [x] The static model, animated needle partial, item model, and three textures are compiled from `new_models/steam_pressure_gauge.bbmodel` without altering its geometry or UVs.

## Manual In-World Checks

- [ ] Confirm Steam Pressure Gauge appears in the Full Steam Ahead creative tab with a complete, correctly scaled inventory model.
- [ ] Build and heat a valid boiler, then sneak-right-click any tank block with the gauge item; confirm the item shimmers and reports a selected source.
- [ ] Place several gauges from that stack; confirm every gauge remains linked and the stack retains its selection.
- [ ] Confirm ordinary right-click still places the gauge and sneak-use in air clears the selected source.
- [ ] Confirm an ordinary unheated/non-boiler Fluid Tank is rejected as a new source.
- [ ] Raise and lower boiler pressure; compare the needle and goggle value with the boiler and a Display Link.
- [ ] Confirm zero pressure is the low stop, burst pressure is the high stop, and overpressure does not rotate beyond the dial.
- [ ] Cut heat and drain residual pressure; confirm the gauge remains linked and follows pressure smoothly to zero.
- [ ] Resize the boiler or cause Create to choose another controller while preserving the clicked tank; confirm the gauge continues reading it.
- [ ] Break the selected tank or unload its chunk; confirm the needle eases to zero and goggles report the source unavailable.
- [ ] Restore the exact tank/source chunk; confirm the reading recovers without replacing the gauge.
- [ ] Place gauges facing north, south, east, and west; confirm housing, needle, and model-derived hitbox stay aligned.
- [ ] Wrench a linked gauge through all four directions; confirm it still reads the same stationary boiler.
- [ ] Save/reload and unload/reload the gauge chunk; confirm its source and pressure recover.
- [ ] Assemble a gauge and its boiler on one Sable/Aeronautics simulated contraption, rotate it, and confirm the relative link survives assembly and disassembly.
- [ ] Move only the gauge or only the boiler in a separate contraption; confirm the old link becomes unavailable rather than finding an unrelated tank.
