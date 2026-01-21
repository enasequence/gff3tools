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
package uk.ac.ebi.embl.gff3tools.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;
import uk.ac.ebi.embl.gff3tools.exception.ExitException;

// All the exceptions for this tool are handled here. An appropriate exit code is returned in case of handled errors.
public class ExecutionExceptionHandler implements IExecutionExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionExceptionHandler.class);

    @Override
    public int handleExecutionException(Exception e, CommandLine commandLine, ParseResult parseResult)
            throws Exception {
        // Find ExitException in the cause chain (may be nested multiple levels deep)
        ExitException exitException = findExitException(e);
        if (exitException != null) {
            LOG.error(e.getMessage());
            return exitException.exitCode().asInt();
        }
        throw e;
    }

    /**
     * Recursively searches the exception cause chain for an ExitException.
     */
    private ExitException findExitException(Throwable e) {
        if (e == null) {
            return null;
        }
        if (e instanceof ExitException) {
            return (ExitException) e;
        }
        return findExitException(e.getCause());
    }
}
