package dev.gustavo.fullsteamahead.content.redstone;

import java.util.UUID;

/** A loaded device that shares an analogue position over an Engine Order Telegraph channel. */
public interface TelegraphLinkable {
    UUID getLinkId();

    int getTelegraphState();

    /** Applies a channel update without transmitting it again. */
    void receiveTelegraphState(int state);
}
