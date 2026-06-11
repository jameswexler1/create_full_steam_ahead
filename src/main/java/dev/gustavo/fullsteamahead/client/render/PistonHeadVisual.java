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
    private final TransformedInstance[] finalPistons = new TransformedInstance[PistonHeadAnimation.MAX_PISTON_BLOCKS];
    private final TransformedInstance[] intermediatePistons =
            new TransformedInstance[PistonHeadAnimation.MAX_PISTON_BLOCKS];
    private final TransformedInstance head;
    private final TransformedInstance rodLower;
    private final TransformedInstance[] rodMiddles =
            new TransformedInstance[PistonHeadAnimation.maxConnectingRodMiddleSegments()];
    private final TransformedInstance rodUpper;
    private final TransformedInstance[] cranks = new TransformedInstance[PistonHeadAnimation.MAX_PISTON_BLOCKS];

    public PistonHeadVisual(VisualizationContext context, PistonHeadBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        for (int blockIndex = 0; blockIndex < finalPistons.length; blockIndex++) {
            finalPistons[blockIndex] = transformed(FullSteamPartialModels.pistonBody());
            intermediatePistons[blockIndex] = transformed(FullSteamPartialModels.pistonBodyIntermediate());
        }
        head = transformed(FullSteamPartialModels.pistonHead());
        rodLower = transformed(FullSteamPartialModels.connectingRodLower());
        for (int segmentIndex = 0; segmentIndex < rodMiddles.length; segmentIndex++) {
            rodMiddles[segmentIndex] = transformed(FullSteamPartialModels.connectingRodMiddle());
        }
        rodUpper = transformed(FullSteamPartialModels.connectingRodUpper());
        for (int shaftGap = EngineValidator.MIN_SHAFT_GAP;
             shaftGap <= EngineValidator.MAX_SHAFT_GAP;
             shaftGap++) {
            int index = shaftGap - 1;
            cranks[index] = transformed(FullSteamPartialModels.crank(shaftGap));
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
        for (int blockIndex = 0; blockIndex < finalPistons.length; blockIndex++) {
            BlockPos pistonLightPos = partLightPos(base, animation.pistonY(blockIndex), strokeDirection);
            relight(pistonLightPos, finalPistons[blockIndex]);
            relight(pistonLightPos, intermediatePistons[blockIndex]);
        }
        relight(partLightPos(base, animation.connectingRodY(), strokeDirection), rodLower);
        relight(partLightPos(base, animation.connectingRodY(), strokeDirection), rodMiddles);
        relight(partLightPos(base, animation.connectingRodY(), strokeDirection), rodUpper);
        relight(base.relative(strokeDirection, animation.shaftDistance()), cranks);
    }

    @Override
    protected void _delete() {
        for (TransformedInstance piston : finalPistons) {
            piston.delete();
        }
        for (TransformedInstance piston : intermediatePistons) {
            piston.delete();
        }
        head.delete();
        rodLower.delete();
        for (TransformedInstance rodMiddle : rodMiddles) {
            rodMiddle.delete();
        }
        rodUpper.delete();
        for (TransformedInstance crank : cranks) {
            crank.delete();
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        for (TransformedInstance piston : finalPistons) {
            consumer.accept(piston);
        }
        for (TransformedInstance piston : intermediatePistons) {
            consumer.accept(piston);
        }
        consumer.accept(head);
        consumer.accept(rodLower);
        for (TransformedInstance rodMiddle : rodMiddles) {
            consumer.accept(rodMiddle);
        }
        consumer.accept(rodUpper);
        for (TransformedInstance crank : cranks) {
            consumer.accept(crank);
        }
    }

    private void animate() {
        PistonHeadAnimation.State animation = PistonHeadAnimation.state(blockEntity);
        for (int blockIndex = 0; blockIndex < finalPistons.length; blockIndex++) {
            boolean occupied = animation.visible() && blockIndex < animation.pistonBodyCount();
            boolean finalBody = occupied && animation.isRodConnectionPiston(blockIndex);
            setVisible(finalPistons[blockIndex], finalBody);
            setVisible(intermediatePistons[blockIndex], occupied && !finalBody);
            if (occupied) {
                TransformedInstance piston = base(finalBody
                        ? finalPistons[blockIndex]
                        : intermediatePistons[blockIndex]);
                orientForStroke(piston, animation);
                rotatePistonBody(
                        piston.translate(0, animation.pistonY(blockIndex), 0),
                        animation.shaftAxis()
                ).setChanged();
            }
        }

        setVisible(head, animation.visible());
        setVisible(rodLower, animation.visible());
        setVisible(rodUpper, animation.visible());
        for (int segmentIndex = 0; segmentIndex < rodMiddles.length; segmentIndex++) {
            setVisible(
                    rodMiddles[segmentIndex],
                    animation.visible()
                            && segmentIndex < animation.connectingRodMiddleSegments()
            );
        }
        for (int index = 0; index < cranks.length; index++) {
            boolean activeSize = index == animation.shaftGap() - 1;
            setVisible(cranks[index], animation.visible() && activeSize);
        }
        if (!animation.visible()) {
            return;
        }

        TransformedInstance headInstance = base(head);
        orientForStroke(headInstance, animation);
        headInstance.translate(0, animation.headY(), 0).setChanged();

        animateConnectingRodPart(rodLower, animation, 0);
        for (int segmentIndex = 0;
             segmentIndex < animation.connectingRodMiddleSegments();
             segmentIndex++) {
            animateConnectingRodPart(
                    rodMiddles[segmentIndex],
                    animation,
                    PistonHeadAnimation.connectingRodMiddleOffset(segmentIndex)
            );
        }
        animateConnectingRodPart(
                rodUpper,
                animation,
                animation.connectingRodUpperOffset()
        );

        int linkageIndex = animation.shaftGap() - 1;
        TransformedInstance crankInstance = base(cranks[linkageIndex]);
        orientForStroke(crankInstance, animation);
        rotateCrank(
                crankInstance.translate(0, animation.crankY(), 0),
                animation
        ).setChanged();
    }

    private void animateConnectingRodPart(
            TransformedInstance instance,
            PistonHeadAnimation.State animation,
            float localYOffset
    ) {
        TransformedInstance rodInstance = base(instance);
        orientForStroke(rodInstance, animation);
        rotateConnectingRod(
                rodInstance.translate(0, animation.connectingRodY(), 0),
                animation,
                localYOffset
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
            PistonHeadAnimation.State animation,
            float localYOffset
    ) {
        instance.center();
        yawLinkageFrame(instance, animation.shaftAxis());
        instance.uncenter();
        instance.translate(0.5F, PistonHeadAnimation.CONNECTING_ROD_SMALL_END_Y, 0.5F);
        instance.rotateX(animation.connectingRodRotation());
        return instance
                .translate(-0.5F, -PistonHeadAnimation.CONNECTING_ROD_SMALL_END_Y, -0.5F)
                .translate(0, localYOffset, 0);
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
        int maxDistance = EngineValidator.shaftDistanceForPistonBodies(
                EngineValidator.MAX_PISTON_BODIES,
                EngineValidator.MAX_SHAFT_GAP
        );
        int blockOffset = Math.max(0, Math.min(maxDistance, Math.round(Math.abs(y))));
        return basePos.relative(strokeDirection, blockOffset);
    }
}
