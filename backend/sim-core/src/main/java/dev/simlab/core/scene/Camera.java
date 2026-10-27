package dev.simlab.core.scene;

/**
 * Where the camera starts out. The pointer turns the camera around its target and the mouse wheel moves it
 * closer or farther away
 *
 * @param target the point the camera looks at and turns around
 * @param up points up in the scene and stays up on the screen
 * @param orbiting whether the camera slowly circles its target by itself
 */
public record Camera(Vector3 position, Vector3 target, Vector3 up, boolean orbiting) {

    public Camera {
        double sightX = target.x() - position.x();
        double sightY = target.y() - position.y();
        double sightZ = target.z() - position.z();
        if (sightX == 0 && sightY == 0 && sightZ == 0) {
            throw new IllegalArgumentException("The camera must not be at its target");
        }
        // cross product vanishes if up is zero or parallel to the line of sight
        double crossX = sightY * up.z() - sightZ * up.y();
        double crossY = sightZ * up.x() - sightX * up.z();
        double crossZ = sightX * up.y() - sightY * up.x();
        if (crossX == 0 && crossY == 0 && crossZ == 0) {
            throw new IllegalArgumentException(
                    "The up direction must neither be zero nor point along the line of sight");
        }
    }
}
