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

import java.util.HashMap;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

/**
 * Parses {@code --rules RULE:SEVERITY,RULE:SEVERITY} into a {@link CliRulesOption}.
 *
 * <p>Public, along with {@link CliRulesOption}, so that callers assembling their own
 * {@link CommandLine} can register it; without it {@code --rules} fails to parse.
 */
public class RuleConverter implements CommandLine.ITypeConverter<CliRulesOption> {
    CliRulesOption map = new CliRulesOption(new HashMap<>());

    @Override
    public CliRulesOption convert(String args) throws Exception {
        String[] entries = args.split(",");

        for (String entry : entries) {
            String[] pairs = entry.trim().split(":");
            if (pairs.length != 2) {
                throw new CLIException("Invalid rule: '" + entry + "' There must be 2 values separated by ':' ");
            }
            String key = pairs[0].toUpperCase();
            RuleSeverity value;
            try {
                value = RuleSeverity.valueOf(pairs[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new CLIException("The rule severity: \"" + pairs[1] + "\" is invalid");
            }
            this.map.rules().put(key, value);
        }
        return this.map;
    }
}
