package com.sajad.AITP.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajad.AITP.model.PoiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PoiRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper jackson = new ObjectMapper();

    // ── Nearby ──────────────────────────────────────────────────────────────

    private static final String NEARBY_SQL = """
        WITH ref AS (SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography AS pt)
        SELECT p.id, p.osm_id, p.osm_type, p.name_tr, p.name_en,
               p.category, p.subcategory,
               ST_Y(p.location::geometry) AS lat,
               ST_X(p.location::geometry) AS lon,
               p.completeness_score,
               p.attributes::text        AS attributes_json,
               ST_Distance(p.location, ref.pt) / 1000.0 AS distance_km
        FROM poi p, ref
        WHERE ST_DWithin(p.location, ref.pt, ?)
          AND (? IS NULL OR p.category = ?)
        ORDER BY distance_km
        LIMIT ? OFFSET ?
        """;

    public List<PoiResponse> findNearby(double lat, double lon, double radiusKm,
                                        String category, int page, int size) {
        return jdbc.query(NEARBY_SQL, poiRowMapper(),
            lon, lat,                    // ST_MakePoint(lon, lat)
            radiusKm * 1000,             // radius in metres
            category, category,          // nullable category filter
            size, (long) page * size);
    }

    // ── By ID ───────────────────────────────────────────────────────────────

    private static final String BY_ID_SQL = """
        SELECT id, osm_id, osm_type, name_tr, name_en,
               category, subcategory,
               ST_Y(location::geometry) AS lat,
               ST_X(location::geometry) AS lon,
               completeness_score,
               attributes::text AS attributes_json,
               NULL::double precision   AS distance_km
        FROM poi
        WHERE id = ?::uuid
        """;

    public Optional<PoiResponse> findById(String id) {
        List<PoiResponse> rows = jdbc.query(BY_ID_SQL, poiRowMapper(), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // ── Text search ─────────────────────────────────────────────────────────

    private static final String SEARCH_SQL = """
        SELECT id, osm_id, osm_type, name_tr, name_en,
               category, subcategory,
               ST_Y(location::geometry) AS lat,
               ST_X(location::geometry) AS lon,
               completeness_score,
               attributes::text AS attributes_json,
               NULL::double precision   AS distance_km
        FROM poi
        WHERE name_tr ILIKE ? OR name_en ILIKE ?
        ORDER BY completeness_score DESC
        LIMIT ? OFFSET ?
        """;

    public List<PoiResponse> search(String q, int page, int size) {
        String pattern = "%" + q + "%";
        return jdbc.query(SEARCH_SQL, poiRowMapper(),
            pattern, pattern, size, (long) page * size);
    }

    // ── Categories ──────────────────────────────────────────────────────────

    private static final String CATEGORIES_SQL = """
        SELECT category, subcategory, COUNT(*) AS count
        FROM poi
        GROUP BY category, subcategory
        ORDER BY category, count DESC
        """;

    public List<Map<String, Object>> findCategories() {
        return jdbc.queryForList(CATEGORIES_SQL);
    }

    // ── RowMapper ───────────────────────────────────────────────────────────

    private RowMapper<PoiResponse> poiRowMapper() {
        return (rs, rowNum) -> {
            Map<String, Object> attributes = parseAttributes(rs.getString("attributes_json"));

            double rawDist = rs.getDouble("distance_km");
            Double distanceKm = rs.wasNull() ? null : rawDist;

            return PoiResponse.builder()
                .id(rs.getString("id"))
                .osmId(rs.getLong("osm_id"))
                .osmType(rs.getString("osm_type"))
                .nameTr(rs.getString("name_tr"))
                .nameEn(rs.getString("name_en"))
                .category(rs.getString("category"))
                .subcategory(rs.getString("subcategory"))
                .lat(rs.getDouble("lat"))
                .lon(rs.getDouble("lon"))
                .completenessScore(rs.getInt("completeness_score"))
                .distanceKm(distanceKm)
                .attributes(attributes)
                .build();
        };
    }

    private Map<String, Object> parseAttributes(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return jackson.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse attributes JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
