package com.avdbuddy.themegen;

import com.composables.googlefonts.gradle.GoogleFontsExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension;

/** Builds typography and bundled fonts from the same theme.yml declarations. */
public class AvdBuddyThemePlugin implements Plugin<Project> {
    @Override public void apply(Project project) {
        project.getPluginManager().apply("com.composables.google-fonts");
        var themeFile = project.getLayout().getProjectDirectory().file("theme.yml");
        var families = GenerateTheme.fontFamilies(project.getProviders().fileContents(themeFile).getAsText().get());
        var fonts = project.getExtensions().getByType(GoogleFontsExtension.class);
        fonts.getPackageName().set("com.avdbuddy.fonts");
        int index = 0;
        for (var entry : families.entrySet()) {
            var accessor = "Family" + index++;
            fonts.family(entry.getKey(), spec -> {
                spec.setAccessor(accessor);
                spec.weights(entry.getValue().stream().mapToInt(Integer::intValue).toArray());
            });
        }
        var generate = project.getTasks().register("generateTheme", GenerateTheme.class, task -> {
            task.setGroup("build");
            task.setDescription("Generate typed Compose tokens and bundled typography from theme.yml");
            task.getTokensFile().set(themeFile);
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/theme/kotlin"));
        });
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", ignored ->
            project.getExtensions().getByType(KotlinJvmProjectExtension.class).getSourceSets().getByName("main")
                .getKotlin().srcDir(generate.flatMap(GenerateTheme::getOutputDirectory)));
    }
}
