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
package uk.ac.ebi.embl.gff3tools.gff3.directives;

import java.util.regex.Pattern;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class GFF3Directives {

    /**
     * Matches a valid {@code ##gff-version} directive line, e.g. {@code ##gff-version 3.1.26}.
     * Accepts major-only ({@code 3}), major.minor ({@code 3.1}), or major.minor.patch ({@code 3.1.26}).
     */
    public static final Pattern VERSION_DIRECTIVE = Pattern.compile(
            "^##gff-version (?<version>(?<major>[0-9]+)(\\.(?<minor>[0-9]+)(:?\\.(?<patch>[0-9]+))?)?)\\s*$");

    /** The {@code ##FASTA} directive token, without a trailing newline. */
    public static final String FASTA = "##FASTA";

    /** The {@code ##FASTA} directive as a complete line, with trailing newline. */
    public static final String FASTA_LINE = FASTA + "\n";

    /** The {@code ##gff-version} header line for the current default version, with trailing newline. */
    public static final String VERSION_LINE = "##gff-version " + GFF3Header.DEFAULT_VERSION + "\n";
}
