# Boiler Overpressure Verification

Date: 2026-06-05

Implemented:

- `BoilerOutletBlockEntity` accumulates `overpressureMb += max(0, productionRate - pushedRate)` each
  tick (steam produced but not pushed to a consumer or vented). Bleeds by `reliefMbPerTick` when not
  over-producing. Engines, open pipe ends, and any vented steam count as pushed -> relieve pressure.
- Past `warnPressureMb`: status -> "Overpressure!", FIRE_EXTINGUISH hiss + STEAM_LEAK particles at the
  boiler center every 8 ticks; goggle shows buildup %.
- Past `burstPressureMb`: explosion at the boiler center, power = min(explosionMaxPower,
  explosionBasePower + explosionPowerPerVolume * getTotalTankSize()), block-breaking by default. Resets.
- `steamOverpressure` config (all tunable): enabled, burstPressureMb (60000), warnPressureMb (40000),
  reliefMbPerTick (120), explosionBasePower (4.0), explosionPowerPerVolume (0.15), explosionMaxPower
  (12.0), explosionBreaksBlocks (true). NBT-persisted.

Automated checks:

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Manual runtime checklist:

- [ ] Boiler producing into a closed pipe with no engine builds pressure (goggle %) and eventually
      explodes, breaking nearby blocks.
- [ ] Oversized boiler feeding a smaller engine load (consumes < produced) builds and bursts.
- [ ] Opening a pipe end (steam venting) holds pressure down -> no burst (relief).
- [ ] Enough engine load to consume all production -> pressure stays at 0, no warning.
- [ ] Warning phase: hiss + steam particles + "Overpressure!" before the blast.
- [ ] Bigger boiler -> bigger blast (power scales with tank size, capped at explosionMaxPower).
- [ ] `enabled=false` -> no buildup, no explosion. `explosionBreaksBlocks=false` -> entity damage only.
- [ ] Reload mid-buildup: overpressure value persists.
