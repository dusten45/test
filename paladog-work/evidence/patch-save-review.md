# Patched-tree save and lifecycle review

Read-only comparison of `decoded` against `patched`, followed by targeted inspection of the current patch. No application code was changed by this reviewer.

## Preservation verified

SHA-256 comparisons of files in both decoded trees found exactly five original files changed: `AndroidManifest.xml`, `Paladog.smali`, `LoadingLayer.smali`, `Library.smali`, and `MainMenuLayer.smali`. Additional files under `patched/build/` are generated build products.

The following original groups remain byte-for-byte identical:

| Group | Files |
|---|---:|
| assets | 4,579 |
| native libraries | 4 |
| resources | 189 |
| com/my/Struct classes, including all save serializers | 17 |
| WCEncrypt | 1 |
| AppDelegate | 1 |

The only Library changes are the HTTP wrappers, network status helper, and root-detection helper. Its local file encoding, checksum, buffer parsing, and private storage routines remain intact. No gameplay, character, battle, item, DB, save-format, numeric-checksum, or native-library edits were found in this comparison. This confirms preservation of those files, not working gameplay on Android.

## Identifier behavior

Patched `Paladog.onCreate` sets `ANDROIDSAVEINFO.strUDID` to an interned empty string before `LoadSavedAndoridInfoFromFile`, `LoadSavedInfoFromFile`, and `LoadLocalRankFromFile`. The original null normalization also assigns the empty literal. These are the only two DEX field writes found for that specific global ID. The unchanged Android-info loader only updates `iReal_DownRes`, so the empty global ID persists through save loading.

All five unchanged save loaders still have the explicit global-ID-is-empty branch that clears ID mismatch flags. Their substring parser permits empty fields. Encryption uses constant keys and does not derive a key from the global ID. No field count, delimiter, encryption, or checksum change was introduced.

The phone field is also initialized empty and both original fake-number fallbacks have been changed to empty. Local Build.MODEL handling was retained, preserving its existing old-device compatibility checks.

## Permissions and local storage

The manifest retains only VIBRATE. Private SharedPreferences and `Context.openFileOutput/openFileInput` do not require READ/WRITE_EXTERNAL_STORAGE, so removal of that permission does not directly block the observed save implementation. The observed screenshot and social-avatar paths also point inside the application data directory, although social features are disabled.

For this version, current progress and battle saves specifically live in `shared_prefs/PDWXMLInfo.xml`, `shared_prefs/PDWXMLSlot0.xml` through `PDWXMLSlot7.xml`, and `shared_prefs/PDWXMLBattle.xml` beneath `/data/data/com.Paladog.KorGG/`. The current writers use SharedPreferences. The binary `.sav` files under `files/` are legacy formats, so `files/` may remain empty while saving works. The runtime operator subsequently confirmed Info, all eight Slot XML files, and a roughly 13 KB Battle XML file after quitting a running campaign battle; restart verification is a separate runtime check.

The core game/Cocos2d code had no remaining direct ConnectivityManager, WifiManager, or PowerManager wake-lock calls in the searched trees. `Library.networkCheck` now returns false, avoiding its previous ACCESS_NETWORK_STATE call. Setting window flag 128 (keep screen on) in onCreate does not itself require WAKE_LOCK.

SDK classes containing personal/network APIs remain in the DEX, so this is not code eradication of every such API. The stripped manifest, disabled entry points, removed permissions, and other patch review findings must be described accurately. Manifest permissions are effective on a normal Android sandbox; behavior in a third-party virtualization layer remains untested.

## Billing and lifecycle

The patched startup removes billing bindService and assigns `mServiceConn = null`. The unchanged onDestroy checks that field before calling unbindService, so the normal patched lifecycle will not unbind an unregistered connection. No later write that makes the field non-null was found; its earlier constructor assignment is overwritten during onCreate.

`BuyItemActivity` initially sets buying=true and purchaseOK=false, then synchronously calls the patched `BuyCashItem`. That stub resets both fields to false before showing the disabled-feature message. It does not award goods, mutate saved balances, or report a successful purchase. No current access to a null `mService` remains in this path.

## Crash finding and correction checked

The first patch version made BuyCashItem and ServerSend_ErrorMessageBoxActivity call the existing `ToastActivity` helper. Despite its name, that helper starts MessageBox3Activity. MessageBox3Activity was undeclared even in the original manifest and changes game state in its dialog callback, so the newly unconditional path would cause ActivityNotFoundException.

This was reported immediately. The current patched file instead calls the registered `MessageBoxActivity(title, message)` helper from both stubs. I checked that this helper sends the expected MESSAGE extra, launches the declared private MessageBoxActivity, and uses request code 14, which does not enter the purchase-result branch (1013). This resolves the newly introduced missing-activity issue by static inspection.

Existing MessageBox2Activity and MessageBox3Activity helpers and some callers remain in original code. They were already absent from the original manifest. Their possible reachability from retained game dialogs remains a pre-existing functional risk, not established by this review as a new save/privacy-patch regression.

`HideOnlineControls` safely checks the UI manager and the layer teardown flag. `UIManager.ChangeStateByID` checks a missing UI object before changing state, so hiding IDs absent in a particular menu does not itself dereference null.

## Limits

No APK execution, Android verifier run, live save round-trip, purchase-button tap, campaign/survival/Darkdog playthrough, or save migration was performed by this reviewer. No additional concrete save/lifecycle regression was found in static inspection after the message helper correction. Other agents are reviewing startup/network/menu state transitions and artifact validation.
