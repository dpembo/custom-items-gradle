# KCI Web Editor

The first web-editor slice manages custom-item JSON files in a local project directory. It is intentionally separate from the legacy Swing editor and is scoped to Paper 26.2+.

## Requirements

- Node.js 18+ (for native `fetch`/ESM support) and npm
- A JDK compatible with the repository's Gradle build (needed by `Build plugin files`, which shells out to `./gradlew :kci-export-cli:installDist`)

## Install

From the `web-editor` directory:

```sh
npm install
```

## Development

```sh
npm run dev
```

This runs the Express API (`dev:server`) and the Vite dev server (`dev:client`) together. Open `http://127.0.0.1:5173`; API requests are proxied to `http://127.0.0.1:3210`.

By default, project files are created under `web-editor/project`. To use another location:

```sh
KCI_PROJECT_DIR=/path/to/kci-project npm run dev:server
```

The Node API listens on the `port` and `host` from the project-level `kci-editor.config.json`; its validation rules are also defined there.

## Building for production

```sh
npm run build
```

This runs `vite build` and writes the compiled client to `web-editor/dist`.

## Running in production

```sh
npm start
```

`npm start` runs `node server/index.mjs` directly (no Vite dev server). If `web-editor/dist` exists (i.e. you ran `npm run build` first), the Express server serves the built client itself, so a single process on the configured `port`/`host` (default `0.0.0.0:3210`) handles both the API and the UI. Set `KCI_PROJECT_DIR` the same way as in development to point at a different project directory.

## Export status

`Build plugin files` creates `export/items.cis.txt` and `export/resource-pack.zip`. The ZIP contains the generated texture, model, overrides, pack metadata, and `items.cis.txt`. The current mapper supports textured iron, diamond, and netherite swords, including internal ID, display name, lore, and custom model data from 1 through 32767. It deliberately rejects other item categories and unmapped attributes until their legacy model mappings are implemented.