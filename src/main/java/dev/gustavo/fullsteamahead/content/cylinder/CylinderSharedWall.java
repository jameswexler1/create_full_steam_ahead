package dev.gustavo.fullsteamahead.content.cylinder;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum CylinderSharedWall implements StringRepresentable {
    NONE,
    STRIP_X,
    STRIP_Z;

    private final String serializedName;

    CylinderSharedWall() {
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
