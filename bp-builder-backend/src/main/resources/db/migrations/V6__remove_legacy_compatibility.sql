-- The workflow engine now searches active operations directly and derives
-- compatibility from approved canonical mappings. These tables belonged to
-- the removed OpenAPI/AI pair-checking, schema-comparison and internal
-- profile-rule engines.

-- transform_runs is kept, but its two foreign keys point at schemas. Only
-- those constraints are removed; its columns and rows stay as they are.
DO $$
DECLARE
    constraint_row record;
BEGIN
    IF to_regclass('public.transform_runs') IS NOT NULL AND to_regclass('public.schemas') IS NOT NULL THEN
        FOR constraint_row IN
            SELECT conname FROM pg_constraint
            WHERE conrelid = 'public.transform_runs'::regclass AND confrelid = 'public.schemas'::regclass
        LOOP
            EXECUTE format('ALTER TABLE public.transform_runs DROP CONSTRAINT %I', constraint_row.conname);
        END LOOP;
    END IF;
END $$;

-- No CASCADE: if anything else still depends on these tables, the drop fails
-- instead of silently removing it. compatibility_checks (empty) is kept
-- because bp_builder.task_connections, in another project's schema, still
-- has a foreign key to it.
DROP TABLE IF EXISTS compatibility_issues;
DROP TABLE IF EXISTS compatibility_results;
DROP TABLE IF EXISTS mapping_rules;
DROP TABLE IF EXISTS operation_semantics;
DROP TABLE IF EXISTS schema_fields;
DROP TABLE IF EXISTS schemas;
DROP TABLE IF EXISTS canonical_profile_requirements;
DROP TABLE IF EXISTS canonical_profiles;
