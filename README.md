# Solace

Solace is an Android, offline-first private location-sharing application for exactly two consenting people.

## Current implementation

The Android app includes the Compose navigation shell, location permission flow, a foreground location service, local Room entities, point filtering, and a transaction that persists a captured point with its cloud and peer outbox records.

The backend migration is in `supabase/migrations/001_initial_schema.sql`. It creates the core schema, RLS policies, and timestamp-based latest-location trigger. Supabase URL and publishable key must be supplied outside source control before cloud authentication or sync can be activated.

## Android testing

1. Open this directory in Android Studio.
2. Use JDK 17 for Gradle.
3. Build `app` and install on a physical API 26+ device.
4. Grant location while using the app first; Android requires a user-initiated foreground-service start before background tracking can begin.

No service-role credential belongs in this project.
