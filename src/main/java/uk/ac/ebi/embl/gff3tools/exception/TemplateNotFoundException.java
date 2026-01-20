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
package uk.ac.ebi.embl.gff3tools.exception;

import java.io.IOException;

/**
 * Exception thrown when a TSV file references a template ID that cannot be found
 * in sequencetools' template resources.
 */
public class TemplateNotFoundException extends ReadException {

    public TemplateNotFoundException(String message) {
        super(message, new IOException(message));
    }

    public TemplateNotFoundException(String message, Exception cause) {
        super(message, wrapAsIOException(cause));
    }

    private static IOException wrapAsIOException(Exception e) {
        if (e instanceof IOException) {
            return (IOException) e;
        }
        return new IOException(e);
    }
}
