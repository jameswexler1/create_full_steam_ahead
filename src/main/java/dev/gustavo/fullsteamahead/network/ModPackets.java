package dev.gustavo.fullsteamahead.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ModPackets {
    private static final String NETWORK_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION)
                .playToClient(BoilerBurstPayload.TYPE, BoilerBurstPayload.STREAM_CODEC, ModPackets::handleBoilerBurst);
    }

    private static void handleBoilerBurst(BoilerBurstPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ClientOnly.handleBoilerBurst(payload);
    }

    private static final class ClientOnly {
        private static void handleBoilerBurst(BoilerBurstPayload payload) {
            dev.gustavo.fullsteamahead.client.BoilerBurstEffects.accept(payload);
        }
    }

    private ModPackets() {
    }
}
