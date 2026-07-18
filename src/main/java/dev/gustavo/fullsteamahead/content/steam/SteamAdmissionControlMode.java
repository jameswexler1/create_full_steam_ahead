package dev.gustavo.fullsteamahead.content.steam;

import net.minecraft.util.StringRepresentable;

public enum SteamAdmissionControlMode implements StringRepresentable {
    MANUAL("manual"),
    REDSTONE_LINK("redstone_link");

    private final String serializedName;

    SteamAdmissionControlMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public SteamAdmissionControlMode next() {
        return this == MANUAL ? REDSTONE_LINK : MANUAL;
    }

    public static SteamAdmissionControlMode fromSerializedName(String name) {
        for (SteamAdmissionControlMode mode : values()) {
            if (mode.serializedName.equals(name)) {
                return mode;
            }
        }
        return REDSTONE_LINK;
    }
}
