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
package uk.ac.ebi.embl.converter.utils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ConversionUtils {

  static Map<String, List<ConversionEntry>> ff2gff3 = null;
  static Map<String, ConversionEntry> gff32ff = null;
  static Map<String, String> ff2gff3_qualifiers = null;
  static Map<String, String> gff32ff_qualifiers = null;

  public static Map<String, List<ConversionEntry>> getFFToGFF3FeatureMap()
      throws URISyntaxException, IOException {
    if (ff2gff3 != null) return ff2gff3;
    loadMaps();
    return ff2gff3;
  }

  public static Map<String, String> getFFToGFF3QualifierMap()
      throws URISyntaxException, IOException {
    if (ff2gff3_qualifiers == null) loadMaps();
    return ff2gff3_qualifiers;
  }

  public static Map<String, ConversionEntry> getGFF3ToFFFeatureMap()
      throws URISyntaxException, IOException {
    if (gff32ff != null) return gff32ff;
    loadMaps();
    return gff32ff;
  }

  private static void loadMaps() throws URISyntaxException, IOException {
    ff2gff3 = new HashMap<>();
    gff32ff = new HashMap<>();
    Path filePath =
        Paths.get(
            Objects.requireNonNull(ConversionUtils.class.getResource("/feature-mapping.tsv"))
                .toURI());
    List<String> lines = Files.readAllLines(filePath);
    lines.remove(0);
    for (String line : lines) {
      ConversionEntry conversionEntry = new ConversionEntry(line.split("\t"));
      ff2gff3.putIfAbsent(conversionEntry.feature, new ArrayList<>());
      ff2gff3.get(conversionEntry.feature).add(conversionEntry);
      gff32ff.putIfAbsent(conversionEntry.sOID, conversionEntry);
      gff32ff.putIfAbsent(conversionEntry.sOTerm, conversionEntry);
    }

    ff2gff3_qualifiers = new HashMap<>();
    gff32ff_qualifiers = new HashMap<>();
    filePath =
        Paths.get(
            Objects.requireNonNull(ConversionUtils.class.getResource("/qualifier-mapping.tsv"))
                .toURI());
    lines = Files.readAllLines(filePath);
    lines.remove(0);
    for (String line : lines) {
      String[] words = line.split("\t");
      ff2gff3_qualifiers.put(words[0], words[1]);
      gff32ff_qualifiers.put(words[1], words[0]);
    }
  }
}
