CREATE TABLE fragrance (
    id text PRIMARY KEY,
    name text NOT NULL,
    brand text NOT NULL,
    notes text[] NOT NULL CHECK (cardinality(notes) > 0),
    accords text[] NOT NULL DEFAULT '{}',
    moods text[] NOT NULL DEFAULT '{}',
    family text NOT NULL,
    seasons text[] NOT NULL CHECK (cardinality(seasons) > 0),
    times text[] NOT NULL CHECK (cardinality(times) > 0),
    source_url text NOT NULL CHECK (source_url LIKE 'https://%'),
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE shortlist_item (
    visitor_id uuid NOT NULL,
    fragrance_id text NOT NULL REFERENCES fragrance(id),
    saved_at timestamptz NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (visitor_id, fragrance_id)
);
