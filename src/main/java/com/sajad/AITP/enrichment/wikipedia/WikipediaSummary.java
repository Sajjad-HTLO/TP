package com.sajad.AITP.enrichment.wikipedia;

public record WikipediaSummary(
        String lang,
        String title,
        String extract,
        String description,
        String pageUrl,
        String imageUrl
) {
}
