ALTER TABLE ocpi_partners ADD COLUMN local_token_encrypted text;
ALTER TABLE ocpi_partners ADD COLUMN remote_endpoints jsonb NOT NULL DEFAULT '[]';
