package dev.gustavo.fullsteamahead.content.cylinder;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum CylinderWallShape implements StringRepresentable {
    STANDALONE,
    STRAIGHT_X,
    STRAIGHT_Z,
    CORNER_NORTH_EAST,
    CORNER_SOUTH_EAST,
    CORNER_SOUTH_WEST,
    CORNER_NORTH_WEST,
    SHARED_STRIP_X,
    SHARED_STRIP_Z;

    private final String serializedName;

    CylinderWallShape() {
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
