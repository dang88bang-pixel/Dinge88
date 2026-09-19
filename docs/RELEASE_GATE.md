# Release Gate
A release is considered complete only after:
1. ./gradlew test succeeds.
2. ./gradlew lint succeeds.
3. ./gradlew assembleDebug succeeds.
4. ./gradlew assembleRelease succeeds with the real release keystore.
5. apksigner verify --verbose succeeds.
6. Android 11 API 30 instrumentation/smoke tests succeed.
7. All 22 routes are reachable and their visible controls dispatch to real application logic.
8. No fabricated GNSS/satellite/location/RSSI/battery telemetry is present.
9. console3d is built from source and its measured bundle size is recorded.
10. GitHub Actions artifacts contain the verified APK and SHA-256 checksum.
Current branch must not be described as release-complete until all gates above are green.
