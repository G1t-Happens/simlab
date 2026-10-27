package dev.simlab.server.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import dev.simlab.core.simulations.lorenz.LorenzDefinition;
import dev.simlab.server.lesson.Lesson.Code;
import dev.simlab.server.simulation.SimulationService;
import java.net.URI;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LessonServiceTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(LorenzDefinition.class)
            .withBean(SimulationService.class)
            .withBean(
                    LessonProperties.class,
                    () -> new LessonProperties(URI.create("https://github.com/G1t-Happens/simlab")))
            .withBean(LessonService.class);

    @Test
    void findsTheLessonOfEverySimulationByItsType() {
        contextRunner.run(context -> {
            LessonService lessons = context.getBean(LessonService.class);

            assertThat(lessons.find("lorenz")).isPresent();
            assertThat(lessons.find("quantum")).isEmpty();
        });
    }

    @Test
    void linksTheCodeToItsLinesOnGitHubAtTheCommitOfTheBuild() {
        var build = new Properties();
        build.setProperty("commit.id", "f00d");
        String sourcesAtCommit = "https://github.com/G1t-Happens/simlab/blob/f00d/backend/sim-core/src/main/java/";

        contextRunner
                .withBean(GitProperties.class, () -> new GitProperties(build))
                .run(context -> assertThat(code(context))
                        .isNotEmpty()
                        .allSatisfy(
                                code -> assertThat(String.valueOf(code.url())).startsWith(sourcesAtCommit)));
    }

    @Test
    void linksNowhereWithoutTheGitInformationOfTheBuild() {
        contextRunner.run(context -> assertThat(code(context))
                .isNotEmpty()
                .allSatisfy(code -> assertThat(code.url()).isNull()));
    }

    private static List<Code> code(AssertableApplicationContext context) {
        return context.getBean(LessonService.class).find("lorenz").orElseThrow().blocks().stream()
                .filter(Code.class::isInstance)
                .map(Code.class::cast)
                .toList();
    }
}
