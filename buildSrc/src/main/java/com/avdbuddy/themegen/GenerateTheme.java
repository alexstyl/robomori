package com.avdbuddy.themegen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Build-only compiler for the colors and typography sections of theme.yml. */
@CacheableTask
public abstract class GenerateTheme extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getTokensFile();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        var source = render(Files.readString(getTokensFile().get().getAsFile().toPath()));
        var output = getOutputDirectory().file("com/avdbuddy/theme/AvdBuddyTokens.kt").get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, source, StandardCharsets.UTF_8);
    }

    private record Typography(String name, String family, String size, int weight, String lineHeight) {}

    private record Theme(Map<?, ?> colors, List<Typography> styles, LinkedHashMap<String, TreeSet<Integer>> families) {}

    public static Map<String, List<Integer>> fontFamilies(String yaml) {
        var result = new LinkedHashMap<String, List<Integer>>();
        parse(yaml).families().forEach((family, weights) -> result.put(family, List.copyOf(weights)));
        return result;
    }

    private static Theme parse(String yaml) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        var root = mapping(new Yaml(new SafeConstructor(options)).load(yaml), "theme");
        requireKeys(root, Set.of("colors", "typography"), "theme");
        var colors = mapping(root.get("colors"), "colors");
        var typography = mapping(root.get("typography"), "typography");
        var styles = new ArrayList<Typography>();
        var families = new LinkedHashMap<String, TreeSet<Integer>>();
        for (var entry : typography.entrySet()) {
            var name = name(entry.getKey(), "typography");
            var path = "typography." + name;
            var style = mapping(entry.getValue(), path);
            requireKeys(style, Set.of("fontFamily", "fontSize", "fontWeight", "lineHeight"), path);
            if (!(style.get("fontFamily") instanceof String family) || family.isBlank()
                || family.chars().anyMatch(Character::isISOControl)) {
                throw invalid(path + ".fontFamily", "expected a non-empty font family name");
            }
            var size = positiveNumber(style.get("fontSize"), path + ".fontSize");
            var lineHeight = positiveNumber(style.get("lineHeight"), path + ".lineHeight");
            if (!(style.get("fontWeight") instanceof Number weight) || weight.doubleValue() != weight.intValue()
                || weight.intValue() < 1 || weight.intValue() > 1000) {
                throw invalid(path + ".fontWeight", "expected an integer between 1 and 1000");
            }
            styles.add(new Typography(name, family, size, weight.intValue(), lineHeight));
            families.computeIfAbsent(family, ignored -> new TreeSet<>()).add(weight.intValue());
        }
        return new Theme(colors, styles, families);
    }

    static String render(String yaml) {
        var theme = parse(yaml);
        var colors = theme.colors();
        var styles = theme.styles();
        var families = theme.families();
        var source = new StringBuilder("""
            // Generated from theme.yml. Edit the YAML, not this file.
            package com.avdbuddy.theme

            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.Immutable
            import androidx.compose.runtime.remember
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.text.TextStyle
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.sp
            import com.avdbuddy.fonts.GoogleFonts

            object AvdBuddyColors {
            """);
        for (var entry : colors.entrySet()) {
            var name = name(entry.getKey(), "colors");
            if (!(entry.getValue() instanceof String hex) || !hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                throw invalid("colors." + name, "expected a quoted #RRGGBB or #AARRGGBB color");
            }
            source.append("    val `").append(name).append("` = Color(0x")
                .append(hex.length() == 7 ? "FF" : "")
                .append(hex.substring(1).toUpperCase(Locale.ROOT)).append(")\n");
        }
        source.append("}\n\n@Immutable\ndata class AvdBuddyTypography(\n");
        for (var style : styles) {
            source.append("    val `").append(style.name()).append("`: TextStyle,\n");
        }
        source.append(")\n\n@Composable\ninternal fun rememberAvdBuddyTypography(): AvdBuddyTypography {\n");
        var familyIndexes = new LinkedHashMap<String, Integer>();
        for (var entry : families.entrySet()) {
            int index = familyIndexes.size();
            familyIndexes.put(entry.getKey(), index);
            source.append("    val family").append(index).append(" = GoogleFonts.Family").append(index).append("\n");
        }
        var keys = familyIndexes.values().stream().map(i -> "family" + i).collect(Collectors.joining(", "));
        source.append("    return remember(").append(keys).append(") {\n        AvdBuddyTypography(\n");
        for (var style : styles) {
            source.append("            `").append(style.name()).append("` = TextStyle(\n")
                .append("                fontFamily = family").append(familyIndexes.get(style.family())).append(",\n")
                .append("                fontSize = ").append(style.size()).append(".sp,\n")
                .append("                fontWeight = FontWeight(").append(style.weight()).append("),\n")
                .append("                lineHeight = ").append(style.lineHeight()).append(".sp,\n")
                .append("            ),\n");
        }
        return source.append("        )\n    }\n}\n").toString();
    }

    private static Map<?, ?> mapping(Object value, String path) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            throw invalid(path, "expected a non-empty mapping");
        }
        return map;
    }

    private static void requireKeys(Map<?, ?> map, Set<String> keys, String path) {
        if (!map.keySet().equals(keys)) throw invalid(path, "expected exactly these fields: " + new TreeSet<>(keys));
    }

    private static String name(Object value, String path) {
        if (!(value instanceof String name) || !name.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            throw invalid(path, "names must start with a letter and contain only letters, digits, or underscores");
        }
        return name;
    }

    private static String positiveNumber(Object value, String path) {
        if (!(value instanceof Number n) || !Float.isFinite(n.floatValue()) || n.floatValue() <= 0) {
            throw invalid(path, "expected a finite, positive number");
        }
        return Float.toString(((Number) value).floatValue());
    }

    private static IllegalArgumentException invalid(String path, String message) {
        return new IllegalArgumentException("theme.yml: " + path + ": " + message);
    }
}
