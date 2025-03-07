package uk.ac.ebi.embl.converter.gff3;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public class GFFeatures {
    private final String name;
    private final long  start;
    private final long end;

    Map<String, String> attributes = new HashMap<>();

}
