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

import java.util.List;
import picocli.CommandLine;

/**
 * Shared picocli mixin for {@code --sequence} and {@code --sequence-format} options,
 * used by translate, validation, and conversion commands.
 *
 * <p>Note: {@code --sequence-format} applies globally to all {@code --sequence} entries.
 * If you need to mix formats, rely on extension-based inference (omit {@code --sequence-format})
 * and use appropriate file extensions ({@code .fasta}, {@code .fa}, {@code .fna} for FASTA;
 * {@code .seq} for plain).
 */
public class SequenceOptions {

    @CommandLine.Option(
            names = "--sequence",
            description = "Sequence source. Repeatable. Use path for FASTA files (IDs from headers) "
                    + "or key:path for plain sequences (key = GFF3 seqId, must not contain / or \\). "
                    + "Examples: --sequence seqs.fasta --sequence chr1:chr1.seq")
    public List<String> sequenceSpecs;

    @CommandLine.Option(
            names = "--sequence-format",
            description = "Format of the sequence file: fasta, plain. Inferred from extension if omitted. "
                    + "Applies to all --sequence entries when set explicitly.")
    public SequenceFormat sequenceFormat;
}
