import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.process.CommandLineArgumentProvider

/**
 * Attaches Mockito as a Java agent to a test JVM if [mockitoCore] contains its jar.
 *
 * Mockito has to be attached at JVM startup, self-attaching at runtime is deprecated since JDK 21 (JEP 451)
 * and will be disallowed in a future release. Class data sharing is off for such a JVM because Mockito's
 * inline mock maker appends to the bootstrap class path, which makes the JVM disable it anyway (with a
 * warning on every run)
 */
class MockitoAgentArgumentProvider(@get:Classpath val mockitoCore: FileCollection) :
    CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        if (mockitoCore.isEmpty) {
            emptyList()
        } else {
            listOf("-javaagent:${mockitoCore.singleFile.absolutePath}", "-Xshare:off")
        }
}
