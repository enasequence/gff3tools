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

import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class GFF3Directives {

    /** The {@code ##FASTA} directive token, without a trailing newline. */
    public static final String FASTA = "##FASTA";

    /** The {@code ##FASTA} directive as a complete line, with trailing newline. */
    public static final String FASTA_LINE = FASTA + "\n";

    /** The {@code ##gff-version} header line for the current default version, with trailing newline. */
    public static final String VERSION_LINE = "##gff-version " + GFF3Header.DEFAULT_VERSION + "\n";

}
