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
package uk.ac.ebi.embl.gff3tools.utils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils.MolType;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

public class ValidationUtils {

    public static Long resolveSequenceLength(
            String seqId, Map<String, Long> sequenceLengthCache, ValidationContext context) {
        if (sequenceLengthCache.containsKey(seqId)) {
            return sequenceLengthCache.get(seqId);
        }
        if (context.contains(SequenceLookup.class)) {
            SequenceLookup lookup = context.get(SequenceLookup.class);
            if (lookup != null) {
                try {
                    Long length = lookup.getSequenceLength(seqId);
                    sequenceLengthCache.put(seqId, length);
                    return length;
                } catch (Exception ex) {
                    throw new IllegalStateException("Unable to resolve sequence length for " + seqId, ex);
                }
            }
        }
        return null;
    }

    /**
     * The master metadata for an accession, or empty when none is available. The provider is
     * auto-registered by the classpath scan but holds no sources until a caller supplies a master
     * entry, so an empty result is the normal case rather than a failure.
     */
    public static Optional<MasterMetadata> getMasterMetadata(String accession, ValidationContext context) {
        if (!context.contains(MasterMetadataProvider.class)) {
            return Optional.empty();
        }
        return context.get(MasterMetadataProvider.class).getMetadata(accession);
    }

    /**
     * The molecule type declared for an accession in its FASTA header, or empty when no header is
     * available. This is where a submission's {@code MOLECULETYPE} reaches gff3tools; nothing in the
     * GFF3 file itself carries it.
     */
    public static Optional<MolType> getMoleculeType(String accession, ValidationContext context) {
        if (!context.contains(FastaHeaderProvider.class)) {
            return Optional.empty();
        }
        return context.get(FastaHeaderProvider.class)
                .getHeader(accession)
                .flatMap(ControlledVocabularyUtils::normaliseMolType);
    }

    /**
     * Groups the features an annotation holds into the whole features they make up: segments sharing
     * an ID are one spliced feature, and a segment without an ID is keyed on its own coordinates so
     * that unrelated features are never measured as though they were one.
     *
     * <p>Groups are returned in the order the features were encountered, and are keyed exactly as
     * {@code TranslationFix} keys the translations it records, so a group found here can be matched
     * to its recorded translation.
     *
     * @param selector chooses the features to group, by name or through the ontology
     */
    public static Map<String, List<GFF3Feature>> groupFeaturesById(
            GFF3Annotation annotation, Predicate<GFF3Feature> selector) {
        return annotation.getFeatures().stream()
                .filter(feature -> feature != null && selector.test(feature))
                .collect(Collectors.groupingBy(
                        ValidationUtils::featureGroupKey, LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * The key a feature is grouped under: its ID, or its coordinates when it has none. Features
     * without an ID cannot share a key, so each is treated as a feature in its own right.
     */
    public static String featureGroupKey(GFF3Feature feature) {
        return feature.getId().orElse("__no_id_" + feature.getStart() + "_" + feature.getEnd());
    }

    /**
     * The segment that stands for a whole feature group - the one with the lowest start position.
     * Every segment carries the same accession and ID, so this only fixes which segment's
     * coordinates are reported; it never selects what is measured.
     */
    public static GFF3Feature representativeOfFeatureGroup(List<GFF3Feature> segments) {
        return segments.stream()
                .min(Comparator.comparingLong(GFF3Feature::getStart))
                .orElseThrow();
    }
}
