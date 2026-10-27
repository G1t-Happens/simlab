package dev.simlab.core.scene;

import java.util.List;
import java.util.Optional;

/**
 * What a simulation shows: the objects in view, the camera that shows them, and the objects of an x-ray,
 * which show what goes on inside the simulation while the viewer looks through it.
 *
 * <p>Every frame holds the floats of one object after another, in the order of the objects (see
 * {@link SceneObject#frameFloats()}). Frames that show the x-ray hold the floats of its objects after those,
 * in their order. The points of a scene are numbered across all its {@linkplain PointCloud point clouds}, in
 * the order of the objects
 *
 * @param xray objects of the x-ray in the order of their floats in frames that show it, none if the
 *     simulation offers no x-ray. Neither readouts nor objects the pointer grabs or selects
 */
public record Scene(Camera camera, List<SceneObject> objects, List<SceneObject> xray) {

    public Scene {
        objects = List.copyOf(objects);
        xray = List.copyOf(xray);
        if (objects.stream().filter(Scene::isGrabbable).count() > 1) {
            throw new IllegalArgumentException(
                    "A scene has at most one grabbable mesh, as inputs name a vertex but not its mesh");
        }
        if (objects.stream().filter(Readouts.class::isInstance).count() > 1) {
            throw new IllegalArgumentException(
                    "A scene has at most one object of readouts, as lessons name a readout but not its object");
        }
        if (xray.stream()
                .anyMatch(object -> object instanceof Readouts || isGrabbable(object) || isSelectable(object))) {
            throw new IllegalArgumentException("The x-ray shows neither readouts nor objects that the pointer grabs or"
                    + " selects, which are always in view");
        }
    }

    public Scene(Camera camera, List<SceneObject> objects) {
        this(camera, objects, List.of());
    }

    /** Floats in every frame that doesn't show the x-ray */
    public int frameFloats() {
        return floats(objects);
    }

    /** Floats the x-ray adds to the frames that show it */
    public int xrayFloats() {
        return floats(xray);
    }

    public Optional<Mesh> grabbableMesh() {
        return objects.stream().filter(Scene::isGrabbable).map(Mesh.class::cast).findFirst();
    }

    public Optional<Readouts> readouts() {
        return objects.stream()
                .filter(Readouts.class::isInstance)
                .map(Readouts.class::cast)
                .findFirst();
    }

    public boolean hasSelectablePoints() {
        return objects.stream().anyMatch(Scene::isSelectable);
    }

    /**
     * Whether a point belongs to a selectable point cloud
     *
     * @param point index among the points of all point clouds of the scene
     */
    public boolean isSelectable(int point) {
        int first = 0;
        for (SceneObject object : objects) {
            if (object instanceof PointCloud cloud) {
                if (point >= first && point < first + cloud.count()) {
                    return cloud.selectable();
                }
                first += cloud.count();
            }
        }
        return false;
    }

    private static int floats(List<SceneObject> objects) {
        return objects.stream().mapToInt(SceneObject::frameFloats).reduce(0, Math::addExact);
    }

    private static boolean isGrabbable(SceneObject object) {
        return object instanceof Mesh mesh && mesh.grabbable();
    }

    private static boolean isSelectable(SceneObject object) {
        return object instanceof PointCloud cloud && cloud.selectable();
    }
}
