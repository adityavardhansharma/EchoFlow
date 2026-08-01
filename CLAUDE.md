# EchoFlow — project guide for Claude

EchoFlow is an Android (Jetpack Compose, Material 3 Expressive) AI chat app. The UI layer is
role/theme-based across several palettes (Monochrome, Ocean, Forest, Sunset, Lavender, Rose) plus
dynamic colour — never hardcode hex; always use `MaterialTheme.colorScheme` roles so a change looks
right in every theme, light and dark.

## Pull requests must show the change on-device

Every PR that touches UI must include **emulator screenshots of the change** in the PR body — a
reviewer should see the result without building it. This is required, not optional.

How to do it (the flow that works from the CLI):

1. Build, install, and drive the app on the emulator to the screen you changed, then capture with
   `adb exec-out screencap -p > shot.png`. Show meaningful states (e.g. default + active, and a
   compact/landscape case if the change affects layout). Before/after is ideal.
2. Commit the PNGs into the repo under `docs/screenshots/<feature>/` (keep filenames descriptive).
3. Embed them in the PR body with raw URLs so they render, e.g.
   `![plus menu](https://github.com/<owner>/<repo>/blob/<branch>/docs/screenshots/<feature>/<file>.png?raw=true)`.

Notes:
- A Compose `Popup`/`DropdownMenu`/bottom-sheet lives in its own window, so the Roborazzi test
  harness can't screenshot it (`onRoot()` and full-screen capture both miss it). Verify those
  surfaces live on the emulator and use those screenshots in the PR.
- The emulator/adb setup and how to seed the drawer with sample conversations are documented in the
  session memory (`drawer-live-verification`).

## Commits and PRs

- Never add `Co-Authored-By: Claude` or "Generated with Claude Code" to commits or PRs.
- Branch off `main` for changes; keep one focused change per PR.
