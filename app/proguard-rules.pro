# App-specific R8 rules for Chiaroscuro release builds.
#
# Notes:
#   - AndroidX libraries (Compose, Lifecycle, Navigation, DataStore, ...)
#     ship their own consumer-proguard-rules.pro embedded in each AAR.
#     R8 picks these up automatically. Do not duplicate them here.
#   - The previous `-keep class androidx.compose.** { *; }` rule has been
#     removed: it disabled shrinking, obfuscation and optimization for
#     the entire Compose runtime (~85% of DEX input was untouched by R8).
#   - Add rules here only for code that R8 cannot statically prove is
#     reachable (reflection, JNI, kotlinx.serialization custom serializers,
#     etc.). None of this applies to the current codebase.