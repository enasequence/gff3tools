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
import uk.ac.ebi.embl.api.validation.helper.Ascii7CharacterConverter;
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
            rule = "FASTA_HEADER_VALUE_NORMALISATION",
            description =
                    "Folds FASTA header values to ASCII7 and normalises controlled vocabulary fields to their canonical form.",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
    public void normaliseHeaderValues(GFF3Annotation annotation, int line) {
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

        FastaHeader header = headerOpt.get();

        // First fold every value to ASCII7 (removes diacritics, strips non-printable characters).
        // Ascii7CharacterConverter.convert is null-safe (null in -> null out) and a no-op for already-clean values.
        header.setDescription(Ascii7CharacterConverter.convert(header.getDescription()));
        header.setMoleculeType(Ascii7CharacterConverter.convert(header.getMoleculeType()));
        header.setTopology(Ascii7CharacterConverter.convert(header.getTopology()));
        header.setChromosomeType(Ascii7CharacterConverter.convert(header.getChromosomeType()));
        header.setChromosomeLocation(Ascii7CharacterConverter.convert(header.getChromosomeLocation()));
        header.setChromosomeName(Ascii7CharacterConverter.convert(header.getChromosomeName()));

        // Normalise controlled-vocabulary fields to their canonical form when present.
        // Values that do not match any allowed value are left untouched so that the
        // validation can report them.
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
        if ("nuclear".equalsIgnoreCase(header.getChromosomeLocation())) {
            // "nuclear" is not part of the INSDC /organelle vocabulary -- per the ENA assembly
            // submission docs, a nuclear chromosome_location is expressed by omitting the field.
            header.setChromosomeLocation(null);
        } else if (header.getChromosomeLocation() != null) {
            ControlledVocabularyUtils.canonicalise(ChromosomeLocation.class, header.getChromosomeLocation())
                    .ifPresent(header::setChromosomeLocation);
        }
    }
}
