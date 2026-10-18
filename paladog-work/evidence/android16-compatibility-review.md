# Android 16 / modern-target compatibility review

Read-only audit of `final-jadx` and `patched`, focused on the core game, activity/window behavior and SDK targeting. This does not substitute for a run on the actual Galaxy device or a modern Android emulator.

## Recommendation

Use **targetSdkVersion 28** for the compatibility build, while retaining the existing minimum SDK unless an independent runtime/API change requires raising it. API 24 is the documented Android 15 installation floor; API 28 is a compatibility choice, **not** an Android 16 or 64-bit requirement. It preserves pre-target-35 window behavior while applying a reasonable explicit target above the installation floor. Current `patched/apktool.yml` only specifies minSdkVersion 7 and does not specify a target: that effectively remains target 7 unless the rebuild is given an explicit target.

Target SDK and ABI are independent. Merely raising targetSdkVersion cannot turn ARM32 native libraries into ARM64 libraries. If the separate native-dependency audit permits removing all APK native libraries and their reachable use, the Java/Dalvik game code is architecture neutral and there is no application ELF page-size alignment requirement. Do not add an Android 16 `pageSizeCompat` flag to a no-native-library package; it solves a different issue.

Suggested narrow manifest additions for the game activity:

```xml
<activity
    android:name=".Paladog"
    android:screenOrientation="landscape"
    android:resizeableActivity="false"
    android:configChanges="keyboardHidden|orientation|screenLayout|screenSize|smallestScreenSize"
    ... />
```

Under application, add the remaining Apache class dependency explicitly for target 28 and above:

```xml
<uses-library android:name="org.apache.http.legacy" android:required="false" />
```

`android:appCategory="game"` on the application is accurate optional metadata. It also identifies the app as a game for documented Android 16 large-screen orientation exceptions if a later version targets 36. It is not required to launch this target-28 build.

Do not add an arbitrary `maxAspectRatio` cap if the requested result is use of the full available landscape viewport: the documented result of a cap is letterboxing on wider devices. Do not force cutout rendering to `shortEdges` without testing edge controls. Retaining the existing fullscreen theme/flag while letting the window reserve system/cutout-safe bounds is the minimal change. It is distinct from fully immersive hidden navigation bars, which this game does not currently implement.

## Code evidence and concrete risks

### Window and recreation

- `Paladog.java:134–150` constructs the custom GL SurfaceView, sets `FLAG_FULLSCREEN` (1024), `FLAG_KEEP_SCREEN_ON` (128), selects sensor landscape and then calls `CCDirector.setDeviceOrientation(2)`, which itself selects fixed landscape.
- `CCDirector.java:394–444` takes initial dimensions from `Display.getWidth/getHeight`, stores a static singleton, and attaches its custom renderer.
- `AppDelegate.java:7415–7422` calculates a fixed logical 480x320 game coordinate space and scale factors from dimensions captured by Paladog.onCreate.
- `CCDirector.onSurfaceChanged` updates physical surfaceSize and glViewport, but does not rebuild the app singleton or reinitialize all original display values.
- `Paladog.onConfigurationChanged` only calls super. The original manifest declares only keyboardHidden|orientation. At target >=13 the framework tracks screenSize and screenLayout changes separately; without the added entries, a rotation/size change can recreate the activity around static singleton scene state. Preserving activity handling avoids introducing that regression when raising target.
- `resizeableActivity=false` expresses that the game has not been adapted for freeform resizing. It is not a guarantee against every OEM or large-screen compatibility window. Device/emulator testing remains necessary, especially DeX/multiwindow.

### Why target 35/36 is additional work

- No WindowInsets, setFitsSystemWindows, cutout-aware coordinate correction or system UI visibility management exists in the core game paths.
- Target 35 triggers enforced edge-to-edge on Android 15+. The fixed 480x320 UI and its display-size-to-surface touch conversion currently assume the usable SurfaceView area; drawing beneath gesture/status/cutout areas can obscure controls. This is a UI compatibility risk, not evidence of a guaranteed launch crash.
- Target 36 adds predictive-back behavior where onBackPressed is no longer called unless opted out or migrated. This game handles all back behavior through `Paladog.onBackPressed` and scene `onMyHardKeyPressed`, so blindly selecting target36 would change exit/pause behavior. Explicit `enableOnBackInvokedCallback=false` is a documented temporary option if target36 is later chosen; it is unnecessary for target28.
- Target36's default large-screen resizability rules have documented exceptions for games and for displays smaller than sw600dp. A regular phone and a large-screen/DeX session should not be conflated.

### Actual Java/library compatibility

- The core Cocos renderer is Java and uses public EGL10/GL10/GL11ExtensionPack bindings. Searches found no native method or loadLibrary in `com/Paladog`, `com/common`, or `org/cocos2d`. The third-party AppleJuice/Kakao trees do still contain native methods/loading code in `final-jadx`; the independent removal/reachability audit must cover those before claiming a no-native build.
- `GLSurfaceView` is the bundled legacy custom SurfaceView implementation, not framework GLSurfaceView. It creates an EGL context with no explicit client-version attributes when the field is zero, i.e. the legacy GLES path. It must be tested on the modern GPU/emulator; changing target alone does not modernize this renderer.
- `ServerManagedPolicy` is instantiated on the startup Google_LVL path and retains `org.apache.http.NameValuePair` / `org.apache.http.client.utils.URLEncodedUtils` references in `decodeExtras`. The existing always-true input policy means a remote response path is not normally exercised, but the class dependency remains. The optional library declaration is the supported target-28 solution and adds no INTERNET permission.
- `AESObfuscator` uses `SecretKeyFactory.getInstance("PBEWITHSHAAND256BITAES-CBC-BC")` and standard AES/CBC/PKCS5Padding without explicitly selecting a provider. No evidence in this review establishes that this algorithm fails on Android16; include first-run startup in the modern runtime check because construction throws RuntimeException on a missing algorithm/provider.
- Core local storage uses `getSharedPreferences(name,0)` and `openFileOutput(name,0)` in AppDelegate and Library. There is no MODE_WORLD_READABLE/WRITEABLE path here, so target24+ world-readable restrictions do not require restoring storage permissions. Scoped storage is likewise irrelevant to these internal game saves.
- Core phone/account/network accessor removal is present in the final decompile. The retained runtime service is Vibrator in SoundManager, backed by the retained normal VIBRATE permission. The target23 runtime-dangerous-permission model does not create a new permission dialog requirement for the patched core.
- `startActivity` restrictions still permit explicit local MessageBoxActivity/InputActivity intents; public APIs in this path have not been removed just because the target is newer.

## Focused modern-runtime validation

1. Install without a low-target bypass and verify reported target=28, no application native ABI/library binding, app UID permissions and local startup.
2. Check rendered and touch bounds on a modern tall/wide phone viewport and with gesture navigation; test landscape flips, background/foreground and local message-dialog return.
3. Exercise existing back navigation, save/reload, campaign and offline survival on the new target. The earlier runtime results for the old target do not automatically prove this target's lifecycle behavior.

## Authoritative references checked during this review

- Android 15 changes for all apps: https://developer.android.com/about/versions/15/behavior-changes-all — explicitly blocks installation below target24 and gives `INSTALL_FAILED_DEPRECATED_SDK_VERSION` example for target7.
- Android 15 target-specific changes: https://developer.android.com/about/versions/15/behavior-changes-15 — edge-to-edge enforced for target35 and requires inset handling.
- Android 16 target-specific changes: https://developer.android.com/about/versions/16/behavior-changes-16 — predictive-back migration/opt-out, edge-to-edge opt-out removal for target36, and large-screen game exceptions.
- Android 16 all-app changes: https://developer.android.com/about/versions/16/behavior-changes-all — ELF page-size compatibility behavior; does not make target level an ABI conversion.
- Android 9 target28 changes: https://developer.android.com/about/versions/pie/android-9.0-changes-28 — supported optional org.apache.http.legacy declaration; required=false needed when minimum SDK <=23.
- Activity manifest documentation: https://developer.android.com/guide/topics/manifest/activity-element — screenLayout/screenSize configuration entries, resizeability limitations and maxAspectRatio letterboxing.
