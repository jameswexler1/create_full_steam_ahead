package dev.gustavo.fullsteamahead.content.piston;

import net.minecraft.util.StringRepresentable;

public enum PistonSection implements StringRepresentable {
    INSIDE_LOW("inside_low"),
    INSIDE_HIGH("inside_high"),
    PROTRUDE_LOW("protrude_low"),
    PROTRUDE_HIGH("protrude_high");

    private final String name;

    PistonSection(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
