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
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

public class PistonHeadVisual extends AbstractBlockEntityVisual<PistonHeadBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance[] pistons = new TransformedInstance[PistonHeadAnimation.PISTON_BLOCKS];
    private final TransformedInstance head;
    private final TransformedInstance connectingRod;
    private final TransformedInstance crank;

    public PistonHeadVisual(VisualizationContext context, PistonHeadBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        for (int blockIndex = 0; blockIndex < pistons.length; blockIndex++) {
            pistons[blockIndex] = transformed(FullSteamPartialModels.pistonBody());
        }
        head = transformed(FullSteamPartialModels.pistonHead());
        connectingRod = transformed(FullSteamPartialModels.connectingRod());
        crank = transformed(FullSteamPartialModels.crank());
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
        relight(partLightPos(base, animation.headY()), head);
        relight(partLightPos(base, animation.pistonY(0)), pistons);
        relight(partLightPos(base, animation.connectingRodY()), connectingRod);
        relight(base.relative(animation.strokeDirection(), 3), crank);
    }

    @Override
    protected void _delete() {
        for (TransformedInstance piston : pistons) {
            piston.delete();
        }
        head.delete();
        connectingRod.delete();
        crank.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        for (TransformedInstance piston : pistons) {
            consumer.accept(piston);
        }
        consumer.accept(head);
        consumer.accept(connectingRod);
        consumer.accept(crank);
    }

    private void animate() {
        PistonHeadAnimation.State animation = PistonHeadAnimation.state(blockEntity);
        for (int blockIndex = 0; blockIndex < pistons.length; blockIndex++) {
            setVisible(pistons[blockIndex], animation.visible());
            if (animation.visible()) {
                orientForStroke(rotatePistonBody(
                        base(pistons[blockIndex])
                                .translate(0, animation.pistonY(blockIndex), 0),
                        animation.shaftAxis()
                ), animation).setChanged();
            }
        }

        setVisible(head, animation.visible());
        setVisible(connectingRod, animation.visible());
        setVisible(crank, animation.visible());
        if (!animation.visible()) {
            return;
        }

        orientForStroke(
                base(head).translate(0, animation.headY(), 0),
                animation
        )
                .setChanged();
        orientForStroke(rotateConnectingRod(
                base(connectingRod)
                        .translate(0, animation.connectingRodY(), 0),
                animation
        ), animation).setChanged();
        orientForStroke(rotateCrank(
                base(crank)
                        .translate(0, animation.crankY(), 0),
                animation
        ), animation).setChanged();
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

    private static BlockPos partLightPos(BlockPos basePos, float y) {
        int blockOffset = Math.max(0, Math.min(3, Math.round(Math.abs(y))));
        Direction direction = y < 0 ? Direction.DOWN : Direction.UP;
        return basePos.relative(direction, blockOffset);
    }
}
