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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

@Slf4j
@Gff3Validation(name = "MOLECULE_TYPE_FEATURE")
public class MoleculeTypeValidation implements Validation {
    public static final String REQUIRED_FEATURE_RULE = "MOLECULE_TYPE_REQUIRED_FEATURE";
    public static final String FORBIDDEN_FEATURE_RULE = "MOLECULE_TYPE_FORBIDDEN_FEATURE";
    public static final String MRNA_CDS_COMPLEMENT_RULE = "MRNA_CDS_COMPLEMENT";
    public static final String MRNA_CDS_JOINED_LOCATION_RULE = "MRNA_CDS_JOINED_LOCATION";

    /**
     * The molecule types the specification names in b.vi.9: a record of either holds a transcript
     * whose introns have already been spliced out.
     */
    private static final Set<ControlledVocabularyUtils.MolType> PROCESSED_TRANSCRIPT_MOLECULE_TYPES =
            Set.of(ControlledVocabularyUtils.MolType.MRNA, ControlledVocabularyUtils.MolType.TRANSCRIBED_RNA);

    private static final String CDS_JOINED_LOCATION_MESSAGE =
            "Coding regions must not span multiple joined locations on %s entries. The coding region on"
                    + " accession \"%s\" spans %d locations (%s). Where the join is a programmed frameshift,"
                    + " declare it with the ribosomal_slippage attribute.";

    private static final Map<ControlledVocabularyUtils.MolType, OntologyTerm> REQUIRED_FEATURE_BY_MOLECULE_TYPE =
            Map.of(
                    ControlledVocabularyUtils.MolType.RRNA,
                    OntologyTerm.RRNA,
                    ControlledVocabularyUtils.MolType.TRNA,
                    OntologyTerm.TRNA);

    private static final Map<ControlledVocabularyUtils.MolType, List<OntologyTerm>>
            FORBIDDEN_FEATURES_BY_MOLECULE_TYPE = Map.of(
                    ControlledVocabularyUtils.MolType.MRNA,
                    List.of(OntologyTerm.MRNA, OntologyTerm.TRNA),
                    ControlledVocabularyUtils.MolType.RRNA,
                    List.of(OntologyTerm.TRNA, OntologyTerm.CDS));

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = REQUIRED_FEATURE_RULE,
            description = "Check that molecule types with mandatory features contain those features",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
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
            rule = FORBIDDEN_FEATURE_RULE,
            description = "Check that molecule types do not contain features that are not permitted for them",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
    public void validateForbiddenFeature(GFF3Annotation annotation, int line) throws ValidationException {
        Optional<ControlledVocabularyUtils.MolType> moleculeType = getMoleculeType(annotation.getAccession());
        if (moleculeType.isEmpty()) {
            return;
        }

        List<OntologyTerm> forbiddenFeatureParents = FORBIDDEN_FEATURES_BY_MOLECULE_TYPE.get(moleculeType.get());
        if (forbiddenFeatureParents == null) {
            return;
        }

        OntologyClient ontologyClient = context.get(OntologyClient.class);
        for (final GFF3Feature feature : annotation.getFeatures()) {
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isEmpty()) continue;
            String soId = soIdOpt.get();
            for (final OntologyTerm forbiddenFeatureParent : forbiddenFeatureParents) {
                if (ontologyClient.isSelfOrDescendantOf(soId, forbiddenFeatureParent.ID)) {
                    throw new ValidationException(
                            FORBIDDEN_FEATURE_RULE,
                            line,
                            "Feature %s is not permitted when molecule type is %s."
                                    .formatted(feature.getName(), moleculeType.get()));
                }
            }
        }
    }

    @ValidationMethod(
            rule = MRNA_CDS_COMPLEMENT_RULE,
            description = "Check that CDS features on mRNA entries do not use complement locations",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
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

    /**
     * INSDC Annotation Minimum Specification b.vi.9.
     *
     * <p>An mRNA or transcribed RNA record holds a processed transcript, whose introns have already
     * been spliced out. A coding region on such a record therefore has nothing left to span and must
     * occupy a single location. The one exception the specification allows is a programmed
     * frameshift, declared with {@code ribosomal_slippage}.
     *
     * <p>The segments of one coding region share an {@code ID} - that is how a join reaches GFF3 -
     * so features are grouped by ID before their segments are counted. A CDS line carrying no ID is
     * a coding region in its own right and cannot be part of a join.
     */
    @ValidationMethod(
            rule = MRNA_CDS_JOINED_LOCATION_RULE,
            description = "Check that coding regions on mRNA and transcribed RNA entries occupy a single location",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
    public void validateMrnaCdsJoinedLocation(GFF3Annotation annotation, int line) throws ValidationException {
        Optional<ControlledVocabularyUtils.MolType> moleculeType = getMoleculeType(annotation.getAccession());
        if (moleculeType.isEmpty() || !PROCESSED_TRANSCRIPT_MOLECULE_TYPES.contains(moleculeType.get())) {
            return;
        }

        for (List<GFF3Feature> segments :
                ValidationUtils.groupFeaturesById(annotation, this::isCds).values()) {
            if (segments.size() < 2 || hasRibosomalSlippage(segments)) {
                continue;
            }
            throw new ValidationException(
                    MRNA_CDS_JOINED_LOCATION_RULE,
                    line,
                    CDS_JOINED_LOCATION_MESSAGE.formatted(
                            moleculeType.get().getValue(),
                            annotation.getAccession(),
                            segments.size(),
                            formatLocations(segments)));
        }
    }

    private boolean hasRibosomalSlippage(List<GFF3Feature> segments) {
        return segments.stream().anyMatch(segment -> segment.hasAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE));
    }

    /** The locations a coding region occupies, in coordinate order. */
    private String formatLocations(List<GFF3Feature> segments) {
        return segments.stream()
                .sorted(Comparator.comparingLong(GFF3Feature::getStart))
                .map(segment -> segment.getStart() + ".." + segment.getEnd())
                .collect(Collectors.joining(", "));
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
        FastaHeaderProvider fastaHeaderProvider =
                context.contains(FastaHeaderProvider.class) ? context.get(FastaHeaderProvider.class) : null;
        if (fastaHeaderProvider == null) {
            return Optional.empty();
        }

        log.debug("Validating molecule type from FASTA header for accession {}", accession);
        Optional<FastaHeader> header = fastaHeaderProvider.getHeader(accession);
        if (header.isEmpty()) {
            log.warn("No FASTA header found for accession {}", accession);
            return Optional.empty();
        }

        return ControlledVocabularyUtils.normaliseMolType(header.get());
    }
}
