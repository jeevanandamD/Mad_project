# SUCS Android App

This project converts the provided SUCS design mockups into a functional Android app shell for:

- Customer dashboard and complaint registration
- Admin dashboard and complaint monitoring
- Worker availability and task management
- Firebase Authentication + Firestore-backed role logic
- Real-time complaint and emergency request flows

## Project Structure

- app/src/main/java/com/example/sucs
- app/src/main/res/layout
- app/src/main/res/values

## Supabase Setup

1. Create a Supabase project at https://app.supabase.com and open the project dashboard.
2. In Project Settings -> API, copy the **Project URL** and the **anon/public** key.
3. Add the following to `local.properties` in the project root (`android_app/local.properties`) — this file is typically excluded from version control:

   SUPABASE_URL=https://your-project-id.supabase.co
   SUPABASE_ANON_KEY=your-anon-key

   Alternatively you can set the environment variables SUPABASE_URL and SUPABASE_ANON_KEY on your build machine.
4. In the Supabase SQL editor or Table editor, create a `profiles` table with at least these columns:
   - `uid` (text, primary key)
   - `email` (text)
   - `name` (text)
   - `role` (text)
   - `phone` (text)
   - `address` (text)

5. The app uses Supabase GoTrue for authentication and PostgREST for the `profiles` table. See the `app/build.gradle.kts` dependencies for the libraries used.

## Role model

User documents in Firestore should include:

```json
{
  "uid": "...",
  "email": "user@example.com",
  "name": "User Name",
  "role": "customer",
  "phone": "+1234567890",
  "address": "Street, City",
  "available": true
}
```

Allowed roles:
- admin
- worker
- customer

## Open in Android Studio

Open the `android_app` folder in Android Studio and let Gradle sync.

## Build validation

This project was validated with the Gradle wrapper in the workspace using:

```bash
cd android_app
./gradlew.bat help
```

The Gradle task completed successfully.
