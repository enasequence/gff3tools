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
package uk.ac.ebi.embl.converter.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.cli.CLIExitCode;
import uk.ac.ebi.embl.converter.cli.ConversionFileFormat;

public class FormatSupportExceptionTest {

    @Test
    void testConstructor() {
        ConversionFileFormat fromFt = ConversionFileFormat.embl;
        ConversionFileFormat toFt = ConversionFileFormat.gff3;
        FormatSupportException exception = new FormatSupportException(fromFt, toFt);

        assertEquals("Conversion from \"embl\" to \"gff3\" is not supported", exception.getMessage());
        assertNull(exception.getCause());
        assertEquals(CLIExitCode.UNSUPPORTED_FORMAT_CONVERSION, exception.exitCode());
    }

    @Test
    void testExitCode() {
        FormatSupportException exception =
                new FormatSupportException(ConversionFileFormat.embl, ConversionFileFormat.gff3);
        assertEquals(CLIExitCode.UNSUPPORTED_FORMAT_CONVERSION, exception.exitCode());
    }
}
