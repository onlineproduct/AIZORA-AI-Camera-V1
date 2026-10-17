# AIZORA AI Camera V2.0 — RTSP Viewer

This branch is a clean V2 rebuild focused on viewing network CCTV streams with LibVLC.

## Features in this starter
- RTSP URL input (defaults to the previously tested local URL)
- Connect and Stop controls
- Fullscreen toggle
- Playback status and basic stream error message
- Keeps the screen awake while the app is open

## Build APK
GitHub Actions workflow: `.github/workflows/build-apk.yml`
It builds a debug APK on pushes to `v2.0-clean-rebuild` and uploads it as an artifact.

## Important status
The source changes have been committed, but successful compilation and real-camera playback must still be verified by the Actions run and on an Android device. The default RTSP address only works when the phone can reach that camera on the same network.

The original `main` branch remains untouched by the V2 work.