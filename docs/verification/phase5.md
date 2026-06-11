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
- Open/unconnected steam vents show custom translucent steam leak particles through the outlet and Create open-pipe effect hook.
- Outlet pressure traversal respects Create fluid valves; closed valves block steam instead of being bypassed or treated as open vents.
- Steam in Create tanks and fluid pipes uses the original tinted water-based fluid texture path so pipe contents remain visible.
- Steam fluid rendering now explicitly handles stack-based and world-based render lookups, and the outlet's direct fallback preserves a live source reserve for Create pipe flow visuals.
- Goggles show outlet status, steam buffer, production, pushed amount, and pressure range.
- Outlet production now scales by boiler height: `active burner units * boiler height`.
- Outlet production is water-gated by Create's boiler water heat level, multiplied by boiler height.
- Multiple outlets on the same boiler split one shared steam budget instead of duplicating output.
- One pipe-fed engine consumes at most `90 mB/t`, producing at most `147,456 SU`; surplus steam is for additional engines.
- Passive Create Fluid Tanks now contribute network pressure volume from their configured fluid capacity, not only their block count.

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
- Latest automated run on 2026-05-31 after fixing pipe-fed per-engine caps, boiler-height water scaling, and alternating piston phase: `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed.
- Latest automated run on 2026-05-31 after fair steam distribution, per-inlet intake caps, and proportional pipe-fed SU: `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed.
- Latest automated run on 2026-05-31 after enhancing open steam particles and making boiler outlet pressure respect Create fluid valves: `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed.
- Latest automated run on 2026-05-31 after switching steam leak vents to custom particles and restoring pipe fluid visibility: `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed.
- Latest automated run on 2026-06-01 after preserving pipe-flow source steam and adding explicit fluid render overrides: `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed.
- Latest automated run on 2026-06-06 after scaling passive steam tank pressure volume by configured Create tank capacity: `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed.
- Latest automated run on 2026-06-11 after making boiler-outlet boiler activation geometric instead of block-entity-cache dependent: `git diff --check` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed.

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
- [ ] Confirm open boiler outlets and open pipe ends use the custom translucent steam leak particles instead of the old cloud/jet puff mix.
- [ ] Confirm a closed Create fluid valve between the outlet and an engine/tank stops steam transfer.
- [ ] Confirm reopening the same valve restores steam transfer without replacing the pipes.
- [ ] Confirm a closed valve does not make the outlet vent as if the pipe end were open.
- [ ] Confirm steam remains visible inside Create tanks and fluid pipes after the pipe-flow reserve and explicit fluid render override fix.

User report after polish runtime test: everything works.

Scaled production checklist:

- [ ] Confirm a `1x1x1` boiler with one normal active burner produces `10 mB/t` steam and one piped engine reports `16,384 SU`.
- [ ] Confirm a `3x3x1` boiler with 9 normal active burners produces `90 mB/t` steam and one piped engine reports `147,456 SU`.
- [ ] Confirm one engine on a larger normal boiler still caps at `90 mB/t`, `147,456 SU`, and `64 RPM`.
- [ ] Confirm a `3x3x3` boiler with 9 normal active burners feeds three full engines at `147,456 SU` each and `64 RPM`.
- [ ] Confirm a short stream such as `26` steam units across three reachable engines is shared across all three engines instead of filling two engines and starving the third.
- [ ] Confirm each short-fed engine reports proportional SU from exact consumed steam, while RPM follows the rounded consumed-unit tier.
- [ ] Confirm a `3x3x6` boiler with 9 normal active burners produces `540 mB/t` total steam and can feed six full normal engines.
- [ ] Confirm a `3x3x6` boiler with 9 Blaze Cake burners produces `1080 mB/t` total steam and can feed twelve full pipe-fed engines.
- [ ] Confirm two outlets on one boiler split the same total steam budget instead of each producing the full amount.
- [ ] Confirm two independent boilers feeding the same pipe network both contribute steam and the combined stream is still shared across reachable engine inlets.
- [ ] Confirm adjacent engines on one shaft alternate piston phase instead of rising and falling together.
- [ ] Confirm adding a large passive Create Fluid Tank to a closed steam network increases displayed network volume by its configured capacity and buffers pressure instead of rapidly climbing while barely filled.
