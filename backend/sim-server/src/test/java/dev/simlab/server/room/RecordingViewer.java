package dev.simlab.server.room;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class RecordingViewer implements Viewer {

    private final List<ByteBuffer> messages = new ArrayList<>();
    private @Nullable CloseReason disconnectReason;

    @Override
    public void send(ByteBuffer message) {
        messages.add(message);
    }

    @Override
    public void disconnect(CloseReason reason) {
        disconnectReason = reason;
    }

    List<ByteBuffer> messages() {
        return List.copyOf(messages);
    }

    @Nullable CloseReason disconnectReason() {
        return disconnectReason;
    }
}
