package com.drewshelper.routing;

public final class DrewsHelperDataProvenance
{
    public static final DrewsHelperDataProvenance INHERITED =
        new DrewsHelperDataProvenance(DrewsHelperDataConfidence.INHERITED, "legacy-resource");

    public static final DrewsHelperDataProvenance INFERRED =
        new DrewsHelperDataProvenance(DrewsHelperDataConfidence.INFERRED, "cache-derived");

    private final DrewsHelperDataConfidence confidence;
    private final String source;

    public DrewsHelperDataProvenance(DrewsHelperDataConfidence confidence, String source)
    {
        this.confidence = confidence == null ? DrewsHelperDataConfidence.INHERITED : confidence;
        this.source = source == null ? "" : source.trim();
    }

    public DrewsHelperDataConfidence getConfidence()
    {
        return confidence;
    }

    public String getSource()
    {
        return source;
    }
}
