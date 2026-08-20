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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

@Gff3Validation(name = "LENGTH")
public class LengthValidation implements Validation {

    private static final long INTRON_FEATURE_MIN_LENGTH = 10;
    private static final long EXON_FEATURE_MIN_LENGTH = 15;
    private static final long COMPLETE_CDS_MIN_AMINO_ACIDS = 25;
    private static final long COMPLETE_CDS_MIN_LENGTH = (COMPLETE_CDS_MIN_AMINO_ACIDS + 1) * 3;

    /** INSDC Annotation Minimum Specification b.v.2: complete tRNA features are 50-150 bp long. */
    private static final long COMPLETE_TRNA_MIN_LENGTH = 50;

    private static final long COMPLETE_TRNA_MAX_LENGTH = 150;

    private static final String INVALID_PROPEPTIDE_LENGTH_MESSAGE =
            "Propeptide feature length must be a multiple of 3 for accession \"%s\"";
    private static final String INVALID_INTRON_LENGTH_MESSAGE = "Intron feature length is invalid for accession \"%s\"";
    private static final String INVALID_EXON_LENGTH_MESSAGE = "Exon feature length is invalid for accession \"%s\"";
    private static final String INVALID_CDS_LENGTH_MESSAGE =
            "Complete coding regions must be at least %d amino acids long for accession \"%s\". "
                    + "Provide /experiment or evidence for the coding region, "
                    + "or mark the feature as 5' or 3' partial.";
    private static final String INVALID_TRNA_LENGTH_MESSAGE =
            "Complete tRNA features must be between %d and %d bp long, but this one is %d bp, "
                    + "for accession \"%s\". Mark the feature as 5' or 3' partial if it is incomplete.";
    private static final String INVALID_CDS_INTRON_LENGTH_MESSAGE =
            "Intron usually expected to be at least 10 nt long. "
                    + "Please check accuracy and Use one of the following options for annotation: "
                    + "\n /artificial_location=\"heterogeneous population sequenced\" \n "
                    + "OR \n /artificial_location=\"low-quality sequence region\". "
                    + "\n Alternatively, use where appropriate: "
                    + "\n /pseudo, /pseudogene, /trans_splicing, /ribosomal_slippage";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(rule = "INTRON_LENGTH", type = ValidationType.FEATURE)
    public void validateIntronLength(GFF3Feature feature, int line) throws ValidationException {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        long length = feature.getLength();
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (soIdOpt.isEmpty()) return;

        if (ontologyClient.isSelfOrDescendantOf(soIdOpt.get(), OntologyTerm.INTRON.ID)
                && length < INTRON_FEATURE_MIN_LENGTH) {
            throw new ValidationException(line, INVALID_INTRON_LENGTH_MESSAGE.formatted(feature.accession()));
        }
    }

    @ValidationMethod(rule = "CDS_INTRON_LENGTH", type = ValidationType.ANNOTATION)
    public void validateCdsIntronLength(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        Map<String, List<GFF3Feature>> cdsListById = new HashMap<>();

        for (GFF3Feature feature : gff3Annotation.getFeatures()) {

            if (feature == null) continue;

            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isEmpty()) continue;

            boolean isCds = OntologyTerm.CDS.ID.equals(soIdOpt.get())
                    || ontologyClient.isSelfOrDescendantOf(soIdOpt.get(), OntologyTerm.CDS.ID);

            if (!isCds) continue;

            if (isPseudo(feature)
                    || feature.hasAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE)
                    || feature.hasAttribute(GFF3Attributes.TRANS_SPLICING)) {
                continue;
            }

            String cdsId = feature.getAttribute(GFF3Attributes.ATTRIBUTE_ID).orElse(null);

            cdsListById.computeIfAbsent(cdsId, k -> new ArrayList<>()).add(feature);
        }

        for (List<GFF3Feature> cdsGroup : cdsListById.values()) {
            validateCdsIntronLength(cdsGroup, line);
        }
    }

    private void validateCdsIntronLength(List<GFF3Feature> cdsList, int line) throws ValidationException {

        if (cdsList.size() <= 1) {
            return;
        }
        cdsList.sort(Comparator.comparingLong(GFF3Feature::getStart));

        for (int i = 1; i < cdsList.size(); i++) {
            GFF3Feature prev = cdsList.get(i - 1);
            GFF3Feature curr = cdsList.get(i);
            long intronLen = curr.getStart() - prev.getEnd();
            if (intronLen >= 0 && intronLen < 10) {
                boolean artificial = prev.hasAttribute(GFF3Attributes.ARTIFICIAL_LOCATION)
                        || curr.hasAttribute(GFF3Attributes.ARTIFICIAL_LOCATION);

                if (!artificial && !isPseudo(curr)) {
                    throw new ValidationException(line, INVALID_CDS_INTRON_LENGTH_MESSAGE);
                }
            }
        }
        cdsList.clear();
    }

    /**
     * Runs at LOW priority so that it executes after the LOW-priority
     * {@link uk.ac.ebi.embl.gff3tools.validation.fix.TranslationFix}
     */
    @ValidationMethod(
            rule = "CDS_LENGTH",
            description = "Complete coding regions must be at least 25 amino acids long",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.LOW)
    public void validateCdsLength(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        // Segments of a spliced coding region share an ID, and are keyed here exactly as
        // TranslationFix keys them so that the recorded translations can be looked up below.
        Map<String, List<GFF3Feature>> cdsGroups = gff3Annotation.getFeatures().stream()
                .filter(this::isCds)
                .collect(Collectors.groupingBy(
                        f -> f.getId().orElse("__no_id_" + f.getStart() + "_" + f.getEnd()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (List<GFF3Feature> segments : cdsGroups.values()) {
            validateCdsLength(segments, line);
        }
    }

    @ValidationMethod(
            rule = "TRNA_LENGTH",
            description = "Complete tRNA features must be between 50 and 150 bp long, measured across"
                    + " the segments sharing an ID that make up one spliced tRNA",
            type = ValidationType.ANNOTATION)
    public void validateTrnaLength(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        // Features without an ID are keyed individually, so unrelated tRNAs are never summed as one.
        Map<String, List<GFF3Feature>> trnaGroups = gff3Annotation.getFeatures().stream()
                .filter(this::isTrna)
                .collect(Collectors.groupingBy(
                        f -> f.getId().orElse("__no_id_" + f.getStart() + "_" + f.getEnd()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (List<GFF3Feature> segments : trnaGroups.values()) {
            validateTrnaLength(segments, line);
        }
    }

    @ValidationMethod(rule = "EXON_LENGTH", type = ValidationType.FEATURE, severity = RuleSeverity.WARN)
    public void validateExonLength(GFF3Feature feature, int line) throws ValidationException {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        long length = feature.getLength();
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (soIdOpt.isEmpty()) return;

        if (ontologyClient.isSelfOrDescendantOf(soIdOpt.get(), OntologyTerm.EXON.ID)
                && length < EXON_FEATURE_MIN_LENGTH) {
            throw new ValidationException(line, INVALID_EXON_LENGTH_MESSAGE.formatted(feature.accession()));
        }
    }

    @ValidationMethod(rule = "PROPEPTIDE_LENGTH", type = ValidationType.FEATURE)
    public void validatePropeptideLength(GFF3Feature feature, int line) throws ValidationException {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (soIdOpt.isEmpty()) return;

        if (!OntologyTerm.PROPEPTIDE.ID.equals(soIdOpt.get())) {
            return;
        }
        if (!feature.hasAttribute(GFF3Attributes.TRANSL_EXCEPT)
                && !feature.hasAttribute(GFF3Attributes.EXCEPTION)
                && !feature.hasAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE)
                && feature.getLength() % 3 != 0) {
            throw new ValidationException(line, INVALID_PROPEPTIDE_LENGTH_MESSAGE.formatted(feature.accession()));
        }
    }

    public boolean isPseudo(GFF3Feature feature) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (soIdOpt.isEmpty()) return false;

        if (ontologyClient.isSelfOrDescendantOf(soIdOpt.get(), OntologyTerm.PSEUDOGENIC_REGION.ID)) {
            return true;
        }
        return feature.hasAttribute(GFF3Attributes.PSEUDO) || feature.hasAttribute(GFF3Attributes.PSEUDOGENE);
    }

    private boolean isCds(GFF3Feature feature) {
        return feature != null && OntologyTerm.CDS.name().equals(feature.getName());
    }

    private boolean isTrna(GFF3Feature feature) {
        if (feature == null) {
            return false;
        }
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        return ontologyClient
                .findTermByNameOrSynonym(feature.getName())
                .map(soId -> ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.TRNA.ID))
                .orElse(false);
    }

    private void validateTrnaLength(List<GFF3Feature> trnaList, int line) throws ValidationException {
        List<GFF3Feature> sortedTrnaGroup = new ArrayList<>(trnaList);
        sortedTrnaGroup.sort(Comparator.comparingLong(GFF3Feature::getStart));
        if (sortedTrnaGroup.isEmpty()
                || isPartial(sortedTrnaGroup)
                || sortedTrnaGroup.stream().anyMatch(this::isPseudo)) {
            return;
        }

        long length = sortedTrnaGroup.stream().mapToLong(GFF3Feature::getLength).sum();
        if (length < COMPLETE_TRNA_MIN_LENGTH || length > COMPLETE_TRNA_MAX_LENGTH) {
            throw new ValidationException(
                    line,
                    INVALID_TRNA_LENGTH_MESSAGE.formatted(
                            COMPLETE_TRNA_MIN_LENGTH,
                            COMPLETE_TRNA_MAX_LENGTH,
                            length,
                            sortedTrnaGroup.get(0).accession()));
        }
    }

    private void validateCdsLength(List<GFF3Feature> cdsList, int line) throws ValidationException {
        List<GFF3Feature> sortedCdsGroup = new ArrayList<>(cdsList);
        sortedCdsGroup.sort(Comparator.comparingLong(GFF3Feature::getStart));
        //length does not need to be validated on incomplete CDS or one that has inference/evidence as to why it's short
        if (sortedCdsGroup.isEmpty()
                || isPartial(sortedCdsGroup)
                || sortedCdsGroup.stream().anyMatch(cds -> isPseudo(cds) || hasEvidence(cds))) {
            return;
        }

        String translation = getCdsTranslation(sortedCdsGroup);
        boolean tooShort;
        if (translation != null && !translation.isEmpty()) {
            // Preferred measure: the conceptual translation computed by the TRANSLATION fix
            tooShort = translation.length() < COMPLETE_CDS_MIN_AMINO_ACIDS;
        } else if (sortedCdsGroup.stream().anyMatch(cds -> cds.hasAttribute(GFF3Attributes.TRANSL_EXCEPT))) {
            // No translation available, and "transl_except" make the nucleotide measure below unreliable.
            return;
        } else {
            // No translation available eg. due to translation turned off, but no transl_except either
            long lengthInNucleotides =
                    sortedCdsGroup.stream().mapToLong(GFF3Feature::getLength).sum();
            tooShort = lengthInNucleotides < COMPLETE_CDS_MIN_LENGTH;
        }

        if (tooShort) {
            throw new ValidationException(
                    line,
                    INVALID_CDS_LENGTH_MESSAGE.formatted(
                            COMPLETE_CDS_MIN_AMINO_ACIDS, sortedCdsGroup.get(0).accession()));
        }
    }

    private boolean isPartial(List<GFF3Feature> segments) {
        GFF3Feature first = segments.get(0);
        GFF3Feature last = segments.get(segments.size() - 1);
        return first.isFivePrimePartial()
                || last.isFivePrimePartial()
                || first.isThreePrimePartial()
                || last.isThreePrimePartial();
    }

    private String getCdsTranslation(List<GFF3Feature> segments) {
        if (!context.contains(TranslationState.class)) {
            return null;
        }
        GFF3Feature keySource = segments.get(0);
        String key = TranslationState.buildKey(
                keySource.accession(), keySource.getId().orElse(null));
        if (key == null) {
            return null;
        }
        TranslationState.TranslationEntry entry =
                context.get(TranslationState.class).get(key);
        return entry == null ? null : entry.newTranslation();
    }

    /** INSDC Annotation Minimum Specification, table 2: the b.v.1 exception for short coding regions. */
    private boolean hasEvidence(GFF3Feature feature) {
        return feature.hasAttribute(GFF3Attributes.EXPERIMENT);
    }
}
