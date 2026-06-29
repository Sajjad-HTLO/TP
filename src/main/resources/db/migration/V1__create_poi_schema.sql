CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE poi (
    id                  UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    osm_id              BIGINT      NOT NULL,
    osm_type            CHAR(1)     NOT NULL CHECK (osm_type IN ('N', 'W', 'R')),
    wikidata_id         VARCHAR(20),
    name_tr             VARCHAR(500) NOT NULL DEFAULT '',
    name_en             VARCHAR(500),
    category            VARCHAR(100) NOT NULL,
    subcategory         VARCHAR(100),
    location            GEOGRAPHY(POINT, 4326),
    boundary            GEOGRAPHY(POLYGON, 4326),
    completeness_score  SMALLINT    NOT NULL DEFAULT 0,
    data_sources        TEXT[]      NOT NULL DEFAULT '{}',
    attributes          JSONB       NOT NULL DEFAULT '{}',
    verified            BOOLEAN     NOT NULL DEFAULT FALSE,
    last_synced_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (osm_id, osm_type)
);

CREATE INDEX poi_location_idx    ON poi USING GIST (location);
CREATE INDEX poi_category_idx    ON poi (category);
CREATE INDEX poi_subcategory_idx ON poi (subcategory);
CREATE INDEX poi_attributes_idx  ON poi USING GIN (attributes);

CREATE TABLE poi_source_data (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    poi_id     UUID        NOT NULL REFERENCES poi(id) ON DELETE CASCADE,
    source     VARCHAR(50) NOT NULL,
    field      VARCHAR(100) NOT NULL,
    value      TEXT,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX poi_source_data_poi_id_idx ON poi_source_data (poi_id);