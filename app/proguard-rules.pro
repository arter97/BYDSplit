# R8 repackages some Kotlin multifile facade parts (e.g. CollectionsKt__IteratorsKt)
# out of kotlin.collections while their package-private superclass stays behind,
# which crashes with IllegalAccessError at class load. Keep Kotlin's packages intact.
-keeppackagenames kotlin.**

# Shizuku.newProcess() is private and called via reflection from SplitService
-keepclassmembers class rikka.shizuku.Shizuku {
    private static *** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
