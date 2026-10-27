package dev.simlab.server.protocol;

/** Types of the binary messages from rooms to browsers */
public enum MessageType {
    /** state of a simulation after a tick */
    FRAME(1),
    /** what the simulation shows and how its frames are laid out, sent to every viewer before its first frame */
    SCENE(2);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    /** identifies the message type in the header, as an unsigned byte */
    public int id() {
        return id;
    }
}
