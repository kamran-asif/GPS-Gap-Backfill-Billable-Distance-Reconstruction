-- Flyway V1: Initial Enterprise Schema for MoveInSync Trip Distance Engine

CREATE TABLE IF NOT EXISTS trips (
    id BIGSERIAL PRIMARY KEY,
    trip_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    total_distance_km DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) NOT NULL,
    rule_version VARCHAR(50) NOT NULL,
    raw_pings_count INT NOT NULL DEFAULT 0,
    cleaned_pings_count INT NOT NULL DEFAULT 0,
    teleport_dropped_count INT NOT NULL DEFAULT 0,
    low_accuracy_dropped_count INT NOT NULL DEFAULT 0,
    anchors_inserted_count INT NOT NULL DEFAULT 0,
    provider_calls_attempted INT NOT NULL DEFAULT 0,
    provider_cache_hits INT NOT NULL DEFAULT 0,
    provider_cache_misses INT NOT NULL DEFAULT 0,
    provider_calls_failed INT NOT NULL DEFAULT 0,
    provider_cost_usd DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    idempotency_key VARCHAR(150),
    evidence_snapshot TEXT,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_trip_tenant_version ON trips (trip_id, tenant_id, version);
CREATE INDEX IF NOT EXISTS idx_trip_tenant ON trips (tenant_id);
CREATE INDEX IF NOT EXISTS idx_trip_status ON trips (status, tenant_id);
CREATE INDEX IF NOT EXISTS idx_trip_idempotency ON trips (trip_id, tenant_id, idempotency_key);

CREATE TABLE IF NOT EXISTS trip_segments (
    id BIGSERIAL PRIMARY KEY,
    trip_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    segment_index INT NOT NULL,
    start_lat DOUBLE PRECISION NOT NULL,
    start_lon DOUBLE PRECISION NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    start_type VARCHAR(50) NOT NULL,
    start_desc VARCHAR(255),
    end_lat DOUBLE PRECISION NOT NULL,
    end_lon DOUBLE PRECISION NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_type VARCHAR(50) NOT NULL,
    end_desc VARCHAR(255),
    delta_seconds BIGINT NOT NULL,
    implied_aerial_speed_kmh DOUBLE PRECISION NOT NULL,
    aerial_distance_km DOUBLE PRECISION NOT NULL,
    final_distance_km DOUBLE PRECISION NOT NULL,
    rule_applied VARCHAR(50) NOT NULL,
    explanation TEXT NOT NULL,
    routed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_segment_trip_version ON trip_segments (trip_id, tenant_id, version);

CREATE TABLE IF NOT EXISTS trip_adjustments (
    id BIGSERIAL PRIMARY KEY,
    trip_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    previous_version INT NOT NULL,
    new_version INT NOT NULL,
    previous_distance_km DOUBLE PRECISION NOT NULL,
    new_distance_km DOUBLE PRECISION NOT NULL,
    delta_km DOUBLE PRECISION NOT NULL,
    formatted_delta VARCHAR(50) NOT NULL,
    adjustment_type VARCHAR(50) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_adjustment_trip_tenant ON trip_adjustments (trip_id, tenant_id);
