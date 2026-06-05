package dev.gustavo.fullsteamahead.client.render;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.gustavo.fullsteamahead.content.piston.EngineValidator;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

public class PistonHeadVisual extends AbstractBlockEntityVisual<PistonHeadBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance[] pistons = new TransformedInstance[PistonHeadAnimation.MAX_PISTON_BLOCKS];
    private final TransformedInstance head;
    private final TransformedInstance[] connectingRods = new TransformedInstance[PistonHeadAnimation.MAX_PISTON_BLOCKS];
    private final TransformedInstance[] cranks = new TransformedInstance[PistonHeadAnimation.MAX_PISTON_BLOCKS];

    public PistonHeadVisual(VisualizationContext context, PistonHeadBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        for (int blockIndex = 0; blockIndex < pistons.length; blockIndex++) {
            pistons[blockIndex] = transformed(FullSteamPartialModels.pistonBody());
        }
        head = transformed(FullSteamPartialModels.pistonHead());
        for (int bodyCount = EngineValidator.MIN_PISTON_BODIES;
             bodyCount <= EngineValidator.MAX_PISTON_BODIES;
             bodyCount++) {
            int index = bodyCount - 1;
            connectingRods[index] = transformed(FullSteamPartialModels.connectingRod(bodyCount));
            cranks[index] = transformed(FullSteamPartialModels.crank(bodyCount));
        }
        animate();
    }

    public static void register() {
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.PISTON_HEAD.get(),
                SimpleBlockEntityVisualizer.builder(ModBlockEntities.PISTON_HEAD.get())
                        .factory(PistonHeadVisual::new)
                        .skipVanillaRender(engine -> true)
                        .apply()
        );
    }

    @Override
    public void beginFrame(DynamicVisual.Context context) {
        animate();
    }

    @Override
    public void updateLight(float partialTick) {
        BlockPos base = blockEntity.getBlockPos();
        PistonHeadAnimation.State animation = PistonHeadAnimation.state(blockEntity);
        Direction strokeDirection = animation.strokeDirection();
        relight(partLightPos(base, animation.headY(), strokeDirection), head);
        for (int blockIndex = 0; blockIndex < pistons.length; blockIndex++) {
            relight(partLightPos(base, animation.pistonY(blockIndex), strokeDirection), pistons[blockIndex]);
        }
        relight(partLightPos(base, animation.connectingRodY(), strokeDirection), connectingRods);
        relight(base.relative(strokeDirection, animation.shaftDistance()), cranks);
    }

    @Override
    protected void _delete() {
        for (TransformedInstance piston : pistons) {
            piston.delete();
        }
        head.delete();
        for (TransformedInstance connectingRod : connectingRods) {
            connectingRod.delete();
        }
        for (TransformedInstance crank : cranks) {
            crank.delete();
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        for (TransformedInstance piston : pistons) {
            consumer.accept(piston);
        }
        consumer.accept(head);
        for (TransformedInstance connectingRod : connectingRods) {
            consumer.accept(connectingRod);
        }
        for (TransformedInstance crank : cranks) {
            consumer.accept(crank);
        }
    }

    private void animate() {
        PistonHeadAnimation.State animation = PistonHeadAnimation.state(blockEntity);
        for (int blockIndex = 0; blockIndex < pistons.length; blockIndex++) {
            boolean pistonVisible = animation.visible() && blockIndex < animation.pistonBodyCount();
            setVisible(pistons[blockIndex], pistonVisible);
            if (pistonVisible) {
                TransformedInstance piston = base(pistons[blockIndex]);
                orientForStroke(piston, animation);
                rotatePistonBody(
                        piston.translate(0, animation.pistonY(blockIndex), 0),
                        animation.shaftAxis()
                ).setChanged();
            }
        }

        setVisible(head, animation.visible());
        for (int index = 0; index < connectingRods.length; index++) {
            boolean activeSize = index == animation.pistonBodyCount() - 1;
            setVisible(connectingRods[index], animation.visible() && activeSize);
            setVisible(cranks[index], animation.visible() && activeSize);
        }
        if (!animation.visible()) {
            return;
        }

        TransformedInstance headInstance = base(head);
        orientForStroke(headInstance, animation);
        headInstance.translate(0, animation.headY(), 0).setChanged();

        int linkageIndex = animation.pistonBodyCount() - 1;
        TransformedInstance rodInstance = base(connectingRods[linkageIndex]);
        orientForStroke(rodInstance, animation);
        rotateConnectingRod(
                rodInstance.translate(0, animation.connectingRodY(), 0),
                animation
        ).setChanged();

        TransformedInstance crankInstance = base(cranks[linkageIndex]);
        orientForStroke(crankInstance, animation);
        rotateCrank(
                crankInstance.translate(0, animation.crankY(), 0),
                animation
        ).setChanged();
    }

    private TransformedInstance transformed(PartialModel partial) {
        return instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(partial))
                .createInstance();
    }

    private TransformedInstance base(TransformedInstance instance) {
        BlockPos visualPosition = getVisualPosition();
        return instance
                .setIdentityTransform()
                .translate(visualPosition.getX(), visualPosition.getY(), visualPosition.getZ());
    }

    private static TransformedInstance rotateConnectingRod(
            TransformedInstance instance,
            PistonHeadAnimation.State animation
    ) {
        instance.center();
        yawLinkageFrame(instance, animation.shaftAxis());
        instance.uncenter();
        instance.translate(0.5F, PistonHeadAnimation.CONNECTING_ROD_SMALL_END_Y, 0.5F);
        instance.rotateX(animation.connectingRodRotation());
        return instance.translate(-0.5F, -PistonHeadAnimation.CONNECTING_ROD_SMALL_END_Y, -0.5F);
    }

    private static TransformedInstance rotateCrank(
            TransformedInstance instance,
            PistonHeadAnimation.State animation
    ) {
        instance.center();
        yawLinkageFrame(instance, animation.shaftAxis());
        instance.rotateX(animation.crankRotation());
        return instance.uncenter();
    }

    private static TransformedInstance rotatePistonBody(TransformedInstance instance, Direction.Axis axis) {
        instance.center();
        yawPistonBodyFrame(instance, axis);
        return instance.uncenter();
    }

    // Applied right after base() and before per-part positioning so it remains the outermost
    // local transform: the upright-posed linkage is rigidly flipped 180 degrees about the head
    // block center, inverting head, piston, rod, and crank together with their joints intact.
    private static TransformedInstance orientForStroke(
            TransformedInstance instance,
            PistonHeadAnimation.State animation
    ) {
        if (animation.strokeDirection() == Direction.DOWN) {
            instance.center();
            instance.rotateX((float) Math.PI);
            instance.uncenter();
        }
        return instance;
    }

    private static TransformedInstance yawPistonBodyFrame(TransformedInstance instance, Direction.Axis axis) {
        if (axis == Direction.Axis.X) {
            instance.rotateY((float) (-Math.PI / 2.0D));
        }
        return instance;
    }

    private static TransformedInstance yawLinkageFrame(TransformedInstance instance, Direction.Axis axis) {
        if (axis == Direction.Axis.Z) {
            instance.rotateY((float) (-Math.PI / 2.0D));
        }
        return instance;
    }

    private static void setVisible(TransformedInstance instance, boolean visible) {
        instance.setVisible(visible);
    }

    private static BlockPos partLightPos(BlockPos basePos, float y, Direction strokeDirection) {
        int maxDistance = EngineValidator.shaftDistanceForPistonBodies(EngineValidator.MAX_PISTON_BODIES);
        int blockOffset = Math.max(0, Math.min(maxDistance, Math.round(Math.abs(y))));
        return basePos.relative(strokeDirection, blockOffset);
    }
}
