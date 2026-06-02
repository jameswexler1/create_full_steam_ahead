package dev.gustavo.fullsteamahead.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;

/**
 * Experimental Ponder storyboards for Create: Full Steam Ahead.
 *
 * <p>Uses the {@code testing_ponder} structure: a complete pipe-fed steam engine (cylinder ring
 * with piston, shaft, and a steam inlet, fed by a Create boiler through a boiler outlet and
 * pipes). The cylinder-wall scene leads with the ring it belongs to, completes the engine, then
 * reveals the steam source.</p>
 */
public final class FullSteamPonderScenes {

    public static void cylinder(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("testing_ponder", "Building a pipe-fed steam engine");
        scene.configureBasePlate(0, 0, 10);
        scene.setSceneOffsetY(-0.5F);
        scene.scaleSceneView(0.86F);
        scene.addInstruction(ponderScene ->
                // The inlet in testing_ponder is on the east side of the cylinder ring.
                ponderScene.getTransform().yRotation.startWithValue(270));
        scene.world().modifyEntities(ItemEntity.class, ItemEntity::discard);

        Selection centerColumn = util.select().fromTo(6, 0, 8, 6, 1, 8);
        Selection inlet = util.select().position(7, 0, 8);
        Selection lowerCylinderWalls = util.select()
                .fromTo(5, 0, 7, 7, 0, 9)
                .substract(util.select().position(6, 0, 8))
                .substract(inlet);
        Selection upperCylinderWalls = util.select()
                .fromTo(5, 1, 7, 7, 1, 9)
                .substract(util.select().position(6, 1, 8));
        Selection engineCore = util.select()
                .position(6, 0, 8)
                .add(util.select().position(6, 1, 8));
        Selection engineAssembly = util.select()
                .fromTo(5, 0, 7, 7, 1, 9)
                .substract(centerColumn)
                .add(engineCore)
                .add(inlet);
        Selection pistonHead = util.select().position(6, 0, 8);
        Selection pistonBody = util.select().position(6, 1, 8);
        Selection shaft = util.select().position(6, 3, 8);
        Selection boiler = util.select().fromTo(2, 0, 3, 5, 1, 6);
        Selection outlet = util.select().position(6, 1, 5);
        Selection pipeRun = util.select()
                .position(7, 1, 5)
                .add(util.select().position(7, 0, 5))
                .add(util.select().fromTo(8, 0, 5, 8, 0, 8));
        Selection steamConnection = outlet.copy().add(pipeRun);
        Selection stagedMachine = engineAssembly.copy()
                .add(shaft)
                .add(boiler)
                .add(steamConnection);

        scene.world().setBlocks(stagedMachine, Blocks.AIR.defaultBlockState(), false);
        buildCheckeredBasePlate(scene);
        scene.showBasePlate();
        scene.idle(15);

        scene.world().restoreBlocks(pistonHead);
        scene.world().showIndependentSection(pistonHead, Direction.UP);
        scene.idle(10);
        scene.world().restoreBlocks(pistonBody);
        scene.world().showIndependentSection(pistonBody, Direction.UP);
        scene.idle(12);
        scene.world().restoreBlocks(lowerCylinderWalls);
        scene.world().showIndependentSection(lowerCylinderWalls, Direction.EAST);
        scene.idle(8);
        scene.world().restoreBlocks(upperCylinderWalls);
        scene.world().showIndependentSection(upperCylinderWalls, Direction.DOWN);
        scene.idle(10);
        scene.world().restoreBlocks(inlet);
        scene.world().showIndependentSection(inlet, Direction.EAST);
        scene.effects().indicateSuccess(util.grid().at(7, 0, 8));
        scene.idle(18);
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

        scene.world().restoreBlocks(shaft);
        scene.world().showIndependentSection(shaft, Direction.DOWN);
        scene.idle(18);
        scene.overlay().showText(75)
                .text("A regular Create shaft sits above the empty stroke space and becomes the engine output")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().topOf(6, 3, 8))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        scene.world().restoreBlocks(boiler);
        scene.world().showIndependentSection(boiler, Direction.WEST);
        scene.effects().indicateSuccess(util.grid().at(4, 1, 4));
        scene.idle(24);
        scene.overlay().showText(90)
                .text("Build a Create boiler beside the engine: Fluid Tanks, Blaze Burners, and a steady water supply")
                .colored(PonderPalette.MEDIUM)
                .pointAt(util.vector().topOf(4, 1, 4))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.world().restoreBlocks(outlet);
        scene.world().showIndependentSection(outlet, Direction.EAST);
        scene.effects().indicateSuccess(util.grid().at(6, 1, 5));
        scene.idle(18);
        scene.overlay().showText(95)
                .text("The Boiler Outlet turns boiler pressure into steam flow")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().centerOf(6, 1, 5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(55);

        scene.world().restoreBlocks(pipeRun);
        scene.world().showIndependentSection(pipeRun, Direction.EAST);
        scene.idle(22);
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

    private static void buildCheckeredBasePlate(CreateSceneBuilder scene) {
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                if (isReservedBasePlateCell(x, z)) {
                    scene.world().setBlock(
                            new BlockPos(x, 0, z),
                            Blocks.AIR.defaultBlockState(),
                            false
                    );
                    continue;
                }

                scene.world().setBlock(
                        new BlockPos(x, 0, z),
                        ((x + z) & 1) == 0
                                ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                                : Blocks.ANDESITE.defaultBlockState(),
                        false
                );
            }
        }
    }

    private static boolean isReservedBasePlateCell(int x, int z) {
        if (x >= 5 && x <= 7 && z >= 7 && z <= 9) {
            return true;
        }
        if (x >= 4 && x <= 5 && z >= 4 && z <= 5) {
            return true;
        }
        return (x == 7 && z == 5) || (x == 8 && z >= 5 && z <= 8);
    }

    private FullSteamPonderScenes() {
    }
}
