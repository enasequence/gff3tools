/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.cli;

import io.vavr.Function0;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.ExitException;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.UnregisteredValidationRuleException;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

public abstract class AbstractCommand implements Runnable {

    @CommandLine.Option(
            names = "--rules",
            paramLabel = "<key:value,key:value>",
            description = "Specify rules in the format key:value")
    public CliRulesOption rules;

    @CommandLine.Option(names = "-f", description = "The type of the file to be converted")
    public ConversionFileFormat fromFileType;

    @CommandLine.Option(names = "-t", description = "The type of the file to convert to")
    public ConversionFileFormat toFileType;

    @CommandLine.Option(names = "-m", description = "Optional master file")
    public Path masterFilePath;

    @CommandLine.Parameters(
            paramLabel = "[input-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path inputFilePath;

    protected Map<String, RuleSeverity> getRuleOverrides() {
        return Optional.ofNullable(rules).map((r) -> r.rules()).orElse(new HashMap<>());
    }

    protected ValidationEngine initValidationEngine(Map<String, RuleSeverity> ruleOverrides)
            throws UnregisteredValidationRuleException {
        return new ValidationEngineBuilder().overrideMethodRules(ruleOverrides).build();
    }

    @FunctionalInterface
    interface NewPipeFunction<T> {
        T apply(Path p, Charset c) throws IOException;
    }

    protected <T> T getPipe(NewPipeFunction<T> newFilePipe, Function0<T> newStdPipe, Path filePath)
            throws ExitException {
        if (filePath != null && !filePath.toString().isEmpty()) {
            try {
                return newFilePipe.apply(filePath, StandardCharsets.UTF_8);
            } catch (NoSuchFileException e) {
                throw new NonExistingFile("The file does not exist: " + filePath, e);
            } catch (IOException e) {
                throw new ReadException("Error opening file: " + filePath, e);
            }
        } else {
            return newStdPipe.apply();
        }
    }

    protected static Optional<String> getFileExtension(Path path) {
        String name = path.getFileName().toString();

        if (name.endsWith(".gz")) {
            name = name.substring(0, name.length() - 3);
        }

        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? Optional.of(name.substring(dot + 1)) : Optional.empty();
    }
}
