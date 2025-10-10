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

import java.lang.reflect.Method;
import java.util.*;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class ValidationEngine {
    private static Logger LOG = LoggerFactory.getLogger(ValidationEngine.class);

    private final Map<String, RuleSeverity> severityMap;
    private final List<ValidationException> parsingErrors;
    public ValidationConfig validationConfig;
    private List<Validation> validations =  new ArrayList();



    ValidationEngine(
            Map<String, RuleSeverity> severityMap) {
        this.severityMap = severityMap;
        this.parsingErrors = new java.util.ArrayList<>();
        validationConfig = new ValidationConfig(severityMap, new HashMap<>());
        ValidationRegistry.clearRegistry();
    }


    public <T> void validate(T target, int line) throws ValidationException, ClassCastException {
        executeValidations(target, line);
        /*for (AnnotationValidation validation : activeAnnotationValidations) {
            try {
                validation.validateAnnotation(annotation, line);
            } catch (ValidationException exception) {
                handleValidationException(exception);
            }
        }*/
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

    public <T> void executeValidations(T target, int line) throws ValidationException {
        List<ValidatorDescriptor> validators =
                ValidationRegistry.getValidators(validationConfig);

        for (ValidatorDescriptor descriptor : validators) {
            ValidationMethod vm = descriptor.method().getAnnotation(ValidationMethod.class);
            RuleSeverity ruleSeverity = validationConfig.getSeverity(vm.rule(), vm.severity());

            if (ruleSeverity == RuleSeverity.OFF) continue;

            try {
                if (vm.type() == ValidationType.FEATURE && target instanceof GFF3Feature) {
                    descriptor.method().invoke(descriptor.instance(), target, line);
                } else if (vm.type() == ValidationType.ANNOTATION && target instanceof GFF3Annotation) {
                    descriptor.method().invoke(descriptor.instance(), target, line);
                }

            } catch (Exception e) {
                String msg = e.getMessage();
                if (ruleSeverity == RuleSeverity.WARN) {
                    System.out.println("[WARN] " + msg);
                } else if (ruleSeverity == RuleSeverity.ERROR) {
                    throw new ValidationException(msg);
                } else {
                   throw new RuntimeException(e);
                }
            }
        }

    }
}
