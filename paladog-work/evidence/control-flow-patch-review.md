# Independent control-flow patch review

Reviewed actual `/workspace/paladog-work/patched` Smali and `privacy.patch` against the original decoded tree. Read-only review; no patch source edits by this reviewer.

## Substantive issue found and resolved

The first patch version called `Paladog.ToastActivity` for the offline purchase notification. Despite the name, that method starts `MessageBox3Activity`; the input and patched manifests do not register that activity. The new offline message would therefore throw `ActivityNotFoundException` when reached. The parent agent corrected `BuyCashItem` and `ServerSend_ErrorMessageBoxActivity` to call the already registered `MessageBoxActivity(title,msg)`, whose request code 14 has nonfatal existing completion behavior. Original dormant ToastActivity remains unchanged. The live corrected file was inspected after this change.

## Reviewed paths without additional substantive issues

- `MainMenuLayer.TouchEndUIProc` maps controls 131, 160 and 161 to 162 before the existing switch. Control 162 repeats both original survival availability predicates (`iAllowSurvival` and Darkdog survival availability), so this does not unlock content. It shows the original offline-mode dialog. Its existing OK handler, control 168 with `m_iMsgBoxKind=2`, calls both `ReadyForSurvival()` and `StartSurvivalOffline()`. This preserves save-slot selection/initialization, local state and offline scene target 4 while skipping the Facebook/email choice screen.
- `MainMenuProc` has exactly one `return-void`, and the new hide helper is inserted there. `HideOnlineControls` checks both the layer deallocation flag and shared UI manager presence. Deallocation sets that flag before resetting/replacing shared scene controls, avoiding accidental hiding of a new scene's unrelated controls with the same numeric IDs.
- The target controls with actual widgets (YouTube 50, Facebook login/logout 91/92, online survival login 160/161) have no disabled-state texture. `ChangeState(2)` hides their sprites and `CheckTouchBegin/End` rejects state 2. The remaining QQ IDs do not introduce side effects when absent because `ChangeStateByID` checks for a matching UI object.
- Loading mode 30–37 replacement is the reviewed range-normalization block. It physically removes the SMS text, Naver agreement widget construction, and both registration request paths. State 26 still initializes local game data and shared dialogs, detects interrupted battle saves, verifies DB checksum and runs the original local licensing callback.
- `Google_LVL` uses a fixed synthetic value for the preference obfuscator's device-id input. The original callback and already-always-true input policy are retained, so `iCalcOne/iCalc[]` initialization remains available to the main-menu integrity check.
- The `Paladog.startActivity` and `startActivityForResult` overrides permit explicit components in the app's own package. Existing registered MessageBoxActivity and InputActivity calls use that form and still launch. Original implicit browser/store intents lack such a component and are dropped. The subclass overrides call the Activity superclass and do not recurse into themselves.
- Billing bind removal sets `mServiceConn=null`, matching the existing `onDestroy` null guard and preventing an unbound-service exception.

## Scope and remaining validation

No further static blocker was identified in the requested loading, menu, local survival or activity-dispatch changes after the message-path correction. This is a static review, not proof of Android runtime verification. Runtime checks should cover first-run no-consent startup, offline survival entry and OK, battle-resume choice, message dialog return, gameplay and save/reload. Broad network/SDK call reachability and full application security are outside this focused review.
