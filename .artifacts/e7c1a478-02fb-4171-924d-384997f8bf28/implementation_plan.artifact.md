# Fix Duplicate Dex Type Error for Lifecycle

The project is failing to build with the error `Type androidx.lifecycle.lifecycle.viewmodel.anchor.R is defined multiple times`. This is a known issue in some versions of the `androidx.lifecycle` 2.8.x library where duplicate resource classes are generated during the Dex merge process.

## Proposed Changes

### Build Configuration

#### [MODIFY] [app/build.gradle.kts](file:///D:/stitch_smart_utility_complaint_system/android_app/app/build.gradle.kts)
- Update `androidx.lifecycle` dependencies to `2.8.7`.
- Update `androidx.navigation` dependencies to `2.8.7` for compatibility.
- Update `androidx.activity` and `androidx.fragment` to compatible versions (`1.9.3` and `1.8.5` respectively).
- Aligning these versions ensures that transitive dependencies don't pull in conflicting versions of the Lifecycle library.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify that the Dex merge error is resolved and the project builds successfully.
