# JNI precision verification on Android

Codex added a detached precision fixture in `a847f46`. The fixture
uses bundled Prusa CORE One 0.6, Speed and Prusament PLA profiles.
It invokes the production settings snapshot once and decodes the
Process, Filament and Printer settings through the Java consumer.
It exercises numeric values and relative and absolute widths without
saving a profile or changing the active model.

Android Studio installed the debug app on Medium Phone API 37 from
`a847f46`. Computer Use opened Общие → Разработчику → Проверить нативные
настройки. The first block reported 52 checks. The second reported six
precision checks and displayed every passing key.

- `layer_height`
- `sparse_infill_density`
- `line_width`
- `outer_wall_line_width`
- `filament_diameter`
- `printable_height`

Both blocks reported a saved JSON. The original report still writes the
exact bytes returned by native code. The separate precision report writes
expected and actual values. The files were not independently reopened
through Device Explorer in this check. The installed app was checked once
in portrait with Russian strings at the original font size.
[Device screenshot](screenshots/JNI-precision/precision-success.png)

Offline build and lint passed. The five relevant host suites ran 354
checks. The packaged native library retained SHA-256
`8C146C742B8A3B23B4ADE5C5E0E54A3EAE0705A52EDDFAC8CD64406D249DE0E1`.
The technical change is integrated at `50c219b`.
