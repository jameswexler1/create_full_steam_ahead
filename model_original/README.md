# Steam Engine Reference Notes

This folder is for reference notes only. Do not copy, edit, or commit Create's original asset files here.
Create's code is MIT, but its models and textures under `assets/create/` are All Rights Reserved.

Use Create's steam engine assets as visual reference in Blockbench, then build our own models from a blank
`Java Block/Item` project.

## Create Assets To Study

Open these from the local Create jar or from Create's source repository for visual inspection only:

- `assets/create/models/block/steam_engine/block.json`
- `assets/create/models/block/steam_engine/piston.json`
- `assets/create/models/block/steam_engine/linkage.json`
- `assets/create/models/block/steam_engine/shaft_connector.json`
- `assets/create/textures/block/engine.png`
- `assets/create/textures/block/cam_linkage.png`

## What To Recreate For Full Steam Ahead

- `piston_head_proxy`: larger, heavier piston head inspired by Create's steam engine piston, centered on X/Z.
- `piston_rod_proxy`: slim vertical rod using Create-like dark linkage language.
- `crank_pin_proxy`: offset crank pin/cam piece that reads as the rotating crank connection.
- `crankshaft`: static axial shaft body with one horizontal axis and visible end ports.

## Safe Blockbench Workflow

1. Create a new `Java Block/Item` model.
2. Set texture size to `32x32`.
3. Start from blank cubes; do not import Create's JSON as the base.
4. Use runtime texture references such as `create:block/cam_linkage` and `create:block/engine`.
5. Keep animated partial origins centered around `8, 8` on X/Z.
6. Export only original work into `src/main/resources/assets/full_steam_ahead/models/block/`.

## Current Export Targets

- `src/main/resources/assets/full_steam_ahead/models/block/partial/piston_rod_proxy.json`
- `src/main/resources/assets/full_steam_ahead/models/block/partial/piston_head_proxy.json`
- `src/main/resources/assets/full_steam_ahead/models/block/partial/crank_pin_proxy.json`
- `src/main/resources/assets/full_steam_ahead/models/block/crankshaft.json`
