# Focused text-rendering fix for the modern APK

## Observed problem and cause

The earlier modern APK (`44d1885f7a37bc38acd895a6a793924afc239f3cd56ad3f09f57e697ad55d5c2`) rendered the quit-dialog body with cropped line fragments on Android 16. Evidence is `runtime/final-api36/11-final-quit-dialog.png`. This happened after successful 64-bit gameplay/save validation; it was a text-display problem, not a failed native-library conversion or touch-coordinate problem.

`CCLabel.makeLabel` creates an automatically sized label. `CCTexture2D.calculateTextSize` uses `Paint.measureText` on the whole string and allocates one line of height. `CCTexture2D.initWithText` then calls `WrapText`, which estimates line width by adding individual word and space widths. Whole-string font shaping and summed word measurements can differ, causing a string already measured to fit to wrap into two lines inside a one-line bitmap. The screenshot's clipped glyph fragments and this code path motivated the focused fix. Letterboxing cannot reliably repair pixels already clipped when that bitmap was generated.

## Exact modern-only change

Only the beginning of `org/cocos2d/opengl/CCTexture2D.WrapText(Paint,String,float)` changes:

```java
if (textPaint.measureText(text) <= width) {
    ArrayList<String> lines = new ArrayList<>();
    lines.add(text);
    return lines;
}
// Original word-wrapping implementation follows unchanged.
```

The Smali uses `cmpg-float` followed by `if-gtz` to select the existing fallback. Equality belongs to the fitting branch; an unordered comparison follows the original algorithm. The returned text is the original string, including its existing spacing. No font choice, glyph size, aspect ratio, viewport, touch mapping, gameplay, payment, licensing, or save behavior changes. The ordinary privacy APK is untouched.

## Build and static verification

- Final APK: `output/Paladog-2.2.0-modern-offline.apk`.
- SHA-256: `c8211db68b9b093da2ae9a349365063241fd18da286738c563b081fc24c15bfe`.
- Size: 40,227,841 bytes. Version code remains 26 and the private signer is unchanged, supporting an in-place update of the previous modern build.
- 1,175 retained classes remain byte-identical to the reviewed privacy source. Only `CCTexture2D` changes; stripping the exact added prefix recreates its complete baseline source, including the entire fallback and every other method.
- The post-build decode verifies all opcodes, registers, operands and branch targets against the prepared source, with normalization only for label names, method ordering and debug/Unicode representation.
- Focused guards verify whole-string measurement, `<=` behavior, singleton original-text return, and branch-to-unchanged-fallback for longer text.
- Comparing final APK ZIP contents to the prior `44d...` build finds `classes.dex` as the only changed member outside signature metadata. Manifest, version, resources, assets, permissions and ABI inventory remain the same.
- The full existing static verification records 1,207 successful checks. The prior APK, decoded/prepared source trees, scripts and build evidence are preserved under `pre-text-fit-fix/`.

The focused Android 16 quit-dialog/save regression and Android 6 regression are performed separately by runtime agents; this report does not claim those results before their evidence is available. Historical gameplay evidence for `44d...` must not be relabeled as execution of the final `c821...` APK.

## Runtime follow-up

The final `c821...` APK displayed the complete formerly clipped quit message on API36 (`runtime/final-api36-textfix/03-quit-text-fixed.png`). It also passed a new natural save/exit/relaunch and actual combat restoration (`07-restored-live-combat.png`). API23 installed the same final APK in place with all 11 preference files unchanged and restored its existing battle. The separate `final-api23-textfix/RESULT.md` and `final-api36-textfix/RESULT.md` reports define the precise regression scope; the historical 44d full-flow reports remain unchanged.
