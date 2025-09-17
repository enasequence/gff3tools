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
package uk.ac.ebi.embl.converter.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.cli.CliRulesOption;
import uk.ac.ebi.embl.gff3tools.cli.ConversionFileFormat;
import uk.ac.ebi.embl.gff3tools.cli.FileConversionCommand;
import uk.ac.ebi.embl.gff3tools.cli.RuleConverter;
import uk.ac.ebi.embl.gff3tools.validation.RuleSeverity;

public class MainTest {

    @Test
    void testParseRules() {
        for (String rule : new String[] {"A", "B"}) {
            for (RuleSeverity severity : RuleSeverity.values()) {
                String ruleName = rule.toLowerCase();
                String severityName = severity.name().toLowerCase();
                String[] args = new String[] {"--rules=" + ruleName + ":" + severityName};

                FileConversionCommand cc = new FileConversionCommand();
                CommandLine commandLine =
                        new CommandLine(cc).registerConverter(CliRulesOption.class, new RuleConverter());
                commandLine.parseArgs(args);

                assertEquals(
                        severity,
                        cc.rules.rules().get(rule),
                        "Failed for rule: " + rule + " with severity: " + severity.name());
            }
        }
    }

    // TODO: Add validation for invalid rules
    // @Test
    void testParseRules_InvalidRuleName() {
        String[] args = new String[] {"--rules=non_existent_rule:warn"};
        FileConversionCommand cc = new FileConversionCommand();
        CommandLine commandLine = new CommandLine(cc).registerConverter(CliRulesOption.class, new RuleConverter());
        // Expect an exception when parsing an invalid rule name
        CommandLine.ParameterException exception = org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> commandLine.parseArgs(args));
        assertTrue(
                exception.getMessage().contains("non_existent_rule"),
                "Exception message should contain the invalid rule name");
    }

    @Test
    void testParseRules_InvalidRuleSeverity() {
        String[] args = new String[] {"--rules=flatfile_no_source:invalid_severity"};
        FileConversionCommand cc = new FileConversionCommand();
        CommandLine commandLine = new CommandLine(cc).registerConverter(CliRulesOption.class, new RuleConverter());
        // Expect an exception when parsing an invalid severity
        CommandLine.ParameterException exception = org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> commandLine.parseArgs(args));
        assertTrue(
                exception.getMessage().contains("invalid_severity"),
                "Exception message should contain the invalid severity name");
    }

    @Test
    void testParseRules_MultipleRules() {
        String[] args = new String[] {"--rules=flatfile_no_ontology_feature:warn,flatfile_no_source:error"};
        FileConversionCommand cc = new FileConversionCommand();
        CommandLine commandLine = new CommandLine(cc).registerConverter(CliRulesOption.class, new RuleConverter());
        commandLine.parseArgs(args);

        assertEquals(
                RuleSeverity.WARN,
                cc.rules.rules().get("FLATFILE_NO_ONTOLOGY_FEATURE"),
                "Failed for rule: FLATFILE_NO_ONTOLOGY_FEATURE with severity: WARN");
        assertEquals(
                RuleSeverity.ERROR,
                cc.rules.rules().get("FLATFILE_NO_SOURCE"),
                "Failed for rule: FLATFILE_NO_SOURCE with severity: ERROR");
    }

    @Test
    public void testValidateFileType() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        FileConversionCommand command = new FileConversionCommand();
        Method method = FileConversionCommand.class.getDeclaredMethod(
                "validateFileType", ConversionFileFormat.class, Path.class, String.class);
        method.setAccessible(true);

        Object validate = null;

        validate = method.invoke(command, ConversionFileFormat.embl, Path.of("foo"), "-f");
        assertEquals(ConversionFileFormat.embl, validate, "If format is provided returns the same format");

        validate = method.invoke(command, ConversionFileFormat.gff3, Path.of("foo"), "-f");
        assertEquals(ConversionFileFormat.gff3, validate, "If format is provided returns the same format");

        validate = method.invoke(command, null, Path.of("foo.embl"), "-f");
        assertEquals(ConversionFileFormat.embl, validate, "If format in path extension, use it");

        validate = method.invoke(command, null, Path.of("foo.gff3"), "-f");
        assertEquals(ConversionFileFormat.gff3, validate, "If format in path extension, use it");

        try {
            validate = method.invoke(command, null, Path.of("foo.embl"), "-f");
        } catch (InvocationTargetException e) {
            assertTrue(
                    e.getTargetException().getMessage().contains("-f"),
                    "If no format matches extension, show error with options");
        }

        try {
            validate = method.invoke(command, null, Path.of("foo.embl"), "-t");
        } catch (InvocationTargetException e) {
            assertTrue(
                    e.getTargetException().getMessage().contains("-t"),
                    "If no format matches extension, show error with options");
        }
    }
}
