# Metal Girder Shaft Compatibility Verification

Date: 2026-06-05

## Implemented

- Added hidden `full_steam_ahead:powered_girder_encased_shaft` for engine-owned Create metal girder encased shafts.
- Engine shaft claiming now preserves the shaft form:
  - `create:shaft` becomes `full_steam_ahead:powered_shaft`.
  - `create:metal_girder_encased_shaft` becomes `full_steam_ahead:powered_girder_encased_shaft`.
- Engine validation, shaft bank phase scanning, block-place revalidation, and contraption movement rules now treat girder-encased shaft variants as valid horizontal shaft-line blocks.
- The hidden girder-powered shaft reverts to Create's `metal_girder_encased_shaft` when engine ownership is cleared and drops both `create:metal_girder` and `create:shaft`.

## Automated checks

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `git diff --check`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

## Manual runtime checklist

- [ ] Build a single engine with a plain shaft; confirm it assembles, powers, disassembles, and reloads correctly.
- [ ] Encapsulate the engine output position as a metal girder encased shaft; confirm RPM/SU do not drop.
- [ ] Build multiple connected engines with girder-encased shaft sections in the bank; confirm total output and alternating piston phase remain correct.
- [ ] Break/remove the girder-encased output shaft; confirm the owning engine stops or revalidates cleanly.
- [ ] Test the same setup on a simulated contraption.
