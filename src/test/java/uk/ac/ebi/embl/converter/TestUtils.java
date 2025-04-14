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
package uk.ac.ebi.embl.converter;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class TestUtils {

    public static BufferedReader getResourceReader(String resourceName) throws IOException {
        FileReader reader = new FileReader(resourceName);
        return new BufferedReader(reader);
    }

    public static Map<String, Path> getTestFiles(String resourceName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        Map<String, Path> inFiles = new LinkedHashMap<>();
        if (resource != null) {
            File folder = new File(resource.getPath());
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                if (file.getName().endsWith(".embl")) {
                    inFiles.put(file.getName().replace(".embl", ""), file.toPath());
                }
            }
        } else {
            System.out.println("Directory not found!");
        }
        return inFiles;
    }
}
