# Solace

Solace is an Android, offline-first private location-sharing application for exactly two consenting people.

## Current implementation

The Android app includes the Compose navigation shell, location permission flow, a foreground location service, local Room entities, point filtering, and a transaction that persists a captured point with its cloud and peer outbox records.

The backend migration is in `supabase/migrations/001_initial_schema.sql`. It creates the core schema, RLS policies, and timestamp-based latest-location trigger. Supabase URL and publishable key must be supplied outside source control before cloud authentication or sync can be activated.

## Supabase email confirmation

In Supabase Dashboard, open **Authentication → URL Configuration** and add `solace://auth-callback` as an Additional Redirect URL. The Android manifest owns this deep link. Without it, Supabase uses its default Site URL (often `http://localhost:3000`) after email confirmation.

## Pairing and sync

Run `supabase/migrations/002_pairing_and_sharing.sql` in the Supabase SQL Editor (after 001). It adds profile bootstrap, one-time pairing codes, the sharing toggle and `couple_status`. No custom SMTP or domain is needed.

Flow: both people sign in, one taps **Create invitation code**, the other enters it under **Join with code**, then each turns on **Share my location**. Points upload only while your own sharing is on, and you only see each other when both are on.

## Android testing

1. Open this directory in Android Studio.
2. Use JDK 17 for Gradle.
3. Build `app` and install on a physical API 26+ device.
4. Grant location while using the app first; Android requires a user-initiated foreground-service start before background tracking can begin.

No service-role credential belongs in this project.
