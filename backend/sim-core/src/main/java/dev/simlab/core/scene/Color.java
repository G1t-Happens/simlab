package dev.simlab.core.scene;

/** sRGB color, 8 bits per channel */
public record Color(int red, int green, int blue) {

    public Color {
        if (!(isChannel(red) && isChannel(green) && isChannel(blue))) {
            throw new IllegalArgumentException(
                    "Channels must lie between 0 and 255, but were (%d, %d, %d)".formatted(red, green, blue));
        }
    }

    /** Color of a hex value like {@code 0xd9513c}, which CSS writes as {@code #d9513c} */
    public static Color rgb(int value) {
        if (value >>> 24 != 0) {
            throw new IllegalArgumentException("%#x is not a 24-bit RGB value".formatted(value));
        }
        return new Color(value >> 16, (value >> 8) & 0xff, value & 0xff);
    }

    private static boolean isChannel(int value) {
        return value >= 0 && value <= 255;
    }
}
