package com.sajad.AITP.enrichment.wikipedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajad.AITP.enrichment.PoiEnrichmentCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class WikipediaClient {

    private static final String USER_AGENT = "AITP-TravelOS/0.1 (POI enrichment; contact: dev@local)";

    private final RestClient restClient;
    private final RequestRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public WikipediaClient(RequestRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = new ObjectMapper();

        // Connection-pooled HTTP client — reuses TCP connections across threads
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) return null;
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    public Optional<WikipediaSummary> lookup(PoiEnrichmentCandidate poi) throws InterruptedException {
        Map<String, Object> attrs = poi.getAttributes();
        if (attrs != null) {
            Optional<WikipediaSummary> fromTag = fetchFromOsmTag(attrs, "wikipedia");
            if (fromTag.isPresent()) return fromTag;

            Optional<WikipediaSummary> fromEn = fetchFromOsmTag(attrs, "wikipedia:en");
            if (fromEn.isPresent()) return fromEn;

            Optional<WikipediaSummary> fromTr = fetchFromOsmTag(attrs, "wikipedia:tr");
            if (fromTr.isPresent()) return fromTr;
        }

        if (poi.getNameEn() != null && !poi.getNameEn().isBlank()) {
            Optional<WikipediaSummary> hit = searchAndSummarize("en", poi.getNameEn());
            if (hit.isPresent()) return hit;
        }

        if (poi.getNameTr() != null && !poi.getNameTr().isBlank()) {
            Optional<WikipediaSummary> hit = searchAndSummarize("tr", poi.getNameTr());
            if (hit.isPresent()) return hit;

            return searchAndSummarize("en", poi.getNameTr());
        }

        return Optional.empty();
    }

    private Optional<WikipediaSummary> fetchFromOsmTag(Map<String, Object> attrs, String key)
            throws InterruptedException {
        Object raw = attrs.get(key);
        if (raw == null) return Optional.empty();

        String value = raw.toString().trim();
        if (value.isBlank()) return Optional.empty();

        int sep = value.indexOf(':');
        if (sep <= 0 || sep >= value.length() - 1) return Optional.empty();

        String lang = value.substring(0, sep).trim();
        String title = value.substring(sep + 1).trim();
        return fetchSummary(lang, title);
    }

    private Optional<WikipediaSummary> searchAndSummarize(String lang, String query)
            throws InterruptedException {
        rateLimiter.acquire();

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            ResponseEntity<String> response = restClient.get()
                    .uri("https://" + lang + ".wikipedia.org/w/api.php"
                            + "?action=opensearch&search=" + encodedQuery
                            + "&limit=1&namespace=0&format=json")
                    .retrieve()
                    .toEntity(String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.isArray() || root.size() < 2) {
                return Optional.empty();
            }

            JsonNode titles = root.get(1);
            if (!titles.isArray() || titles.isEmpty()) {
                return Optional.empty();
            }

            String title = titles.get(0).asText();
            return fetchSummary(lang, title);
        } catch (RestClientException e) {
            log.debug("Wikipedia search failed for [{}] {}: {}", lang, query, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Wikipedia search parse failed for [{}] {}: {}", lang, query, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<WikipediaSummary> fetchSummary(String lang, String title)
            throws InterruptedException {
        rateLimiter.acquire();

        try {
            String encodedTitle = URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8);
            ResponseEntity<String> response = restClient.get()
                    .uri("https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/" + encodedTitle)
                    .retrieve()
                    .toEntity(String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }

            return parseSummaryBody(lang, title, response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.debug("Wikipedia summary not found for {}:{} ({})", lang, title, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<WikipediaSummary> parseSummaryBody(String lang, String title, String json) {
        JsonNode body;
        try {
            body = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse Wikipedia summary for {}:{}", lang, title);
            return Optional.empty();
        }

        if (body.has("type") && body.get("type").asText("").contains("not_found")) {
            return Optional.empty();
        }

        String extract = textOrNull(body.get("extract"));
        if (extract == null || extract.isBlank()) {
            return Optional.empty();
        }

        String pageUrl = textOrNull(body.path("content_urls").path("desktop").path("page"));
        String imageUrl = textOrNull(body.path("thumbnail").path("source"));
        String description = textOrNull(body.get("description"));
        String resolvedTitle = textOrNull(body.get("title"));

        return Optional.of(new WikipediaSummary(
                lang,
                resolvedTitle != null ? resolvedTitle : title,
                extract,
                description,
                pageUrl,
                imageUrl
        ));
    }
}
