# Pressure/Volume/Temperature Steam Model Verification

Date: 2026-06-05

Implemented (v2 — consumption-limited / volume-tiered):

- `content/steam/SteamPhysics` derives from boiler volume `V` (`getTotalTankSize`) and a heat ratio
  `h = clamp(waterGatedHeat / getMaxHeatLevelForBoilerSize(V), 0, heatRatioMax)`:
  - production `= round(steamPerBlock * V * h)` mB/t (steamPerBlock = 90/27 -> full-heat 3x3x3 = 90),
  - pressure `p = h * maxVolumeReference / V` (1.0 at a full-heat 3x3x3),
  - RPM `= clamp(rpmAtMaxVolume * p, 0, maxRpm)`,
  - SU `= min(cylinderMaxSu, consumed * suPerSteamMb)`, consumed <= cylinderMaxIntakeMb.
- Both direct (compact, reads its boiler locally) and piped (outlet reports pressure to inlets;
  SU from steam consumed) use the same model. Boiler's finite production splits across the engines
  drawing it (no duplication).
- Config (`steamPhysics`, all tunable): cylinderMaxIntakeMb (90), cylinderMaxSu (147456), steamPerBlock
  (90/27), maxVolumeReference (27), rpmAtMaxVolume (16), maxRpm (64), heatRatioMax (2.0).
- Full-heat tiers: 1x1x1 -> 64 RPM / ~5.5k SU; 3x3x1 -> 48 / 49152; 3x3x2 -> 24 / 98304;
  3x3x3 -> 16 / 147456 (maxed). Bigger than 3x3x3 overproduces (surplus -> future overpressure).
- Goggles: outlet shows steam pressure + boiler volume/heat; piston head shows pressure + RPM + SU.

Automated checks:

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Manual runtime checklist:

- [ ] Compact engine on a full-heat 3x3x1 boiler: ~48 RPM, ~49152 SU (mid tier).
- [ ] Piped engine off a 3x3x3 boiler maxes: ~16 RPM, 147456 SU. Off 3x3x2: ~24 RPM, ~98304 SU.
      Off 3x3x1: ~48 RPM, ~49152 SU. Off 1x1x1: ~64 RPM, low SU (~5.5k).
- [ ] SU now DIFFERS across 3x3x1/2/3 (was flat) and RPM drops as the boiler grows.
- [ ] Two engines sharing one boiler split its production (lower SU each), not double it.
- [ ] Goggle pressure/RPM/SU lines update live on both outlet and piston head.
- [ ] Cutting the boiler heat or water stops the engine (pressure -> 0, RPM -> 0).
- [ ] A piped engine with no outlet-reported pressure still runs at the reference RPM baseline.
- [ ] Reload the world: engine pressure/RPM/SU restore correctly.
