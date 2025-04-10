package uk.ac.ebi.embl.converter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions.*;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.converter.fftogff3.GFF3AnnotationFactory;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class GFF3AnnotationFactoryTest {
    static Map<String, String> featureRelationMap;
    @BeforeAll
    public static void setUp() throws Exception {
        featureRelationMap = ConversionUtils.getFeatureRelationMap();

    }

    @Test
    public void buildFeatureTreeFullMapTest(){
        GFF3AnnotationFactory gFF3AnnotationFactory = new GFF3AnnotationFactory(true);
        featureRelationMap.forEach((child,parent)->{
            List<GFF3Feature> featureList = new ArrayList<>();
            FeatureFactory featureFactory = new FeatureFactory();
            GFF3Feature childFeature = getGFF3Feature(Optional.of(child),Optional.of(parent));
            GFF3Feature parentFeature = getGFF3Feature(Optional.of(parent),Optional.empty());
            featureList.add(childFeature);
            featureList.add(parentFeature);

            Map<String, GFF3Feature> idMap =
                    featureList.stream()
                            .filter(f -> f.getId().isPresent())
                            .collect(Collectors.toMap(f -> f.getId().get(), Function.identity()));

            List<GFF3Feature> gff3Features = gFF3AnnotationFactory.buildFeatureTree(featureList);

            GFF3Feature firstFeature = gff3Features.stream().findFirst().get();
            assertTrue (firstFeature.getChildren().get(0).equals(childFeature));

        });
    }

    private GFF3Feature getGFF3Feature(Optional<String> featureName, Optional<String> parentFeatureName){
        return new GFF3Feature(
                featureName,
                parentFeatureName,
                "1234",
                ".",
                featureName.get(),
                1,
                800,
                ".",
                "+",
                "",
                null);
    }
}
