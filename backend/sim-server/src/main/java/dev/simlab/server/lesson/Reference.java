package dev.simlab.server.lesson;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A method a lesson refers to in Javadoc notation, like {@code RungeKutta4#step}. The parameter types tell
 * methods of the same name apart, as in {@code Tethers#add(int, int, double)}
 *
 * @param parameterTypes without type arguments, like {@code double[]} or {@code int...}. {@code null} if the
 *     reference doesn't name them
 */
record Reference(
        String className, String methodName, @Nullable List<String> parameterTypes) {

    /** as a lesson writes it: a class in upper camel case, a method in lower camel case */
    static final Pattern PATTERN = Pattern.compile("([A-Z]\\w*)#([a-z]\\w*)(?:\\(([^()]*)\\))?");

    Reference {
        parameterTypes = parameterTypes == null ? null : List.copyOf(parameterTypes);
    }

    /** @throws IllegalArgumentException if the text isn't a reference */
    static Reference parse(String text) {
        Matcher matcher = PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("'%s' is no reference such as RungeKutta4#step".formatted(text));
        }
        String parameters = matcher.group(3);
        return new Reference(
                matcher.group(1), matcher.group(2), parameters == null ? null : parameterTypes(parameters));
    }

    private static List<String> parameterTypes(String parameters) {
        if (parameters.isBlank()) {
            return List.of();
        }
        return Arrays.stream(parameters.split(","))
                .map(type -> type.replaceAll("\\s", ""))
                .toList();
    }

    /** the reference as a lesson writes it */
    @Override
    public String toString() {
        String method = className + "#" + methodName;
        return parameterTypes == null ? method : method + "(" + String.join(", ", parameterTypes) + ")";
    }
}
