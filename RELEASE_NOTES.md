# Termux:Float 0.17.0+muxy.5

Restore the original Termux-style tap policy shared with muxy 0.119.8:

- With mouse reporting enabled, one tap sends an immediate mouse press/release pair and does not request the keyboard.
- Without mouse reporting, a confirmed single tap opens the keyboard on either primary or alternate screens.
- Double tap has no custom action. Two taps in a mouse-reporting application are two ordinary clicks.
- A click that exits the alternate screen or disables mouse reporting cannot become a delayed keyboard request. Contacts invalidated by session, screen, or mouse-state changes are discarded.

The overlay does not open the keyboard on every touch release. Explicit window controls and the keyboard shortcut remain available. TUI widget focus is not exposed by the terminal protocol.

The terminal libraries are pinned to muxy `4f19a15cdbdce7334a7f2429bf45aa086b698896`. Existing libghostty-vt, CJK input, excess-Backspace, native color, and disposal behavior is retained.

Install over earlier muxy-signed Float builds without uninstalling. Package `com.termux.window`, version code 1005, minimum Android 7. Shared UID, permissions, and certificate are unchanged:

```text
756768af514d3fa0d1c214d54dabd7b0cd088a1723a22f520b90e8e8545262ce
```

The overlay/PTY suite covers ordinary/alternate screens with modes 0/1000/1002/1003, keyboard visibility, exact click bytes, explicit keyboard access, CJK commits, deletion, native colors, and disposal. Physical devices and per-widget focus in arbitrary TUIs remain outside the verified matrix.

Corresponding source, GPLv3 text, build provenance, and checksums accompany the release.
