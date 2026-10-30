package com.avdbuddy.themegen;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class GenerateThemeTest {
    private static final String THEME = """
        colors:
          surface: "#f1f1f1"
          overlay: "#80000000"
        typography:
          body:
            fontFamily: Inter
            fontSize: 13
            fontWeight: 400
            lineHeight: 18
          heading:
            fontFamily: Inter
            fontSize: 14
            fontWeight: 700
            lineHeight: 20
        """;

    @Test public void generatesColorsAndCompleteTextStylesDeterministically() {
        var source = GenerateTheme.render(THEME);
        assertEquals(source, GenerateTheme.render(THEME));
        assertTrue(source.contains("val `surface` = Color(0xFFF1F1F1)"));
        assertTrue(source.contains("val `overlay` = Color(0x80000000)"));
        assertTrue(source.contains("val `body`: TextStyle"));
        assertTrue(source.contains("`heading` = TextStyle("));
        assertTrue(source.contains("fontFamily = family0"));
        assertTrue(source.contains("fontSize = 13.0.sp"));
        assertTrue(source.contains("fontWeight = FontWeight(700)"));
        assertTrue(source.contains("lineHeight = 20.0.sp"));
        assertTrue(source.contains("return remember(family0)"));
        assertFalse(source.contains("jewel"));
    }

    @Test public void loadsEachFamilyOnceWithItsRequiredWeights() {
        var yaml = THEME + """
              caption:
                fontFamily: Inter
                fontSize: 11
                fontWeight: 400
                lineHeight: 16
              code:
                fontFamily: JetBrains Mono
                fontSize: 13
                fontWeight: 500
                lineHeight: 18
            """;
        var source = GenerateTheme.render(yaml);
        assertEquals(Map.of("Inter", List.of(400, 700), "JetBrains Mono", List.of(500)), GenerateTheme.fontFamilies(yaml));
        assertTrue(source.contains("val family0 = GoogleFonts.Family0"));
        assertTrue(source.contains("val family1 = GoogleFonts.Family1"));
        assertFalse(source.contains("rememberGoogleFontFamily"));
        assertTrue(source.contains("return remember(family0, family1)"));
    }

    @Test public void escapesKotlinNamesAndFontStrings() {
        var source = GenerateTheme.render(THEME.replace("heading:", "when:")
            .replace("fontFamily: Inter", "fontFamily: 'Family \"Test\" $name'"));
        assertTrue(source.contains("val `when`: TextStyle"));
        assertTrue(GenerateTheme.fontFamilies(THEME.replace("fontFamily: Inter", "fontFamily: 'Family \"Test\" $name'"))
            .containsKey("Family \"Test\" $name"));
    }

    @Test public void rejectsInvalidValuesWithTheirYamlPath() {
        invalid(THEME.replace("#f1f1f1", "#xyz"), "colors.surface");
        invalid(THEME.replace("fontSize: 13", "fontSize: -1"), "typography.body.fontSize");
        invalid(THEME.replace("lineHeight: 18", "lineHeight: .nan"), "typography.body.lineHeight");
        invalid(THEME.replace("fontWeight: 400", "fontWeight: 1001"), "typography.body.fontWeight");
        invalid(THEME.replace("fontWeight: 400", "fontWeight: 400.5"), "typography.body.fontWeight");
        invalid(THEME.replace("fontFamily: Inter", "fontFamily: ''"), "typography.body.fontFamily");
        invalid(THEME.replace("lineHeight: 18", "lineHieght: 18"), "typography.body");
        invalid(THEME.replace("surface:", "bad-name:"), "colors");
        invalid(THEME + "spacing: { small: 4 }", "theme");
    }

    @Test public void rejectsEmptySectionsAndDuplicateKeys() {
        for (var yaml : new String[] { "", "[]", "colors: {}\ntypography: {}",
            THEME.replace("  overlay: \"#80000000\"", "  surface: \"#000000\"") }) {
            assertThrows(RuntimeException.class, () -> GenerateTheme.render(yaml));
        }
    }

    private static void invalid(String yaml, String path) {
        var error = assertThrows(IllegalArgumentException.class, () -> GenerateTheme.render(yaml));
        assertTrue(error.getMessage(), error.getMessage().contains("theme.yml: " + path + ":"));
    }
}
