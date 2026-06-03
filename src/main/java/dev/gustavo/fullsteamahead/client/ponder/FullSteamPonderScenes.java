package dev.gustavo.fullsteamahead.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderSection;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderSharedWall;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderWallShape;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlock;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ponder storyboard for the pipe-fed steam engine, using the {@code testing_ponder_v2} structure.
 *
 * <p>Structure layout (6x5x6): a snow/white-concrete checkerboard floor at y=0; the cylinder ring
 * around the piston at x1-3/z3-5 (head 2,1,4; body 2,2,4; shaft 2,4,4; steam inlet 3,1,4 on the
 * east face); a boiler of Blaze Burners (y=1) under Fluid Tanks (y=2) at x0-1/z0-1 with a boiler
 * outlet at 2,2,1; and a fluid-pipe run linking the outlet to the inlet.</p>
 *
 * <p>Reveals follow Create's own pattern: un-shown sections are hidden by default (only the base
 * plate is shown on load), and each part is brought in with {@code showIndependentSection}, which
 * uses Ponder's standard 15-tick fade — no manual block hiding/restoring, which would pop blocks
 * in instead of fading them.</p>
 */
public final class FullSteamPonderScenes {

    public static void cylinder(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("steam_engine", "Building a pipe-fed steam engine");
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        // Face the east side of the ring (the steam inlet) at Create's usual 3/4 angle. The default
        // yaw is 145 (a 55-degree tilt); 235 keeps that exact tilt while turning toward the inlet.
        scene.addInstruction(ponderScene -> ponderScene.getTransform().yRotation.startWithValue(235));
        scene.world().modifyEntities(ItemEntity.class, ItemEntity::discard);
        scene.idle(10);

        Selection pistonHead = util.select().position(2, 1, 4);
        Selection pistonBody = util.select().position(2, 2, 4);
        Selection shaft = util.select().position(2, 4, 4);
        Selection inlet = util.select().position(3, 1, 4);
        Selection lowerWalls = util.select().fromTo(1, 1, 3, 3, 1, 5)
                .substract(pistonHead)
                .substract(inlet);
        Selection upperWalls = util.select().fromTo(1, 2, 3, 3, 2, 5).substract(pistonBody);
        Selection lowerRing = lowerWalls.copy().add(inlet).add(pistonHead);
        Selection upperRing = upperWalls.copy().add(pistonBody);
        Selection burners = util.select().fromTo(0, 1, 0, 1, 1, 1);
        Selection boilerTanks = util.select().fromTo(0, 2, 0, 1, 2, 1);
        Selection outlet = util.select().position(2, 2, 1);
        Selection pipes = util.select().position(3, 1, 1)
                .add(util.select().fromTo(4, 1, 1, 4, 1, 4))
                .add(util.select().position(3, 2, 1));
        // The whole boiler shows up in one go: burners, fluid tanks and the outlet together.
        Selection boiler = burners.copy().add(boilerTanks).add(outlet);

        // 1: bottom cylinder (and steam inlet).
        scene.world().showIndependentSection(lowerRing, Direction.DOWN);
        scene.effects().indicateSuccess(util.grid().at(2, 1, 4));
        scene.idle(20);
        scene.overlay().showText(90)
                .text("The engine body is a 2-block tall cylinder, with one of its faces being the Steam Inlet.")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().blockSurface(util.grid().at(3, 1, 4), Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // 2: upper cylinder closes the body — shown silently, no text.
        scene.world().showIndependentSection(upperRing, Direction.DOWN);
        scene.idle(30);

        // 3: shaft.
        scene.world().showIndependentSection(shaft, Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(80)
                .text("Above an empty stroke space, place a Shaft to start the engine.")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().topOf(2, 4, 4))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // 4: the boiler, placed all at once.
        scene.world().showIndependentSection(boiler, Direction.DOWN);
        scene.effects().indicateSuccess(util.grid().at(2, 2, 1));
        scene.idle(20);
        scene.overlay().showText(95)
                .text("A boiler provides steam through the Boiler Outlet.")
                .colored(PonderPalette.MEDIUM)
                .pointAt(util.vector().centerOf(2, 2, 1))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(105);

        // 5: pipes.
        scene.world().showIndependentSection(pipes, Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(90)
                .text("Fluid Pipes carry the steam from the outlet to the engine's Steam Inlet.")
                .colored(PonderPalette.INPUT)
                .pointAt(util.vector().blockSurface(util.grid().at(3, 1, 4), Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // Fed with steam, run the engine as a silent payoff.
        scene.world().setKineticSpeed(shaft, 32.0F);
        scene.world().setKineticSpeed(pistonHead, 32.0F);
        scene.idle(60);

        scene.markAsFinished();
    }

    /**
     * Placeholder scene for the {@code scene_3} structure: a row of three steam-engine cylinders
     * banked along X (centers at x=1,3,5) that share their boundary walls. Reveals follow the same
     * style as {@link #cylinder} — base plate shown on load, everything else faded in with
     * {@code showIndependentSection} (15-tick fade, Direction.DOWN), same 235 yaw / 55-degree tilt.
     *
     * <p>Revealed nearest-first: the x5 cylinder, then x3, then x1. The boundary walls (x=4 and x=2)
     * are baked as shared in the structure, so they start re-set to ordinary single-cylinder walls and
     * only switch to the shared variant once the adjacent cylinder is placed.</p>
     */
    public static void cylinderBank(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("steam_engine_bank", "Banking cylinders side by side");
        scene.configureBasePlate(0, 0, 8);
        scene.showBasePlate();
        scene.addInstruction(ponderScene -> ponderScene.getTransform().yRotation.startWithValue(235));
        scene.world().modifyEntities(ItemEntity.class, ItemEntity::discard);
        scene.idle(10);

        // Rings are 3 wide in x (center +-1), 3 deep in z (1..3), 2 tall (y1..2); shaft line at y4.
        // Boundary columns x=2 (between cyl #1 and #2) and x=4 (between #2 and #3) are shared in the
        // structure; reset them to ordinary cylinder walls until their neighbour shows up.
        setColumn(scene, util, 2, false);
        setColumn(scene, util, 4, false);

        Selection nearCylinder = util.select().fromTo(4, 1, 1, 6, 4, 3);   // x5 ring, nearest
        Selection midCylinder = util.select().fromTo(2, 1, 1, 3, 4, 3);    // x3 ring
        Selection farCylinder = util.select().fromTo(0, 1, 1, 1, 4, 3);    // x1 ring
        Selection pipes = util.select().fromTo(0, 1, 0, 7, 1, 0);

        // 1: the cylinder nearest the camera, on its own as a normal single-cylinder engine.
        scene.world().showIndependentSection(nearCylinder, Direction.DOWN);
        scene.effects().indicateSuccess(util.grid().at(5, 1, 2));
        scene.idle(20);
        scene.overlay().showText(110)
                .text("The multiblock steam engine can be both single or multiple, sharing a wall between them, forming an engine block.")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().centerOf(5, 1, 2))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(120);

        // 2: the next cylinder; once placed, the wall between them becomes a shared wall.
        scene.world().showIndependentSection(midCylinder, Direction.DOWN);
        scene.idle(15);
        setColumn(scene, util, 4, true);
        scene.idle(5);
        scene.overlay().showText(95)
                .text("Place another cylinder beside it and they will merge into one shared wall.")
                .colored(PonderPalette.MEDIUM)
                .pointAt(util.vector().blockSurface(util.grid().at(4, 2, 2), Direction.UP))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(105);

        // 3: the last cylinder (its shared wall forms too) and the pipe run — shown silently, no text.
        scene.world().showIndependentSection(farCylinder, Direction.DOWN);
        scene.idle(15);
        setColumn(scene, util, 2, true);
        scene.idle(5);
        scene.world().showIndependentSection(pipes, Direction.DOWN);
        scene.idle(40);

        scene.markAsFinished();
    }

    /**
     * Sets a boundary cylinder column (x, z=1..3, y=1..2) either as a shared wall ({@code shared=true},
     * the structure's baked strip_z east-column state) or as an ordinary standalone west wall.
     */
    private static void setColumn(CreateSceneBuilder scene, SceneBuildingUtil util, int x, boolean shared) {
        CylinderSection[] lower = shared
                ? new CylinderSection[]{CylinderSection.LOWER_NORTH_EAST, CylinderSection.LOWER_EAST, CylinderSection.LOWER_SOUTH_EAST}
                : new CylinderSection[]{CylinderSection.LOWER_NORTH_WEST, CylinderSection.LOWER_WEST, CylinderSection.LOWER_SOUTH_WEST};
        CylinderSection[] upper = shared
                ? new CylinderSection[]{CylinderSection.UPPER_NORTH_EAST, CylinderSection.UPPER_EAST, CylinderSection.UPPER_SOUTH_EAST}
                : new CylinderSection[]{CylinderSection.UPPER_NORTH_WEST, CylinderSection.UPPER_WEST, CylinderSection.UPPER_SOUTH_WEST};
        CylinderSharedWall sw = shared ? CylinderSharedWall.STRIP_Z : CylinderSharedWall.NONE;
        for (int i = 0; i < 3; i++) {
            scene.world().setBlock(util.grid().at(x, 1, 1 + i), cyl(lower[i], sw), false);
            scene.world().setBlock(util.grid().at(x, 2, 1 + i), cyl(upper[i], sw), false);
        }
    }

    private static BlockState cyl(CylinderSection section, CylinderSharedWall shared) {
        return ModBlocks.STEAM_CYLINDER.get().defaultBlockState()
                .setValue(SteamCylinderBlock.ASSEMBLED, true)
                .setValue(SteamCylinderBlock.SECTION, section)
                .setValue(SteamCylinderBlock.WALL_SHAPE, CylinderWallShape.STANDALONE)
                .setValue(SteamCylinderBlock.SHARED_WALL, shared)
                .setValue(SteamCylinderBlock.FACING, Direction.UP);
    }

    private FullSteamPonderScenes() {
    }
}
