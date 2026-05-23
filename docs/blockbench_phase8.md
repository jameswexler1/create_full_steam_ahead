# Phase 8 Blockbench Guide

Use Blockbench to make final art after the proxy animation is working. The current files are technical placeholders, so they are allowed to be simple, but the exported paths should stay stable.

## Static Block Models

Create these as Java Block/Item models and export them over the existing files in:

- `src/main/resources/assets/full_steam_ahead/models/block/steam_cylinder.json`
- `src/main/resources/assets/full_steam_ahead/models/block/steam_cylinder_assembled.json`
- `src/main/resources/assets/full_steam_ahead/models/block/steam_inlet.json`
- `src/main/resources/assets/full_steam_ahead/models/block/steam_inlet_assembled.json`
- `src/main/resources/assets/full_steam_ahead/models/block/boiler_outlet.json`
- `src/main/resources/assets/full_steam_ahead/models/block/crankshaft.json`
- `src/main/resources/assets/full_steam_ahead/models/block/piston.json`
- `src/main/resources/assets/full_steam_ahead/models/block/piston_inside_low.json`
- `src/main/resources/assets/full_steam_ahead/models/block/piston_inside_high.json`
- `src/main/resources/assets/full_steam_ahead/models/block/piston_protrude_low.json`
- `src/main/resources/assets/full_steam_ahead/models/block/piston_protrude_high.json`

Keep every static block inside the normal 16x16x16 block cube. Do not create faces that sit exactly on top of another face. If two cubes touch, let them meet edge-to-edge; do not let them occupy the same space.

The unassembled `piston.json` is used for all four piston section variants while `assembled=false`.
The four assembled piston files are distinct section models and should remain separate exports even if
their first pass looks similar.

## Animated Partial Models

Animated parts live in:

- `src/main/resources/assets/full_steam_ahead/models/block/partial/piston_rod_proxy.json`
- `src/main/resources/assets/full_steam_ahead/models/block/partial/piston_head_proxy.json`
- `src/main/resources/assets/full_steam_ahead/models/block/partial/crank_pin_proxy.json`

The animation code moves these parts, so their origin matters:

- Model with the engine centered on X/Z at `8, 8`.
- Keep the piston rod vertical.
- Keep the crank pin centered around the crankshaft block.
- Avoid baked rotations unless the code explicitly needs them.

## Texture Direction

Prefer Create texture references over copied PNGs:

- `create:block/copper_casing_connected`
- `create:block/brass_casing`
- `create:block/andesite_casing_connected`
- `create:block/andesite_casing_piston`
- `create:block/cam_linkage`
- `create:block/engine`

Do not copy, edit, or redistribute Create PNG files for now. If a unique texture is needed later, make an original small texture under `assets/full_steam_ahead/textures/block/`.

## Export Check

After exporting from Blockbench:

```sh
find src/main/resources -name '*.json' -exec jq empty {} +
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
```

Then load a world and check for missing purple-black textures, flickering faces, and oversized geometry.
