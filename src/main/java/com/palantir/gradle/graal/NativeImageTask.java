/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.graal;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/** Runs GraalVM's native-image command with configured options and parameters. */
public class NativeImageTask extends DefaultTask {

    @Input private Provider<String> mainClass;
    @Input private Provider<String> outputName;
    @Input private Provider<String> graalVersion;

    @TaskAction
    public final void nativeImage() {
        // TODO(rfink): declare classpath list as @Input in order to unlock incremental builds
        getProject().exec(spec -> {
            if (!mainClass.isPresent()) {
                throw new IllegalArgumentException("nativeImage requires graal.mainClass to be defined.");
            }
            if (!graalVersion.isPresent()) {
                throw new IllegalStateException("nativeImage requires graal.version to be defined.");
            }

            List<String> args = new ArrayList<>();
            args.add("-cp");
            args.add(generateClasspathArgument());

            args.add("-H:Path=" + getOutputDirectory());

            if (outputName.isPresent()) {
                args.add("-H:Name=" + outputName.get());
            }

            args.add(mainClass.get());

            spec.executable(getExecutable(graalVersion.get()));
            spec.args(args);
        });
    }

    @Override
    public final String getGroup() {
        return GradleGraalPlugin.TASK_GROUP;
    }

    @Override
    public final String getDescription() {
        return "Runs GraalVM's native-image command with configured options and parameters.";
    }

    private String getOutputDirectory() {
        File outputDirectory = getProject().getProjectDir().toPath().resolve(Paths.get("build", "graal")).toFile();

        if (!(outputDirectory.mkdirs() || outputDirectory.exists())) {
            throw new IllegalStateException(
                    "Output directory does not exist and cannot be created: " + outputDirectory);
        }

        return outputDirectory.getAbsolutePath();
    }

    private String getExecutable(String version) {
        return GradleGraalPlugin.CACHE_DIR
                .resolve(Paths.get(version, "graalvm-ce-" + version))
                .resolve(getArchitectureSpecifiedBinaryPath())
                .toFile()
                .getAbsolutePath();
    }

    private String generateClasspathArgument() {
        Set<File> classpath = new LinkedHashSet<>();
        classpath.addAll(getProject().getConfigurations().getByName("runtimeClasspath").getFiles());
        classpath.addAll(getProject().getTasks().getByName("jar").getOutputs().getFiles().getFiles());

        return classpath.stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(":"));
    }

    @SuppressWarnings("checkstyle:hiddenfield")
    public final void configure(Provider<String> mainClass, Provider<String> outputName,
            Provider<String> graalVersion) {
        this.mainClass = mainClass;
        this.outputName = outputName;
        this.graalVersion = graalVersion;
    }

    private Path getArchitectureSpecifiedBinaryPath() {
        switch (Platform.operatingSystem()) {
            case MAC: return Paths.get("Contents", "Home", "bin", "native-image");
            case LINUX: return Paths.get("bin", "native-image");
            default:
                throw new IllegalStateException("No GraalVM support for " + Platform.operatingSystem());
        }
    }

}
