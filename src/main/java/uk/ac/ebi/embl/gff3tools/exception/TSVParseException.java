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
 * Exception thrown when a TSV file cannot be parsed due to format errors,
 * missing mandatory fields, or other structural issues.
 */
public class TSVParseException extends ReadException {

    private final int lineNumber;

    public TSVParseException(String message) {
        super(message, new IOException(message));
        this.lineNumber = -1;
    }

    public TSVParseException(String message, int lineNumber) {
        super("Line " + lineNumber + ": " + message, new IOException(message));
        this.lineNumber = lineNumber;
    }

    public TSVParseException(String message, Exception cause) {
        super(message, wrapAsIOException(cause));
        this.lineNumber = -1;
    }

    public TSVParseException(String message, int lineNumber, Exception cause) {
        super("Line " + lineNumber + ": " + message, wrapAsIOException(cause));
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
