package uk.ac.ebi.embl.converter;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class TestUtils {

    public static BufferedReader getResourceReader(String resourceName) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(classLoader.getResourceAsStream(resourceName)));
        //InputStreamReader reader = new InputStreamReader(new InputStreamReader(new FileInputStream(resourceName).));
        FileReader reader = new FileReader(resourceName);
        return new BufferedReader(reader);
    }

    public static Map<String,Path> getTestFiles(String resourceName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        Map<String, Path> inFiles = new LinkedHashMap<>();
        if (resource != null) {
            File folder = new File(resource.getPath());
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                if(file.getName().endsWith(".embl")){
                    inFiles.put(file.getName().replace(".embl",""), file.toPath());
                }
            }
        } else {
            System.out.println("Directory not found!");
        }
        return inFiles;
    }
}
