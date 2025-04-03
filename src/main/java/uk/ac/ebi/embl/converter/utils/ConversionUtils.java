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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public enum ConversionUtils {
  INSTANCE;
  private Map<String, List<ConversionEntry>> ff2gff3 = null;
  private Map<String, ConversionEntry> gff32ff = null;
  private Map<String, String> ff2gff3_qualifiers = null;
  private Map<String, String> gff32ff_qualifiers = null;

  private ConversionUtils() {
    this.loadMaps();
  }

  public static Map<String, List<ConversionEntry>> getFF2GFF3FeatureMap() {
    return INSTANCE.ff2gff3;
  }

  public static Map<String, String> getFF2GFF3QualifierMap() {
    return INSTANCE.ff2gff3_qualifiers;
  }

  public static Map<String, ConversionEntry> getGFF32FFFeatureMap() {
    return INSTANCE.gff32ff;
  }

  public static Map<String, String> getGFF32FFQualifierMap() {
    return INSTANCE.gff32ff_qualifiers;
  }

  private void loadMaps() {
    try {
      ff2gff3 = new HashMap<>();
      gff32ff = new HashMap<>();
      List<String> lines = readTsvFile("feature-mapping.tsv");
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
      lines = readTsvFile("qualifier-mapping.tsv");
      lines.remove(0);
      for (String line : lines) {
        String[] words = line.split("\t");
        ff2gff3_qualifiers.put(words[0], words[1]);
        gff32ff_qualifiers.put(words[1], words[0]);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static List<String> readTsvFile(String fileName) {
    try (InputStream inputStream =
        ConversionUtils.class.getClassLoader().getResourceAsStream(fileName)) {
      if (inputStream == null) {
        throw new IllegalArgumentException("File not found: " + fileName);
      }

      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        return reader.lines().collect(Collectors.toList());
      }
    } catch (Exception e) {
      throw new RuntimeException("Error reading file: " + fileName, e);
    }
  }
}
