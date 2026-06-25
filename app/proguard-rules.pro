# Spine runtime - keep all classes for all versions
-keep class com.spineviewer.spine.runtime.** { *; }
-keep class com.spineviewer.spine.engines.** { *; }
-keep class com.spineviewer.spine.SpineVersion { *; }
-keep class com.spineviewer.spine.SpineViewerEngine { *; }

# libGDX
-keep class com.badlogic.gdx.** { *; }
-keep class com.badlogic.gdx.backends.android.** { *; }
-dontwarn com.badlogic.**

# Keep esotericsoftware JSON utils if used by spine
-keep class com.esotericsoftware.** { *; }
-dontwarn com.esotericsoftware.**

# Additional spine runtime versions (1.6 through 3.4)
-keep class com.spineviewer.spine.runtime.v16.** { *; }
-keep class com.spineviewer.spine.runtime.v17.** { *; }
-keep class com.spineviewer.spine.runtime.v20.** { *; }
-keep class com.spineviewer.spine.runtime.v21.** { *; }
-keep class com.spineviewer.spine.runtime.v31.** { *; }
-keep class com.spineviewer.spine.runtime.v34.** { *; }
