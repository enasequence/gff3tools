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

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.FEATURE;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Slf4j
@Gff3Fix(
        name = "GENE_LOCUS_TAG",
        description =
                "Transfers gene, gene_synonym, and locus_tag attributes from gene features to their corresponding CDS, rRNA, and tRNA child features based on location overlap.")
public class GeneLocusTagFix {

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();
    private final Map<String, Map<String, GFF3Feature>> accessionToGene = new HashMap<>();
    private final Map<String, List<GFF3Feature>> pendingChildren = new HashMap<>();

    @FixMethod(
            rule = "GENE_LOCUS_TAG",
            description =
                    "Transfers gene, gene_synonym, and locus_tag attributes from gene features to their corresponding CDS, rRNA, and tRNA child features based on location overlap.",
            type = FEATURE)
    public void fixFeature(GFF3Feature feature, int line) {
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (soIdOpt.isEmpty()) return;

        String soId = soIdOpt.get();
        String accession = feature.accession();

        // Determine if this is a gene feature
        boolean isGene = OntologyTerm.GENE.ID.equals(soId)
                || OntologyTerm.PSEUDOGENE.ID.equals(soId)
                || OntologyTerm.UNITARY_PSEUDOGENE.ID.equals(soId)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENE.ID)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.UNITARY_PSEUDOGENE.ID);

        // Determine if this is a relevant CDS/tRNA/rRNA feature
        boolean isRelevant = OntologyTerm.CDS.ID.equals(soId)
                || OntologyTerm.TRNA.ID.equals(soId)
                || OntologyTerm.RRNA.ID.equals(soId)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.CDS.ID)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.TRNA.ID)
                || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.RRNA.ID);

        // Gene feature: store in gene map and propagate to pending children
        if (isGene) {
            Map<String, GFF3Feature> geneMap = accessionToGene.computeIfAbsent(accession, k -> new HashMap<>());
            String geneKey = feature.getStart() + ":" + feature.getEnd();
            geneMap.put(geneKey, feature);

            // Check pending children for this accession
            List<GFF3Feature> pending = pendingChildren.remove(accession);
            if (pending != null) {
                Iterator<GFF3Feature> it = pending.iterator();
                while (it.hasNext()) {
                    GFF3Feature child = it.next();
                    if (isLocationWithin(child.getStart(), child.getEnd(), feature.getStart(), feature.getEnd())) {
                        propagateGeneAttributes(feature, child, line);
                        it.remove();
                    }
                }
            }
            return;
        }

        if (!isRelevant) return;

        if (feature.hasAttribute(GFF3Attributes.LOCUS_TAG)
                || feature.hasAttribute(GFF3Attributes.GENE)
                || feature.hasAttribute(GFF3Attributes.GENE_SYNONYM)) {
            return;
        }

        Map<String, GFF3Feature> geneMap = accessionToGene.get(accession);
        if (geneMap == null || geneMap.isEmpty()) {
            pendingChildren.computeIfAbsent(accession, k -> new ArrayList<>()).add(feature);
            return;
        }

        boolean propagated = false;
        for (GFF3Feature geneFeature : geneMap.values()) {
            if (isLocationWithin(feature.getStart(), feature.getEnd(), geneFeature.getStart(), geneFeature.getEnd())) {
                propagateGeneAttributes(geneFeature, feature, line);
                propagated = true;
                break;
            }
        }

        if (!propagated) {
            pendingChildren.computeIfAbsent(accession, k -> new ArrayList<>()).add(feature);
        }
    }

    private boolean isLocationWithin(long start1, long end1, long start2, long end2) {
        return start1 >= start2 && end1 <= end2;
    }

    private void propagateGeneAttributes(GFF3Feature geneFeature, GFF3Feature childFeature, int line) {
        String locusTag = geneFeature.getAttributeByName(GFF3Attributes.LOCUS_TAG);
        String gene = geneFeature.getAttributeByName(GFF3Attributes.GENE);
        String geneSynonym = geneFeature.getAttributeByName(GFF3Attributes.GENE_SYNONYM);

        if (locusTag != null && !childFeature.hasAttribute(GFF3Attributes.LOCUS_TAG)) {
            childFeature.setAttribute(GFF3Attributes.LOCUS_TAG, locusTag);
            log.info(
                    "Adding {} from gene {} to {} at line {}",
                    GFF3Attributes.LOCUS_TAG,
                    geneFeature.getName(),
                    childFeature.getName(),
                    line);
        }
        if (geneSynonym != null && !childFeature.hasAttribute(GFF3Attributes.GENE_SYNONYM)) {
            childFeature.setAttribute(GFF3Attributes.GENE_SYNONYM, geneSynonym);
            log.info(
                    "Adding {} from gene {} to {} at line {}",
                    GFF3Attributes.GENE_SYNONYM,
                    geneFeature.getName(),
                    childFeature.getName(),
                    line);
        }
        if (gene != null && !childFeature.hasAttribute(GFF3Attributes.GENE)) {
            childFeature.setAttribute(GFF3Attributes.GENE, gene);
            log.info(
                    "Adding {} from gene {} to {} at line {}",
                    GFF3Attributes.GENE,
                    geneFeature.getName(),
                    childFeature.getName(),
                    line);
        }
    }
}
