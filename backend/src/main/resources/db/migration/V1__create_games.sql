CREATE TABLE games (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    owner_id UUID NOT NULL,
    white_name VARCHAR(40) NOT NULL,
    black_name VARCHAR(40) NOT NULL,
    moves_json TEXT NOT NULL,
    result VARCHAR(7) NOT NULL,
    termination VARCHAR(40) NOT NULL,
    move_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_games_owner_updated ON games(owner_id, updated_at DESC);
