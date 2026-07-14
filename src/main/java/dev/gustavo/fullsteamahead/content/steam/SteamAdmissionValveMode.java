package dev.gustavo.fullsteamahead.content.steam;

import net.minecraft.util.StringRepresentable;

public enum SteamAdmissionValveMode implements StringRepresentable {
    UNLINKED("unlinked"),
    TERMINAL_STRAIGHT("terminal_straight"),
    TERMINAL_CLOCKWISE("terminal_clockwise"),
    TERMINAL_COUNTERCLOCKWISE("terminal_counterclockwise"),
    THROUGH_BRANCH("through_branch");

    private final String serializedName;

    SteamAdmissionValveMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public SteamAdmissionValveMode mirrored() {
        return switch (this) {
            case TERMINAL_CLOCKWISE -> TERMINAL_COUNTERCLOCKWISE;
            case TERMINAL_COUNTERCLOCKWISE -> TERMINAL_CLOCKWISE;
            default -> this;
        };
    }
}
