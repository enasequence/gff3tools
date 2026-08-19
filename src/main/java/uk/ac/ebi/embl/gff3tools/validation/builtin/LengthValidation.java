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

    /** INSDC Annotation Minimum Specification b.v.1: complete coding regions must be at least 30 aa long. */
    private static final long COMPLETE_CDS_MIN_AMINO_ACIDS = 25;

    /** 30 amino acids of coding sequence plus the terminal stop codon, which INSDC includes in the CDS. */
    private static final long COMPLETE_CDS_MIN_LENGTH = (COMPLETE_CDS_MIN_AMINO_ACIDS + 1) * 3;

    private static final String INVALID_PROPEPTIDE_LENGTH_MESSAGE =
            "Propeptide feature length must be a multiple of 3 for accession \"%s\"";
    private static final String INVALID_INTRON_LENGTH_MESSAGE = "Intron feature length is invalid for accession \"%s\"";
    private static final String INVALID_EXON_LENGTH_MESSAGE = "Exon feature length is invalid for accession \"%s\"";

    private static final String INVALID_CDS_LENGTH_MESSAGE =
            "Complete coding regions must be at least %d amino acids long for accession \"%s\". Provide /experiment or /inference evidence for the coding region, or mark the feature as 5' or 3' partial.";

    private static final String INVALID_CDS_INTRON_LENGTH_MESSAGE =
            "Intron usually expected to be at least 10 nt long. Please check accuracy and Use one of the following options for annotation: \n /artificial_location=\"heterogeneous population sequenced\" \n OR \n /artificial_location=\"low-quality sequence region\". \n Alternatively, use where appropriate: \n /pseudo, /pseudogene, /trans_splicing, /ribosomal_slippage";

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
     * Runs at LOW priority so that it executes after the LOW-priority {@link uk.ac.ebi.embl.gff3tools.validation.fix.TranslationFix}, whose
     * conceptual translations this rule counts and whose partiality fixes it must observe.
     */
    @ValidationMethod(rule = "CDS_LENGTH", type = ValidationType.ANNOTATION, priority = ValidationPriority.LOW)
    public void validateCdsLength(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        Map<String, List<GFF3Feature>> cdsListById = new LinkedHashMap<>();
        List<List<GFF3Feature>> cdsWithoutId = new ArrayList<>();

        for (GFF3Feature feature : gff3Annotation.getFeatures()) {

            if (feature == null) continue;

            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isEmpty()) continue;

            boolean isCds = OntologyTerm.CDS.ID.equals(soIdOpt.get())
                    || ontologyClient.isSelfOrDescendantOf(soIdOpt.get(), OntologyTerm.CDS.ID);

            if (!isCds) continue;

            String cdsId = feature.getAttribute(GFF3Attributes.ATTRIBUTE_ID).orElse(null);

            // Segments of a spliced coding region share an ID. A CDS without one is measured on its
            // own so that unrelated coding regions are never summed into a single length.
            if (cdsId == null) {
                cdsWithoutId.add(List.of(feature));
            } else {
                cdsListById.computeIfAbsent(cdsId, k -> new ArrayList<>()).add(feature);
            }
        }

        for (List<GFF3Feature> cdsGroup : cdsListById.values()) {
            validateCdsLength(cdsGroup, line);
        }
        for (List<GFF3Feature> cdsGroup : cdsWithoutId) {
            validateCdsLength(cdsGroup, line);
        }
    }

    private void validateCdsLength(List<GFF3Feature> cdsList, int line) throws ValidationException {

        if (cdsList.isEmpty()) {
            return;
        }

        List<GFF3Feature> sortedCdsList = new ArrayList<>(cdsList);
        sortedCdsList.sort(Comparator.comparingLong(GFF3Feature::getStart));
        GFF3Feature first = sortedCdsList.get(0);
        GFF3Feature last = sortedCdsList.get(sortedCdsList.size() - 1);

        // Join-level partiality is determined from the boundary segments. On complement joins,
        // either boundary can carry the effective 5'/3' partial flag.
        if (first.isFivePrimePartial()
                || last.isFivePrimePartial()
                || first.isThreePrimePartial()
                || last.isThreePrimePartial()) {
            return;
        }

        long length = 0;
        for (GFF3Feature cds : sortedCdsList) {
            if (isPseudo(cds) || hasEvidence(cds)) {
                return;
            }
            length += cds.getLength();
        }

        // Preferred measure: the conceptual translation computed by the TRANSLATION fix, which
        // excludes the trailing stop codon and so is the amino acid count the specification means.
        String translation = getComputedTranslation(first);
        if (translation != null && !translation.isEmpty()) {
            if (translation.length() < COMPLETE_CDS_MIN_AMINO_ACIDS) {
                throw new ValidationException(
                        line, INVALID_CDS_LENGTH_MESSAGE.formatted(COMPLETE_CDS_MIN_AMINO_ACIDS, first.accession()));
            }
            return;
        }

        // No translation available (no sequence supplied, or a coding region without an ID), so fall
        // back to measuring nucleotides. A "transl_except" may declare a one or two base stop codon at
        // the 3' end, which leaves a complete coding region short of a multiple of three and makes the
        // nucleotide measure unreliable, so it is not applied to those features.
        for (GFF3Feature cds : sortedCdsList) {
            if (cds.hasAttribute(GFF3Attributes.TRANSL_EXCEPT)) {
                return;
            }
        }

        if (length < COMPLETE_CDS_MIN_LENGTH) {
            throw new ValidationException(
                    line, INVALID_CDS_LENGTH_MESSAGE.formatted(COMPLETE_CDS_MIN_AMINO_ACIDS, first.accession()));
        }
    }

    /**
     * The translation recorded by the TRANSLATION fix for this coding region, or {@code null} when
     * none was computed. Keyed exactly as {@code TranslationFix} keys it, from the segment with the
     * lowest start position.
     */
    private String getComputedTranslation(GFF3Feature representative) {
        if (!context.contains(TranslationState.class)) {
            return null;
        }
        String key = TranslationState.buildKey(
                representative.accession(), representative.getId().orElse(null));
        if (key == null) {
            return null;
        }
        TranslationState.TranslationEntry entry =
                context.get(TranslationState.class).get(key);
        return entry == null ? null : entry.newTranslation();
    }

    /** INSDC Annotation Minimum Specification, table 2: the b.v.1 exception for short coding regions. */
    private boolean hasEvidence(GFF3Feature feature) {
        return feature.hasAttribute(GFF3Attributes.EXPERIMENT) || feature.hasAttribute(GFF3Attributes.INFERENCE);
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
}
