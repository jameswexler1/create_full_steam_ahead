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
  depressurized, and vanilla explosion gameplay still uses
  `min(maxPower, basePower + powerPerVolume * networkVolume)`.
- Phase A polish adds a clientbound boiler burst packet. Nearby clients spawn a large steam cloud,
  play layered placeholder boom/hiss sounds, and apply distance-delayed configurable camera shake.
- `steamOverpressure` config covers enabled, explosion base/per-volume/max power, block breaking, and
  client effect packet radius. Client config covers local visuals, volume, cloud scale, shake scale, and
  blast wave speed.

Automated checks:

- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `git diff --check`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Manual runtime checklist:

- [ ] Boiler producing into a closed pipe with no engine builds pressure and eventually bursts.
- [ ] Burst still breaks/damages blocks according to `steamOverpressure.explosionBreaksBlocks`.
- [ ] Burst shows a large white steam cloud at the boiler center.
- [ ] Burst plays louder layered boom/hiss placeholder sounds.
- [ ] Screen shake happens near the burst, falls off with distance, and respects client config.
- [ ] Multiple outlets on one boiler still produce one burst/effect, not duplicates.
- [ ] Multiple separate boilers bursting create separate burst effects.
- [ ] Opening a pipe end relieves pressure and prevents burst when relief is sufficient.
- [ ] Enough engine load to consume production keeps pressure below warning/burst.
