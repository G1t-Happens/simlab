package dev.simlab.server.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SourceLinksTest {

    private static final URI REPOSITORY = URI.create("https://github.com/G1t-Happens/simlab");

    @Test
    void linksToTheLinesOfASourceFileAtTheCommit() {
        var links = new SourceLinks(REPOSITORY, "99f38b6");

        assertThat(links.link("dev/simlab/core/toolkit/ode/RungeKutta4.java", 40, 72))
                .contains(URI.create("https://github.com/G1t-Happens/simlab/blob/99f38b6/backend/sim-core/src/main/java"
                        + "/dev/simlab/core/toolkit/ode/RungeKutta4.java#L40-L72"));
    }

    @Test
    void linksNowhereWithoutRepositoryOrCommit() {
        assertThat(new SourceLinks(null, "99f38b6").link("dev/simlab/core/Simulation.java", 1, 2))
                .isEmpty();
        assertThat(new SourceLinks(REPOSITORY, null).link("dev/simlab/core/Simulation.java", 1, 2))
                .isEmpty();
    }

    @Test
    void findsTheSourcesOfSimCoreWhereTheRepositoryHoldsThem() {
        // tests run in the directory of sim-server, two levels below the root of the repository
        assertThat(Path.of("../..", SourceLinks.SOURCE_ROOT, "dev/simlab/core/Simulation.java"))
                .isRegularFile();
    }
}
