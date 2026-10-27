package dev.simlab.server.lesson;

import java.net.URI;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Links code to its lines on GitHub, at the commit the server was built from
 *
 * @param repository like {@code https://github.com/G1t-Happens/simlab}, {@code null} if unknown
 * @param commit {@code null} if unknown
 */
record SourceLinks(@Nullable URI repository, @Nullable String commit) {

    static final String SOURCE_ROOT = "backend/sim-core/src/main/java/";

    /**
     * Link to lines of a source file, if the repository and the commit are known
     *
     * @param path below the source root
     */
    Optional<URI> link(String path, int firstLine, int lastLine) {
        if (repository == null || commit == null) {
            return Optional.empty();
        }
        return Optional.of(URI.create(
                "%s/blob/%s/%s%s#L%d-L%d".formatted(repository, commit, SOURCE_ROOT, path, firstLine, lastLine)));
    }
}
