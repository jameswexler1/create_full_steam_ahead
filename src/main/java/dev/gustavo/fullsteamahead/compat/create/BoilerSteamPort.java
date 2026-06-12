package dev.gustavo.fullsteamahead.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record BoilerSteamPort(Type type, BlockPos pos, Direction direction) implements Comparable<BoilerSteamPort> {
    public static BoilerSteamPort outlet(BlockPos pos, Direction direction) {
        return new BoilerSteamPort(Type.PHYSICAL_OUTLET, pos.immutable(), direction);
    }

    public static BoilerSteamPort directPipe(BlockPos tankPos, Direction direction) {
        return new BoilerSteamPort(Type.DIRECT_PIPE, tankPos.immutable(), direction);
    }

    public BlockPos pipePos() {
        return pos.relative(direction);
    }

    @Override
    public int compareTo(BoilerSteamPort other) {
        int typeCompare = Integer.compare(type.ordinal(), other.type.ordinal());
        if (typeCompare != 0) {
            return typeCompare;
        }
        int yCompare = Integer.compare(pos.getY(), other.pos.getY());
        if (yCompare != 0) {
            return yCompare;
        }
        int xCompare = Integer.compare(pos.getX(), other.pos.getX());
        if (xCompare != 0) {
            return xCompare;
        }
        int zCompare = Integer.compare(pos.getZ(), other.pos.getZ());
        if (zCompare != 0) {
            return zCompare;
        }
        return Integer.compare(direction.ordinal(), other.direction.ordinal());
    }

    public enum Type {
        PHYSICAL_OUTLET,
        DIRECT_PIPE
    }
}
