RUN '001-SENTRY-327.derby.sql';
RUN '002-SENTRY-339.derby.sql';
RUN '003-SENTRY-380.derby.sql';
-- Version update
UPDATE SENTRY_VERSION SET SCHEMA_VERSION='1.4.0-mapr', VERSION_COMMENT='Sentry release version 1.4.0-mapr' WHERE VER_ID=1;
