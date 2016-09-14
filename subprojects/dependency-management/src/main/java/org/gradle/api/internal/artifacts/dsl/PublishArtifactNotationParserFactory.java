/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.dsl;

import org.apache.tools.ant.Task;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.Factory;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.*;

import java.io.File;
import java.util.Date;

public class PublishArtifactNotationParserFactory implements Factory<NotationParser<Object, PublishArtifact>> {
    private final Instantiator instantiator;
    private final DependencyMetaDataProvider metaDataProvider;

    public PublishArtifactNotationParserFactory(Instantiator instantiator, DependencyMetaDataProvider metaDataProvider) {
        this.instantiator = instantiator;
        this.metaDataProvider = metaDataProvider;
    }

    public NotationParser<Object, PublishArtifact> create() {
        FileNotationConverter fileConverter = new FileNotationConverter();
        return NotationParserBuilder
                .toType(PublishArtifact.class)
                .converter(new ArchiveTaskNotationConverter())
                .converter(new FileMapNotationConverter(fileConverter))
                .converter(fileConverter)
                .converter(new TaskNotationConverter())
                .toComposite();
    }

    private class ArchiveTaskNotationConverter extends TypedNotationConverter<AbstractArchiveTask, PublishArtifact> {
        private ArchiveTaskNotationConverter() {
            super(AbstractArchiveTask.class);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Instances of AbstractArchiveTask").example("jar.");
        }

        @Override
        protected PublishArtifact parseType(AbstractArchiveTask notation) {
            return instantiator.newInstance(ArchivePublishArtifact.class, notation);
        }
    }

    private class FileMapNotationConverter extends MapNotationConverter<PublishArtifact> {
        private final FileNotationConverter fileConverter;

        private FileMapNotationConverter(FileNotationConverter fileConverter) {
            this.fileConverter = fileConverter;
        }

        protected PublishArtifact parseMap(@MapKey("file") File file) {
            return fileConverter.parseType(file);
        }
    }

    private class FileNotationConverter extends TypedNotationConverter<File, PublishArtifact> {
        private FileNotationConverter() {
            super(File.class);
        }

        @Override
        protected PublishArtifact parseType(File file) {
            Module module = metaDataProvider.getModule();
            ArtifactFile artifactFile = new ArtifactFile(file, module.getVersion());
            return instantiator.newInstance(DefaultPublishArtifact.class, artifactFile.getName(), artifactFile.getExtension(),
                                            artifactFile.getExtension() == null? "":artifactFile.getExtension(),
                                            artifactFile.getClassifier(), null, file, new Task[0]);
        }
    }

    private class TaskNotationConverter extends TypedNotationConverter<TaskInternal.NamedOutput, PublishArtifact> {

        public TaskNotationConverter() {
            super(TaskInternal.NamedOutput.class);
        }

        @Override
        protected PublishArtifact parseType(final TaskInternal.NamedOutput notation) {
            return new PublishArtifact() {
                @Override
                public String getName() {
                    return getFile().getName();
                }

                @Override
                public String getExtension() {
                    String fileName = getFile().getName();
                    int idx = fileName.lastIndexOf(".");
                    if (idx>0) {
                        return fileName.substring(idx+1);
                    }
                    return "";
                }

                @Override
                public String getType() {
                    return "task output";
                }

                @Override
                public String getClassifier() {
                    return null;
                }

                @Override
                public File getFile() {
                    return notation.getFiles().getSingleFile();
                }

                @Override
                public Date getDate() {
                    return new Date(getFile().lastModified());
                }

                @Override
                public TaskDependency getBuildDependencies() {
                    DefaultTaskDependency defaultTaskDependency = new DefaultTaskDependency();
                    defaultTaskDependency.add(notation.getTask());
                    return defaultTaskDependency;
                }
            };
        }
    }
}
