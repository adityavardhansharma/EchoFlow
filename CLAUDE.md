# EchoFlow — project guide for Claude

EchoFlow is an Android (Jetpack Compose, Material 3 Expressive) AI chat app. The UI layer is
role/theme-based across several palettes (Monochrome, Ocean, Forest, Sunset, Lavender, Rose) plus
dynamic colour — never hardcode hex; always use `MaterialTheme.colorScheme` roles so a change looks
right in every theme, light and dark.

## Commits and PRs

- Never add `Co-Authored-By: Claude` or "Generated with Claude Code" to commits or PRs.
- Branch off `main` for changes; keep one focused change per PR.
