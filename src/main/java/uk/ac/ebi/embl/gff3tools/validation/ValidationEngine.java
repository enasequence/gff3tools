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
package uk.ac.ebi.embl.gff3tools.validation;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class ValidationEngine {
    private static Logger LOG = LoggerFactory.getLogger(ValidationEngine.class);

    private final List<ValidationException> parsingErrors;
    public ValidationConfig validationConfig;
    public ValidationRegistry validationRegistry;

    ValidationEngine(ValidationConfig validationConfig, ValidationRegistry validationRegistry) {
        this.parsingErrors = new java.util.ArrayList<>();
        this.validationConfig = validationConfig;
        this.validationRegistry = validationRegistry;
    }

    public <T> void validate(T target, int line) throws ValidationException {

        executeFixs(target, line);
        executeValidations(target, line);
    }

    public <T> void executeValidations(T target, int line) throws ValidationException {
        List<ValidatorDescriptor> validators = ValidationRegistry.getValidations(validationConfig);

        for (ValidatorDescriptor validator : validators) {

            ValidationMethod methodAnnotation = validator.method().getAnnotation(ValidationMethod.class);
            RuleSeverity ruleSeverity =
                    validationConfig.getSeverity(methodAnnotation.rule(), methodAnnotation.severity());

            if (ruleSeverity == RuleSeverity.OFF) continue;

            try {
                if (methodAnnotation.type() == ValidationType.FEATURE && target instanceof GFF3Feature) {
                    validator.method().invoke(validator.instance(), target, line);
                } else if (methodAnnotation.type() == ValidationType.ANNOTATION && target instanceof GFF3Annotation) {
                    validator.method().invoke(validator.instance(), target, line);
                }

            } catch (Exception e) {
                handleRuleException(e.getCause(), ruleSeverity);
            }
        }
    }

    public <T> void executeFixs(T target, int line) throws ValidationException {
        List<ValidatorDescriptor> validators = ValidationRegistry.getInstance().getFixs(validationConfig);

        for (ValidatorDescriptor validator : validators) {

            FixMethod methodAnnotation = validator.method().getAnnotation(FixMethod.class);
            RuleSeverity ruleSeverity =
                    validationConfig.getSeverity(methodAnnotation.rule(), methodAnnotation.severity());

            if (ruleSeverity == RuleSeverity.OFF) continue;

            try {
                if (methodAnnotation.type() == ValidationType.FEATURE && target instanceof GFF3Feature) {
                    validator.method().invoke(validator.instance(), target, line);
                } else if (methodAnnotation.type() == ValidationType.ANNOTATION && target instanceof GFF3Annotation) {
                    validator.method().invoke(validator.instance(), target, line);
                }

            } catch (Exception e) {
                handleRuleException(e.getCause(), ruleSeverity);
            }
        }
    }

    public void handleSyntacticError(ValidationException exception) throws ValidationException {
        handleSyntacticValidationException(exception);
    }

    public List<ValidationException> getParsingErrors() {
        return parsingErrors;
    }

    private void handleSyntacticValidationException(ValidationException exception) throws ValidationException {
        String rule = exception.getValidationRule().toString();
        RuleSeverity severity = validationConfig.getSeverity(rule, RuleSeverity.ERROR);
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

    private void handleRuleException(Throwable cause, RuleSeverity severity) throws ValidationException {
        if (cause instanceof ValidationException ve) {
            if (severity == RuleSeverity.WARN) {
                parsingErrors.add(ve);
            } else if (severity == RuleSeverity.ERROR) {
                throw ve;
            }
        } else {
            throw new RuntimeException(cause);
        }
    }
}
