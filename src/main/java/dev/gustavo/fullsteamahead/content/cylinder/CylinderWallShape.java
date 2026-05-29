package dev.gustavo.fullsteamahead.content.cylinder;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum CylinderWallShape implements StringRepresentable {
    STANDALONE,
    STRAIGHT_X,
    STRAIGHT_Z;

    private final String serializedName;

    CylinderWallShape() {
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
