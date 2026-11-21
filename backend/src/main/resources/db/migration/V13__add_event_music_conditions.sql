SET LOCAL lock_timeout = '3s';

ALTER TABLE events
    ADD CONSTRAINT ck_events_genres_distinct CHECK (
        array_ndims(genres) = 1
        AND array_lower(genres, 1) = 1
        AND (cardinality(genres) < 2 OR genres[1] <> genres[2])
        AND (cardinality(genres) < 3 OR (genres[1] <> genres[3] AND genres[2] <> genres[3]))
    );
