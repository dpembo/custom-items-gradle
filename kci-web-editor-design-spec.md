# Design Specification: KCI Web Editor

**Subject:** A Node.js-based web replacement for the `editor` module of [knokko/custom-items-gradle](https://github.com/knokko/custom-items-gradle)
**Status:** Scoped — personal tool, single target version
**Scope decisions:**
- Personal tool for Globeworks only, not a general release
- Targets Minecraft/Paper 26.2+ only — no multi-version abstraction needed
- Binary compatibility: Option A (shell out to existing Java `bit-helper` code)
- Fully offline, local-first tool — no live dev-server preview integration
- Frontend: React

---

## 1. Problem statement

The current Editor is a Java Swing desktop app built on a custom, in-house `gui` library. Per the project's own `docs/modules.md`, that library scales everything relative to window size — consistent across machines, but blurry/distorted text and no access to modern widget conventions (proper text inputs, drag-and-drop, live previews, keyboard-driven forms). It also has to be downloaded and run locally by every server owner who wants to configure items, which is friction for a plugin whose stated goal is being free and easy to adopt.

Goal: replace it with a **Node.js server + browser-based UI** that server owners run (locally, in Docker, or on a small VPS) and open in any browser. Browser rendering solves the scaling/fidelity problem for free and unlocks a much richer widget/interaction vocabulary (react-select-style pickers, drag-and-drop crafting grids, live texture atlas previews, undo history, etc.).

---

## 2. Non-negotiable constraints

These come from how KCI actually works, not preference:

1. **Output compatibility.** The plugin (`plug-in` module) deserializes a binary config file produced by the current Editor via the `bit-helper` library. The web editor's output must remain byte-compatible with what `plug-in` expects — or `plug-in`'s deserializer needs to change in lockstep. This is the highest-risk part of the whole project and needs its own decision (§6).
2. **Version scope is fixed, not general.** The `kci-nmsXX` modules imply the plugin behaves differently across Minecraft versions, but this build only needs to target **26.2+ / current Paper**. That removes a whole axis of complexity: no need to model "which fields are valid for which version," no need for a version selector in the UI. Validation rules can just be "what's valid on 26.2+," full stop. If this ever needs to support older servers, that's a later, separate project — don't build the abstraction now on spec.
3. **Texture/resourcepack pipeline.** KCI's whole pitch is custom textures without overwriting vanilla items (see `docs/custom-texture-system.md`). Whatever replaces the editor still needs to produce/validate resourcepack assets (textures, model JSON, item ID assignment) alongside the item config.
4. **Single-user, local-first tool.** This has never needed multi-user auth, cloud sync, or a database server. Don't build that unless asked — see §5.

---

## 3. High-level architecture

```
┌─────────────────────────────┐        ┌──────────────────────────────┐
│  Browser (SPA)               │  HTTP/ │  Node.js server               │
│  - Item list / browser       │  WS    │  - REST API (CRUD on items,   │
│  - Item edit forms           │◄──────►│    recipes, textures, config) │
│  - Texture atlas preview     │        │  - Project file I/O           │
│  - Recipe grid editor        │        │  - Binary encoder/decoder     │
│  - Live resourcepack preview │        │    (port of bit-helper logic) │
└─────────────────────────────┘        │  - Validation engine           │
                                        │  - Resourcepack builder        │
                                        └──────────────────────────────┘
                                                     │
                                        ┌──────────────────────────────┐
                                        │  Project directory on disk    │
                                        │  - items/*.json (editable)    │
                                        │  - textures/*.png             │
                                        │  - export/config.bin (plugin) │
                                        │  - export/resourcepack.zip    │
                                        └──────────────────────────────┘
```

**Decision: local-first, single-project-at-a-time server.** Launch with `npx kci-editor` (or `node server.js`) in a project folder, it starts a web server on `localhost:PORT`, opens the browser. No accounts, no multi-tenancy. This matches how the desktop app is used today and avoids inventing problems (auth, concurrent-edit conflicts) nobody has.

**Decision: project state lives as human-readable JSON on disk, not a database.** One file per item (or per category) under `items/`, git-friendly, diffable, greppable — a real improvement over an opaque binary file server owners currently can't inspect or version-control. The binary `config.bin` the plugin reads becomes a **build artifact**, generated from the JSON on save/export, the same way you'd compile source to a binary. This also gives you a natural migration path (see §7) and dovetails with your existing preference for config-driven, inspectable systems.

---

## 4. Technology choices

| Layer | Choice | Why |
|---|---|---|
| Server runtime | Node.js (LTS), plain `http`/Express or Fastify | Matches your existing Node stack (Orchelium); no need for a heavier framework for a single-tenant local tool |
| Frontend | Vite + a component framework (Svelte or React — see below) | Fast dev loop, modern DOM, no build-step pain |
| Styling | Plain CSS / CSS variables, no heavy design system | Editor UI is forms-and-tables heavy; a component library adds weight without much payoff |
| Live updates | WebSocket (or SSE) for texture/resourcepack preview reload | Editor changes should reflect in a live preview pane without full page reload |
| Binary encode/decode | Ported/rewritten in JS, mirroring `bit-helper`'s format | Needed regardless of frontend choice — see §6 |
| Packaging | npm package + optional Docker image | `npx kci-editor` for casual users, Docker for VPS/homelab hosting |

**Frontend framework: React**, per your call. Its ecosystem has mature drag-and-drop libraries (e.g. `dnd-kit` or `react-dnd`) which will help most with the recipe-grid editor — the trickiest widget in this whole app.

Because this is a personal tool for one server on one MC/Paper version, packaging can stay minimal: a `package.json` with a `start` script is enough. No need for `npx`-style distribution, Docker image, or install docs unless you later decide to share it.

---

## 5. Core UI modules

1. **Item browser** — searchable/filterable list of all custom items, grouped by category (weapon/armor/tool/food/block/etc., mirroring however KCI currently categorizes them), with texture thumbnails inline. Replaces whatever flat/paged list the Swing UI uses today.
2. **Item editor form** — the bulk of the UI. Tabbed or accordion sections per concern: identity (name, display name, lore), base item + texture, attributes (attack damage, durability, etc.), enchantments, special effects/abilities, projectile behavior (if applicable). Each field gets inline validation against the target MC version's constraints, replacing whatever silent failure mode exists today.
3. **Texture manager** — upload/preview PNGs, auto-generate the model JSON needed for the custom-texture trick, flag ID collisions before they cause an in-game problem instead of after.
4. **Recipe editor** — visual 3x3 (or cooking/smithing equivalent) grid, drag items from a palette. This is the single biggest UX win over a Swing dropdown-based recipe editor and worth prioritizing early.
5. **Live preview pane** — renders the assigned item texture (and ideally an approximation of the in-game item) as you edit, using the generated resourcepack assets.
6. **Validation/problems panel** — persistent list of current config errors/warnings (missing texture, duplicate custom ID, invalid attribute value for target version), replacing pop-up dialog validation with something you can triage in one place.
7. **Export panel** — "Build config.bin" and "Build resourcepack.zip" actions, with a log of what changed since last export.

---

## 6. The hard problem: binary format compatibility

You have three real options here, in increasing order of effort and decreasing order of risk:

**Option A — Shell out to Java.** Keep `bit-helper`/the existing serialization code as-is in Java, and have the Node server invoke a small headless Java CLI (built from the existing modules, minus the GUI) to do JSON → binary conversion. Fastest to build, zero risk of format drift, but means the "modern Node.js app" still has a JVM dependency at export time. Given this is a self-hosted homelab-style tool, that's a reasonable trade-off, not a dealbreaker.

**Option B — Port the binary format to JS.** Reimplement `bit-helper`'s (de)serialization logic in JavaScript/TypeScript. Removes the JVM dependency entirely, but is real reverse-engineering work and every future plugin-side format change needs a matching JS-side change — ongoing maintenance tax split across two languages.

**Option C — Change the plugin to read JSON/a new format instead of the legacy binary.** Cleanest long-term, since JSON is what you're editing anyway, but it's a breaking change to `plug-in` and forces existing KCI users (if this is meant to be shared) to re-export. Only worth it if you're comfortable versioning the plugin's config format.

**Decision: Option A.** The Node server shells out to a small headless Java CLI, built from the existing `bit-helper`/`shared-code` serialization classes with the GUI stripped out, to do JSON ↔ binary conversion. This keeps risk low and gets a working end-to-end tool fastest — worth revisiting only if the JVM dependency becomes a real annoyance in practice.

---

## 7. Starting state

There's no existing `config.bin` to migrate — this is a from-scratch project, not a port of live data. That simplifies things: no import tool is needed for v1, and there's nothing to validate a round-trip against up front. The first real test of the Option A binary bridge will be "does the plugin correctly load what the new editor exported," using a fresh config against a test server.

An import path (binary → JSON) is still worth keeping in mind as a *later* addition, in case you ever want to pull in item configs from another KCI server or an old backup — but it's not on the critical path now.

---

## 8. Configurability

In line with keeping things tweakable rather than hardcoded: the server should read a project-level config file (`kci-editor.config.json` or similar) covering at minimum:
- Port/host to bind the dev server to
- Paths for the project's items/textures/export directories (defaults sane, but overridable)
- Which optional feature modules are enabled (e.g., CrazyEnchantments support, mirroring `ce-event-handler`'s optional dependency)
- The validation ruleset (which attribute/value combinations are legal on 26.2+) lives in config — see §10; keeping this in config rather than hardcoded means it doesn't need a code change if the plugin's own constraints shift

---

## 9. Suggested phased roadmap

1. **Java export CLI:** strip the GUI out of the existing `editor`/`shared-code`/`bit-helper` classes into a small headless Java command (`java -jar kci-export.jar in.json out.bin`, or similar). This is the foundation everything else calls — get it encoding a minimal hand-written test item correctly, and confirm the plugin loads that output on a 26.2+ test server, before building UI on top of it.
2. **MVP UI:** item browser + item edit form for one item category (e.g., weapons only), JSON persistence, no export yet.
3. **Export path:** wire the "Build config.bin" button to the Java CLI from step 1; test against the real plugin on your test server.
4. **Expand item categories** to full coverage of what the Swing editor currently supports.
5. **Recipe editor** (visual grid, React + a drag-and-drop library) and **texture manager** (upload + preview + collision detection).
6. **Live preview pane** and **validation panel** polish.

---

## 10. Validation ruleset location — resolved

Lives in config, not hardcoded. Add a `validation` (or `rules`) section to `kci-editor.config.json` describing legal attribute/value ranges and combinations for 26.2+ — e.g. min/max attack damage, which enchantment/item-type pairings are valid, required vs. optional fields per category. The validation engine (§3) reads this at startup and checks item data against it, so tightening or loosening a constraint is a config edit, not a code change.
