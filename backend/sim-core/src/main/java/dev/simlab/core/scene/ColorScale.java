package dev.simlab.core.scene;

import java.util.List;

/**
 * Colors for the values of a quantity, and what the legend explaining them says. The colors spread evenly
 * from the smallest to the largest value, a value in between blends the two colors next to it and a value
 * beyond the ends takes the color of the nearer end
 *
 * @param label the quantity in the legend, like a symbol
 * @param unit unit of the values like {@code %}, empty for dimensionless values
 * @param colors at least two, from the color of the smallest value to that of the largest
 */
public record ColorScale(String label, String unit, double min, double max, List<Color> colors) {

    public ColorScale {
        colors = List.copyOf(colors);
        if (label.isBlank()) {
            throw new IllegalArgumentException("A color scale needs a label");
        }
        if (!(Double.isFinite(min) && Double.isFinite(max) && min < max)) {
            throw new IllegalArgumentException("Range [%s, %s] of the color scale is invalid".formatted(min, max));
        }
        if (colors.size() < 2) {
            throw new IllegalArgumentException("A color scale needs at least two colors, but got " + colors.size());
        }
    }
}
