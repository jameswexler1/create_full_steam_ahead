# Steam Exhaust Hazard Verification

Date: 2026-06-05

Implemented:

- Engine exhaust puffs now use the custom translucent `steam_leak` particles.
- Exhaust puffs originate from the cylinder ring bore at the outer stroke point.
- Exhaust puffs are server-timed from generated RPM and only run when the engine itself is steam-powered.
- Passive shaft-driven linkage motion does not emit harmful steam.
- Open pipe steam effects and engine exhaust share the same scald damage helper.
- New server config:
  - `steamLeak.engineExhaustEnabled`
  - `steamLeak.engineExhaustDamageScale`

Automated checks:

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `git diff --check`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Manual runtime checklist:

- [ ] Running pipe-fed engines puff custom steam from the outer bore once per cycle.
- [ ] Upside-down pipe-fed engines emit the puff downward from the inverted bore.
- [ ] Passive shaft-driven engines with no generated steam do not emit harmful steam.
- [ ] Adjacent engines on the same shaft puff out of phase.
- [ ] Standing near the bore takes steam scald damage.
- [ ] Disabling `steamLeakDamageEnabled` prevents scald damage.
- [ ] Disabling `engineExhaustEnabled` removes the engine exhaust puff.
