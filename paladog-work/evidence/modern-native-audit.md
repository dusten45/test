# Native dependency audit for the modern offline build

This is an independent static review of the original APK, the first privacy build, their Apktool Smali, and `final-jadx/sources`. The attached `Paladog_deep_audit_and_64bit_feasibility.md` was treated as a source of claims to check, not instructions or proof. No APK was executed during this audit. The subsequent modern-build static result is recorded in `modern-static-validation.json`; device execution evidence belongs in a separate runtime report.

## Inputs and independently confirmed inventory

- Original APK: `input/original.apk`, SHA-256 `db9d5c399d7f436a27dba28eebd66771fdc29c812c0e06008c67409bd595a6e1`.
- The first privacy APK audited here had SHA-256 `4f95219cc9ed6b33aa3680be24ca463717df190ed2a3ae1b926cb6c2be1226bc`. Later privacy rebuilds have their own hashes. The dependency conclusions were based on `verified-decode/smali`, not a filename alone.
- Both original and privacy Smali trees contain 2,104 classes and 327 native method declarations. These are AppleJuice: 321 declarations in 147 classes; Kakao: four in one class; Tenpay: two in one class. No native declaration occurs in the game, save, Cocos2d, Google licensing, or other remaining packages.
- All APK members were decompressed and their file headers inspected. The only ELF files are the four below; every one has ELF class 1 and machine 40, meaning 32-bit ARM. They were unchanged in the first privacy APK. No other ELF, nested ZIP/JAR/APK, secondary DEX, PE, Mach-O, Java class file, or shell-script header was found. `classes.dex` is the sole DEX. This is a header-based inventory; it does not prove that arbitrary opaque data could never encode another payload.

| APK member | Bytes | SHA-256 |
|---|---:|---|
| `lib/armeabi/libAppleJuice.so` | 428332 | `20df941fcc10fdb42adf8614fed716b2332152d5ebe706a460ad8ae586fe98d7` |
| `lib/armeabi/libdes3jni.so` | 18740 | `914dd60d795e89a1eff5ffa8720aa443edb4ef8a495f3996382ab7250ab199e6` |
| `lib/armeabi/libsimpleencryption.so` | 156236 | `aff37c2e01059c3e393f0648e22820ec58530c7594c5bc4fbcdd3db82757997f` |
| `lib/armeabi-v7a/libsimpleencryption.so` | 152148 | `3bef7242f234a1571d4f0795d168d609ed4065c78087c071aa391b8de6cade15` |

`readelf --dyn-syms --wide` independently exposes JNI names for AppleJuice protocol/authentication, `com.kakao.api.util.SimpleEncryption`, and `com.tenpay.android.des3.DES3`. The reviewed declaration and loading boundaries place these libraries in SDK functionality, not native game rendering or simulation.

## Native loading and actual package boundaries

Exactly three explicit native library load instructions exist. No `Runtime.load`, `Runtime.loadLibrary`, `System.load` or extra `System.loadLibrary` call was found.

| Location in `verified-decode/smali` | Loader | Caller boundary |
|---|---|---|
| `com/appleJuice/AppleJuice.smali:682` | `AppleJuice.Initialized(Context)` loads `AppleJuice` | Called by SDK `AppleJuiceService.Initialize(Context)`; no game-side reference to either initializer |
| `com/kakao/api/util/SimpleEncryption.smali:14` | Static initializer loads `simpleencryption` | Referenced internally by Kakao `SharedUtil`; no external package descriptor reference into Kakao |
| `com/tenpay/android/des3/DES3.smali:24` | Static initializer loads `des3jni` | Constructed by ApplePay `APPayCFTSaveQbqdWebActivity`; no game-side reference |

The review scanned **every non-SDK Smali file**, not just the main activity, for class descriptors into the SDK namespaces. There is exactly one inbound descriptor reference: `com/SPC/TestAJService.smali:131`, inside `OpenLogin()`, calls `AJAuthService.LaunchAuthView(long,int)`. This dormant helper still exists in the original privacy patch. Searching all other classes finds no caller, constructor, field, type, or string reference to `TestAJService`. Thus it can be removed together with the SDK island. Alternatively its login method could be stubbed before retaining the class, but the modern build removes the unused class.

`com/my/Layer/TencentProcLayer` is game code and stays. It calls `Paladog.AppleJuiceActivity()`, whose body is already just `return-void`; `AJViewProc(float)` and `AJHonorProc(float)` are likewise empty. The game scheduling string `TencentProcProc` refers to this retained game class. Its name does not imply a native Tencent dependency.

No non-SDK class contains a literal fully qualified SDK class name with dotted or slash separators. No decoded XML/YML file, including the manifest and resource layouts, refers to these SDK classes or TestAJService. The privacy manifest registers only Paladog and two local dialog activities; no custom Application, SDK component, provider, or startup receiver is present.

## Exact deletion boundary

The following namespace directories and one class comprise a closed SDK dependency island with the single dormant bridge removed. Counts include nested classes.

| Path under `smali/` | Classes |
|---|---:|
| `com/appleJuice/` | 643 |
| `com/applePay/` | 111 |
| `comm/applePay/` | 1 |
| `com/kakao/` | 93 |
| `com/tenpay/` | 1 |
| `oicq/` | 78 |
| `com/SPC/TestAJService.smali` | 1 |
| Total removed | 928 |
| Total retained | 1176 |

There is no `com/tencent/` package in this APK. No `org/` package is in the removal set. Cocos2d, Apache Commons, Android support, Facebook, Google licensing/billing classes, and all game/save implementation classes remain. Retaining a Java SDK class elsewhere does not itself introduce an application-native ABI requirement.

Deleting all four `.so` files **together with this dependency island** is statically supported: it removes every app-native declaration and every native load site. The first modern build preserved all 1,176 retained Smali files; the final build preserves 1,175 and adds one focused text-fit check in `CCTexture2D.WrapText`, described in `modern-text-fit-review.md`. Deleting only the libraries and keeping live loaders would not be equivalent: accidental entry into a loader could throw `UnsatisfiedLinkError`. The modern preparation and post-build verifier reject any remaining descriptor or class-name literal referencing a deleted class.

## Game and save implementation evidence

JADX shows actual Java bodies in `com/my/Game/GameManager.java:1693` (`GameStepProc`), `com/my/Layer/GameLayer.java:1017` (`GameProc`), and `org/cocos2d/nodes/CCDirector.java:294` (`onDrawFrame`). The renderer uses Android's OpenGL ES interfaces; platform-native implementation is supplied by the device and is not an APK-owned ARM library. `Library` creates `WCEncrypt` at lines 496–497 and uses Android SharedPreferences for saves. Neither class refers to the deleted SDK island. All retained game logic, save serializers, encryption and existing licensing policy are copied unchanged from the privacy baseline into the modern variant.

## Reflection, execution and limits

The APK uses reflection: Cocos2d schedules Java methods by strings and invokes them reflectively; Base64 helpers can deserialize classes through `Class.forName`; Android support uses framework reflection; SDK serialization and callbacks have their own reflection. No `DexClassLoader`, `PathClassLoader`, `DexFile.loadDex`, downloaded-code execution path or class-name literal leading from the retained application into the removed SDKs was found. This is not a formal proof covering every dynamically constructed name or external OS callback.

The remaining `Runtime.exec` instruction is Apache Commons `FileSystemUtils.openProcess(String[])`, a generic operating-system disk-space helper. It is not an app-library loader and does not name a bundled executable. It was preserved rather than deleting unrelated library implementation.

The inspected APK therefore has no identified native game dependency, and an APK without these SDKs and four libraries is a credible ABI-independent Java build. This does **not** establish Android 16 / Galaxy S26 runtime compatibility by itself. Installation policy, target-SDK behavior, renderer compatibility, audio, touch, lifecycle, save/reload, and every game mode still require execution testing. A successful x86_64-only runtime test would demonstrate the lack of an ARM32 execution requirement on that runtime; it would not substitute for testing the actual handset/GPU.

## Built modern artifact

`output/Paladog-2.2.0-modern-offline.apk` was built and independently decoded. Its final SHA-256 is `c8211db68b9b093da2ae9a349365063241fd18da286738c563b081fc24c15bfe`, size 40,227,841 bytes. `modern-static-validation.json` records 1,207 passed checks, including source hashes for all 1,176 retained classes, 1,175 byte-identical baseline classes, and exact guards for the sole text-renderer change. Built DEX instructions, registers, operands and branch-target relationships match the prepared sources; normalization accounts for assembler label renumbering, Unicode escaping, debug metadata and method ordering. The built APK contains zero native declarations, zero native loads and zero ELF files. All 4,763 retained original APK members outside the replaced manifest/DEX/signatures are byte-identical. V1/V2 signatures and ZIP alignment verify, and the signer certificate matches the previous privacy build. The earlier fully tested `44d1885f...55d5c2` build and its evidence are retained in `pre-text-fit-fix/`; its gameplay evidence remains historical evidence for that hash. These are build/static results only; focused runtime regression results for the final hash are reported separately.
