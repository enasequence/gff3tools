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

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Slf4j
@Gff3Fix(
        name = "CDS_RNA_LOCUS",
        description =
                "Transfers gene, gene_synonym, and locus_tag attributes from gene features to their corresponding CDS, rRNA, and tRNA child features based on location overlap.")

public class CdsRnaLocusFix {

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @FixMethod(
            rule = "CDS_RNA_LOCUS",
            description =
                    "Transfers gene, gene_synonym, and locus_tag attributes from gene features to their corresponding CDS, rRNA, and tRNA child features based on location overlap.",
            type = ANNOTATION)
    public void fix(GFF3Annotation annotation, int line) {
        List<GFF3Feature> geneFeatures = new ArrayList<>();
        List<GFF3Feature> nonLocusFeatures = new ArrayList<>();
        for (GFF3Feature feature : annotation.getFeatures()) {

            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isEmpty()) continue;

            String soId = soIdOpt.get();

            if (isGeneFeature(soId)) {
                geneFeatures.add(feature);
            } else if (isNonLocusFeature(soId) && !hasGeneAttributes(feature)) {
                nonLocusFeatures.add(feature);
            }
        }
        if (geneFeatures.isEmpty() || nonLocusFeatures.isEmpty()) {
            return;
        }
        // Sorting the gene Features to check the parent child location
        geneFeatures.sort(Comparator.comparingLong(GFF3Feature::getStart));

        for (GFF3Feature child : nonLocusFeatures) {
            long cs = child.getStart();
            long ce = child.getEnd();

            for (GFF3Feature gene : geneFeatures) {

                // Ensure gene and child feature belong to the same sequence/contig
                if (!Objects.equals(gene.getSeqId(), child.getSeqId())) {
                    continue;
                }

                if (gene.getStart() > cs) {
                    break;
                }
                if (gene.getEnd() < ce) {
                    continue;
                }
                if (isLocationWithin(cs, ce, gene.getStart(), gene.getEnd())) {
                    propagateGeneAttributes(gene, child);
                    break;
                }
            }
        }
    }

    // Determine if this is a gene feature
    private boolean isGeneFeature(String soId) {
        return OntologyTerm.GENE.ID.equals(soId)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENE.ID)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.UNITARY_PSEUDOGENE.ID);
    }

    // Determine if this is a NonLocus CDS/tRNA/rRNA feature
    private boolean isNonLocusFeature(String soId) {
        return ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.CDS.ID)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.TRNA.ID)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.RRNA.ID);
    }

    private boolean hasGeneAttributes(GFF3Feature feature) {
        return feature.hasAttribute(GFF3Attributes.LOCUS_TAG)
                || feature.hasAttribute(GFF3Attributes.GENE)
                || feature.hasAttribute(GFF3Attributes.GENE_SYNONYM);
    }

    // checks if the child feature is fully contained within the gene feature
    private boolean isLocationWithin(long childStart, long childEnd, long parentStart, long parentEnd) {

        if (childStart > childEnd || parentStart > parentEnd) {
            return false;
        }
        return childStart >= parentStart && childEnd <= parentEnd;
    }

    private void propagateGeneAttributes(GFF3Feature geneFeature, GFF3Feature childFeature) {
        List<String> locusTagList =
                geneFeature.getAttributeList(GFF3Attributes.LOCUS_TAG).orElse(new ArrayList<>());
        List<String> geneList =
                geneFeature.getAttributeList(GFF3Attributes.GENE).orElse(new ArrayList<>());
        List<String> geneSynonymList =
                geneFeature.getAttributeList(GFF3Attributes.GENE_SYNONYM).orElse(new ArrayList<>());

        if (!locusTagList.isEmpty() && !childFeature.hasAttribute(GFF3Attributes.LOCUS_TAG)) {

            childFeature.setAttributeList(GFF3Attributes.LOCUS_TAG, new ArrayList<>(locusTagList));

            log.info(
                    "Adding {} from gene {} to feature {} at [{}:{}]",
                    GFF3Attributes.LOCUS_TAG,
                    geneFeature.getName(),
                    childFeature.getName(),
                    childFeature.getStart(),
                    childFeature.getEnd());
        }
        if (!geneSynonymList.isEmpty() && !childFeature.hasAttribute(GFF3Attributes.GENE_SYNONYM)) {

            childFeature.setAttributeList(GFF3Attributes.GENE_SYNONYM, new ArrayList<>(geneSynonymList));

            log.info(
                    "Adding {} from gene {} to {} at [{}:{}]",
                    GFF3Attributes.GENE_SYNONYM,
                    geneFeature.getName(),
                    childFeature.getName(),
                    childFeature.getStart(),
                    childFeature.getEnd());
        }
        if (!geneList.isEmpty() && !childFeature.hasAttribute(GFF3Attributes.GENE)) {

            childFeature.setAttributeList(GFF3Attributes.GENE, new ArrayList<>(geneList));

            log.info(
                    "Adding {} from gene {} to {} at [{}:{}]",
                    GFF3Attributes.GENE,
                    geneFeature.getName(),
                    childFeature.getName(),
                    childFeature.getStart(),
                    childFeature.getEnd());
        }
    }
}
