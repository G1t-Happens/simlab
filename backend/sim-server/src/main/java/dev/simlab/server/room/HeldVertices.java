package dev.simlab.server.room;

import dev.simlab.core.Drag;
import dev.simlab.core.Grab;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.Release;
import dev.simlab.core.Selection;
import dev.simlab.core.SimulationInput;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps track of the vertices each viewer holds, so they can be released when the viewer leaves. Not
 * thread-safe
 */
final class HeldVertices {

    private final Map<Viewer, Set<Integer>> byViewer = new HashMap<>();

    void track(Viewer viewer, SimulationInput input) {
        switch (input) {
            case Grab grab ->
                byViewer.computeIfAbsent(viewer, ignored -> new TreeSet<>()).add(grab.vertex());
            case Release release -> {
                Set<Integer> vertices = byViewer.get(viewer);
                if (vertices != null && vertices.remove(release.vertex()) && vertices.isEmpty()) {
                    byViewer.remove(viewer);
                }
            }
            case ParameterChange _, Drag _, Selection _ -> {
                // none of them changes which vertices the viewer holds
            }
        }
    }

    List<Release> releaseAll(Viewer viewer) {
        Set<Integer> vertices = byViewer.remove(viewer);
        if (vertices == null) {
            return List.of();
        }
        return vertices.stream().map(Release::new).toList();
    }
}
