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
        scene.title("steam_cylinder", "Building a steam engine");
        scene.showBasePlate();
        scene.idle(10);

        // The cylinder ring is the subject: reveal the 3x3 x 2-tall body first.
        Selection ring = util.select().fromTo(5, 0, 7, 7, 1, 9);
        scene.world().showSection(ring, Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(80)
                .text("Cylinder Walls form the hollow body of a steam engine: a 3x3 ring, two blocks tall")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(5, 1, 7))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(70)
                .text("A piston head sits at the bottom centre, with the piston body in the ring above it")
                .pointAt(util.vector().centerOf(6, 0, 8))
                .placeNearTarget();
        scene.idle(80);

        // Cap the column with the empty stroke space and the output shaft.
        Selection shaft = util.select().fromTo(6, 2, 8, 6, 3, 8);
        scene.world().showSection(shaft, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(75)
                .text("An empty stroke space and a Create shaft cap the engine; the shaft carries the power out")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().topOf(6, 3, 8))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        scene.overlay().showText(70)
                .text("One wall is a Steam Inlet; it feeds the ring with steam")
                .colored(PonderPalette.INPUT)
                .pointAt(util.vector().blockSurface(util.grid().at(7, 0, 8), Direction.EAST))
                .placeNearTarget();
        scene.idle(80);

        // Where the steam comes from: a Create boiler with burners and a water supply.
        Selection boiler = util.select().fromTo(2, 0, 3, 5, 1, 6);
        scene.world().showSection(boiler, Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(90)
                .text("Steam comes from a Create boiler: a Fluid Tank heated by Blaze Burners, with a water supply")
                .pointAt(util.vector().topOf(4, 1, 4))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // The boiler outlet and the pipe run that carries steam to the inlet.
        scene.world().showSection(util.select().fromTo(6, 1, 5, 7, 1, 5), Direction.DOWN);
        scene.world().showSection(util.select().position(7, 0, 5), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(8, 0, 5, 8, 0, 8), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(95)
                .text("A Boiler Outlet pushes steam through pipes into the inlet, and the fed engine spins its shaft")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().centerOf(6, 1, 5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.markAsFinished();
    }

    private FullSteamPonderScenes() {
    }
}
