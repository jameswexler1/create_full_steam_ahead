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
- Outlet applies Create pipe pressure so generated steam can render inside connected pipes.
- Outlet keeps a bounded direct pipe traversal fallback up to 30 blocks if the Create pipe network does not drain immediately.
- Open/unconnected steam vents show cloud particles through the outlet and Create open-pipe effect hook.
- Goggles show outlet status, steam buffer, production, pushed amount, and pressure range.
- Outlet production now scales by boiler height: `active burner units * boiler height`.
- Outlet production is water-gated at `10 mB/t` water supply per steam unit.
- Multiple outlets on the same boiler split one shared steam budget instead of duplicating output.

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
- Latest automated run on 2026-05-31 after scaled boiler outlet production: `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed.

Manual runtime checklist:

- [x] Run `./gradlew runClient`.
- [x] Confirm `Boiler Outlet` appears in the creative tab.
- [x] Place `boiler_outlet` on a Create Fluid Tank face and confirm it is placeable without crashing.
- [x] With no active boiler heat or no water supply, confirm goggles show no steam production.
- [x] Fire the boiler with water supply and confirm the outlet buffer fills with `steam`.
- [x] Connect Create fluid pipes from the outlet to a Create Fluid Tank and confirm steam arrives without a mechanical pump.
- [x] Confirm a normal tank containing stored steam does not auto-pump steam without a valid boiler outlet.
- [x] Confirm the existing direct compact crankshaft engine still follows the Phase 4 output table.

User report after initial Phase 5 runtime test: all checklist items above work as expected; only pipe fluid visibility and open-pipe leaking needed follow-up.

Polish runtime checklist:

- [x] Confirm steam is visible inside Create fluid pipes while the boiler outlet is producing.
- [x] Confirm an outlet facing open air vents steam particles instead of silently filling its buffer.
- [x] Confirm a pipe connected to the outlet but ending open vents steam particles from the open pipe end.
- [x] Confirm a pipe connected to a tank still moves steam without a mechanical pump after the visibility change.

User report after polish runtime test: everything works.

Scaled production checklist:

- [ ] Confirm a `1x1x1` boiler with one normal active burner produces `10 mB/t` steam and one piped engine reports `16,384 SU`.
- [ ] Confirm a `3x3x1` boiler with 9 normal active burners produces `90 mB/t` steam and one piped engine reports `147,456 SU`.
- [ ] Confirm a `3x3x6` boiler with 9 normal active burners produces `540 mB/t` total steam and can feed six full normal engines.
- [ ] Confirm a `3x3x6` boiler with 9 Blaze Cake burners produces `1080 mB/t` total steam and can feed six doubled engines.
- [ ] Confirm two outlets on one boiler split the same total steam budget instead of each producing the full amount.
