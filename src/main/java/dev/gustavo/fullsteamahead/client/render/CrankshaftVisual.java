package dev.gustavo.fullsteamahead.client.render;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.gustavo.fullsteamahead.content.crankshaft.CrankshaftBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

public class CrankshaftVisual extends AbstractBlockEntityVisual<CrankshaftBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance[] rods = new TransformedInstance[CrankshaftAnimation.ROD_SEGMENTS];
    private final TransformedInstance head;
    private final TransformedInstance crankPin;

    public CrankshaftVisual(VisualizationContext context, CrankshaftBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        for (int segment = 0; segment < rods.length; segment++) {
            rods[segment] = transformed(FullSteamPartialModels.pistonRodProxy());
        }
        head = transformed(FullSteamPartialModels.pistonHeadProxy());
        crankPin = transformed(FullSteamPartialModels.crankPinProxy());
        animate();
    }

    public static void register() {
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.CRANKSHAFT.get(),
                SimpleBlockEntityVisualizer.builder(ModBlockEntities.CRANKSHAFT.get())
                        .factory(CrankshaftVisual::new)
                        .skipVanillaRender(crankshaft -> true)
                        .apply()
        );
    }

    @Override
    public void beginFrame(DynamicVisual.Context context) {
        animate();
    }

    @Override
    public void updateLight(float partialTick) {
        FlatLit[] instances = new FlatLit[rods.length + 2];
        System.arraycopy(rods, 0, instances, 0, rods.length);
        instances[rods.length] = head;
        instances[rods.length + 1] = crankPin;
        relight(instances);
    }

    @Override
    protected void _delete() {
        for (TransformedInstance rod : rods) {
            rod.delete();
        }
        head.delete();
        crankPin.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        for (TransformedInstance rod : rods) {
            consumer.accept(rod);
        }
        consumer.accept(head);
        consumer.accept(crankPin);
    }

    private void animate() {
        CrankshaftAnimation.State animation = CrankshaftAnimation.state(blockEntity);
        for (int segment = 0; segment < rods.length; segment++) {
            setVisible(rods[segment], animation.visible());
            if (animation.visible()) {
                base(rods[segment])
                        .translate(0, animation.rodY(segment), 0)
                        .setChanged();
            }
        }

        setVisible(head, animation.visible());
        setVisible(crankPin, animation.visible());
        if (!animation.visible()) {
            return;
        }

        base(head)
                .translate(0, animation.headY(), 0)
                .setChanged();
        base(crankPin)
                .center()
                .rotateY(animation.angle())
                .uncenter()
                .setChanged();
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

    private static void setVisible(TransformedInstance instance, boolean visible) {
        instance.setVisible(visible);
    }
}
