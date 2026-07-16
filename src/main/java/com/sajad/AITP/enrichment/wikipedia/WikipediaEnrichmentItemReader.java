package com.sajad.AITP.enrichment.wikipedia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajad.AITP.enrichment.PoiEnrichmentCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class WikipediaEnrichmentItemReader implements ItemReader<PoiEnrichmentCandidate> {

    private static final String SELECT_SQL = """
            SELECT p.id, p.name_tr, p.name_en, p.category, p.completeness_score, p.attributes::text
            FROM poi p
            WHERE NOT EXISTS (
                SELECT 1 FROM poi_source_data psd
                WHERE psd.poi_id = p.id AND psd.source = 'wikipedia'
            )
              AND (p.name_tr <> '' OR p.name_en IS NOT NULL)
              AND mod(abs(hashtext(p.id::text)), ?) = ?
            ORDER BY
                CASE WHEN jsonb_exists(p.attributes, 'wikipedia') THEN 0 ELSE 1 END,
                p.completeness_score ASC,
                p.id
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wikipedia.enrichment.max-items-per-run:50}")
    private int maxItemsPerRun;

    @Value("#{stepExecutionContext['partition']}")
    private Integer partition;

    @Value("#{stepExecutionContext['partitionCount']}")
    private Integer partitionCount;

    private Iterator<PoiEnrichmentCandidate> iterator;

    @Override
    public PoiEnrichmentCandidate read() {
        if (iterator == null) {
            iterator = loadCandidates().iterator();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }

    private List<PoiEnrichmentCandidate> loadCandidates() {
        int shard = partition != null ? partition : 0;
        int shards = partitionCount != null ? partitionCount : 1;
        List<PoiEnrichmentCandidate> candidates = jdbc.query(SELECT_SQL, (rs, rowNum) -> {
            Map<String, Object> attributes = parseAttributes(rs.getString("attributes"));
            return PoiEnrichmentCandidate.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .nameTr(rs.getString("name_tr"))
                    .nameEn(rs.getString("name_en"))
                    .category(rs.getString("category"))
                    .completenessScore(rs.getShort("completeness_score"))
                    .attributes(attributes)
                    .build();
        }, shards, shard, maxItemsPerRun);
        log.info("Partition {}/{} loaded {} POI candidates", shard, shards, candidates.size());
        return candidates;
    }

    private Map<String, Object> parseAttributes(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
