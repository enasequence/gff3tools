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

import java.util.*;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Decides whether a feature is pseudo, from its own attributes and type or from any ancestor
 * reached through {@code Parent}. A feature is pseudo when it has {@code pseudo} or
 * {@code pseudogene}, or its SO term is under {@code pseudogene} or {@code pseudogenic_region}.
 * The whole {@code pseudogene} subtree counts, {@code translated_*_pseudogene} included: GFF3 to
 * flat file converts each of them to a {@code gene} with {@code /pseudogene}.
 */
public final class PseudoLookup {

    private final OntologyClient ontologyClient;
    private final Map<String, GFF3Feature> featuresById = new HashMap<>();
    private final Map<GFF3Feature, Optional<String>> ownReasons = new IdentityHashMap<>();

    private PseudoLookup(GFF3Annotation annotation, OntologyClient ontologyClient) {
        this.ontologyClient = ontologyClient;
        for (GFF3Feature feature : annotation.getFeatures()) {
            if (feature != null) {
                feature.getId().ifPresent(id -> featuresById.putIfAbsent(id, feature));
            }
        }
    }

    /**
     * A lookup over one annotation. Parents always resolve inside it, since {@code DANGLING_PARENT}
     * rejects any that do not.
     */
    public static PseudoLookup of(GFF3Annotation annotation, ValidationContext context) {
        return new PseudoLookup(annotation, ontologyClient(context));
    }

    /** True for a feature typed {@code CDS} or {@code pseudogenic_CDS}. */
    public boolean isCdsOrPseudogenicCds(GFF3Feature feature) {
        return isCdsOrPseudogenicCds(feature, ontologyClient);
    }

    /** True for a feature typed {@code CDS} or {@code pseudogenic_CDS}. */
    public static boolean isCdsOrPseudogenicCds(GFF3Feature feature, OntologyClient ontologyClient) {
        return ontologyClient
                .findTermByNameOrSynonym(feature.getName())
                .map(soId -> OntologyTerm.CDS.ID.equals(soId)
                        || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENIC_CDS.ID))
                .orElse(false);
    }

    /**
     * Why a feature spread over {@code segments} (one or more lines sharing an ID) is pseudo, or
     * empty when it is not. The feature is pseudo when any segment, or any segment's ancestor, is.
     */
    public Optional<String> pseudoReason(List<GFF3Feature> segments) {
        for (GFF3Feature segment : segments) {
            Optional<String> reason = pseudoReason(segment);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }

    public boolean isPseudo(List<GFF3Feature> segments) {
        return pseudoReason(segments).isPresent();
    }

    private Optional<String> pseudoReason(GFF3Feature feature) {
        Optional<String> own = ownReason(feature);
        if (own.isPresent()) {
            return own.map(reason -> "it " + reason);
        }

        Set<GFF3Feature> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(feature);
        GFF3Feature current = feature;
        GFF3Feature parent;
        while ((parent = parentOf(current)) != null && seen.add(parent)) {
            Optional<String> inherited = ownReason(parent);
            if (inherited.isPresent()) {
                return Optional.of(
                        "its ancestor \"%s\" %s".formatted(parent.getId().orElse(""), inherited.get()));
            }
            current = parent;
        }
        return Optional.empty();
    }

    /**
     * Why the feature itself is pseudo, ignoring its ancestors. Memoised, because sibling features
     * walk up through the same ancestors.
     */
    private Optional<String> ownReason(GFF3Feature feature) {
        return ownReasons.computeIfAbsent(feature, this::computeOwnReason);
    }

    private Optional<String> computeOwnReason(GFF3Feature feature) {
        for (String attribute : List.of(GFF3Attributes.PSEUDO, GFF3Attributes.PSEUDOGENE)) {
            if (feature.hasAttribute(attribute)) {
                return Optional.of("has %s=%s"
                        .formatted(attribute, feature.getAttribute(attribute).orElse("")));
            }
        }
        boolean pseudoType = ontologyClient
                .findTermByNameOrSynonym(feature.getName())
                .map(soId -> ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENE.ID)
                        || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENIC_REGION.ID))
                .orElse(false);
        return pseudoType ? Optional.of("is of type " + feature.getName()) : Optional.empty();
    }

    /**
     * The parent of a feature, resolved from its {@code Parent} reference. The linked parent object
     * is not consulted because only the flat file to GFF3 path ever sets it.
     */
    private GFF3Feature parentOf(GFF3Feature feature) {
        return feature.getParentId().map(featuresById::get).orElse(null);
    }

    /**
     * The context's ontology client. {@code OntologyClientProvider} only ever hands out the
     * singleton, so falling back to it gives the same client when no provider is registered.
     */
    private static OntologyClient ontologyClient(ValidationContext context) {
        return context.contains(OntologyClient.class)
                ? context.get(OntologyClient.class)
                : OntologyClient.getInstance();
    }
}
