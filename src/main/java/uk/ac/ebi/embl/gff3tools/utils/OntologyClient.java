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

import java.io.InputStream;
import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.gff3tools.validation.ValidationConfig;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;

public class OntologyClient {
    private static final OntologyClient INSTANCE = new OntologyClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(OntologyClient.class);
    static final String GENEONTOLOGY_IRI_BASE = "http://www.geneontology.org/formats/oboInOwl";
    static final String OBOLIBRARY_IRI_BASE = "http://purl.obolibrary.org/obo/";
    private OWLOntology ontology;
    private OWLDataFactory dataFactory;
    private OWLReasoner reasoner;

    Map<String, Optional<String>> searchCache = new HashMap<>();
    Map<String, Set<String>> descendantsCache = new HashMap<>();

    public static OntologyClient getInstance() {
        INSTANCE.initClient();
        return INSTANCE;
    }

    private void initClient() {
        if(dataFactory==null) {
            this.dataFactory = OWLManager.createOWLOntologyManager().getOWLDataFactory();
            loadOntology();
        }
    }

    private void loadOntology() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("so.owl")) {
            if (is == null) {
                LOGGER.error("so.owl resource not found.");
                return;
            }
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            this.ontology = manager.loadOntologyFromOntologyDocument(is);
            // Initialize the reasoner after the ontology is loaded
            OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
            this.reasoner = reasonerFactory.createReasoner(ontology);
            precomputeDescendants();
            LOGGER.info("SO Ontology loaded successfully.");
        } catch (OWLOntologyCreationException e) {
            LOGGER.error("Error loading SO Ontology: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error loading SO Ontology: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a given child ontology ID is a child of a given parent ontology ID in the loaded SO ontology.
     *
     * @param childOntologyId The child ontology ID (e.g., "SO:0000123").
     * @param parentOntologyId The parent ontology ID (e.g., "SO:0000456").
     * @return true if the child term is a child of the parent term, false otherwise.
     */
    public boolean isChildOf(String childOntologyId, String parentOntologyId) {
        if (ontology == null || descendantsCache == null) {
            LOGGER.warn("Ontology or descendants cache not loaded. Cannot check for child relationship.");
            return false;
        }

        Set<String> descendants = descendantsCache.get(parentOntologyId);
        return descendants != null && descendants.contains(childOntologyId);
    }

    private void precomputeDescendants() {
        LOGGER.info("Precomputing ontology descendants...");
        for (OWLClass owlClass : ontology.getClassesInSignature()) {
            String soId = extractOntologyId(owlClass.getIRI());
            if (soId != null) {
                Set<String> descendants = reasoner.getSubClasses(owlClass, false).getFlattened().stream()
                        .map(OWLClass::getIRI)
                        .map(this::extractOntologyId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                descendantsCache.put(soId, descendants);
            }
        }
        LOGGER.info("Ontology descendants precomputed.");
    }

    /**
     * Finds an ontology term ID by its exact name or any of its exact synonyms (case-insensitive).
     *
     * @param nameOrSynonym The name or synonym to search for.
     * @return An Optional containing the ontology ID (e.g., "SO:0000123") if a match is found, or an empty Optional otherwise.
     */
    public Optional<String> findTermByNameOrSynonym(String nameOrSynonym) {
        if (ontology == null) {
            LOGGER.warn("Ontology not loaded. Cannot search for term by name or synonym.");
            return Optional.empty();
        }

        Optional<String> cachedResult = searchCache.get(nameOrSynonym);
        if (cachedResult != null) {
            return cachedResult;
        }

        final String searchLower = nameOrSynonym.toLowerCase();

        for (OWLClass owlClass : ontology.getClassesInSignature()) {
            // Check rdfs:label
            Optional<String> label = EntitySearcher.getAnnotationObjects(owlClass, ontology, dataFactory.getRDFSLabel())
                    .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
                    .map(annotation ->
                            ((OWLLiteral) annotation.getValue()).getLiteral().toLowerCase())
                    .filter(literal -> literal.equals(searchLower))
                    .findFirst();

            if (label.isPresent()) {
                String soId = extractOntologyId(owlClass.getIRI());
                if (soId != null) {
                    Optional<String> result = Optional.of(soId);
                    searchCache.put(nameOrSynonym, result);
                    return result;
                }
            }

            // Check oboInOwl:hasExactSynonym
            Optional<String> synonym = EntitySearcher.getAnnotationObjects(
                            owlClass,
                            ontology,
                            dataFactory.getOWLAnnotationProperty(
                                    IRI.create(GENEONTOLOGY_IRI_BASE + "#hasExactSynonym")))
                    .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
                    .map(annotation ->
                            ((OWLLiteral) annotation.getValue()).getLiteral().toLowerCase())
                    .filter(literal -> literal.equals(searchLower))
                    .findFirst()
                    .or(() -> EntitySearcher.getAnnotationObjects(
                                    owlClass,
                                    ontology,
                                    dataFactory.getOWLAnnotationProperty(
                                            IRI.create(GENEONTOLOGY_IRI_BASE + "#hasNarrowSynonym")))
                            .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
                            .map(annotation -> ((OWLLiteral) annotation.getValue())
                                    .getLiteral()
                                    .toLowerCase())
                            .filter(literal -> literal.equals(searchLower))
                            .findFirst());

            if (synonym.isPresent()) {
                String soId = extractOntologyId(owlClass.getIRI());
                if (soId != null) {
                    Optional<String> result = Optional.of(soId);
                    searchCache.put(nameOrSynonym, result);
                    return result;
                }
            }
        }

        searchCache.put(nameOrSynonym, Optional.empty());
        return Optional.empty();
    }

    private String extractOntologyId(IRI iri) {
        // Extract the SO ID from the full IRI, e.g., http://purl.obolibrary.org/obo/SO_0000123 -> SO:0000123
        String iriString = iri.toString();
        if (iriString.startsWith(OBOLIBRARY_IRI_BASE + "SO_")) {
            return "SO:" + iriString.substring(iriString.lastIndexOf('_') + 1).trim();
        }
        return null;
    }

    /**
     * Checks if a given string is a valid ontology ID with the format SO:0000123.
     * @param ontologyId The string to validate.
     * @return true if the string matches the expected format, false otherwise.
     */
    public boolean isValidOntologyId(String ontologyId) {
        return ontologyId != null && ontologyId.matches("SO:[0-9]{7}");
    }

    /**
     * Checks if a given string is a valid feature SO term. This involves:
     * 1. Checking if the string is a valid ontology ID format (e.g., SO:0000123).
     * 2. If it's a valid ID, checking if the term is defined in the ontology and the child of a feature.
     * 3. If not a valid ID or not defined, attempting to find the term by name or synonym.
     * 4. If found, checking if the term is the child of a feature
     *
     * @param soTerm The string to validate/find as an SO term.
     * @return true if the string corresponds to a defined feature SO term, false otherwise.
     */
    public boolean isFeatureSoTerm(String soTerm) {
        if (soTerm == null || soTerm.isEmpty()) {
            return false;
        }

        // 1. Check if the string is a valid ontology ID format
        if (isValidOntologyId(soTerm)) {
            // 2. If it's a valid ID, check if the term is defined in the ontology as a child of feature
            return isChildOf(soTerm, OntologyTerm.FEATURE.ID);
        } else {
            // 3. If not a valid ID, attempt to find the term by name or synonym
            return findTermByNameOrSynonym(soTerm)
                    // 4. If valid, check if is a child of feature.
                    .map((String termId) -> isChildOf(termId, OntologyTerm.FEATURE.ID))
                    .orElse(false);
        }
    }

    ///  Returns the list of parents (as SOIds) for a given SOTerm.
    public Stream<String> getParents(String SOTerm) {
        if (SOTerm == null || SOTerm.isEmpty()) {
            return Stream.empty();
        }

        if (isValidOntologyId(SOTerm)) {
            OWLClass owlClass = dataFactory.getOWLClass(IRI.create(OBOLIBRARY_IRI_BASE + SOTerm.replace(":", "_")));
            return reasoner.getSuperClasses(owlClass, false)
                    .entities()
                    .map(HasIRI::getIRI)
                    .map(this::extractOntologyId);
        } else {
            return findTermByNameOrSynonym(SOTerm).map(this::getParents).orElse(Stream.empty());
        }
    }
}
