package com.avdbuddy.development;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.JavaExec;

/** Gives local Java launches the packaged macOS Dock icon and app name. */
public class MacDevelopmentIconPlugin implements Plugin<Project> {
    @Override public void apply(Project project) {
        if (!System.getProperty("os.name").startsWith("Mac")) return;

        var packagedIcon = project.file("packaging/app-icon.icns");
        var icon = project.getLayout().getBuildDirectory().file("development-icon/app-icon.png").get().getAsFile();
        var prepareIcon = project.getTasks().register("prepareDevelopmentMacIcon", Exec.class, task -> {
            task.getInputs().file(packagedIcon);
            task.getOutputs().file(icon);
            task.doFirst(ignored -> icon.getParentFile().mkdirs());
            task.commandLine("/usr/bin/sips", "-s", "format", "png", packagedIcon.getAbsolutePath(), "--out", icon.getAbsolutePath());
        });
        project.getTasks().withType(JavaExec.class)
            .matching(task -> task.getName().equals("run") || task.getName().equals("hotRun"))
            .configureEach(task -> {
                task.dependsOn(prepareIcon);
                task.jvmArgs("-Xdock:icon=" + icon.getAbsolutePath());
                task.jvmArgs("-Xdock:name=Robomori");
            });
    }
}
