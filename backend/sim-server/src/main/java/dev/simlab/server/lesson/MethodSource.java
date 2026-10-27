package dev.simlab.server.lesson;

import java.util.List;

/**
 * A method cut out of its source file, from the comment above it to its closing brace
 *
 * @param path path of the source file below the source root, like
 *     {@code dev/simlab/core/toolkit/ode/RungeKutta4.java}
 * @param firstLine counted from 1
 * @param code the lines as the file writes them
 * @param identifiers every identifier in the code outside comments and strings (like the names of variables
 *     and of the methods it calls), in the order of the code
 */
record MethodSource(
        String packageName, String path, int firstLine, int lastLine, String code, List<Identifier> identifiers) {

    MethodSource {
        identifiers = List.copyOf(identifiers);
    }

    /**
     * An identifier in the code of a method
     *
     * @param line counted from 1
     * @param column of its first character in the line, counted from 1
     */
    record Identifier(String name, int line, int column) {}
}
