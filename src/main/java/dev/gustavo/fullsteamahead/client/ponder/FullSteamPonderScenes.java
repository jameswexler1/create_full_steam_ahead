package dev.gustavo.fullsteamahead.client.ponder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;

/**
 * Experimental Ponder storyboards for Create: Full Steam Ahead.
 *
 * <p>Uses the {@code testing_ponder} structure: a complete pipe-fed steam engine (cylinder ring
 * with piston, shaft, and a steam inlet, fed by a Create boiler through a boiler outlet and
 * pipes). The cylinder-wall scene leads with the ring it belongs to, completes the engine, then
 * reveals the steam source.</p>
 */
public final class FullSteamPonderScenes {

    public static void cylinder(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("testing_ponder", "Building a pipe-fed steam engine");
        scene.configureBasePlate(0, 0, 10);
        scene.scaleSceneView(0.86F);
        // The inlet in testing_ponder is on the east side of the cylinder ring.
        scene.rotateCameraY(180);
        scene.showBasePlate();
        scene.idle(10);

        Selection centerColumn = util.select().fromTo(6, 0, 8, 6, 1, 8);
        Selection inlet = util.select().position(7, 0, 8);
        Selection cylinderWalls = util.select()
                .fromTo(5, 0, 7, 7, 1, 9)
                .substract(centerColumn)
                .substract(inlet);
        Selection pistonHead = util.select().position(6, 0, 8);
        Selection pistonBody = util.select().position(6, 1, 8);
        Selection shaft = util.select().position(6, 3, 8);

        scene.world().showSection(pistonHead, Direction.UP);
        scene.idle(8);
        scene.world().showSection(pistonBody, Direction.UP);
        scene.idle(8);
        scene.world().showSection(cylinderWalls, Direction.EAST);
        scene.idle(12);
        scene.world().showSection(inlet, Direction.EAST);
        scene.idle(16);
        scene.overlay().showText(80)
                .text("Start with the piston head, piston body, and a two-layer ring of Cylinder Walls")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().centerOf(6, 1, 8))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        scene.overlay().showText(75)
                .text("One wall may be replaced by a Steam Inlet; this is where piped steam enters the engine")
                .colored(PonderPalette.INPUT)
                .pointAt(util.vector().blockSurface(util.grid().at(7, 0, 8), Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        scene.world().showSection(shaft, Direction.DOWN);
        scene.idle(18);
        scene.overlay().showText(75)
                .text("A regular Create shaft sits above the empty stroke space and becomes the engine output")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().topOf(6, 3, 8))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        Selection boiler = util.select().fromTo(2, 0, 3, 5, 1, 6);
        scene.world().showSection(boiler, Direction.WEST);
        scene.idle(20);
        scene.overlay().showText(90)
                .text("Build a Create boiler beside the engine: Fluid Tanks, Blaze Burners, and a steady water supply")
                .colored(PonderPalette.MEDIUM)
                .pointAt(util.vector().topOf(4, 1, 4))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        Selection outlet = util.select().position(6, 1, 5);
        Selection pipeRun = util.select()
                .position(7, 1, 5)
                .add(util.select().position(7, 0, 5))
                .add(util.select().fromTo(8, 0, 5, 8, 0, 8));

        scene.world().showSection(outlet, Direction.EAST);
        scene.idle(14);
        scene.overlay().showText(95)
                .text("The Boiler Outlet turns boiler pressure into steam flow")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().centerOf(6, 1, 5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(55);

        scene.world().showSection(pipeRun, Direction.EAST);
        scene.idle(18);
        scene.overlay().showText(95)
                .text("Finally, connect the outlet to the Steam Inlet with Create fluid pipes")
                .colored(PonderPalette.INPUT)
                .pointAt(util.vector().blockSurface(util.grid().at(7, 0, 8), Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("When steam reaches the inlet, the piston drives the shaft like a large Create steam engine")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(6, 3, 8))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }

    private FullSteamPonderScenes() {
    }
}
