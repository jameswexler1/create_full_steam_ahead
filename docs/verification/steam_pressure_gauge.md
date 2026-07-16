# Steam Pressure Gauge Verification

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `git diff --check`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew runClient` completed mod initialization, model baking, texture-atlas creation, and shader loading with no Full Steam Ahead model/resource errors; the smoke client was then stopped intentionally.
- [x] Coordinate audit confirms all 35 source elements match the centered item model, wall-shifted static model, and wall-shifted needle partial; all four saved facings preserve their pre-fix world geometry.

Verified on 2026-07-16. Compilation, generated resources, JSON syntax, and the assembled mod jar pass; in-world rendering and interaction checks remain below.

## Implementation Contract

- [x] A gauge item captures the exact clicked tank block only when it belongs to an active or residually pressurized boiler.
- [x] The item stores a dimension-aware source and the placed block stores a relative offset plus link orientation.
- [x] Each reading resolves the selected tank's current controller and uses the existing `SteamNetworkReadout` pressure.
- [x] Source chunks are never force-loaded; unavailable sources render toward zero.
- [x] Client interpolation is independent of the server polling cadence.
- [x] The needle's full 270-degree sweep represents zero through configured burst pressure.
- [x] The editable source, static world model, animated needle partial, item model, blockstates, and hitboxes use one north-facing local coordinate system; the world geometry retains a sub-pixel anti-flicker gap against its backing surface.
- [x] The needle rotates around its local model pivot before the complete posed partial is rotated into its north, south, east, or west world facing.
- [x] The default dial art maps the 1.5 MpN/m^2 warning point to 60% of the sweep, the 2.2 MpN/m^2 relief point near 88%, and the 2.5 MpN/m^2 burst point to the final deep-red stop.

## Manual In-World Checks

- [ ] Confirm Steam Pressure Gauge appears front-facing in the Full Steam Ahead creative tab, inventory, and hotbar with a complete, correctly scaled model.
- [ ] Build and heat a valid boiler, then sneak-right-click any tank block with the gauge item; confirm the item shimmers and reports a selected source.
- [ ] Place several gauges from that stack; confirm every gauge remains linked and the stack retains its selection.
- [ ] Confirm ordinary right-click still places the gauge and sneak-use in air clears the selected source.
- [ ] Confirm an ordinary unheated/non-boiler Fluid Tank is rejected as a new source.
- [ ] Raise and lower boiler pressure; compare the needle and goggle value with the boiler and a Display Link.
- [ ] Confirm zero pressure starts at the lower-left stop, increasing pressure moves clockwise through the top, burst pressure ends at the lower-right stop, and overpressure does not rotate beyond the dial.
- [ ] Confirm the unused cream gap is at the bottom of the dial, amber begins near 1.5 MpN/m^2, orange appears near relief pressure, and the needle reaches deep red only at approximately 2.5 MpN/m^2.
- [ ] Cut heat and drain residual pressure; confirm the gauge remains linked and follows pressure smoothly to zero.
- [ ] Resize the boiler or cause Create to choose another controller while preserving the clicked tank; confirm the gauge continues reading it.
- [ ] Break the selected tank or unload its chunk; confirm the needle eases to zero and goggles report the source unavailable.
- [ ] Restore the exact tank/source chunk; confirm the reading recovers without replacing the gauge.
- [ ] Place gauges against north, south, east, and west wall faces; confirm the rear housing is flush to the clicked wall and the dial faces away from it.
- [ ] Place gauges on the floor while facing north, south, east, and west; confirm the dial faces the player and remains on the correct backing side of its block.
- [ ] Confirm housing, animated needle, outline, and collision shape occupy the same thin wall-adjacent volume in every facing.
- [ ] Wrench a linked gauge through all four directions; confirm it still reads the same stationary boiler.
- [ ] Save/reload and unload/reload the gauge chunk; confirm its source and pressure recover.
- [ ] Assemble a gauge and its boiler on one Sable/Aeronautics simulated contraption, rotate it, and confirm the relative link survives assembly and disassembly.
- [ ] Move only the gauge or only the boiler in a separate contraption; confirm the old link becomes unavailable rather than finding an unrelated tank.
