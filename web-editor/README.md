# KCI Web Editor

The first web-editor slice manages custom-item JSON files in a local project directory. It is intentionally separate from the legacy Swing editor and is scoped to Paper 26.2+.

## Run

```sh
npm install
npm run dev
```

Open `http://127.0.0.1:5173`. By default, project files are created under `web-editor/project`. To use another location:

```sh
KCI_PROJECT_DIR=/path/to/kci-project npm run dev:server
```

The Node API listens on the `port` and `host` from the project-level `kci-editor.config.json`; its validation rules are also defined there.

## Export status

`Build plugin files` creates `export/items.cis.txt` and `export/resource-pack.zip`. The ZIP contains the generated texture, model, overrides, pack metadata, and `items.cis.txt`. The current mapper supports textured iron, diamond, and netherite swords, including internal ID, display name, lore, and custom model data from 1 through 32767. It deliberately rejects other item categories and unmapped attributes until their legacy model mappings are implemented.