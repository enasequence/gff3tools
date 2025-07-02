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

import uk.ac.ebi.embl.converter.cli.CLIExitCode;
import uk.ac.ebi.embl.converter.cli.ConversionFileFormat;

public class FormatSupportException extends ExitException {
    public FormatSupportException(final ConversionFileFormat fromFt, final ConversionFileFormat toFt) {
        super("Conversion from \"" + fromFt + "\" to \"" + toFt + "\" is not supported");
    }

    @Override
    public CLIExitCode exitCode() {
        return CLIExitCode.UNSUPPORTED_FORMAT_CONVERSION;
    }
}
