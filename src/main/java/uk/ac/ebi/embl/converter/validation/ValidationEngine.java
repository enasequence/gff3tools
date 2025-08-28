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
package uk.ac.ebi.embl.converter.validation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.converter.exception.ValidationException;
import uk.ac.ebi.embl.converter.gff3.GFF3Annotation;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;

public class ValidationEngine {
    private static Logger LOG = LoggerFactory.getLogger(ValidationEngine.class);

    private final List<FeatureValidation> activeFeatureValidations;
    private final List<AnnotationValidation> activeAnnotationValidations;
    private final Map<String, RuleSeverity> severityMap;
    private final List<ValidationException> parsingErrors;

    ValidationEngine(
            List<FeatureValidation> activeFeatureValidations,
            List<AnnotationValidation> activeAnnotationValidations,
            Map<String, RuleSeverity> severityMap) {
        this.activeFeatureValidations = activeFeatureValidations;
        this.activeAnnotationValidations = activeAnnotationValidations;
        this.severityMap = severityMap;
        this.parsingErrors = new java.util.ArrayList<>();
    }

    // Getter for testing purposes
    public List<FeatureValidation> getFeatureValidations() {
        return activeFeatureValidations;
    }

    public List<AnnotationValidation> getAnnotationValidations() {
        return activeAnnotationValidations;
    }

    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        for (FeatureValidation validation : activeFeatureValidations) {
            try {
                validation.validateFeature(feature, line);
            } catch (ValidationException exception) {
                handleValidationException(exception);
            }
        }
    }

    public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException, ClassCastException {
        for (AnnotationValidation validation : activeAnnotationValidations) {
            try {
                validation.validateAnnotation(annotation, line);
            } catch (ValidationException exception) {
                handleValidationException(exception);
            }
        }
    }

    public void handleSyntacticError(ValidationException exception) throws ValidationException {
        handleValidationException(exception);
    }

    public List<ValidationException> getParsingErrors() {
        return parsingErrors;
    }

    private void handleValidationException(ValidationException exception) throws ValidationException {
        String rule = exception.getValidationRule().toString();
        RuleSeverity severity = Optional.ofNullable(severityMap.get(rule)).orElse(RuleSeverity.ERROR);
        switch (severity) {
            case OFF -> {}
            case WARN -> {
                this.parsingErrors.add(exception);
                LOG.warn(exception.getMessage());
            }
            case ERROR -> {
                throw exception;
            }
        }
    }
}
