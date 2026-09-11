CREATE TABLE users (
 id UUID PRIMARY KEY, user_code VARCHAR(32) NOT NULL UNIQUE,
 username VARCHAR(64) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(10) NOT NULL CHECK (role IN ('USER','ADMIN')),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE devices (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
 device_id VARCHAR(100) NOT NULL, device_name VARCHAR(100) NOT NULL,
 registered_at TIMESTAMPTZ NOT NULL, last_access_at TIMESTAMPTZ NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(user_id, device_id)
);
CREATE TABLE authentication_logs (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
 device_id UUID NOT NULL REFERENCES devices(id), event_id UUID NOT NULL,
 result VARCHAR(10) NOT NULL CHECK (result IN ('SUCCESS','FAILED')),
 fuzzy_score DOUBLE PRECISION NOT NULL CHECK (fuzzy_score BETWEEN 0 AND 1),
 mahalanobis_score DOUBLE PRECISION NOT NULL CHECK (mahalanobis_score BETWEEN 0 AND 1),
 final_score DOUBLE PRECISION NOT NULL CHECK (final_score BETWEEN 0 AND 1),
 coverage DOUBLE PRECISION NOT NULL CHECK (coverage BETWEEN 0 AND 1),
 margin DOUBLE PRECISION NOT NULL CHECK (margin BETWEEN 0 AND 1),
 liveness BOOLEAN NOT NULL, failure_reason VARCHAR(80),
 occurred_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(user_id, event_id),
 CHECK ((result='SUCCESS' AND liveness=TRUE AND failure_reason IS NULL) OR
        (result='FAILED' AND failure_reason IS NOT NULL))
);
CREATE INDEX authentication_logs_user_time ON authentication_logs(user_id, created_at DESC);
CREATE INDEX authentication_logs_time ON authentication_logs(created_at DESC);
