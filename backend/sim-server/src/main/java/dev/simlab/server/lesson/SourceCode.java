package dev.simlab.server.lesson;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import dev.simlab.server.lesson.MethodSource.Identifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * The sources of sim-core, which its jar holds below {@code sources/}. Cuts the methods lessons refer to out
 * of them, from their Javadoc to their closing brace, in their original formatting.
 *
 * <p>Parses every source file once. Not thread-safe
 */
final class SourceCode {

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private final JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_25));

    private final Map<String, Source> sources = new HashMap<>();

    /**
     * The method a reference names
     *
     * @throws IllegalArgumentException if sim-core declares no such method, or several
     */
    MethodSource method(Reference reference) {
        Source source = sources.computeIfAbsent(reference.className(), this::parse);
        List<String> types = reference.parameterTypes();
        List<MethodDeclaration> methods = source.type().getMethodsByName(reference.methodName()).stream()
                .filter(method -> types == null || types.equals(parameterTypes(method)))
                .toList();
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("sim-core has no method %s".formatted(reference));
        }
        if (methods.size() > 1) {
            throw new IllegalArgumentException("%s names %d methods, which the types of their parameters tell apart"
                    .formatted(reference, methods.size()));
        }
        return source.cut(methods.getFirst());
    }

    private Source parse(String className) {
        Resource file = find(className);
        String text;
        try {
            text = file.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ParseResult<CompilationUnit> result = parser.parse(text);
        CompilationUnit unit = result.getResult()
                .filter(parsed -> result.isSuccessful())
                .orElseThrow(() -> new IllegalStateException(
                        "%s cannot be parsed: %s".formatted(file.getDescription(), result.getProblems())));
        // every source of sim-core lies in a package and declares the class it's named after
        TypeDeclaration<?> type = unit.getTypes().stream()
                .filter(candidate -> candidate.getNameAsString().equals(className))
                .findFirst()
                .orElseThrow();
        String packageName = unit.getPackageDeclaration().orElseThrow().getNameAsString();
        return new Source(type, packageName, text.lines().toList());
    }

    /** Finds the source file of a class by its simple name, which has to name exactly one class of sim-core */
    private Resource find(String className) {
        Resource[] files;
        try {
            files = resolver.getResources("classpath*:sources/**/" + className + ".java");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (files.length == 0) {
            throw new IllegalArgumentException("sim-core has no class %s".formatted(className));
        }
        if (files.length > 1) {
            throw new IllegalArgumentException(
                    "sim-core has %d classes named %s: %s".formatted(files.length, className, Arrays.toString(files)));
        }
        return files[0];
    }

    /** parameter types of a method as Javadoc writes them */
    private static List<String> parameterTypes(MethodDeclaration method) {
        return method.getParameters().stream().map(SourceCode::typeName).toList();
    }

    /** type of a parameter without type arguments, with {@code ...} for varargs */
    private static String typeName(Parameter parameter) {
        Type type = parameter.getType();
        String elementName = type.getElementType() instanceof ClassOrInterfaceType classType
                ? classType.getNameAsString()
                : type.getElementType().asString();
        return elementName + "[]".repeat(type.getArrayLevel()) + (parameter.isVarArgs() ? "..." : "");
    }

    /**
     * A parsed source file
     *
     * @param type the class the file is named after
     */
    private record Source(TypeDeclaration<?> type, String packageName, List<String> lines) {

        /** Cuts a method out of the file, from the comment above it to its closing brace */
        MethodSource cut(MethodDeclaration method) {
            int firstLine = method.getComment()
                    .flatMap(Node::getBegin)
                    .or(method::getBegin)
                    .orElseThrow()
                    .line;
            int lastLine = method.getEnd().orElseThrow().line;
            return new MethodSource(
                    packageName,
                    packageName.replace('.', '/') + "/" + type.getNameAsString() + ".java",
                    firstLine,
                    lastLine,
                    String.join("\n", lines.subList(firstLine - 1, lastLine)),
                    identifiers(method));
        }

        /**
         * Names in the syntax tree of a method, which holds none of the words of its comments and strings, in
         * the order of the code
         */
        private static List<Identifier> identifiers(MethodDeclaration method) {
            return method.findAll(SimpleName.class).stream()
                    .map(name -> {
                        Position begin = name.getBegin().orElseThrow();
                        return new Identifier(name.getIdentifier(), begin.line, begin.column);
                    })
                    .sorted(Comparator.comparingInt(Identifier::line).thenComparingInt(Identifier::column))
                    .toList();
        }
    }
}
