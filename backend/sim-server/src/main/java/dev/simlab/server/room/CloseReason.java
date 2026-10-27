package dev.simlab.server.room;

public enum CloseReason {
    /** the last viewer left */
    ABANDONED,
    /** nobody joined the room in time */
    NOT_JOINED,
    FAILED,
    SHUTDOWN
}
