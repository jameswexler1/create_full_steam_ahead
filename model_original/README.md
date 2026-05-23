# Steam Engine Reference Notes

This folder is for reference notes and local-only study files. Do not commit Create's original asset files.
Create's code is MIT, but its models and textures under `assets/create/` are not assets we should redistribute.

Use Create's steam engine assets as visual reference in Blockbench, then build our own models from a blank
`Java Block/Item` project.

## Create Assets To Study

Open these from the local Create jar, Create's source repository, or the ignored local `create_reference/`
folder for visual inspection only. In Blockbench, prefer the resource-pack layout copies under
`model_original/create_reference/assets/create/...`; the Minecraft texture IDs in the JSON need that
`assets/create/textures/...` namespace layout to resolve automatically.

- `assets/create/models/block/steam_engine/block.json`
- `assets/create/models/block/steam_engine/piston.json`
- `assets/create/models/block/steam_engine/linkage.json`
- `assets/create/models/block/steam_engine/shaft_connector.json`
- `assets/create/textures/block/engine.png`
- `assets/create/textures/block/cam_linkage.png`

## What To Recreate For Full Steam Ahead

- `piston`: unassembled static piston model used by all piston section variants when `assembled=false`.
- `piston_head`: separate block for the upper center of the cylinder bore; it will later move with the animated piston assembly.
- `piston_inside_low`: assembled lower piston guide inside the cylinder bore.
- `piston_inside_high`: assembled upper piston guide inside the cylinder bore.
- `piston_protrude_low`: assembled lower exposed piston guide above the cylinder.
- `piston_protrude_high`: assembled upper exposed piston guide below the crankshaft.
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

- `src/main/resources/assets/full_steam_ahead/models/block/piston.json`
- `src/main/resources/assets/full_steam_ahead/models/block/piston_head.json`
- `src/main/resources/assets/full_steam_ahead/models/block/piston_inside_low.json`
- `src/main/resources/assets/full_steam_ahead/models/block/piston_inside_high.json`
- `src/main/resources/assets/full_steam_ahead/models/block/piston_protrude_low.json`
- `src/main/resources/assets/full_steam_ahead/models/block/piston_protrude_high.json`
- `src/main/resources/assets/full_steam_ahead/models/block/partial/piston_rod_proxy.json`
- `src/main/resources/assets/full_steam_ahead/models/block/partial/piston_head_proxy.json`
- `src/main/resources/assets/full_steam_ahead/models/block/partial/crank_pin_proxy.json`
- `src/main/resources/assets/full_steam_ahead/models/block/crankshaft.json`
