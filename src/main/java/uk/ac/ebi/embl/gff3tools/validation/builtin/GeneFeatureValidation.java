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

import java.util.*;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;
import uk.ac.ebi.embl.gff3tools.validation.provider.TaxonProvider;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

@Gff3Validation(name = "GENE_FEATURE")
public class GeneFeatureValidation implements Validation {

    @InjectContext
    private ValidationContext context;

    private static final String GENE_ASSOCIATION_VALIDATION =
            "Features sharing gene \"%s\" are associated with \"%s\" attributes with different values (\"%s\" and \"%s\")";

    private static final String GENE_FEATURE_LOCUS_VALIDATION = "locus_tag=\"%s\" already used by \"%s\" and \"%s\"";

    private static final String DIFFERENT_GENE_VALUES_MESSAGE =
            "Features sharing locus_tag \"%s\" are associated with \"gene\" qualifiers with different values (\"%s\" and \"%s\").";

    private static final String DIFFERENT_GENE_SYNONYM_VALUES_MESSAGE =
            "Features sharing locus_tag \"%s\" are associated with \"gene_synonym\" qualifiers with different sets of values. They should all share the same values.";

    public static final String LOCUS_TAG_EXISTS_RULE = "LOCUS_TAG_EXISTS";

    private static final String LOCUS_TAG_EXISTS_MESSAGE =
            "/locus_tag must exist for annotated contigs/scaffolds/chromosomes.";

    /**
     * Entry data classes that identify a genome submission. WGS covers assembly contigs; STD is
     * included for parity with ENA, which applies this rule to standard entries too — such an entry
     * missing a locus_tag is rejected at Webin, so passing it here would only defer the failure.
     */
    private static final Set<String> GENOME_DATA_CLASSES = Set.of("WGS", "STD");

    /** The domain that opens a viral lineage, e.g. "Viruses; Riboviria; ...". */
    private static final String VIRUS_LINEAGE_DOMAIN = "Viruses";

    private final Map<String, Map<String, String>> annotationGeneToLocusTag = new HashMap<>();
    private final Map<String, Map<String, String>> annotationGeneToPseudoGene = new HashMap<>();
    private final Map<String, Map<String, GFF3Feature>> annotationLocusTagToGeneFeature = new HashMap<>();
    private final Map<String, Map<String, String>> annotationLocusTagToGene = new HashMap<>();
    private final Map<String, Map<String, List<String>>> annotationLocusTagToSynonyms = new HashMap<>();

    @ValidationMethod(
            rule = "GENE_ASSOCIATION",
            description =
                    "Check that features sharing a gene name are associated with the same locus_tag and pseudogene values",
            type = ValidationType.ANNOTATION,
            severity = RuleSeverity.WARN)
    public void validateGeneAssociation(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        Map<String, String> geneToLocusTag = new HashMap<>();
        Map<String, String> geneToPseudoGene = new HashMap<>();
        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            if (feature == null || !feature.hasAttribute(GFF3Attributes.GENE)) continue;

            String geneName = feature.getAttribute(GFF3Attributes.GENE).orElse(null);
            String locusTag = feature.getAttribute(GFF3Attributes.LOCUS_TAG).orElse(null);
            String existingLocus = geneToLocusTag.get(geneName);
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());

            if (soIdOpt.isEmpty()) continue;

            String soId = soIdOpt.get();
            if (existingLocus != null && !Objects.equals(existingLocus, locusTag)) {
                boolean isRrna = soId.equals(OntologyTerm.RRNA.ID)
                        || soId.equals(OntologyTerm.PSEUDOGENIC_RRNA.ID)
                        || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENIC_RRNA.ID);

                if (!isRrna) {
                    throw new ValidationException(
                            line,
                            GENE_ASSOCIATION_VALIDATION.formatted(
                                    geneName, GFF3Attributes.LOCUS_TAG, existingLocus, locusTag));
                }
            }
            geneToLocusTag.put(geneName, locusTag);

            String pseudoGeneName =
                    feature.getAttribute(GFF3Attributes.PSEUDOGENE).orElse(null);
            String existingPseudo = geneToPseudoGene.get(geneName);
            if (existingPseudo != null && !Objects.equals(existingPseudo, pseudoGeneName)) {
                throw new ValidationException(
                        line,
                        GENE_ASSOCIATION_VALIDATION.formatted(
                                geneName, GFF3Attributes.PSEUDOGENE, existingPseudo, pseudoGeneName));
            }
            geneToPseudoGene.put(geneName, pseudoGeneName);
        }
    }

    @ValidationMethod(
            rule = "GENE_LOCUS_TAG_ASSOCIATION",
            description = "Check that different gene features do not share the same locus_tag",
            type = ValidationType.ANNOTATION)
    public void validateGeneLocusTagAssociation(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        Map<String, GFF3Feature> locusTagToGeneFeature = new HashMap<>();
        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());

            if (soIdOpt.isEmpty()) continue;

            String soId = soIdOpt.get();

            // Check if feature is a gene or pseudogene type
            boolean isGene = soId.equals(OntologyTerm.GENE.ID)
                    || soId.equals(OntologyTerm.PSEUDOGENE.ID)
                    || soId.equals(OntologyTerm.UNITARY_PSEUDOGENE.ID)
                    || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENE.ID)
                    || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.UNITARY_PSEUDOGENE.ID);

            if (!isGene) {
                continue;
            }

            String locusTag = feature.getAttribute(GFF3Attributes.LOCUS_TAG).orElse(null);
            if (locusTag == null || locusTag.isBlank()) {
                continue;
            }

            GFF3Feature existing = locusTagToGeneFeature.putIfAbsent(locusTag, feature);
            if (existing != null) {
                throw new ValidationException(
                        line, GENE_FEATURE_LOCUS_VALIDATION.formatted(locusTag, existing.getName(), feature.getName()));
            }
        }
    }

    @ValidationMethod(
            rule = "LOCUS_TAG_ASSOCIATION",
            description =
                    "Check that features sharing a locus_tag are associated with the same gene and gene_synonym values",
            type = ValidationType.ANNOTATION)
    public void validateLocusTagAssociation(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        Map<String, String> locusTagToGene = new HashMap<>();
        Map<String, List<String>> locusTagToSynonyms = new HashMap<>();
        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            if (feature == null || !feature.hasAttribute(GFF3Attributes.LOCUS_TAG)) {
                continue;
            }

            String locusTag = feature.getAttribute(GFF3Attributes.LOCUS_TAG).orElse(null);
            if (locusTag == null || locusTag.isBlank()) {
                continue;
            }

            if (isGeneOrCds(feature)) {
                extractLocusMappings(feature, locusTagToGene, locusTagToSynonyms);
            }

            String currentGene = feature.getAttribute(GFF3Attributes.GENE).orElse(null);
            List<String> currentSynonyms = parseSynonyms(
                    feature.getAttribute(GFF3Attributes.GENE_SYNONYM).orElse(null));

            if (currentGene != null) {
                String masterGene = locusTagToGene.get(locusTag);
                if (masterGene != null && !masterGene.equals(currentGene)) {
                    throw new ValidationException(
                            line, DIFFERENT_GENE_VALUES_MESSAGE.formatted(locusTag, masterGene, currentGene));
                }
                locusTagToGene.putIfAbsent(locusTag, currentGene);
            }

            List<String> masterSynonyms = locusTagToSynonyms.get(locusTag);
            if (masterSynonyms != null) {
                if (!currentSynonyms.isEmpty()
                        && !masterSynonyms.isEmpty()
                        && !areSynonymListsEqual(masterSynonyms, currentSynonyms)) {
                    throw new ValidationException(line, DIFFERENT_GENE_SYNONYM_VALUES_MESSAGE.formatted(locusTag));
                }
            } else {
                locusTagToSynonyms.put(locusTag, currentSynonyms);
            }
        }
    }

    private void extractLocusMappings(
            GFF3Feature feature, Map<String, String> locusTagToGene, Map<String, List<String>> locusTagToSynonyms) {
        String locusTag = feature.getAttribute(GFF3Attributes.LOCUS_TAG).orElse(null);
        if (locusTag == null) return;

        String gene = feature.getAttribute(GFF3Attributes.GENE).orElse(null);
        if (gene != null && !gene.isEmpty() && locusTagToGene.isEmpty()) {
            locusTagToGene.put(locusTag, gene);
        }

        String synonymsRaw = feature.getAttribute(GFF3Attributes.GENE_SYNONYM).orElse(null);
        if (synonymsRaw != null && !synonymsRaw.isEmpty() && locusTagToSynonyms.isEmpty()) {
            List<String> synonyms = Arrays.stream(synonymsRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            locusTagToSynonyms.put(locusTag, synonyms);
        }
    }

    private List<String> parseSynonyms(String synonymValue) {
        if (synonymValue == null || synonymValue.isEmpty()) return List.of();
        return Arrays.stream(synonymValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private boolean areSynonymListsEqual(List<String> list1, List<String> list2) {
        if (list1.size() != list2.size()) return false;
        return new HashSet<>(list1).equals(new HashSet<>(list2));
    }

    private boolean isGeneOrCds(GFF3Feature feature) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        String featureName = feature.getName();
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(featureName);
        if (soIdOpt.isEmpty()) {
            return false;
        }

        String soId = soIdOpt.get();

        return soId.equals(OntologyTerm.GENE.ID)
                || soId.equals(OntologyTerm.CDS.ID)
                || soId.equals(OntologyTerm.PSEUDOGENIC_CDS.ID)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.GENE.ID)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.CDS.ID)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENE.ID)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENIC_CDS.ID);
    }

    /**
     * Ports {@code LocustagExistsCheck}: an annotated entry of a genome submission must carry a
     * locus_tag somewhere. ENA's own check never runs for GFF3 input — it is gated on the EMBL file
     * type — which is why it is reimplemented here rather than inherited.
     *
     * <p>The gates are ENA's, in ENA's order: the submission must be a genome, an entry carrying a
     * repeat_region or misc_feature is exempt outright, viruses are exempt, and an entry with no
     * annotation has nothing to tag. Note this is an entry-level requirement — one locus_tag
     * anywhere satisfies it. That each gene needs its own is {@code GENE_LOCUS_TAG_ASSOCIATION}'s
     * business, not this rule's.
     */
    @ValidationMethod(
            rule = LOCUS_TAG_EXISTS_RULE,
            description = "Check that annotated entries of a genome submission carry a locus_tag",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateLocusTagExists(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        String accession = gff3Annotation.getAccession();

        if (!isGenomeSubmission(accession)
                || hasLocusTagExemptFeature(gff3Annotation)
                || isVirus(accession)
                || !hasAnnotation(gff3Annotation)
                || hasLocusTag(gff3Annotation)) {
            return;
        }

        throw new ValidationException(line, LOCUS_TAG_EXISTS_MESSAGE);
    }

    /**
     * A genome submission is identified from caller-supplied context, never from the GFF3 file
     * itself, which carries no such marker. Either signal is enough: the analysis type when the
     * caller registered an {@link AnalysisContext}, or the entry data class when a master entry is
     * available. A caller may supply one without the other.
     *
     * <p>Both providers are auto-registered by the classpath scan, but with nothing in them unless
     * a caller supplies it: the analysis type defaults to {@link AnalysisType#UNKNOWN} and the
     * metadata provider has no sources until {@code --master-entry} is given. So a plain
     * {@code gff3tools validation <file>} resolves to neither signal and the rule does not apply —
     * firing there would reject single-sequence submissions that never needed a locus_tag.
     */
    private boolean isGenomeSubmission(String accession) {
        AnalysisType analysisType = context.contains(AnalysisContext.class)
                ? context.get(AnalysisContext.class).getAnalysisType()
                : AnalysisType.UNKNOWN;

        // A transcriptome is never a genome, whatever data class it may have inherited from a master.
        if (analysisType == AnalysisType.TRANSCRIPTOME_ASSEMBLY) {
            return false;
        }
        if (analysisType == AnalysisType.SEQUENCE_ASSEMBLY) {
            return true;
        }

        return getMasterMetadata(accession)
                .map(MasterMetadata::getEffectiveDataClass)
                .map(dataClass -> GENOME_DATA_CLASSES.contains(dataClass.toUpperCase(Locale.ROOT)))
                .orElse(false);
    }

    /**
     * Whether the entry carries a feature that exempts it from the requirement. ENA drops the whole
     * entry from the check when a repeat_region or misc_feature is present, which is deliberately
     * lenient towards feature-sparse entries.
     *
     * <p>Matched by SO term rather than INSDC name: all seventeen SO terms that map to
     * repeat_region are {@code SO:0000657} or its descendants, while misc_feature comes from
     * exactly three terms, matched exactly — {@code sequence_feature} and {@code biological_region}
     * sit near the root of the ontology, so descending from them would exempt almost anything.
     */
    private boolean hasLocusTagExemptFeature(GFF3Annotation gff3Annotation) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            if (feature == null) {
                continue;
            }
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isEmpty()) {
                continue;
            }
            String soId = soIdOpt.get();
            if (ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.REPEAT_REGION.ID)
                    || OntologyTerm.FEATURE.ID.equals(soId)
                    || OntologyTerm.BIOLOGICAL_REGION.ID.equals(soId)
                    || OntologyTerm.SEQUENCE_COMPARISON.ID.equals(soId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Viruses are exempt. ENA asks its taxonomy client whether the organism is a child of
     * "Viruses"; the lineage on the master entry answers the same question without a lookup, and
     * {@link TaxonProvider} covers callers that supply taxonomy but no master entry.
     *
     * <p>An organism that cannot be resolved is not treated as a virus: ENA's check falls through
     * to the locus_tag test when it has no source feature either, and guessing the other way would
     * silently exempt every entry a caller supplies no taxonomy for.
     */
    private boolean isVirus(String accession) {
        Optional<String> lineage = getMasterMetadata(accession).map(MasterMetadata::getLineage);

        if (lineage.isEmpty() && context.contains(TaxonProvider.class)) {
            lineage = context.get(TaxonProvider.class).resolve(accession).map(Taxon::getLineage);
        }

        return lineage.filter(GeneFeatureValidation::isVirusLineage).isPresent();
    }

    private static boolean isVirusLineage(String lineage) {
        return VIRUS_LINEAGE_DOMAIN.equalsIgnoreCase(lineage.split(";", 2)[0].trim());
    }

    /**
     * Mirrors {@code SequenceEntryUtils.hasAnnotation}: any feature beyond the source feature and
     * gaps counts as annotation. The source feature arrives as a whole-sequence {@code region} in
     * GFF3, matched exactly because nearly every SO term descends from it. A feature whose name
     * resolves to no SO term is counted as annotation rather than dismissed.
     */
    private boolean hasAnnotation(GFF3Annotation gff3Annotation) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            if (feature == null) {
                continue;
            }
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isEmpty()) {
                return true;
            }
            String soId = soIdOpt.get();
            boolean sourceOrGap = OntologyTerm.REGION.ID.equals(soId)
                    || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.GAP.ID);
            if (!sourceOrGap) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLocusTag(GFF3Annotation gff3Annotation) {
        return gff3Annotation.getFeatures().stream()
                .filter(Objects::nonNull)
                .map(feature -> feature.getAttribute(GFF3Attributes.LOCUS_TAG).orElse(null))
                .anyMatch(locusTag -> locusTag != null && !locusTag.isBlank());
    }

    private Optional<MasterMetadata> getMasterMetadata(String accession) {
        if (!context.contains(MasterMetadataProvider.class)) {
            return Optional.empty();
        }
        return context.get(MasterMetadataProvider.class).getMetadata(accession);
    }
}
