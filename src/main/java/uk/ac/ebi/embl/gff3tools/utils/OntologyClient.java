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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OntologyClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OntologyClient.class);
    private OWLOntology ontology;
    private OWLDataFactory dataFactory;
    private OWLReasoner reasoner;

    public OntologyClient() {
        this.dataFactory = OWLManager.createOWLOntologyManager().getOWLDataFactory();
        loadOntology();
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
            LOGGER.info("SO Ontology loaded successfully.");
        } catch (OWLOntologyCreationException e) {
            LOGGER.error("Error loading SO Ontology: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error loading SO Ontology: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a given ontology ID is defined in the loaded SO ontology.
     *
     * @param ontologyId The ontology ID (e.g., "SO:0000123").
     * @return true if the term is defined, false otherwise.
     */
    public boolean isTermDefined(String ontologyId) {
        if (ontology == null) {
            LOGGER.warn("Ontology not loaded. Cannot check for term definition.");
            return false;
        }
        // Convert the ontology ID to a full IRI (Internationalized Resource Identifier)
        // This assumes a standard OBO Foundry IRI pattern. e.g., http://purl.obolibrary.org/obo/SO_0000123
        String iriString = "http://purl.obolibrary.org/obo/" + ontologyId.replace(":", "_");
        OWLClass owlClass = dataFactory.getOWLClass(IRI.create(iriString));

        // Check if the ontology contains a declaration for this OWLClass
        return ontology.containsClassInSignature(owlClass.getIRI());
    }

    /**
     * Checks if a given child ontology ID is a child of a given parent ontology ID in the loaded SO ontology.
     *
     * @param childOntologyId The child ontology ID (e.g., "SO:0000123").
     * @param parentOntologyId The parent ontology ID (e.g., "SO:0000456").
     * @return true if the child term is a child of the parent term, false otherwise.
     */
    public boolean isChildOf(String childOntologyId, String parentOntologyId) {
        if (ontology == null || reasoner == null) {
            LOGGER.warn("Ontology or reasoner not loaded. Cannot check for child relationship.");
            return false;
        }

        OWLClass childClass = dataFactory.getOWLClass(
                IRI.create("http://purl.obolibrary.org/obo/" + childOntologyId.replace(":", "_")));
        OWLClass parentClass = dataFactory.getOWLClass(
                IRI.create("http://purl.obolibrary.org/obo/" + parentOntologyId.replace(":", "_")));

        // Check if childClass is a subclass of parentClass, excluding the case where they are the same class
        return reasoner.getSubClasses(parentClass, false).getFlattened().stream()
                .anyMatch(subClass -> subClass.equals(childClass));
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
                    return Optional.of(soId);
                }
            }

            // Check oboInOwl:hasExactSynonym
            Optional<String> synonym = EntitySearcher.getAnnotationObjects(
                            owlClass,
                            ontology,
                            dataFactory.getOWLAnnotationProperty(
                                    IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym")))
                    .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
                    .map(annotation ->
                            ((OWLLiteral) annotation.getValue()).getLiteral().toLowerCase())
                    .filter(literal -> literal.equals(searchLower))
                    .findFirst()
                    .or(() -> EntitySearcher.getAnnotationObjects(
                                    owlClass,
                                    ontology,
                                    dataFactory.getOWLAnnotationProperty(IRI.create(
                                            "http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym")))
                            .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
                            .map(annotation -> ((OWLLiteral) annotation.getValue())
                                    .getLiteral()
                                    .toLowerCase())
                            .filter(literal -> literal.equals(searchLower))
                            .findFirst());

            if (synonym.isPresent()) {
                String soId = extractOntologyId(owlClass.getIRI());
                if (soId != null) {
                    return Optional.of(soId);
                }
            }
        }

        return Optional.empty();
    }

    private String extractOntologyId(IRI iri) {
        // Extract the SO ID from the full IRI, e.g., http://purl.obolibrary.org/obo/SO_0000123 -> SO:0000123
        String iriString = iri.toString();
        if (iriString.startsWith("http://purl.obolibrary.org/obo/SO_")) {
            return "SO:" + iriString.substring(iriString.lastIndexOf('_') + 1);
        }
        return null;
    }

    /**
     * Finds the SO ID for a given SO term (name or synonym).
     *
     * @param soTerm The SO term (e.g., "gene", "exon").
     * @return The SO ID (e.g., "SO:0000704") if found, or null otherwise.
     */
    public String getSOId(String soTerm) {
        if (soTerm == null || soTerm.isEmpty()) {
            return null;
        }
        return findTermByNameOrSynonym(soTerm).orElse(null);
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
     * Debug method to print rdfs:label and oboInOwl:hasExactSynonym annotations for a given ontology ID.
     * @param ontologyId The ontology ID to debug (e.g., "SO:0000123").
     */
    public void debugTermAnnotations(String ontologyId) {
        if (ontology == null) {
            LOGGER.warn("Ontology not loaded. Cannot debug term annotations.");
            return;
        }

        String iriString = "http://purl.obolibrary.org/obo/" + ontologyId.replace(":", "_");
        OWLClass owlClass = dataFactory.getOWLClass(IRI.create(iriString));

        LOGGER.info("Debugging annotations for term: " + ontologyId);

        // Get and print rdfs:label
        EntitySearcher.getAnnotationObjects(owlClass, ontology, dataFactory.getRDFSLabel())
                .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
                .forEach(annotation -> LOGGER.info(" Annotation: " + annotation.getProperty() + "\n Label: "
                        + ((OWLLiteral) annotation.getValue()).getLiteral()));
        // .map(annotation -> ((OWLLiteral) annotation.getValue()).getLiteral())
        // .forEach(label -> LOGGER.info("  RDFS Label: " + label));

        // Get and print oboInOwl:hasExactSynonym
        EntitySearcher.getAnnotationObjects(
                        owlClass,
                        ontology,
                        dataFactory.getOWLAnnotationProperty(
                                IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym")))
                .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
                .map(annotation -> ((OWLLiteral) annotation.getValue()).getLiteral())
                .forEach(synonym -> LOGGER.info("  Exact Synonym: " + synonym));
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
            return isChildOf(soTerm, "SO:0000110");
        } else {
            // 3. If not a valid ID, attempt to find the term by name or synonym
            return findTermByNameOrSynonym(soTerm)
                    // 4. If valid, check if is a child of feature.
                    .map((String termId) -> isChildOf(termId, "SO:0000110"))
                    .orElse(false);
        }
    }

    /**
     * Retrieves a map of EMBL feature names to a list of corresponding SO terms.
     * This method extracts feature names (rdfs:label or synonyms) from the loaded
     * ontology and maps them to their respective SO terms.
     *
     * @return A map where the key is an EMBL feature name (String) and the value
     *     is a List of SO terms (String) that correspond to that feature.
     */
    public Map<String, ConversionEntry> getFeatureMap() {
        Map<String, ConversionEntry> featureMap = new HashMap<>();
        if (ontology == null) {
            LOGGER.warn("Ontology not loaded. Cannot retrieve feature map.");
            return featureMap;
        }

        for (OWLClass owlClass : ontology.getClassesInSignature()) {
            // Check if the term is a feature SO term
            String soId = extractOntologyId(owlClass.getIRI());
            if (soId != null && isFeatureSoTerm(soId)) {

                // Get the RDFS Label
                Optional<String> label = EntitySearcher.getAnnotationObjects(
                                owlClass, ontology, dataFactory.getRDFSLabel())
                        .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
                        .map(annotation -> ((OWLLiteral) annotation.getValue()).getLiteral())
                        .findFirst();

                if (label.isPresent()) {
                    // Get INSDC_feature synonyms
                    Optional<String> insdcFeature = EntitySearcher.getAnnotationObjects(
                                    owlClass,
                                    ontology,
                                    dataFactory.getOWLAnnotationProperty(
                                            IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym")))
                            .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
                            .map(annotation -> ((OWLLiteral) annotation.getValue()).getLiteral())
                            .filter(literal -> literal.startsWith("INSDC_feature"))
                            .map(literal -> {
                                String[] parts = literal.split(":");
                                return parts[parts.length - 1];
                            })
                            .findFirst();

                    if (insdcFeature.isPresent()
                            && insdcFeature.get() != null
                            && !insdcFeature.get().isEmpty()) {
                        featureMap.put(soId, new ConversionEntry(soId, label.get(), insdcFeature.get()));
                    }

                    // Get INSDC_feature synonyms
                    insdcFeature = EntitySearcher.getAnnotationObjects(
                                    owlClass,
                                    ontology,
                                    dataFactory.getOWLAnnotationProperty(
                                            IRI.create("http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym")))
                            .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
                            .map(annotation -> ((OWLLiteral) annotation.getValue()).getLiteral())
                            .filter(literal -> literal.startsWith("INSDC_feature"))
                            .map(literal -> {
                                String[] parts = literal.split(":");
                                return parts[parts.length - 1];
                            })
                            .findFirst();


                    if (insdcFeature.isPresent()
                            && insdcFeature.get() != null
                            && !insdcFeature.get().isEmpty()) {
                        featureMap.put(soId, new ConversionEntry(soId, label.get(), insdcFeature.get()));
                    }

                }
            }
        }
        return featureMap;
    }
}
