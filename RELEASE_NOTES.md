# Termux:Float 0.17.0+muxy.3

## Fixed

Pin muxy revision `c096eebb5b37588b736ede7802c335421ae3dbc5` to correct Hangul input after repeated Backspace beyond an empty editor. This covers the same origin/caret mismatch as muxy 0.119.6, including alternate-screen input.

The editor origin decreases after retained input is exhausted and stops at zero. Terminal Backspace events are still forwarded. No IME restart or Unicode normalization is added.

## Upgrade

Install over `0.17.0+muxy.2`. Package `com.termux.window`, version code 1003, minimum Android 7. The shared UID, overlay permission, and private muxy signing certificate are unchanged:

```text
756768af514d3fa0d1c214d54dabd7b0cd088a1723a22f520b90e8e8545262ce
```

Use with muxy 0.119.6. Differently signed upstream/F-Droid/Play APKs cannot share the muxy UID. Do not uninstall muxy or disable signature verification.

The release includes the APK, checksums, GPLv3 text, build provenance, and corresponding source for this fork plus the exact muxy library revision. The native terminal is packaged for arm64-v8a, armeabi-v7a, x86, and x86_64.

## Verification

The overlay/PTY suite covers CJK commits, repeated deletion, navigation, empty-editor excess Backspace on ordinary and alternate screens, native color reset, and terminal disposal. The optional live test accepts `gboardExpected`, `gboardSubmittedContext=true`, and `gboardAlternateScreen=true` for externally driven Gboard input.

The API 35 overlay/PTY suite completes six scenarios without failures, with the optional live scenario skipped when its argument is absent. Actual Gboard `18.4.1.985164140-release-arm64-v8a` passes four live captures: ordinary/alternate screen after eight/30 excess touch Backspaces, followed by `안녕`. Captures assert actual PTY bytes. The 30-deletion cases cover reaching zero and continuing to delete.

The shared terminal host tests pass 159 emulator and 51 View tests in both debug and release. The main app's eight host tests pass per build type. Android connection/View/pointer suites complete 52 scenarios on API 21, 34, and 35 without failures, with unsupported/manual cases skipped.

Physical devices, other live IMEs, and arbitrary TUI bindings remain unverified for this correction.
