# Boiler Overpressure Verification

Date: 2026-06-07

Implemented:

- `SteamNetworkManager` computes one effective pressure per connected steam network from stored steam,
  temperature, and pressure volume.
- Open pipe ends and unconnected outlets vent stored steam toward atmospheric pressure before burst
  checks, so sufficient relief prevents an explosion.
- Past `steamPhysics.warnPressure`, the outlet reports overpressure and emits warning hiss/steam at the
  boiler center.
- Past `steamPhysics.burstPressure`, each physical boiler bursts once, the whole connected network is
  depressurized, and vanilla explosion gameplay uses
  `min(maxPower, basePower + powerPerVolume * networkVolume) * explosionPowerScale`.
- Phase A polish adds a clientbound boiler burst packet. Nearby clients spawn a large steam cloud,
  play layered placeholder boom/hiss sounds, and apply distance-delayed configurable camera shake.
- Burst sound now cuts off past 200 blocks, and screen shake cuts off past 150 blocks.
- Optional Sable/Aeronautics compat projects simulated-contraption burst effects into real-world
  coordinates and applies a randomized local block-damage pass to nearby sublevel blocks. Local
  sublevel destruction mostly vaporizes blocks and only drops sparse edge salvage.
- `steamOverpressure` config covers enabled, explosion base/per-volume/max power, block breaking, and
  final power scale, and client effect packet radius. Client config covers local visuals, volume,
  sound radius, cloud scale, shake scale/radius, and blast wave speed.

Automated checks:

- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `git diff --check`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Manual runtime checklist:

- [ ] Boiler producing into a closed pipe with no engine builds pressure and eventually bursts.
- [ ] Burst still breaks/damages blocks according to `steamOverpressure.explosionBreaksBlocks`.
- [ ] Burst radius feels roughly half of the previous oversized tuning.
- [ ] Burst shows a large white steam cloud at the boiler center.
- [ ] Burst plays louder layered boom/hiss placeholder sounds.
- [ ] Sound plays within 200 blocks and does not play at 201+ blocks.
- [ ] Screen shake happens within 150 blocks and does not apply at 151+ blocks.
- [ ] Multiple outlets on one boiler still produce one burst/effect, not duplicates.
- [ ] Multiple separate boilers bursting create separate burst effects.
- [ ] Simulated contraption boiler burst damages nearby ship/sublevel blocks.
- [ ] Simulated contraption boiler burst does not drop every destroyed ship/sublevel block; drops are sparse and biased toward edge damage.
- [ ] Simulated contraption boiler burst still affects nearby real-world terrain/entities.
- [ ] `explosionBreaksBlocks=false` disables both world and simulated contraption block destruction.
- [ ] Opening a pipe end relieves pressure and prevents burst when relief is sufficient.
- [ ] Enough engine load to consume production keeps pressure below warning/burst.
