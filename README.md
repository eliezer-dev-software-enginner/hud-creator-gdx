# hud-creator-gdx

A native libGDX app for visually building libGDX Scene2D UI. Load a skin
(`skin.json` + `.atlas` + `.png`), drag buttons/labels/images from a
palette onto a free-positioning canvas, tweak canvas size/grid/background,
and Save/Export the layout as JSON.

## What it does

You load a skin, drag buttons/labels/images from a palette straight onto a
canvas that matches your game's actual target resolution, position
everything exactly where you want it (WYSIWYG, free positioning — not a
grid), and export the layout as a plain JSON file.

A small companion library, `scene2d-hud-loader`, turns that JSON into a
real Scene2D `Skin` + `Group` + `Actor`s with one call:

```java
HudView hud = HudLoader.load(Gdx.files.internal("ui/hud.json"));
stage.addActor(hud.root);
Gdx.input.setInputProcessor(stage);
```

That's it — no `Table` cells, no manual positioning math, no separate
loader code to write yourself.

## What you need

Just a skin: `skin.json` + `.atlas` + the texture `.png`. If you don't
already have one lying around, [**Skin Composer**](https://github.com/raeleus/skin-composer)
is the standard way to build one from scratch or reskin an existing UI
pack — this app loads whatever it produces directly.

To consume the exported JSON in your game, add the `scene2d-hud-loader`
library via JitPack:

```kotlin
// build.gradle.kts, in the module that needs it
repositories {
    maven { url = uri("https://jitpack.io") }
}
dependencies {
    implementation("com.github.eliezer-dev-software-enginner:scene2d-hud-loader:v1.0.1-beta")
}
```

Loader library repo: https://github.com/eliezer-dev-software-enginner/scene2d-hud-loader

## Features

- **Free-positioning canvas** at your real target resolution — what you
  see is what you get in-game
- **Drag straight from your skin's palette** — buttons, text buttons,
  labels, and raw atlas regions all show up automatically once a skin is
  loaded
- **Multi-select and group drag**, snap-to-grid on move and resize,
  in-app clipboard (copy/paste/duplicate)
- **Anchoring** — dock a widget to the canvas' own edges/corners/center
  (with a margin) or to another widget's, instead of only a fixed pixel
  position. Resolved by `scene2d-hud-loader` at load time using the real
  device's canvas size, so it survives a canvas resize in the builder and
  a different screen size in the actual game
- **Alignment guides** while dragging, plus an optional **grid overlay**
- **Give any widget an id**, then look it up by it in your own game code
  and wire up a click listener like you normally would — this app never
  needs to know anything about your game logic:
  ```java
  TextButton playButton = hud.get("play", TextButton.class);
  playButton.addListener(new ClickListener() { ... });
  ```
- **Background image**, exported and loaded like any other asset — a real
  Scene2D `Image` behind every widget in the loaded HUD, not just a
  reference while you work
- **Light/dark theme**
- **Self-contained export** — copies the skin's files alongside the
  exported JSON, so the output folder is ready to drop straight into your
  assets

## Consuming the exported JSON

After exporting a layout, you'll have a JSON file plus the skin's
`.json`/`.atlas`/`.png` files sitting next to it (the export is
self-contained). Drop that whole folder into your libGDX project's assets
(e.g. `android/assets/ui/` or `core/assets/ui/`), then load it with
`scene2d-hud-loader`:

```java
// One-time setup, e.g. in your Screen's show() or an initializer
HudView hud = HudLoader.load(Gdx.files.internal("ui/hud.json"));
stage.addActor(hud.root);
Gdx.input.setInputProcessor(stage);
```

`HudView` exposes:

- `hud.root` — a `Group` containing every widget you positioned in this
  app, already placed at the exact coordinates from your export.
- `hud.skin` — the real Scene2D `Skin`, built from the same skin files
  this app loaded, in case you need it to build additional widgets in
  code.
- `hud.get(String id, Class<T> type)` — looks up a widget by the id you
  gave it (editable in the Inspector panel), cast to the widget type you
  expect.

Wiring up behavior is just normal Scene2D — look widgets up by id and
attach listeners as you would with any hand-built UI:

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

The loader has no dependency on this app itself — it just reads the
exported JSON and skin files, so it's a normal runtime dependency of your
game (see [What you need](#what-you-need) above for the JitPack setup).

## Before you start: you need a skin

This app doesn't create a skin for you — it loads one you already have. A
libGDX skin is three files that go together: `skin.json`, `skin.atlas`,
and the texture `skin.png` (or whatever they're named — the app follows
libGDX's own convention of all three living in the same folder, the atlas
and image sharing the JSON's base name).

If you don't already have one, [**Skin Composer**](https://github.com/raeleus/skin-composer)
is the standard tool for building a libGDX skin from scratch or reskinning
one of the free UI packs — export from there, then point this app's
**Load Skin...** menu item at the resulting `skin.json`.

## Running

```bash
./gradlew :lwjgl3:run
```

Part of the [`scene2d-suite`](..) trio — see [`../README.md`](../README.md)
for how this project relates to `libgdx-example-game` (the sample game
that consumes the exported JSON) and `scene2d-hud-loader` (the library
that parses it there).

Still early — this is a young project and actively being worked on, so
bug reports, feature requests, and general feedback are welcome.
