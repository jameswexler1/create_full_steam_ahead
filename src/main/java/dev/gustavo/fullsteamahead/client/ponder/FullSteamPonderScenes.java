package dev.gustavo.fullsteamahead.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;

/**
 * Ponder storyboard for the pipe-fed steam engine, using the {@code testing_ponder_v2} structure.
 *
 * <p>Structure layout (6x5x6): a snow/light-gray-concrete checkerboard floor at y=0; the cylinder
 * ring around the piston at x1-3/z3-5 (head 2,1,4; body 2,2,4; shaft 2,4,4; steam inlet 3,1,4 on
 * the east face); a boiler of Blaze Burners (y=1) under Fluid Tanks (y=2) at x0-1/z0-1 with a
 * boiler outlet at 2,2,1; and a fluid-pipe run linking the outlet to the inlet. Nothing but the
 * floor is shown on load; the machine is then assembled piece by piece and finally driven.</p>
 */
public final class FullSteamPonderScenes {

    public static void cylinder(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("steam_engine", "Building a pipe-fed steam engine");
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        // Face the east side of the ring, where the steam inlet is, at Create's usual viewing angle.
        scene.addInstruction(ponderScene -> ponderScene.getTransform().yRotation.startWithValue(270));
        scene.world().modifyEntities(ItemEntity.class, ItemEntity::discard);

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

        // Hide everything above the floor; reveal it in build order.
        scene.world().setBlocks(util.select().fromTo(0, 1, 0, 5, 4, 5), Blocks.AIR.defaultBlockState(), false);
        scene.idle(15);

        scene.world().restoreBlocks(lowerRing);
        scene.world().showIndependentSection(lowerRing, Direction.DOWN);
        scene.effects().indicateSuccess(util.grid().at(2, 1, 4));
        scene.idle(18);
        scene.overlay().showText(90)
                .text("The engine body is a ring of Cylinder Walls around a Piston Head; one wall is a Steam Inlet")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().blockSurface(util.grid().at(3, 1, 4), Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(95);

        scene.world().restoreBlocks(upperRing);
        scene.world().showIndependentSection(upperRing, Direction.DOWN);
        scene.idle(14);
        scene.overlay().showText(80)
                .text("A second layer of Cylinder Walls closes around the Piston Body")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().centerOf(2, 2, 4))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        scene.world().restoreBlocks(shaft);
        scene.world().showIndependentSection(shaft, Direction.DOWN);
        scene.idle(16);
        scene.overlay().showText(80)
                .text("Above an empty stroke space, a Create shaft becomes the engine's power output")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().topOf(2, 4, 4))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        scene.world().restoreBlocks(burners);
        scene.world().showIndependentSection(burners, Direction.DOWN);
        scene.idle(16);
        scene.overlay().showText(75)
                .text("Beside it, Blaze Burners provide the heat")
                .colored(PonderPalette.FAST)
                .pointAt(util.vector().topOf(1, 1, 1))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().restoreBlocks(boilerTanks);
        scene.world().showIndependentSection(boilerTanks, Direction.DOWN);
        scene.idle(14);
        scene.world().restoreBlocks(outlet);
        scene.world().showIndependentSection(outlet, Direction.DOWN);
        scene.effects().indicateSuccess(util.grid().at(2, 2, 1));
        scene.idle(18);
        scene.overlay().showText(95)
                .text("Fluid Tanks over the burners form a boiler; a Boiler Outlet draws off its pressurized steam")
                .colored(PonderPalette.MEDIUM)
                .pointAt(util.vector().centerOf(2, 2, 1))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.world().restoreBlocks(pipes);
        scene.world().showIndependentSection(pipes, Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(90)
                .text("Fluid pipes carry the steam from the outlet to the engine's Steam Inlet")
                .colored(PonderPalette.INPUT)
                .pointAt(util.vector().blockSurface(util.grid().at(3, 1, 4), Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(95);

        // Steam reaches the engine: drive the shaft so the piston linkage runs.
        scene.world().setKineticSpeed(shaft, 32.0F);
        scene.world().setKineticSpeed(pistonHead, 32.0F);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Fed with steam, the piston drives the shaft, powering your contraptions like a giant steam engine")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(2, 4, 4))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.markAsFinished();
    }

    private FullSteamPonderScenes() {
    }
}
