package dev.xpple.seedmapper.buildscript;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.tasks.Exec;

public abstract class CreateJavaBindingsTask extends Exec {

    private static final String EXTENSION = Os.isFamily(Os.FAMILY_WINDOWS) ? ".bat" : "";

    {
        // always run task
        this.getOutputs().upToDateWhen(task -> false);

        this.setWorkingDir(this.getProject().getRootDir());
        this.setStandardOutput(System.out);
        this.commandLine("./jextract/build/jextract/bin/jextract" + EXTENSION, "--include-dir", "src/main/c/cubiomes", "--output", "src/main/java", "--source", "--target-package", "com.github.cubiomes", "--header-class-name", "Cubiomes", "@includes.txt", "src/main/c/cubiomes/seedmapper_jextract.h");
    }
}
