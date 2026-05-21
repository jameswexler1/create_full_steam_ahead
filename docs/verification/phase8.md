# Phase 8 Verification

Date: 2026-05-21

Planned scope:

- Replace placeholder block models with Create-style visual models.
- Add crankshaft-driven piston animation through Flywheel plus a fallback renderer.
- Add running steam particles and rhythmic chuff sound.
- Add Ponder scenes after visuals are stable.
- Preserve all Phase 7 mechanics and Aeronautics compatibility.

Automated checks to run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
find src/main/resources -name '*.json' -exec jq empty {} +
```

Manual runtime checklist:

- [ ] Existing direct compact engines still assemble and run.
- [ ] Existing pipe-fed engines still assemble and run.
- [ ] Old worlds with existing engines load without blockstate/model errors.
- [ ] Piston motion is synchronized with crankshaft rotation at 16, 32, 48, and 64 RPM.
- [ ] Piston motion stops when the engine has no steam.
- [ ] Steam particles appear only while running and scale reasonably with speed.
- [ ] Chuff sound is audible but not spammy or overlapping harshly.
- [ ] Resource reload (`F3+T`) keeps partial models and textures intact.
- [ ] Dedicated server starts without client-class loading errors.
- [ ] Aeronautics/Sable assembled sublevel still moves and powers propellers with visuals active.
- [ ] Ponder entries appear and teach direct compact and pipe-fed setups.
