# Development notes

- Build: `./gradlew buildAndroid` (output: `patches/build/libs/patches-*.mpp`). This also runs the Android API check.
- Use JDK 17 for Gradle (`JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2`). JDK 27 fails in the Android `jlink` step.
- The Morphe Gradle plugin needs `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties` (or `GITHUB_ACTOR`/`GITHUB_TOKEN`).
- Test a build by patching with Morphe Desktop (needs Java 21+):
  `java -jar morphe-desktop-*-all.jar patch -p patches/build/libs/patches-*.mpp [-p other.mpp] instagram.apkm`
- Extension methods documented as "replaced at patch time" are stubs. `extensions/proguard-rules.pro` must keep
  `-dontoptimize` so they are not inlined.
- Patches must not depend on other bundles (such as Piko): hook with fingerprints that still match after other
  bundles have patched the same methods.
