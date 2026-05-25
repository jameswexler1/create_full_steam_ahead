package dev.gustavo.fullsteamahead.content.cylinder;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum CylinderSection implements StringRepresentable {
    NONE(-1, -1, -1),
    LOWER_NORTH_WEST(0, 0, 0),
    LOWER_NORTH(1, 0, 0),
    LOWER_NORTH_EAST(2, 0, 0),
    LOWER_WEST(0, 0, 1),
    LOWER_EAST(2, 0, 1),
    LOWER_SOUTH_WEST(0, 0, 2),
    LOWER_SOUTH(1, 0, 2),
    LOWER_SOUTH_EAST(2, 0, 2),
    UPPER_NORTH_WEST(0, 1, 0),
    UPPER_NORTH(1, 1, 0),
    UPPER_NORTH_EAST(2, 1, 0),
    UPPER_WEST(0, 1, 1),
    UPPER_EAST(2, 1, 1),
    UPPER_SOUTH_WEST(0, 1, 2),
    UPPER_SOUTH(1, 1, 2),
    UPPER_SOUTH_EAST(2, 1, 2);

    private final int xOffset;
    private final int yOffset;
    private final int zOffset;
    private final String serializedName;

    CylinderSection(int xOffset, int yOffset, int zOffset) {
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    public int xOffset() {
        return xOffset;
    }

    public int yOffset() {
        return yOffset;
    }

    public int zOffset() {
        return zOffset;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static CylinderSection fromOffsets(int xOffset, int yOffset, int zOffset) {
        for (CylinderSection section : values()) {
            if (section.xOffset == xOffset && section.yOffset == yOffset && section.zOffset == zOffset) {
                return section;
            }
        }
        return NONE;
    }
}
