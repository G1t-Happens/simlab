package dev.simlab.server.room;

import java.nio.ByteBuffer;

public interface Viewer {

    /**
     * Sends an encoded message. The tick loop of the room calls this, so implementations must neither block
     * for long nor throw
     *
     * @param message a buffer for this viewer alone, positioned at the start of the message
     */
    void send(ByteBuffer message);

    void disconnect(CloseReason reason);
}
