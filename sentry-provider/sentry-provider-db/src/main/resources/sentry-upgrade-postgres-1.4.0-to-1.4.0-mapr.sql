SELECT 'Upgrading Sentry store schema from 1.4.0 to 1.4.0-mapr';
\i 001-SENTRY-339.postgres.sql;
\i 002-SENTRY-339.postgres.sql;
\i 003-SENTRY-380.postgres.sql;

UPDATE SENTRY_VERSION SET SCHEMA_VERSION='1.4.0-mapr', VERSION_COMMENT='Sentry release version 1.4.0-mapr' WHERE VER_ID=1;
SELECT 'Finished upgrading Sentry store schema from 1.4.0 to 1.4.0-mapr';
