# SpineViewer for Android

A file manager and animation previewer for [Spine](https://esotericsoftware.com/) skeleton files on Android.

## Features

- **Browse folders** using Android Storage Access Framework (no root needed)
- **Auto-detects Spine version** from `.json` or `.skel` file headers (supports 1.6 → 4.3)
- **Manual version override** — switch runtime in-app when auto-detection fails or is unavailable (e.g. Spine 1.6/1.7 store no version string)
- **Interactive preview** with pinch-to-zoom and pan gestures
- **Animation selector** with prev/next navigation and play/pause
- **Skin switcher**, including multi-skin combination where the runtime supports it
- **Animation speed control** (0.1× – 2.0×)
- **Debug bone overlay** toggle
- **Error recovery** — if a runtime fails to load (e.g. wrong version, missing atlas, unsupported binary format on older runtimes), prompts you to try another version

## Supported Spine Versions

| Runtime | Notes |
|---------|-------|
| 1.6     | No version string in file; binary (`.skel`) supported |
| 1.7     | No version string in file; binary (`.skel`) supported |
| 2.0     | Version string introduced; binary supported |
| 2.1     | JSON only — no `SkeletonBinary` in this runtime |
| 3.1     | JSON only — no `SkeletonBinary` in this runtime |
| 3.4     | Binary supported |
| 3.5     | Binary supported |
| 3.6     | Binary supported |
| 3.7     | Binary supported |
| 3.8     | Binary supported |
| 4.0     | Binary supported |
| 4.1     | Binary supported |
| 4.2     | Physics constraints (`Skeleton.Physics`) |
| 4.3     | Physics constraints (top-level `Physics`), renamed pose APIs (`setupPose`/`setupPoseSlots`) |

Versions 2.1 and 3.1 only support the JSON skeleton format in their original runtime; opening a `.skel` file with those versions selected will prompt you to pick a different runtime.

## How to Use

1. Tap **+** → select a folder containing `.json` or `.skel` files
2. The app scans for skeleton files and automatically pairs them with `.atlas` files
3. Detected version is shown on each card
4. Tap a card to open the preview
5. In preview, tap the version badge to manually switch runtime if needed

## File Layout

Each skeleton needs:
```
my_animation/
├── skeleton.json   (or skeleton.skel)
├── skeleton.atlas
└── skeleton.png    (texture image referenced by the atlas)
```

## Building

Requirements: Android Studio (current stable), JDK 17, Android SDK 35.

```bash
# In Android Studio: File > Open > SpineViewer
# Or on command line (after setting sdk.dir in local.properties):
./gradlew assembleDebug
```

Gradle version: **8.13**
AGP version: **8.7.3**
libGDX version: **1.13.0**
Min SDK: **24** (Android 7.0)
Target SDK: **35**

## Architecture

```
app/src/main/java/com/spineviewer/
├── spine/
│   ├── SpineVersion.java          — enum: V1_6 … V4_3 (14 versions)
│   ├── SpineFileDetector.java     — reads version from JSON/binary header
│   ├── SpineFileInfo.java         — metadata for a skeleton + atlas pair
│   ├── SpineViewerEngine.java     — abstract base (ApplicationListener)
│   ├── SpineEngineFactory.java    — creates the right engine per version
│   ├── engines/
│   │   ├── AbstractSpineEngine.java  — shared libGDX setup, camera, file copy
│   │   ├── SpineEngine16.java        — Spine 1.6 runtime
│   │   ├── SpineEngine17.java        — Spine 1.7 runtime
│   │   ├── SpineEngine20.java        — Spine 2.0 runtime
│   │   ├── SpineEngine21.java        — Spine 2.1 runtime (JSON only)
│   │   ├── SpineEngine31.java        — Spine 3.1 runtime (JSON only)
│   │   ├── SpineEngine34.java        — Spine 3.4 runtime
│   │   ├── SpineEngine35.java        — Spine 3.5 runtime
│   │   ├── SpineEngine36.java        — Spine 3.6 runtime
│   │   ├── SpineEngine37.java        — Spine 3.7 runtime
│   │   ├── SpineEngine38.java        — Spine 3.8 runtime
│   │   ├── SpineEngine40.java        — Spine 4.0 runtime
│   │   ├── SpineEngine41.java        — Spine 4.1 runtime
│   │   ├── SpineEngine42.java        — Spine 4.2 runtime (Physics)
│   │   └── SpineEngine43.java        — Spine 4.3 runtime (Physics + renamed pose APIs)
│   └── runtime/
│       ├── v16/ … v43/            — verbatim spine-libgdx sources, re-packaged per version
├── ui/
│   ├── MainActivity.java          — folder browser + file list
│   ├── SpineFileAdapter.java      — RecyclerView adapter
│   └── SpinePreviewActivity.java  — GL preview (extends AndroidApplication)
└── utils/
    └── FileScanner.java           — SAF tree scanner, pairs skel+atlas files
```

## License

The bundled Spine Runtimes are subject to the [Spine Runtimes License Agreement](https://esotericsoftware.com/spine-runtimes-license).
This viewer app code is provided as-is for personal/development use.
