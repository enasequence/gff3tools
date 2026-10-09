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
package uk.ac.ebi.embl.gff3tools.metrics;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The gff3tools build version, resolved once from the build-generated
 * {@code gff3tools.properties} resource (see the {@code processResources} task in
 * {@code build.gradle}). Falls back to "unknown" when the resource is missing or malformed,
 * so a report is never blocked by version resolution.
 */
public final class Gff3ToolsVersion {

    public static final String VERSION = readVersion();

    private Gff3ToolsVersion() {}

    private static String readVersion() {
        try (InputStream in = Gff3ToolsVersion.class.getResourceAsStream("/gff3tools.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            return version != null && !version.isBlank() ? version : "unknown";
        } catch (IOException e) {
            // Static-initializer context: keep the report producible and say the version is
            // unresolved rather than failing the run over it.
            return "unknown";
        }
    }
}
