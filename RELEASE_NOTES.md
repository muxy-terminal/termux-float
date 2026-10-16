# Termux:Float 0.17.0+muxy.4

Use the same touchscreen gestures as muxy 0.119.7:

- A confirmed single tap opens the keyboard without a terminal click.
- A completed double tap sends one mouse press/release pair when the application requests mouse reporting. It does not open the keyboard.
- The outer overlay no longer opens the keyboard on every touch release. Touch down may restore window focus without requesting the IME.
- Drags, long presses, cancelled gestures, and multitouch do not become TUI clicks. Physical mouse and touchpad paths are unchanged.

Single-tap handling waits for Android's double-tap decision. Double taps do not hide an already open keyboard. Initial launch, explicit window controls, and keyboard shortcuts retain keyboard access.

The shared terminal libraries are pinned to muxy `7a03b9938e9d9eb4a1456045d60147dd54848d02`. This includes the excess-Backspace Hangul fix from muxy.3.

Install over previous muxy-signed Float builds. Package `com.termux.window`, version code 1004, minimum Android 7. Shared UID, permissions, and certificate are unchanged:

```text
756768af514d3fa0d1c214d54dabd7b0cd088a1723a22f520b90e8e8545262ce
```

The overlay/PTY suite checks single/double tap keyboard visibility and exact mouse bytes on ordinary/alternate screens with modes 0, 1000, 1002, and 1003. It also checks CJK input, empty-editor deletion, native colors, and disposal. Physical devices and arbitrary TUI bindings remain outside the verified matrix.

The corresponding-source archive and GPLv3 text accompany the APK. The coordinated distribution records exact source commits, hashes, and compatibility coverage.
