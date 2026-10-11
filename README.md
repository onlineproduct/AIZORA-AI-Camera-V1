# AIZORA AI Camera V1.0 (starter)

Native Android camera starter. Phone preview, photo capture, video recording, front/back flip, and gallery saving through MediaStore. Android 8+ (API 26+). Android 10+ is recommended for scoped-storage gallery saving; Android 8/9 may require additional storage permission handling. The name "AI" is branding only in V1; AI detection, PTZ/ONVIF, external cameras, and Facebook Live are not included.

## Build
Upload the *contents* of this folder to the root of a GitHub repository on main. GitHub Actions -> Build Android APK -> latest successful run -> Artifacts -> AIZORA-AI-Camera-V1-debug. The artifact is a ZIP containing app-debug.apk.

The workflow uses preinstalled GitHub runner Android SDK (no setup-android action) and Gradle 8.9. Requires Android SDK platform 35 installed on the runner or automatically installable by Gradle. No build has been run in this environment; compilation and device functionality must be verified through GitHub Actions and a real device.

Do not overwrite your existing repository until you have backed it up; using a fresh repository is safest.
