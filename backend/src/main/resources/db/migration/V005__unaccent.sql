-- Accent-insensitive Person search (genealogy.md §11, data-model.md §23.1, Phase 2 plan §3.3).
-- unaccent is a trusted extension: the database owner can create it.
CREATE EXTENSION IF NOT EXISTS unaccent;
