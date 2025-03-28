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
package uk.ac.ebi.embl.converter.cli;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.converter.fftogff3.FFToGff3Converter;

public class FFToGff3CLI {

  private static final Logger LOG = LoggerFactory.getLogger(FFToGff3CLI.class);

  public static void main(String[] args) {
    try {
      // Parse command line arguments
      Params params = Params.parse(args);

      // Convert FF to Gff3
      new FFToGff3Converter().convert(params);

    } catch (CommandLine.ParameterException | IOException e) {
      LOG.error(e.getMessage());
      System.exit(1);
    }
  }
}
