package dev.gustavo.fullsteamahead.network;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BoilerBurstPayload(
        double x,
        double y,
        double z,
        float power,
        double networkVolumeM3,
        long seed
) implements CustomPacketPayload {
    public static final Type<BoilerBurstPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FullSteamAhead.MOD_ID, "boiler_burst")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BoilerBurstPayload> STREAM_CODEC = StreamCodec.of(
            BoilerBurstPayload::write,
            BoilerBurstPayload::read
    );

    private static void write(RegistryFriendlyByteBuf buffer, BoilerBurstPayload payload) {
        buffer.writeDouble(payload.x);
        buffer.writeDouble(payload.y);
        buffer.writeDouble(payload.z);
        buffer.writeFloat(payload.power);
        buffer.writeDouble(payload.networkVolumeM3);
        buffer.writeLong(payload.seed);
    }

    private static BoilerBurstPayload read(RegistryFriendlyByteBuf buffer) {
        return new BoilerBurstPayload(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readDouble(),
                buffer.readLong()
        );
    }

    @Override
    public Type<BoilerBurstPayload> type() {
        return TYPE;
    }
}
