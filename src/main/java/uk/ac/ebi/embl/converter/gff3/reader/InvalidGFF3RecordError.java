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
package uk.ac.ebi.embl.converter.gff3.reader;

import lombok.Getter;
import uk.ac.ebi.embl.converter.validation.ValidationError;
import uk.ac.ebi.embl.converter.validation.ValidationRule;

@Getter
public class InvalidGFF3RecordError extends ValidationError {
    public InvalidGFF3RecordError(int line, String message) {
        super(ValidationRule.GFF3_INVALID_RECORD, line, message);
    }
}
