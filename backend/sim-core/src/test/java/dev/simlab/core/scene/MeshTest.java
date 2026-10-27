package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.simlab.core.scene.Material.Finish;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MeshTest {

    private static final Material GRAY = new Material(Color.rgb(0x808080), Finish.MATTE);

    private static final List<Integer> SQUARE = List.of(0, 1, 2, 0, 2, 3);

    @Test
    void takesUpThreeFloatsPerVertex() {
        assertThat(new Mesh(4, SQUARE, GRAY, false).frameFloats()).isEqualTo(12);
    }

    @Test
    void keepsItsTrianglesFromChanging() {
        List<Integer> triangles = new ArrayList<>(SQUARE);
        var mesh = new Mesh(4, triangles, GRAY, false);

        triangles.clear();

        assertThat(mesh.triangles()).isEqualTo(SQUARE);
    }

    static List<List<Integer>> incompleteTriangles() {
        return List.of(List.of(), List.of(0, 1), List.of(0, 1, 2, 3));
    }

    @ParameterizedTest
    @MethodSource("incompleteTriangles")
    void needsAtLeastOneWholeTriangle(List<Integer> triangles) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Mesh(4, triangles, GRAY, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 4})
    void rejectsIndicesOfVerticesThatItDoesNotHave(int index) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Mesh(4, List.of(0, 1, index), GRAY, false))
                .withMessage("There is no vertex %d among the 4 vertices of the mesh", index);
    }
}
