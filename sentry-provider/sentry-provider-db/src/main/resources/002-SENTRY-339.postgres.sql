-- SENTRY-339
ALTER TABLE SENTRY_DB_PRIVILEGE DROP CONSTRAINT "SENTRY_DB_PRIV_PRIV_NAME_UNIQ";
ALTER TABLE SENTRY_DB_PRIVILEGE ADD CONSTRAINT "SENTRY_DB_PRIV_PRIV_NAME_UNIQ" UNIQUE ("SERVER_NAME","DB_NAME","TABLE_NAME","URI", "ACTION","WITH_GRANT_OPTION");
ALTER TABLE SENTRY_DB_PRIVILEGE DROP COLUMN PRIVILEGE_NAME;
