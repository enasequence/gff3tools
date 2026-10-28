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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;
import uk.ac.ebi.embl.gff3tools.validation.provider.LocusTagProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.TaxonProvider;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

@Gff3Validation(name = "LOCUS_TAG")
public class LocusTagExistsValidation implements Validation {

    public static final String LOCUS_TAG_EXISTS_RULE = "LOCUS_TAG_EXISTS";
    public static final String LOCUS_TAG_PREFIX_RULE = "LOCUS_TAG_PREFIX_CHECK";
    public static final String LOCUS_TAG_REGISTERED_PREFIX_RULE = "LOCUS_TAG_REGISTERED_PREFIX_CHECK";

    // INSDC: prefix is 3-12 alphanumeric characters, must start with a letter.
    // LocusTagFix (FEATURE-level fix, same priority) always runs before this validation, so the
    // value is already uppercased by the time the check fires.
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]{2,11}$");

    private static final String LOCUS_TAG_EXISTS_MESSAGE =
            "/locus_tag must exist for annotated contigs/scaffolds/chromosomes.";

    /** The domain that opens a viral lineage, e.g. "Viruses; Riboviria; ...". */
    private static final String VIRUS_LINEAGE_DOMAIN = "Viruses";

    /**
     * The GFF3 stand-ins for INSDC {@code misc_feature}. These are the three SO terms that
     * {@code feature-mapping.tsv} converts to a {@code misc_feature}:
     *
     * <ul>
     *   <li>{@code sequence_feature} (SO:0000110), which {@code so.owl} also lists as an exact
     *       synonym of {@code misc_feature};
     *   <li>{@code biological_region} (SO:0001411);
     *   <li>{@code sequence_comparison} (SO:0002072), written as a {@code misc_feature} with
     *       {@code /note="sequence_comparison"}.
     * </ul>
     *
     * <p>Exempting them gives the same outcome as {@code LocustagExistsCheck} would on the converted
     * flat file.
     */
    private static final Set<String> MISC_FEATURE_SO_IDS =
            Set.of(OntologyTerm.FEATURE.ID, OntologyTerm.BIOLOGICAL_REGION.ID, OntologyTerm.SEQUENCE_COMPARISON.ID);

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = LOCUS_TAG_EXISTS_RULE,
            description = "Check that annotated entries of a genome submission carry a locus_tag",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateLocusTagExists(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        String accession = gff3Annotation.getAccession();

        if (!isGenomeSubmission() || !lacksRequiredLocusTag(gff3Annotation) || !isKnownNonVirus(accession)) {
            return;
        }

        throw new ValidationException(LOCUS_TAG_EXISTS_RULE, line, LOCUS_TAG_EXISTS_MESSAGE);
    }

    /**
     * Only the caller can tell us this is an assembly submission - nothing in a GFF3 marks one.
     * {@link AnalysisContext} is classpath-scanned and defaults to {@link AnalysisType#UNKNOWN}, so
     * the rule stays inert unless a caller registers a real analysis type.
     *
     * <p>{@code sequencetools} gates this rule on the WGS and STD data classes within the assembly
     * scopes, which is {@link AnalysisType#SEQUENCE_ASSEMBLY} here. It places no condition on the
     * molecule type.
     */
    private boolean isGenomeSubmission() {
        AnalysisType analysisType = context.contains(AnalysisContext.class)
                ? context.get(AnalysisContext.class).getAnalysisType()
                : AnalysisType.UNKNOWN;
        return analysisType == AnalysisType.SEQUENCE_ASSEMBLY;
    }

    /**
     * Whether the annotation needs a locus_tag that none of its features carries, decided in one
     * pass over the features:
     *
     * <ul>
     *   <li>One repeat_region or misc_feature exempts the whole entry, not just that feature -
     *       matching the {@code excludeFeatureCheckList} early return in {@code sequencetools}.
     *   <li>One feature with a locus_tag satisfies the rule.
     *   <li>Otherwise a locus_tag is needed only if the entry is annotated. Gaps and the
     *       whole-sequence region line do not count, which excludes FASTA submissions by default.
     * </ul>
     */
    private boolean lacksRequiredLocusTag(GFF3Annotation gff3Annotation) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        boolean annotated = false;
        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            if (feature == null) {
                continue;
            }
            Optional<String> soId = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soId.isPresent() && isLocusTagExempt(ontologyClient, soId.get())) {
                return false;
            }
            if (hasLocusTag(feature)) {
                return false;
            }
            annotated |= soId.map(id -> !OntologyTerm.REGION.ID.equals(id)
                            && !ontologyClient.isSelfOrDescendantOf(id, OntologyTerm.GAP.ID))
                    .orElse(true);
        }
        return annotated;
    }

    /**
     * The GFF3 form of {@code LocustagExistsCheck}'s two exempt features, {@code repeat_region} and
     * {@code misc_feature}:
     *
     * <ul>
     *   <li>{@code repeat_region} (SO:0000657) and its descendants, such as {@code tandem_repeat}.
     *       Every descendant that {@code feature-mapping.tsv} maps converts to an INSDC
     *       {@code repeat_region}.
     *   <li>{@link #MISC_FEATURE_SO_IDS} for {@code misc_feature}.
     * </ul>
     */
    private boolean isLocusTagExempt(OntologyClient ontologyClient, String soId) {
        return ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.REPEAT_REGION.ID)
                || MISC_FEATURE_SO_IDS.contains(soId);
    }

    private boolean hasLocusTag(GFF3Feature feature) {
        return feature.getAttribute(GFF3Attributes.LOCUS_TAG)
                .filter(locusTag -> !locusTag.isBlank())
                .isPresent();
    }

    /**
     * Whether the organism is known and is not a virus. Viruses are exempt. An organism that does
     * not resolve cannot be ruled a non-virus, so the rule does not fire for it. That covers no
     * {@link TaxonProvider} being registered, as in client-side processing, and a lookup that finds
     * nothing, which includes a taxonomy service failure.
     */
    private boolean isKnownNonVirus(String accession) {
        return resolveTaxon(accession)
                .filter(taxon -> !taxon.isChildOf(VIRUS_LINEAGE_DOMAIN))
                .isPresent();
    }

    private Optional<Taxon> resolveTaxon(String accession) {
        return context.contains(TaxonProvider.class)
                ? context.get(TaxonProvider.class).resolve(accession)
                : Optional.empty();
    }

    @ValidationMethod(rule = LOCUS_TAG_PREFIX_RULE, type = ValidationType.FEATURE)
    public void validateLocusTagPrefix(GFF3Feature feature, int line) throws ValidationException {
        for (String locusTag :
                feature.getAttributeList(GFF3Attributes.LOCUS_TAG).orElse(List.of())) {
            validateLocusTagFormat(locusTag, line);
        }
    }

    private void validateLocusTagFormat(String locusTag, int line) throws ValidationException {
        int underscoreIdx = locusTag.indexOf('_');
        if (underscoreIdx < 0) {
            throw new ValidationException(
                    LOCUS_TAG_PREFIX_RULE,
                    line,
                    "Invalid locus_tag \"%s\": must be in format PREFIX_tag".formatted(locusTag));
        }

        String prefix = locusTag.substring(0, underscoreIdx);
        String tag = locusTag.substring(underscoreIdx + 1);

        if (!PREFIX_PATTERN.matcher(prefix).matches()) {
            throw new ValidationException(
                    LOCUS_TAG_PREFIX_RULE,
                    line,
                    "Invalid locus_tag \"%s\": prefix \"%s\" must be 3-12 alphanumeric characters starting with a letter"
                            .formatted(locusTag, prefix));
        }

        if (tag.isEmpty()) {
            throw new ValidationException(
                    LOCUS_TAG_PREFIX_RULE,
                    line,
                    "Invalid locus_tag \"%s\": tag after underscore must not be empty".formatted(locusTag));
        }
    }

    /**
     * Checks that the locus_tag prefix is registered with the project in ERA.
     *
     * <p>Inert when no {@link LocusTagProvider} is registered on the context — the rule does
     * not fire in offline or client-side processing, and for submission types (FASTA-only) that
     * carry no locus_tag attributes.
     */
    @ValidationMethod(
            rule = LOCUS_TAG_REGISTERED_PREFIX_RULE,
            description = "Check that locus_tag prefix is registered with the project",
            type = ValidationType.ANNOTATION)
    public void validateRegisteredLocusTagPrefix(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        if (!context.contains(LocusTagProvider.class)) {
            return;
        }
        Set<String> registeredPrefixes = context.get(LocusTagProvider.class).getPrefixes().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toUnmodifiableSet());
        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            for (String locusTag :
                    feature.getAttributeList(GFF3Attributes.LOCUS_TAG).orElse(List.of())) {
                int idx = locusTag.indexOf('_');
                if (idx >= 0) {
                    String prefix = locusTag.substring(0, idx).toUpperCase();
                    if (!registeredPrefixes.contains(prefix)) {
                        throw new ValidationException(
                                LOCUS_TAG_REGISTERED_PREFIX_RULE,
                                line,
                                "locus_tag \"%s\": prefix \"%s\" is not registered with the project"
                                        .formatted(locusTag, prefix));
                    }
                }
            }
        }
    }
}
