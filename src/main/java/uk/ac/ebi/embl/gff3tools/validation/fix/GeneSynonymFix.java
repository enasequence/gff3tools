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

import java.util.*;

import jdk.jshell.spi.ExecutionControl;
import uk.ac.ebi.embl.gff3tools.fftogff3.FeatureMapping;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Fix(
        name = "GENE_SYNONYM",
        description =
                "Normalizes gene_synonym values across features sharing the same locus_tag/gene; CDS sets the canonical list",
        enabled = true)
public class GeneSynonymFix {

    private static final String FF_CDS = "CDS";
    private static final String GENE_SYNONYM = GFF3Attributes.GENE_SYNONYM;

    private HashSet<String> CDS_SYNONYMS = new HashSet<>();

    private HashMap<String, HashSet<String>> accessionNumberToLocusTagSet = new HashMap<>();

    public GeneSynonymFix() {
        FeatureMapping.getGFF3FeatureCandidateIdsAndNames(FF_CDS).forEach(CDS_SYNONYMS::add);
    }

    @FixMethod(
            rule = "GENE_SYNONYM_ATTRIBUTE_SHOULD_APPEAR_ONLY_ONCE",
            type = ValidationType.FEATURE,
            description =
                    "Harmonizes gene_synonym across features keyed by locus_tag/gene",
            enabled = true)
    public void fix(GFF3Annotation annotation, int line) {

        List<GFF3Feature> features = annotation.getFeatures();
        List<List<GFF3Feature>> featuresGroupedByGene = separateAnnotationFeaturesByLocusTag(features);

        List<GFF3Feature> cleanedFeatures = new ArrayList<>();
        for( var geneFeatures : featuresGroupedByGene){
            var cleanedGeneFeatures = fixGeneSynonymOnFeatures(geneFeatures);
            cleanedGeneFeatures.addAll(cleanedGeneFeatures);
        }

        annotation.setFeatures(cleanedFeatures);
    }

    private List<List<GFF3Feature>> separateAnnotationFeaturesByLocusTag(List<GFF3Feature> features) {
        throw new IllegalStateException("Not implemented yet");
    }

    private List<GFF3Feature> fixGeneSynonymOnFeatures(List<GFF3Feature> features) {
        List<GFF3Feature> withGeneSynonym =
                features.stream()
                        .filter(f -> f.hasAttribute(GENE_SYNONYM))
                        .toList();


        if (withGeneSynonym.isEmpty()) {return features;}

        GFF3Feature progenitor = findMostSeniorGeneLikeFeature(features);
        GFF3Feature progenitorWithGeneSynonym = findMostSeniorGeneLikeFeature(withGeneSynonym);

        //check if the gene_synonyms attribute is present in the oldest INDSC equivalent gene feature or its child feature
        //push the gene_synonym attribute onto the oldest
        //clean the rest: only one feature need have the gene_synonym list
        final String progenitorName = progenitor.getName();
        if(!progenitorName.equals(progenitorWithGeneSynonym.getName()) || !progenitor.getId().equals(progenitorWithGeneSynonym.getId())) {
            features.stream()
                    .filter(f -> progenitorName.equals(f.getName()))
                    .findFirst()
                    .ifPresent(f -> f.setAttribute(GENE_SYNONYM, progenitorWithGeneSynonym.getAttributeValueList(GENE_SYNONYM)));
        }
        var cleanedFeatures = clearGeneSynonymAttributeFromEveryoneBut(progenitor, features);
        return cleanedFeatures;
    }

    private List<GFF3Feature> clearGeneSynonymAttributeFromEveryoneBut(GFF3Feature feature, List<GFF3Feature> features) {
        throw new IllegalStateException("Not implemented yet");
    }

    private GFF3Feature findMostSeniorGeneLikeFeature(List<GFF3Feature> featureList) {
        List<String> names =
                featureList.stream()
                        .map(GFF3Feature::getName)
                        .toList();

        throw new IllegalStateException("Not implemented yet");
    }

    private boolean rememberedGeneSynonymAlreadyFor(String accessionNumber, String locusTag) {
        if (accessionNumberToLocusTagSet.containsKey(accessionNumber)) {
            HashSet<String> locusTagSet = accessionNumberToLocusTagSet.get(accessionNumber);
            return locusTagSet.contains(locusTag);
        }
        return false;
    }

    private void rememberGeneSynonymExistsFor(String accessionNumber, String locusTag) {
        if (accessionNumberToLocusTagSet.containsKey(accessionNumber)) {
            var locusTagSet = accessionNumberToLocusTagSet.get(accessionNumber);
            locusTagSet.add(locusTag);
            accessionNumberToLocusTagSet.put(accessionNumber, locusTagSet);
        } else {
            var locusTagSet = new  HashSet<String>();
            locusTagSet.add(locusTag);
            accessionNumberToLocusTagSet.put(accessionNumber, locusTagSet);
        }
    }
}
