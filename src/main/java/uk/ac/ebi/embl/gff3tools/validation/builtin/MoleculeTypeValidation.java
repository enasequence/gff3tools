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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

@Slf4j
@Gff3Validation(name = "MOLECULE_TYPE_FEATURE")
public class MoleculeTypeValidation implements Validation {
    public static final String REQUIRED_FEATURE_RULE = "MOLECULE_TYPE_REQUIRED_FEATURE";
    public static final String MRNA_CDS_COMPLEMENT_RULE = "MRNA_CDS_COMPLEMENT";

    private static final Map<ControlledVocabularyUtils.MolType, OntologyTerm> REQUIRED_FEATURE_BY_MOLECULE_TYPE =
            Map.of(
                    ControlledVocabularyUtils.MolType.RRNA,
                    OntologyTerm.RRNA,
                    ControlledVocabularyUtils.MolType.TRNA,
                    OntologyTerm.TRNA);

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = REQUIRED_FEATURE_RULE,
            description = "Check that molecule types with mandatory features contain those features",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void validateRequiredFeature(GFF3Annotation annotation, int line) throws ValidationException {
        Optional<ControlledVocabularyUtils.MolType> moleculeType = getMoleculeType(annotation.getAccession());
        if (moleculeType.isEmpty()) {
            return;
        }

        OntologyTerm requiredFeatureParent = REQUIRED_FEATURE_BY_MOLECULE_TYPE.get(moleculeType.get());
        if (requiredFeatureParent == null) {
            return;
        }

        OntologyClient ontologyClient = context.get(OntologyClient.class);
        for (final GFF3Feature feature : annotation.getFeatures()) {
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isEmpty()) continue;
            String soId = soIdOpt.get();
            if (ontologyClient.isSelfOrDescendantOf(soId, requiredFeatureParent.ID)) {
                return;
            }
        }

        throw new ValidationException(
                REQUIRED_FEATURE_RULE,
                line,
                "Feature %s is required when molecule type is %s."
                        .formatted(requiredFeatureParent, moleculeType.get()));
    }

    @ValidationMethod(
            rule = MRNA_CDS_COMPLEMENT_RULE,
            description = "Check that CDS features on mRNA entries do not use complement locations",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void validateMrnaCdsComplement(GFF3Annotation annotation, int line) throws ValidationException {
        if (!isMRNA(annotation.getAccession())) {
            return;
        }

        for (final GFF3Feature feature : annotation.getFeatures()) {
            if (isCds(feature) && feature.isComplement()) {
                throw new ValidationException(
                        MRNA_CDS_COMPLEMENT_RULE,
                        line,
                        "Complement locations are not permitted in CDS features on mRNA entries.");
            }
        }
    }

    private boolean isCds(GFF3Feature feature) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        String featureName = feature.getName();
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(featureName);
        if (soIdOpt.isEmpty()) {
            return false;
        }

        String soId = soIdOpt.get();

        return soId.equals(OntologyTerm.CDS.ID) || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.CDS.ID);
    }

    private boolean isMRNA(String accession) {
        Optional<ControlledVocabularyUtils.MolType> molTypeOpt = getMoleculeType(accession);
        return molTypeOpt
                .filter(molType -> molType == ControlledVocabularyUtils.MolType.MRNA)
                .isPresent();
    }

    private Optional<ControlledVocabularyUtils.MolType> getMoleculeType(String accession) {
        FastaHeaderProvider fastaHeaderProvider = context.get(FastaHeaderProvider.class);

        log.debug("Validating molecule type from FASTA header for accession {}", accession);
        Optional<FastaHeader> header = fastaHeaderProvider.getHeader(accession);
        if (header.isEmpty()) {
            log.warn("No FASTA header found for accession {}", accession);
            return Optional.empty();
        }

        return ControlledVocabularyUtils.normaliseMolType(header.get());
    }
}
