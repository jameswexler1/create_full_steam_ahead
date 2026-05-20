# Phase 5 Verification

Date: 2026-05-20

Implemented:

- Registered storable `steam` fluid with NeoForge fluid APIs.
- Kept `steam` non-placeable and no-bucket for now.
- Added `boiler_outlet` block, item, block entity, resources, tags, lang, and creative tab entry.
- `boiler_outlet` attaches by facing away from a Create Fluid Tank boiler.
- `BoilerOutletBlockEntity` reads the attached boiler controller's `BoilerData`.
- Valid boiler outlets count as attached boiler devices for Create boiler visuals and compact sizing.
- Steam generation requires active boiler heat and water supply.
- Outlet exposes output-only `IFluidHandler`; it does not accept steam input.
- Outlet pushes generated steam through a bounded Create pipe traversal up to 30 blocks.
- Goggles show outlet status, steam buffer, production, pushed amount, and pressure range.

Automated checks run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
find src/main/resources -name '*.json' -exec jq empty {} +
```

Results:

- `compileJava`: passed
- `processResources`: passed
- `build`: passed
- JSON validation: passed

Manual runtime checklist:

- [ ] Run `./gradlew runClient`.
- [ ] Confirm `Boiler Outlet` appears in the creative tab.
- [ ] Place `boiler_outlet` on a Create Fluid Tank face and confirm it is placeable without crashing.
- [ ] With no active boiler heat or no water supply, confirm goggles show no steam production.
- [ ] Fire the boiler with water supply and confirm the outlet buffer fills with `steam`.
- [ ] Connect Create fluid pipes from the outlet to a Create Fluid Tank and confirm steam arrives without a mechanical pump.
- [ ] Confirm a normal tank containing stored steam does not auto-pump steam without a valid boiler outlet.
- [ ] Confirm the existing direct compact crankshaft engine still follows the Phase 4 output table.
