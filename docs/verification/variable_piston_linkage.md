# Variable Piston Linkage Verification

Date: 2026-06-05

Implemented:

- Piston head validation now accepts one, two, or three consecutive piston body blocks.
- The shaft position scales with piston body count:
  - 1 body: one empty stroke block, shaft three blocks from the piston head.
  - 2 bodies: two empty stroke blocks, shaft five blocks from the piston head.
  - 3 bodies: three empty stroke blocks, shaft seven blocks from the piston head.
- Four or more piston bodies are invalid and should not assemble.
- Existing one-body engines keep the previous geometry and balance.
- Server state stores the piston body count so animation and shaft lookup survive world reloads.
- Renderer and Flywheel visuals select crank partials by piston body count and assemble the rod from valid repeated segments.

Automated checks:

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `git diff --check`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Manual runtime checklist:

- [ ] Build a normal one-body engine and confirm it still assembles, powers, and animates as before.
- [ ] Build a two-body engine: piston head, two piston bodies, two empty stroke blocks, horizontal shaft.
- [ ] Build a three-body engine: piston head, three piston bodies, three empty stroke blocks, horizontal shaft.
- [ ] Confirm the shaft placement helper previews the shaft at the correct distance for each body count.
- [ ] Confirm the connecting rod renders with the real rod texture for two- and three-body engines, not a purple missing model.
- [ ] Confirm a four-body piston column remains invalid and does not claim the shaft.
- [ ] Confirm upright and upside-down pipe-fed engines work for all valid body counts.
- [ ] Confirm breaking/removing a piston body clears assembled piston visuals and shaft power.
- [ ] Confirm closing and reopening the world preserves the correct body count and linkage animation.
