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
import uk.ac.ebi.embl.gff3tools.validation.provider.PseudoLookupProvider;

/**
 * Which CDS features of one annotation are pseudo, and why. A feature is pseudo when it has
 * {@code pseudo} or {@code pseudogene}, or its SO term is under {@code pseudogene} or
 * {@code pseudogenic_region}, or any ancestor reached through {@code Parent} is. The whole
 * {@code pseudogene} subtree counts, {@code translated_*_pseudogene} included: GFF3 to flat file
 * converts each of them to a {@code gene} with {@code /pseudogene}.
 *
 * <p>Every CDS / {@code pseudogenic_CDS} group (lines sharing an ID) is worked out once, when the
 * lookup is built, and only the pseudo ones are kept. Queries take a group's segments and look it
 * up by {@link ValidationUtils#featureGroupKey}. {@link #of} shares one lookup between all the rules
 * that run on an annotation.
 *
 * <p>An annotation-level fix that changes whether a CDS is pseudo must tell the lookup, as
 * {@code TranslationFix} does with {@link #markPseudo}, or later rules see the old answer. Fixes
 * that remove or re-parent features without changing any CDS's pseudo status, like
 * {@code GeneAssociatedFeatureRemoval}, need not. Nothing enforces this.
 */
public final class PseudoLookup {

    /** Why a pseudo CDS group is pseudo: in full, and through its ancestors alone (if so). */
    private record CdsPseudo(String reason, Optional<String> ancestorReason) {}

    private final OntologyClient ontologyClient;
    private final Map<String, CdsPseudo> pseudoCds = new HashMap<>();

    private PseudoLookup(GFF3Annotation annotation, OntologyClient ontologyClient) {
        this.ontologyClient = ontologyClient;
        Walker walker = new Walker(annotation, ontologyClient);
        for (List<GFF3Feature> segments : ValidationUtils.groupFeaturesById(annotation, this::isCdsOrPseudogenicCds)
                .values()) {
            walker.pseudoReason(segments)
                    .ifPresent(reason ->
                            pseudoCds.put(key(segments), new CdsPseudo(reason, walker.ancestorPseudoReason(segments))));
        }
    }

    /**
     * The lookup for one annotation. Within a validation run, every rule on the annotation gets the
     * same lookup from {@link PseudoLookupProvider}; without that provider, a fresh one is built.
     * Parents always resolve inside the annotation, since {@code DANGLING_PARENT} rejects any that
     * do not.
     */
    public static PseudoLookup of(GFF3Annotation annotation, ValidationContext context) {
        return context.contains(PseudoLookupProvider.class)
                ? context.get(PseudoLookupProvider.class).lookupFor(annotation, context)
                : build(annotation, context);
    }

    /** A new lookup over the annotation as it is now. {@link #of} is the one to call from rules. */
    public static PseudoLookup build(GFF3Annotation annotation, ValidationContext context) {
        return new PseudoLookup(annotation, ontologyClient(context));
    }

    /**
     * A walker over the annotation as it is now, for features that are not CDSs. Nothing is shared
     * or kept between walkers.
     */
    public static Walker walker(GFF3Annotation annotation, ValidationContext context) {
        return new Walker(annotation, ontologyClient(context));
    }

    /** True for a feature typed {@code CDS} or {@code pseudogenic_CDS}. */
    public boolean isCdsOrPseudogenicCds(GFF3Feature feature) {
        return ontologyClient.isSoTerm(feature.getName(), OntologyTerm.CDS.ID)
                || ontologyClient.isSoTerm(feature.getName(), OntologyTerm.PSEUDOGENIC_CDS.ID);
    }

    /**
     * Why the CDS spread over {@code segments} (one or more lines sharing an ID) is pseudo, or empty
     * when it is not. It is pseudo when any segment, or any segment's ancestor, is.
     */
    public Optional<String> pseudoReason(List<GFF3Feature> segments) {
        return Optional.ofNullable(pseudoCds.get(key(segments))).map(CdsPseudo::reason);
    }

    public boolean isPseudo(List<GFF3Feature> segments) {
        return pseudoCds.containsKey(key(segments));
    }

    /**
     * Why an ancestor of the CDS spread over {@code segments} is pseudo, or empty when none is. The
     * CDS's own attributes and type are ignored.
     */
    public Optional<String> ancestorPseudoReason(List<GFF3Feature> segments) {
        return Optional.ofNullable(pseudoCds.get(key(segments))).flatMap(CdsPseudo::ancestorReason);
    }

    /**
     * Records that a fix has just made the CDS spread over {@code segments} pseudo by its own
     * attributes. Only for a CDS that was not pseudo before, so it has no pseudo ancestor; the answer
     * is then the one a lookup built now would give.
     */
    public void markPseudo(List<GFF3Feature> segments) {
        String key = key(segments);
        if (pseudoCds.containsKey(key)) {
            return;
        }
        segments.stream()
                .map(segment -> ownReason(segment, ontologyClient))
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(own -> pseudoCds.put(key, new CdsPseudo("it " + own, Optional.empty())));
    }

    private static String key(List<GFF3Feature> segments) {
        return ValidationUtils.featureGroupKey(segments.get(0));
    }

    /** Why the feature itself is pseudo, ignoring its ancestors. */
    private static Optional<String> ownReason(GFF3Feature feature, OntologyClient ontologyClient) {
        for (String attribute : List.of(GFF3Attributes.PSEUDO, GFF3Attributes.PSEUDOGENE)) {
            if (feature.hasAttribute(attribute)) {
                return Optional.of("has %s=%s"
                        .formatted(attribute, feature.getAttribute(attribute).orElse("")));
            }
        }
        boolean pseudoType = ontologyClient
                .getSoTermId(feature.getName())
                .map(soId -> ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENE.ID)
                        || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENIC_REGION.ID))
                .orElse(false);
        return pseudoType ? Optional.of("is of type " + feature.getName()) : Optional.empty();
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

    /**
     * Walks up {@code Parent} references from any feature of one annotation. Used to build the CDS
     * answers, and on its own for features that are not CDSs.
     */
    public static final class Walker {

        private final OntologyClient ontologyClient;
        private final Map<String, List<GFF3Feature>> linesById = new HashMap<>();
        private final Map<GFF3Feature, Optional<String>> ownReasons = new IdentityHashMap<>();

        private Walker(GFF3Annotation annotation, OntologyClient ontologyClient) {
            this.ontologyClient = ontologyClient;
            for (GFF3Feature feature : annotation.getFeatures()) {
                if (feature != null) {
                    feature.getId().ifPresent(id -> linesById
                            .computeIfAbsent(id, key -> new ArrayList<>())
                            .add(feature));
                }
            }
        }

        /** True when the feature, or any of its ancestors, is pseudo. */
        public boolean isPseudo(GFF3Feature feature) {
            return pseudoReason(feature).isPresent();
        }

        private Optional<String> pseudoReason(List<GFF3Feature> segments) {
            for (GFF3Feature segment : segments) {
                Optional<String> reason = pseudoReason(segment);
                if (reason.isPresent()) {
                    return reason;
                }
            }
            return Optional.empty();
        }

        private Optional<String> ancestorPseudoReason(List<GFF3Feature> segments) {
            for (GFF3Feature segment : segments) {
                Optional<String> reason = ancestorPseudoReason(segment);
                if (reason.isPresent()) {
                    return reason;
                }
            }
            return Optional.empty();
        }

        private Optional<String> pseudoReason(GFF3Feature feature) {
            Optional<String> own = ownReason(feature);
            if (own.isPresent()) {
                return own.map(reason -> "it " + reason);
            }
            return ancestorPseudoReason(feature);
        }

        private Optional<String> ancestorPseudoReason(GFF3Feature feature) {
            // Parents are resolved from Parent references, because only the flat file to GFF3 path ever
            // sets the linked parent object. Every line of a parent counts, as for the feature's own.
            Set<String> seen = new HashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            feature.getParentId().ifPresent(pending::add);
            while (!pending.isEmpty()) {
                String parentId = pending.poll();
                if (!seen.add(parentId)) {
                    continue;
                }
                for (GFF3Feature parentLine : linesById.getOrDefault(parentId, List.of())) {
                    Optional<String> inherited = ownReason(parentLine);
                    if (inherited.isPresent()) {
                        return Optional.of("its ancestor \"%s\" %s".formatted(parentId, inherited.get()));
                    }
                    parentLine.getParentId().ifPresent(pending::add);
                }
            }
            return Optional.empty();
        }

        /** Memoised, because sibling features walk up through the same ancestors. */
        private Optional<String> ownReason(GFF3Feature feature) {
            return ownReasons.computeIfAbsent(feature, f -> PseudoLookup.ownReason(f, ontologyClient));
        }
    }
}
