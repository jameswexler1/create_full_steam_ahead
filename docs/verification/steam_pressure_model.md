# Pressure/Volume/Temperature Steam Model Verification

Date: 2026-06-05

Implemented:

- New `content/steam/SteamPhysics` derives, from boiler heat units `T` and vessel volume `V`
  (`width^2 * height`):
  - pressure ratio `p = (T/T_ref) / (V/V_ref)` (clamped `[pressureMin, pressureMax]`),
  - continuous `RPM = clamp(rpmReference * p, 0, 64)`,
  - `SU = clamp(suReference * (T/T_ref) * (V/V_ref), 0, suMax)`.
- Direct/compact engine: `T` from blaze burners, `V` from the Create Fluid Tank under the ring.
  Replaces the 16/32/48/64 burner tier table.
- Piped engine: `BoilerOutletBlockEntity` computes its boiler's pressure ratio AND capacity SU and
  reports both to reachable assembled steam inlets during the push BFS (`reportSupply`, max-wins,
  10-tick decay). Capacity is split evenly across the inlets the boiler feeds (no duplication).
  Piston RPM follows delivered pressure; SU follows delivered capacity (volume scaling), so bigger
  boiler = higher SU/lower RPM, smaller = lower SU/higher RPM. Unknown supply -> pressure 1.0 + flow SU.
- New `steamPhysics` server config: volumeReference (9), temperatureReference (9), rpmReference
  (64), pressureMin (0.1), pressureMax (4.0), suReference (147456), suMax (2_000_000).
- Baseline = nine NORMAL burners on a 3x3x1 boiler (T=9): pressure 1.0, RPM 64, SU 147456.
  Blaze cakes (T=18) raise pressure to 2.0 but RPM caps at 64, so they only double SU to 294912.
- Goggles: outlet shows steam pressure + boiler volume/heat; piston head shows pressure + RPM + SU.

Automated checks:

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Manual runtime checklist:

- [ ] Compact engine on a 3x3x1 boiler with 9 NORMAL burners reads pressure 1.0, RPM 64, SU 147456.
- [ ] Same engine with 9 BLAZE CAKE burners reads pressure 2.0, RPM still 64, SU 294912 (cakes add SU only).
- [ ] Piped engine on a 3x3x1 boiler: RPM 64, SU 147456. On 3x3x2: RPM 32, SU 294912. On 3x3x3:
      RPM 21, SU 442368 (bigger boiler = more SU, less RPM; SU no longer flat-capped).
- [ ] Small piped boiler (1x1x1): high pressure -> RPM 64, low SU (~3.6k).
- [ ] Two engines sharing one boiler split its capacity (each ~half SU), not double it.
- [ ] Goggle pressure/RPM/SU lines update live on both outlet and piston head.
- [ ] Cutting the boiler heat or water stops the engine (pressure -> 0, RPM -> 0).
- [ ] A piped engine with no outlet-reported pressure still runs at the reference RPM baseline.
- [ ] Reload the world: engine pressure/RPM/SU restore correctly.
