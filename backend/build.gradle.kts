plugins {
    // loads the convention plugins once for all projects so every project shares the same plugin classes,
    // including the build services of Spotless
    id("simlab.java-conventions") apply false
}
