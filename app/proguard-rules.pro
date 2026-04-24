# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.HiltWrapper_ActivityRetainedComponentManager_LifecycleModule

# Keep Room entities by default
-keep class lk.salli.data.db.entities.** { *; }

# Compose keeps
-dontwarn kotlinx.serialization.**
