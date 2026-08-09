# EchoFlow Web — Theme Showcase

Static Astro frontend clone of EchoFlow UI, showcasing the **Echo Signal** brand theme (light + dark only).

No API, no backend — mock chats, sidebar, settings, and composer.

## Run locally

```bash
cd echoflow-web
npm install
npm run dev
```

Open http://localhost:4321

## Pages

| Route | Content |
|-------|---------|
| `/` | Chat with sidebar, mock conversation, composer |
| `/settings` | Settings hub |
| `/settings/appearance` | Theme toggle + color role preview |

## Theme

Echo Signal palette from `src/styles/theme.css`:

- **Light** — warm paper surfaces (`#FAF9F6`), deep teal primary (`#0A7C6E`)
- **Dark** — warm charcoal (`#121110`), luminous teal (`#56D4C5`)

Toggle via the floating button or Appearance settings. Preference is stored in `localStorage`.

## Build

```bash
npm run build
npm run preview
```
