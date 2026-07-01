# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Turkey-focused "Travel Operating System" — a Spring Boot 4 backend PoC.
No security/auth needed at this stage.

## What is already built

### 1. OSM POI importer (Spring Batch)

- Reads `data/turkey-tourist.osm.pbf` (~43,246 tourist POIs) via osm4j
- Maps nodes/ways/relations to the `poi` DB schema
- Variable fields (opening_hours, phone, website, cuisine, wikidata, tags) go into `attributes JSONB`
- Calculates `completeness_score` (0–100)
- Bulk-inserts into PostGIS via JDBC batch
- Disabled by default (`spring.batch.job.enabled=false`); enable to re-import

### 2. POI search API (`/api/pois`)

- `GET /nearby` — spatial radius search, optional category filter
- `GET /{id}` — single POI by UUID
- `GET /search` — full-text search (TR + EN names)
- `GET /categories` — all categories with counts

### 3. Weather API (`/api/weather`)

- Provider: **Open-Meteo** — free, no API key
- Returns current conditions + daily forecast (1–16 days)
- WMO weather code → human-readable description mapping included

### 4. Routing API (`/api/route`)

- Provider: **OSRM public demo server** — free, no API key
- Profiles: `driving` (default), `foot`, `bike`
- Returns distance (km), duration (minutes), road summary, GeoJSON LineString geometry

## DB Schema

- PostgreSQL + PostGIS
- Table: `poi` — id(UUID), osm_id(BIGINT), wikidata_id, name_tr, name_en,
  category, subcategory, location(GEOGRAPHY POINT 4326), boundary(GEOGRAPHY POLYGON),
  completeness_score(SMALLINT), data_sources(TEXT[]), attributes(JSONB),
  verified(BOOLEAN), last_synced_at, created_at, updated_at
- Table: `poi_source_data` — per-source field value tracking

## Dataset

- File: `data/turkey-tourist.osm.pbf` (filtered OSM export, ~43,246 tourist POIs)
- Fields available: name(TR), name:en(7%), opening_hours(7.4%), wikidata(2.3%),
  phone(7.7%), website(5.4%), diet:vegetarian(0.6%)

## Current / next tasks

- [ ] Run OSM import job against local PostGIS and verify POI data loads correctly
- [ ] Decide on additional data enrichment (Wikidata lookup, image URLs)
- [ ] Frontend / mobile client (future)

## Tech Stack

- **Java 25**, **Spring Boot 4.0.6**
- **Maven** (wrapper included — use `./mvnw` instead of `mvn`)

## Common Commands

```bash
# Build
./mvnw clean package

# Run the application
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=AitpApplicationTests

# Run a single test method
./mvnw test -Dtest=AitpApplicationTests#contextLoads
```

## Project Structure

This is a standard Spring Boot project at early scaffold stage.

- Entry point: `src/main/java/com/sajad/AITP/AitpApplication.java`
- Config: `src/main/resources/application.properties`
- Tests: `src/test/java/com/sajad/AITP/`
- Package root: `com.sajad.AITP`