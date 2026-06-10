# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.



Context for this session:

I'm building a Travel Planning app ("Travel Operating System") focused on Turkey.
Current task: build a Spring Boot POI data importer from OpenStreetMap.

Dataset:
- File: turkey-tourist.osm.pbf (filtered, ~43,246 tourist POIs)
- Fields available: name(TR), name:en(7%), opening_hours(7.4%), wikidata(2.3%),
  phone(7.7%), website(5.4%), diet:vegetarian(0.6%)

DB Schema decided:
- PostgreSQL + PostGIS
- Table: poi with columns: id(UUID), osm_id(BIGINT), wikidata_id, name_tr,
  name_en, category, subcategory, location(GEOGRAPHY POINT 4326),
  boundary(GEOGRAPHY POLYGON), completeness_score(SMALLINT),
  data_sources(TEXT[]), attributes(JSONB), verified(BOOLEAN),
  last_synced_at, created_at, updated_at
- Table: poi_source_data for tracking per-source field values

Task:
Build a Spring Batch job in Java that:
1. Reads turkey-tourist.osm.pbf using osm4j library
2. Maps OSM nodes/ways/relations to the poi schema
3. Puts variable fields (opening_hours, phone, website, cuisine,
   wikidata, images, description, tags) into attributes JSONB
4. Calculates completeness_score (0-100)
5. Bulk-inserts into PostGIS using Spring Data JPA or JDBC batch

6. No security needed (PoC).

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