# Phase 6 Verification

Date: 2026-05-20

Planned scope:

- Add `steam_inlet` as a block entity that occupies a cylinder shell slot.
- Allow assembled rings with either 16 cylinder blocks, 15 cylinder blocks plus 1 active inlet, or 14 cylinder blocks plus 1 active inlet and 1 passive visual inlet.
- Accept only `steam` into the active inlet while the ring is assembled.
- Let the crankshaft consume inlet steam for pipe-fed output while preserving direct compact mode.
- Keep more than two inlets invalid for v1.

Automated checks to run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
find src/main/resources -name '*.json' -exec jq empty {} +
```

Automated results:

- `compileJava`: passed
- `processResources`: passed
- `build`: passed
- JSON validation: passed

Stability follow-up:

- Runtime report: after successful pipe-fed testing, the integrated server became broadly unresponsive. Vanilla falling blocks stopped ticking, engine structures stopped reacting, and `latest.log` reported repeated `Too many chained neighbor updates`.
- Cause addressed: broad `neighborChanged` handlers were doing immediate multiblock/boiler revalidation, visual assembly state writes were notifying neighbors, and boiler outlet pipe pressure was writing Create pipe pressure every tick across the pipe graph.
- Fix: visual-only assembly state writes now use client update flags, piston/crankshaft no longer revalidate from unrelated neighbor changes, cylinder/inlet neighbor refreshes ignore external pipe/shaft noise once assembled, boiler outlet only refreshes boiler state from its attached tank side, and pipe pressure refresh is throttled.
- Automated recheck after fix: `compileJava` passed; `build` passed.

Pipe animation follow-up:

- Runtime report: wrenching a Create fluid pipe between regular and glass form made visible steam flow restart from the boiler outlet.
- Cause addressed: Create preserves pipe flow during the block conversion, but the conversion also wipes pipe pressure. If the outlet's pressure cache stayed warm, the next pipe tick could clear the preserved flow before pressure was reapplied.
- Fix: steam outlets now reapply pressure when cached pipe pressure has been wiped, and a targeted mixin refreshes connected steam outlet pressure immediately after Create pipe wrench conversions.
- Automated recheck after fix: `compileJava` passed; `build` passed; JSON validation passed.

Implementation notes:

- `steam_inlet` is registered as a shell-slot block entity and appears in the creative tab.
- Cylinder assembly now accepts 16 cylinders, 15 cylinders plus 1 inlet, or 14 cylinders plus 2 inlets. More than two inlets are invalid.
- The active inlet exposes an input-only steam capability only while assembled; a passive inlet exposes an inert no-fill handler for visual pipe connections. Capabilities are invalidated when assembly or active/passive role changes.
- Crankshafts prefer usable inlet steam, consuming 10 mB/t per heat unit up to 180 mB/t. If no usable inlet steam exists and a direct boiler is present, direct compact mode remains the fallback.
- Crankshaft goggles now show direct boiler vs piped steam source mode.

Dual-inlet symmetry update:

- Automated recheck on 2026-06-16: `compileJava` passed; `build` passed; JSON validation passed; `git diff --check` passed.
- Complete rings now permit up to two `steam_inlet` blocks. Deterministic selection marks one active inlet; the optional second inlet is passive.
- Active selection prefers an inlet whose visible port side is connected to a Create fluid pipe, falling back to ring-position order when neither inlet is piped.
- Passive inlets expose an inert no-fill steam handler so pipes can connect visually without creating a second engine consumer, buffer, or pressure volume.

Manual runtime checklist:

- [x] `Steam Inlet` appears in the creative tab.
- [x] A 15 cylinder + 1 inlet ring assembles visually with the inlet in any shell slot.
- [ ] A 14 cylinder + 2 inlet ring assembles visually with one active inlet and one passive decorative inlet.
- [ ] More than two inlets prevents assembly.
- [x] The inlet accepts steam from Create pipes only when assembled.
- [ ] The passive inlet connects visually to a pipe but accepts 0 mB/t and does not increase engine demand.
- [x] Remote boiler outlet -> pipes -> inlet runs the crankshaft without a direct boiler below.
- [x] Stored steam in the inlet buffer runs the engine briefly after boiler output stops.
- [x] Direct compact boiler mode still works without an inlet.

User report after Phase 6 runtime testing: all inlet behaviours work as expected.
