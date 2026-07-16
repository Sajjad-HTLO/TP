-- OSM tag values (e.g. sport=fitness;swimming;...) can exceed VARCHAR(100).
ALTER TABLE poi
    ALTER COLUMN category    TYPE TEXT,
    ALTER COLUMN subcategory TYPE TEXT;
