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
package uk.ac.ebi.embl.gff3tools.validation.fix;

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils.ChromosomeLocation;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils.ChromosomeType;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils.MolType;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils.Topology;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;

@Slf4j
@Gff3Fix(
        name = "FASTA_HEADER_NORMALISATION",
        description = "Normalises FASTA header values registered for an annotation's accession.")
public class FastaHeaderNormalisationFix implements Fix {

    @InjectContext
    private ValidationContext context;

    @FixMethod(
            rule = "ASCII",
            description = "Normalises FASTA header values to ASCII.",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void AsciiFix(GFF3Annotation annotation, int line) {

        // use Ascii7CharacterConverter from sequencetools if appropriate
    }

    @FixMethod(
            rule = "LOWERCASE",
            description = "Normalises controlled vocabulary fields to their appropriate form.",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void ControlledVocabularyNormalisation(GFF3Annotation annotation, int line) {
        // No FASTA header source registered for this run -> nothing to fix.
        if (!context.contains(FastaHeaderProvider.class)) {
            return;
        }

        String accession = annotation.getAccession();
        Optional<FastaHeader> headerOpt = context.get(FastaHeaderProvider.class).getHeader(accession);
        if (headerOpt.isEmpty()) {
            log.warn("No FASTA header found for accession {}", accession);
            return;
        }

        // Each controlled-vocabulary field is normalised to its canonical form when present.
        // Values that do not match any allowed value are left untouched so that the FASTA header
        // validation can report them.
        FastaHeader header = headerOpt.get();
        if (header.getMoleculeType() != null) {
            ControlledVocabularyUtils.canonicalise(MolType.class, header.getMoleculeType())
                    .ifPresent(header::setMoleculeType);
        }
        if (header.getTopology() != null) {
            ControlledVocabularyUtils.canonicalise(Topology.class, header.getTopology())
                    .ifPresent(header::setTopology);
        }
        if (header.getChromosomeType() != null) {
            ControlledVocabularyUtils.canonicalise(ChromosomeType.class, header.getChromosomeType())
                    .ifPresent(header::setChromosomeType);
        }
        if (header.getChromosomeLocation() != null) {
            ControlledVocabularyUtils.canonicalise(ChromosomeLocation.class, header.getChromosomeLocation())
                    .ifPresent(header::setChromosomeLocation);
        }
    }
}
