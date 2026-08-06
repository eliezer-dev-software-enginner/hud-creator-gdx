# scene2d-ui-buider

A JavaFX app (built on the `Megalodonte` framework) for visually building
libGDX Scene2D UI. Load a skin (`skin.json` + `.atlas` + `.png`), drag
buttons/labels/images from a palette onto a free-positioning canvas, tweak
canvas size/grid/background, and Save/Export the layout as JSON.

![scene2d-buider screenshot](https://i.ibb.co/JWNSzSX3/Screenshot-from-2026-08-05-13-50-09.png)

## What it does

You load a skin, drag buttons/labels/images from a palette straight onto a canvas that matches your game's actual target resolution, position everything exactly where you want it (WYSIWYG, free positioning — not a grid), and export the layout as a plain JSON file.

A small companion library, `scene2d-hud-loader`, turns that JSON into a real Scene2D `Skin` + `Group` + `Actor`s with one call:

```java
HudView hud = HudLoader.load(Gdx.files.internal("ui/hud.json"));
stage.addActor(hud.root);
Gdx.input.setInputProcessor(stage);
```

That's it — no `Table` cells, no manual positioning math, no separate loader code to write yourself.

## What you need

Just a skin: `skin.json` + `.atlas` + the texture `.png`. If you don't already have one lying around, [**Skin Composer**](https://github.com/raeleus/skin-composer) is the standard way to build one from scratch or reskin an existing UI pack — the builder loads whatever it produces directly.

To consume the exported JSON in your game, add the `scene2d-hud-loader` library via JitPack:

```kotlin
// build.gradle.kts, in the module that needs it
repositories {
    maven { url = uri("https://jitpack.io") }
}
dependencies {
    implementation("com.github.eliezer-dev-software-enginner:scene2d-hud-loader:v1.0.0-beta")
}
```

Loader library repo: https://github.com/eliezer-dev-software-enginner/scene2d-hud-loader

## Features

- **Free-positioning canvas** at your real target resolution — what you see is what you get in-game
- **Drag straight from your skin's palette** — buttons, text buttons, labels, and raw atlas regions all show up automatically once a skin is loaded
- **Alignment guides** while dragging, plus an optional **grid overlay**
- **Nickname any widget**, then look it up by name in your own game code and wire up a click listener like you normally would — the builder never needs to know anything about your game logic:
  ```java
  TextButton playButton = hud.get("play", TextButton.class);
  playButton.addListener(new ClickListener() { ... });
  ```
- **Reference background image** while you work (visual only — real game backgrounds are drawn with `SpriteBatch`, so this is ignored by the loader, it's just there to help you line things up)
- **Light/dark theme**
- **Self-contained export** — copies the skin's files alongside the exported JSON, so the output folder is ready to drop straight into your assets

## Consuming the exported JSON

After exporting a layout from the builder, you'll have a JSON file plus the skin's `.json`/`.atlas`/`.png` files sitting next to it (the export is self-contained). Drop that whole folder into your libGDX project's assets (e.g. `android/assets/ui/` or `core/assets/ui/`), then load it with `scene2d-hud-loader`:

```java
// One-time setup, e.g. in your Screen's show() or an initializer
HudView hud = HudLoader.load(Gdx.files.internal("ui/hud.json"));
stage.addActor(hud.root);
Gdx.input.setInputProcessor(stage);
```

`HudView` exposes:

- `hud.root` — a `Group` containing every widget you positioned in the builder, already placed at the exact coordinates from your export.
- `hud.skin` — the real Scene2D `Skin`, built from the same skin files the builder loaded, in case you need it to build additional widgets in code.
- `hud.get(String nickname, Class<T> type)` — looks up a widget by the nickname you gave it in the builder, cast to the widget type you expect.

Wiring up behavior is just normal Scene2D — look widgets up by nickname and attach listeners as you would with any hand-built UI:

```java
TextButton playButton = hud.get("play", TextButton.class);
playButton.addListener(new ClickListener() {
    @Override
    public void clicked(InputEvent event, float x, float y) {
        game.setScreen(new GameScreen());
    }
});

Label scoreLabel = hud.get("score", Label.class);
scoreLabel.setText(String.valueOf(currentScore));
```

The loader has no dependency on the builder itself — it just reads the exported JSON and skin files, so it's a normal runtime dependency of your game (see [What you need](#what-you-need) above for the JitPack setup).

## Get it

- Builder (Windows/Linux installers under **Releases**): https://github.com/eliezer-dev-software-enginner/scene2d-ui-builder
- Loader library (JitPack): `com.github.eliezer-dev-software-enginner:scene2d-hud-loader:v1.0.0-beta`
- Loader library repo: https://github.com/eliezer-dev-software-enginner/scene2d-hud-loader

Still early — this is a young project and I'm actively working on it, so bug reports, feature requests, and general feedback are very welcome. Let me know if it's useful for your project!

Part of the [`scene2d-suite`](..) pair — see [`../README.md`](../README.md)
for how this project relates to `libgdx-example-game` (the sample game that
consumes the exported JSON) and `scene2d-hud-loader` (the library that
parses it there). The full phase-by-phase build history and design notes
live in [`docs/plano-de-acao.md`](docs/plano-de-acao.md).

## Before you start: you need a skin

The builder doesn't create a skin for you — it loads one you already have.
A libGDX skin is three files that go together: `skin.json`, `skin.atlas`,
and the texture `skin.png` (or whatever they're named — the app follows
libGDX's own convention of all three living in the same folder, the atlas
and image sharing the JSON's base name).

If you don't already have one, [**Skin Composer**](https://github.com/raeleus/skin-composer)
is the standard tool for building a libGDX skin from scratch or reskinning
one of the free UI packs — export from there, then point this app's
**Options → Load Skin** at the resulting `skin.json`.

---
# Dev mode section
Only if you want to clone the project and contribute with it. If not this section below is not necessary for you, and you are ready to dive into in.
## Running

```bash
./gradlew run
```

## JavaFX Modules Setup

Running via `./gradlew run` requires the JavaFX SDK modules to be available locally,
pointed to by the `JAVAFX_MODULES_HOME` environment variable (see the `run` task in
`build.gradle.kts`).

1. Download the modules archive:
   https://github.com/eliezer-dev-software-enginner/megalodonte-base/releases/download/utilities/java_fx_modules.zip
2. Extract it somewhere on disk. The extracted folder must contain `linux-25.0.1/`
   and `windows-25.0.1/` subfolders.
3. Point `JAVAFX_MODULES_HOME` at that folder:

**Windows** (PowerShell — persists across sessions):
```powershell
[System.Environment]::SetEnvironmentVariable("JAVAFX_MODULES_HOME", "C:\path\to\java_fx_modules", "User")
```
Restart your terminal/IDE afterwards so the new value is picked up.

**Linux** (bash/zsh — add to `~/.bashrc` or `~/.zshrc`):
```bash
export JAVAFX_MODULES_HOME="/path/to/java_fx_modules"
```
Restart your terminal (or run `source ~/.bashrc`) and any IDE that reads environment
variables from a login shell.

## Packaging

Packaging with the updater (does not modify the original scripts):
```bash
python scripts/create-msi.py   # Windows
python scripts/create-deb.py   # Linux
```

## Hot Reload

### Setup

```bash
python3 -m venv .venv
.venv/bin/pip install watchdog
```

### Usage

```bash
.venv/bin/python dev.py
```

Or activate the venv first:

```bash
source .venv/bin/activate
python dev.py
```

### How it works

1. Watches `src/main/java/` for changes via `watchdog`.
2. On change, compiles the modified `.java` files with `javac`.
3. Copies modified resources to `build/classes/java/main/`.
4. Reloads the screen in JavaFX via `Reloader`.

## FAQ

**Q: I set `fontSize`/color/etc. on a component's props but nothing changes on screen. Why?**

A: Every `Props.apply(node)` call (in `megalodonte-base`) only applies styling once a
theme is set — it subscribes to `ThemeManager.state()` and silently skips applying
anything while that's `null`. Make sure `ThemeManager.setTheme(...)` is called once,
early in `main()`, before any screen renders (see `Main.java`).

**Q: `./gradlew run` fails with "Environment variable JAVAFX_MODULES_HOME is not set", but I did set it. What's going on?**

A: Two common causes:
- The variable was exported in your shell *after* the Gradle daemon (or your IDE's
  background JVM) started, so that process never inherited it. Restart your terminal
  and/or run `./gradlew --stop` to kill the stale daemon, then try again.
- You set it in a terminal, but you're launching the IDE from a desktop icon instead
  of that terminal — desktop launchers usually don't source `~/.bashrc`/`~/.zshrc`,
  so IDE-spawned processes won't see it either. Set it via your desktop environment's
  system-wide environment variables instead, or always launch the IDE from a shell
  that has it exported.

**Q: Where do I get the JavaFX modules?**

A: See [JavaFX Modules Setup](#javafx-modules-setup) above.

**Q: How do I get live-reload while developing instead of restarting the app on every change?**

A: See [Hot Reload](#hot-reload) above — run `python dev.py` after installing `watchdog`.

**Q: How do I produce an installer for Windows/Linux?**

A: See [Packaging](#packaging) above.
# scene2d-ui-builder