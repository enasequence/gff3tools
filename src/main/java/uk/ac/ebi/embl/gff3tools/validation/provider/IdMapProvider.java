package uk.ac.ebi.embl.gff3tools.validation.provider;

import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

public class IdMapProvider implements ContextProvider<IdMap>  {

    private final IdMap idMap;

    public IdMapProvider(ValidationContext context) {
        this.idMap = new IdMap(context);
    }

    @Override
    public IdMap get(ValidationContext context) {
        return idMap;
    }

    @Override
    public Class<IdMap> type() {
        return IdMap.class;
    }
}
