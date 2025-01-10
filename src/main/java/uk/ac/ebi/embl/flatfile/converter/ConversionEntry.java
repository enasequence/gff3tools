package uk.ac.ebi.embl.flatfile.converter;

class ConversionEntry {
    String sOID;
    String sOTerm;
    String definition;
    String feature;
    String qualifier1;
    String qualifier2;

    ConversionEntry(String[] tokens) {
        this.sOID = tokens[0];
        this.sOTerm = tokens[1];
        this.definition = tokens[2];
        this.feature = tokens[3];
        if (tokens.length > 4) this.qualifier1 = tokens[4].equalsIgnoreCase("null") ? null : tokens[4];
        if (tokens.length > 5) this.qualifier2 = tokens[5].equalsIgnoreCase("null") ? null : tokens[5];
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ConversionEntry{");
        //sb.append("sOID='").append(sOID).append('\'');
        sb.append(", sOTerm='").append(sOTerm).append('\'');
        //sb.append(", definition='").append(definition).append('\'');
        sb.append(", feature='").append(feature).append('\'');
        sb.append(", qualifier1='").append(qualifier1).append('\'');
        sb.append(", qualifier2='").append(qualifier2).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
