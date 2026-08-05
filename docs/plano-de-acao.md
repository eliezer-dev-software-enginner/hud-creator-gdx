# Action Plan — Scene2D Builder

Visual UI builder (JSON) for libGDX. JavaFX app built on top of the
`megalodonte` framework (`megalodonte-base`/`megalodonte-components`/`megalodonte-reactivity`/
`megalodonte-theme`). The idea: build the UI on a `Canva` (free positioning)
from a loaded libGDX skin, save it as JSON into the target project
(`libgdx-example-game`), and a parser (future, on the game side) converts that
JSON into real Scene2D `Table`/widgets.

This plan covers the current slice: **load the skin and get it ready to draw
on the Canva**. The final parser (libGDX side) and the export stay on the
roadmap.

## Current project state

- [`HomeScreen.java`](../src/main/java/my_app/HomeScreen.java) already has a
  `MenuBar` (New/Load/Save/Export) wired to `HomeScreenViewModel` (still
  empty) and a `Canva` as the drawing surface.
- An example skin is already present under `source.images.and.assets/`:
  `skin.json` + `skin.atlas` + `skin.png` + `.fnt` fonts (standard libGDX
  `TextureAtlas`/`Skin` format).
- `Context.javafxStage()` gives access to the `Stage`, allowing a native
  JavaFX `FileChooser` for "Load".
- No JSON lib in `build.gradle.kts` yet. `.atlas` **is not JSON** — it's
  libGDX's own text format, needing a dedicated parser.

## Phase 0 — Foundation (without libGDX as a dependency)

The builder is pure JavaFX and can't depend on the libGDX jar (it would pull
in LWJGL/desktop backend). We're going to **reimplement the reading**, not
reuse `com.badlogic.gdx.*`:

- Add a lightweight JSON lib to `build.gradle.kts` (suggestion: **Gson** — the
  `skin.json` is valid JSON; it can be loaded as a `JsonObject` and the
  references resolved manually, since the structure is dynamic per class:
  `"com.badlogic.gdx.scenes.scene2d.ui.Button$ButtonStyle": { "default": {...} }`).
- `.atlas` is line-by-line (`name / rotate / xy / size / split / pad / orig /
  offset / index`) — its own parser, no external dependency.

## Phase 1 — Skin parsing (`my_app.skin`) ✅ implemented

See `src/main/java/my_app/skin/` (`AtlasRegion`, `AtlasParser`, `SkinColor`,
`SkinJsonParser`, `SkinModel`, `SkinLoader`, `SkinLoadException`) and the test
`src/test/java/my_app/skin/SkinLoaderTest.java`, validated against the
example skin under `source.images.and.assets/`. JSON parsed with Jackson
(`jackson-databind`).

1. **`AtlasRegion`** — record with `name, x, y, width, height, splits(int[4]?),
   rotate`.
2. **`AtlasParser`** — reads the `.atlas`, returns `List<AtlasRegion>` + the
   name of the associated `.png` (line 1 of the `skin.png` block).
3. **`SkinColor`** (r,g,b,a) and **`SkinJsonParser`** — used Gson to walk the
   JSON by class (`BitmapFont`, `Color`, `*Style`) and builds:
   - a map of named colors,
   - a map of named fonts → path to the `.fnt` (actual font rendering is
     deferred; for now we just keep the path),
   - a map `styleClass -> styleName -> Map<field, value>` (e.g.:
     `ButtonStyle.default.up = "button-up"`).
4. **`SkinModel`** — the final domain object that aggregates everything above
   and already resolves the references (region name → `AtlasRegion`, color
   name → `SkinColor`), ready for the UI to consume. This is the object that
   travels through `State<SkinModel>`.
5. **`SkinLoader`** — single entry point: takes the path to `skin.json`,
   locates `.atlas` and `.png` (same folder, by libGDX convention), runs the
   parsers above and returns a `SkinModel` (or a friendly error if something's
   missing).

## Phase 2 — Visual region preview (`my_app.skin.render`) ✅ implemented

See `src/main/java/my_app/skin/render/` (`SkinImages`, `AtlasImageView`,
`NinePatchView`, `DrawableView`). `NinePatchView` uses JavaFX's native
`BorderImage`/9-slice mechanism (crops the region with `WritableImage` +
`PixelReader`, applies it as a `BorderImage` with `slices` = the atlas's
`splits`) instead of manually assembling a 3x3 grid — corners stay
pixel-perfect and only the center stretches.

Visual check done via the `./gradlew skinPreviewSnapshot` task
(`src/test/java/my_app/skin/render/SkinPreviewSnapshot.java`, dev-only, not
JUnit): renders simple regions + 9-patch buttons at native size and
stretched, takes a snapshot and saves it to
`build/skin-preview-smoke-test.png`. Result checked: button corners stay
sharp when stretched, no distortion. Rotated regions (`rotate: true`) aren't
fully supported/tested — no such case in the example skin.

To draw the skin onto the Canva we need to crop regions out of `skin.png`:

1. **`AtlasImageView`** — an `ImageView` with a `viewport` (`Rectangle2D`) set
   for a plain (non-9-patch) region, reusing the pattern from
   [`Image.java`](../../../megalodonte-ecossystem/megalodonte-libs/megalodonte-components/src/main/java/megalodonte/components/Image.java)
   already present in the framework.
2. **`NinePatchView`** — new component (a Region with 9 `ImageView`s in a 3x3
   `GridPane`, or a canvas with cropping via `drawImage`) to correctly draw
   drawables with `split` (buttons, panels) — without it, buttons stretch
   incorrectly.
3. Visually test with the example skin before moving on (render `button-up`,
   `button-down`, etc. side by side on a smoke-test screen).

## Phase 3 — Loading the skin into the app ✅ implemented

`HomeScreenViewModel` gained `State<SkinModel> skin` + `State<String>
loadError` and a real `handleLoad()` (`FileChooser` via
`MegalodonteApp.getCurrentContext().javafxStage()` → `SkinLoader.load(...)`).
`HomeScreen` now splits the workspace into `Canva` (left, grows) +
`SkinPalettePanel` (right, 260px), a panel that reacts to `skin`/`loadError`
and shows: nothing loaded, an error, or a scrollable region list with a
preview (via `DrawableView`) — visually confirming the whole parsing → render
→ UI pipeline is wired up.

Visual check via `./gradlew homeScreenSnapshot`
(`src/test/java/my_app/HomeScreenSmokeTest.java`, dev-only): renders the 3
states (empty/error/loaded) stacked. A real bug was found and fixed during
this check: the `Roboto-Black` region (the font's glyph sheet, also packed
into the atlas, without a `split`) had no size cap in the preview and blew
out the list's width — `SkinPalettePanel` now caps plain-region previews at
40px (`ImageView.setFitWidth/Height` + `preserveRatio`).

**Extra — configurable Canva size.** The `Canva` represents a real target
libGDX screen/HUD, so it needs an explicit pixel size (not just "hugging" its
content) to serve as a WYSIWYG preview. Added
[`CanvasSizeToolbar`](../src/main/java/my_app/CanvasSizeToolbar.java) — two
numeric fields (`megalodonte.components.v2.Input`) above the Canva, bound to
`HomeScreenViewModel.canvasWidthState()`/`canvasHeightState()` (default
640×360, matching `libgdx-example-game`'s `GameScreen` viewport).
`HomeScreen` subscribes to both states and resizes the Canva's `Pane`
directly (`setMinSize`/`setPrefSize`/`setMaxSize`) on every change, with a
visible border marking the screen's bounds. Confirmed reactive in the
smoke-test (4th state: resizes after rendering and the Canva shrinks).

## Phase 3 (original roadmap, for reference)

1. `HomeScreenViewModel`: add `State<SkinModel> skin = State.of(null)` and
   implement `handleLoad()`:
   - opens a `FileChooser` (via `MegalodonteApp.getCurrentContext().javafxStage()`)
     filtered to `skin.json`,
   - calls `SkinLoader.load(path)`,
   - on success, `skin.set(result)`; on error, show feedback (a plain
     `Text`/simple toast works for now).
2. `HomeScreen`: reacts to the `skin` state — when not null, shows a simple
   side panel ("Palette") listing the loaded regions/styles (name + preview
   via `AtlasImageView`/`NinePatchView`), just to visually confirm the skin
   loaded correctly. Still **no** drag-and-drop onto the Canva at this phase.

## Phase 4 — dragging from the palette onto the Canva ✅ implemented

New package `my_app.widget` (domain) + `my_app.widget.render` (rendering):

- **`WidgetSpec`** — sealed interface with 4 variants: `ImageSpec` (raw
  region), `ButtonSpec`, `TextButtonSpec`, `LabelSpec` (these three resolved
  against a named style from the skin).
- **`WidgetViews.build(skin, atlasImage, spec)`** — resolves the spec against
  the `SkinModel` and builds the real preview: `Button`/`Image` use
  `DrawableView` (reusing Phase 2); `TextButton` stacks the `up` drawable with
  a centered `Text` in a `StackPane`, colored via the style's `fontColor`;
  `Label` is just the colored `Text`. **Assumed limitation**: text uses
  JavaFX's system font, not the skin's real BitmapFont — `SkinModel` only
  exposes the `.fnt` path (Phase 1); actually parsing/rendering the glyphs is
  deferred (doesn't block validating position/color/layout for now).
- **`PlacedWidget`**/**`DragPayload`** — `PlacedWidget(id, spec, x, y)` is the
  tracked model; `DragPayload` is a static same-process channel for the
  `WidgetSpec` being dragged (JavaFX's `Dragboard` only carries
  string/image/file, not an arbitrary Java object — safe here because the
  drag's source and target are always in the same window/process).
- **`CanvasController.place(spec, centerX, centerY)`** — resolves the widget,
  centers it on the given point, clamps it to fit entirely within the Canva's
  current size (nothing leaks past the "screen rectangle"), adds it to the
  `Canva` via `child(component, x, y)` and registers a `PlacedWidget` in
  `HomeScreenViewModel.placedWidgets()` (`ListState`, just bookkeeping for now
  — becomes the base for export in Phase 5).
- **`SkinPalettePanel`** gained "Buttons"/"Buttons with text"/"Texts" sections
  (one per skin style, skipping the section if the skin doesn't have that
  style type — this example skin has no `LabelStyle`, for instance) besides
  the already-existing "Regions" list — every palette entry is now draggable
  (`setOnDragDetected` + `Dragboard`, with `setDragView` for a cursor preview
  following the mouse).
- **`HomeScreen`** wires `setOnDragOver`/`setOnDragDropped` on the Canva's
  `Pane`, forwarding to `CanvasController`; the Canva also gained
  `setClip(...)` (via `bindCanvasSize`) to crop anything spilling past the
  edge.

**Visual check** — three new dev-only smoke-tests:
`./gradlew widgetViewsSnapshot` (Button/TextButton/Image side by side),
`./gradlew canvasControllerSnapshot` (calls `CanvasController.place(...)`
directly, without simulating a real drag — JavaFX has no simple API for that
outside something like TestFX/Robot — including a drop deliberately outside
the Canva's bounds to prove the clamping), and `homeScreenSnapshot` again for
full-panel regression. Confirmed: centering on the drop point is correct,
edge clamping is correct (checked the math by hand: a widget dropped at
(500,400) on a 400×240 Canva with a 111×43 button ended up at x=289,y=197 =
exactly `400-111` and `240-43`), and the new palette sections appear/disappear
correctly depending on the skin's styles.

**Not covered in this phase** (left for the roadmap): resizing/deleting an
already-placed widget, undo, editing a `TextButton`/`Label`'s text after it's
dropped (uses a fixed placeholder — "Button"/"Text" — for now).

### Fix — moving an already-placed widget on the Canva

Bug reported by the user: after dragging an item from the palette onto the
Canva, it couldn't be moved again — `CanvasController.place` never installed
mouse handlers on the placed widget. Fixed in `CanvasController` with a
`makeMovable(node, widgetId)`: `setOnMousePressed` stores the offset between
the click and the node's current `layoutX`/`layoutY` (in *scene* coordinates,
which don't change as the node moves — unlike coordinates local to the node
itself); `setOnMouseDragged` recomputes the position from that offset, with
the same edge-*clamp* as `place()`; `setOnMouseReleased` updates the matching
`PlacedWidget` in `placedWidgets()` via `ListState.updateIf`.

Unlike the palette's drag-and-drop (which uses `Dragboard`, meant to bring in
a *new* widget from outside), moving a widget already on the Canva is just
mouse-press/drag on the node itself — simpler, no `Dragboard`/`DragEvent`
needed.

**Check** — this time I could actually test it with JUnit
(`CanvasControllerMoveTest`), without needing TestFX/Robot: discovered
empirically (not documented) that a `MouseEvent` built directly, outside the
normal scene-graph dispatch, has `getSceneX()/Y() == getX()/Y()` — so
synthetic events with controlled scene coordinates can be built and the
installed handlers called directly
(`node.getOnMousePressed().handle(event)`). Two tests: moving a button by a
known delta and checking the final position + the updated `PlacedWidget`; and
deliberately dragging well past the edge to confirm the clamp also holds
during the drag, not just on the initial drop.

## Phase 5 — Save/Export ✅ implemented

New package `my_app.project` defines the JSON format and writes it to disk:

- **`UiLayout`**/**`PlacedWidgetDto`** — schema: `formatVersion`, `skinPath`,
  `canvasWidth`/`canvasHeight`, `widgets[]` (`id`, `type` —
  `"image"`/`"button"`/`"textButton"`/`"label"`, mapping directly to the
  Scene2D classes —, `styleName`/`regionName`/`text` depending on the type,
  `x`/`y`). Widget size is **not** saved — a real Scene2D actor built with
  the same skin (`new Button(skin, styleName)`) sizes itself from the same
  drawables/font the builder already used to draw the preview, no need to
  travel separately. **Gotcha documented for Phase 6**: coordinates here have
  their origin at the top-left corner, Y growing downward (same as
  Canva/JavaFX) — Scene2D is the opposite (origin bottom-left, Y growing
  upward), so the future parser needs to flip it:
  `stageY = canvasHeight - y - widgetHeight`.
- **`UiLayoutAssembler`** — converts `List<PlacedWidget>` (Phase 4's internal
  model) into the schema's DTOs, and resolves a relative `skinPath` (with a
  fallback to an absolute path if it can't be relativized, e.g. different
  drives on Windows).
- **`UiLayoutWriter`** — writes the `UiLayout` as indented JSON via Jackson
  (`ObjectMapper`, already used since Phase 1), creating any missing
  directories.
- **`SkinAssetExporter`** — copies the skin's files (`skin.json`, `.atlas`,
  the page image, every referenced `.fnt` — via `SkinModel.fontNames()`, new)
  into a `skin/` folder next to the exported JSON, making the export
  self-contained (no manual "also copy the skin" step needed).

`HomeScreenViewModel` gained two real handlers, each split into "opens the
dialog" (`handleSave()`/`handleExport()`, can't be automated-tested) +
"what happens after" (`saveTo(Path)`/`exportTo(Path)`, package-private,
directly testable — same pattern as `CanvasController` in Phase 4):

- **Save** (`handleSave`/`saveTo`) — references the skin wherever it already
  sits on disk (no copying); meant as "save the builder's project to keep
  editing later" (this phase doesn't implement *loading* it back, only
  *writing* — reopening a saved project is left for the roadmap).
- **Export** (`handleExport`/`exportTo`) — uses `SkinAssetExporter` to copy
  the skin along with it, default suggested output under
  `libgdx-example-game/assets/ui/`; this is the "write into the target
  project" the user asked for from the start.
- Errors from both (skin not loaded, I/O failure) and the success result go
  into a new `State<String> statusMessage`, shown in a status bar at the
  bottom of `HomeScreen`.

**Check** — this time with real JUnit tests, not just a visual smoke-test (it's
I/O + JSON, not rendering): `UiLayoutAssemblerTest`, `UiLayoutWriterTest`,
`SkinAssetExporterTest`, `HomeScreenViewModelExportTest` (end to end, calling
`saveTo`/`exportTo` directly). Also ran `./gradlew exportDemo` (dev-only) to
do a real export against the actual `libgdx-example-game` — the result landed
in `libgdx-example-game/assets/ui/hud-demo.json` +
`libgdx-example-game/assets/ui/skin/` (4 files copied), checked by hand.

**Not covered in this phase**: loading a saved project back (round-trip),
"New" doesn't clear the Canva/widget list yet, no confirmation before
overwriting an existing export.

## Phase 6 — Parser on the libGDX side ✅ implemented

Lives in the OTHER project, `libgdx-example-game/core/src/main/java/eu/dev/ui/`
(`core` module, no dependency on the LWJGL3 backend — Scene2D is
backend-agnostic):

- **`HudLayout`**/**`PlacedWidgetData`** — mirror Phase 5's schema
  (`my_app.project.UiLayout`/`PlacedWidgetDto`) as plain public-field POJOs, a
  format libGDX's reflective `com.badlogic.gdx.utils.Json` already reads with
  no extra configuration (didn't even need a new dependency — the JSON parser
  already ships with `gdx` core).
- **`HudLoader.load(FileHandle)`** — reads the JSON, resolves `skinPath`
  relative to the file's own folder
  (`layoutFile.parent().child(layout.skinPath)`), loads `new Skin(skinFile)`
  (same "`.atlas` next to the `.json`" convention `SkinLoader` already
  followed in Phase 1) and builds each widget via the Scene2D constructor
  that matches the type 1:1: `new Button(skin, styleName)` /
  `new TextButton(text, skin, styleName)` / `new Label(text, skin, styleName)`
  / `new Image(skin, regionName)`. **Deliberate departure from the original
  roadmap wording** ("builds a real Table"): widgets go into a plain `Group`
  positioned via `actor.setPosition`, not into a `Table` with cells — the
  Canva is a free-positioning surface (arbitrary x,y), not a grid, so `Table`
  would fight the model; a `Group` with absolute positioning is the faithful
  Scene2D equivalent. Each actor is sized with
  `getPrefWidth()/getPrefHeight()` (the same drawables/font the builder
  already used to measure the preview) before applying the Y-axis flip
  documented in Phase 5 (`stageY = canvasHeight - y - height`).
- **`HudView`** — just a holder (`Group root`, `Skin skin`, dimensions) that
  forces whoever consumes it to remember to `dispose()` the skin.

**Check** — this time against the game's real rendering pipeline (LWJGL3),
not a mock: `HudSnapshotDemo` (`core`, not part of the shipped game) loads a
layout, draws a real frame in a real window and saves a screenshot;
`HudSnapshotLauncher` (`lwjgl3`) just opens the window at the right size. Ran
it via `./gradlew :lwjgl3:hudSnapshot` (accepts `-PhudLayout=<path>` to point
at a different layout) against the real `ui/hud-demo.json`, produced by Phase
5 (`exportDemo`). Two bugs found and fixed during this check, neither in the
parser itself:
  1. `Pixmap.createFromFrameBuffer` reads OpenGL's framebuffer bottom-up — the
     first screenshot came out vertically mirrored (which actually confirmed
     the `HudLoader`'s Y-axis flip was mathematically correct anyway, since
     mentally undoing the mirroring matched the expected positions). Fixed
     with a manual vertical flip of the `Pixmap` before saving — only in the
     verification tool, not in `HudLoader`.
  2. The first screenshot landed inside `assets/`, which would have made it
     accidentally become a game "asset" on the next build. Fixed by pointing
     the task's `workingDir` at `lwjgl3/build/` (`Gdx.files.internal(...)`
     resolves via the classpath, not the cwd, so it doesn't need the
     `workingDir = assets/` the real `run` task uses).

Also deliberately tested the real `project.json` the user had saved (via
"Save", clicking through the real UI) — it references the skin outside the
`assets/` folder (`../../scene2d-buider/...`, since "Save" doesn't copy).
Confirmed empirically that it **doesn't work**: libGDX's internal `FileHandle`
doesn't cross `..` outside the classpath
(`GdxRuntimeException: File not found`). This is the expected, already
documented limit from Phase 5 — "Save" is for the builder's own round-trip,
not for feeding the game; only "Export" (which copies the skin) is guaranteed
to work here.

### Fix — wiring the real HudLoader into GameScreen

`GameScreen.show()` used to build the UI by hand (a 1x1 `Pixmap` turned into
a `Drawable`, a hardcoded dialog `Table` with "Ola! Esta e a sua caixa de
dialogo.", a separate `uiskin.json` skin). Replaced with
`hud = HudLoader.load(Gdx.files.internal("ui/hud-demo.json")); stage = new
Stage(new FitViewport(hud.canvasWidth, hud.canvasHeight));
stage.addActor(hud.root);` — the Stage now uses the loaded HUD's own size,
no longer the hardcoded `VIEWPORT_WIDTH/HEIGHT` constant (same value by
coincidence, but decoupled). `dispose()` gained
`stage.dispose()`/`hud.dispose()` (the old `Skin` was never disposed).

Deliberately **left untouched** the `TiledMapLoader` line with the other
machine's absolute path (a pre-existing bug, unrelated to the HUD work) — did
not try to fix it, since that wasn't what was asked.

**Check**: ran the real game (`./gradlew :lwjgl3:run`, a real background
process, not just the isolated demo) — compiled clean and kept running for
over a minute with no exception in the log (I stopped it myself via `kill`,
the only "error" logged was the `SIGTERM` I sent). This confirms `show()`
completes successfully — including the broken `.tmx` line, which apparently
doesn't throw a synchronous exception in this version of libGDX, contrary to
my initial suspicion. Couldn't grab a screenshot of the actual window (a
full-screen capture via `ffmpeg x11grab` came back black — likely no
compositor/window manager active on this display), but the integration uses
the exact same `HudLoader`/`HudView` already visually validated in the
previous check's `HudSnapshotDemo`, so the HUD's composition itself is
already confirmed.

**Not covered**: whether `button`/`textButton` widgets need some kind of
click callback (the current schema doesn't carry that, and the UI still
doesn't react to any input).

## Adjustments — Load Layout, widget nickname, looking up an Actor in Java

Three user requests, implemented together since they're connected:

### 1. "Load Layout" in the menu (real round-trip)

`HomeScreenViewModel.handleLoadLayout()`/`loadLayoutFrom(Path)` (same
"dialog separated from logic" pattern as `saveTo`/`exportTo`): opens a layout
JSON, resolves `skinPath` relative to the file's own folder, loads the
`SkinModel`, sets `skin`/`canvasWidth`/`canvasHeight`, and hands the widget
list off to `CanvasController.loadLayout(List<PlacedWidget>)` (new — clears
the Canva via `clear()` and re-places each widget at its exact saved
position, without centering like the drop's `place()` does).
`UiLayoutAssembler.fromDto`/`UiLayoutReader` are new (the inverse of Phase
5's `toDto`/`UiLayoutWriter`).

An easy-to-miss detail I fixed: after loading a layout with
`widget-1`/`widget-2`, a new widget dragged from the palette can't reuse
those ids — `HomeScreenViewModel.bumpNextWidgetIdPast(...)` advances the
counter past the largest numeric id already loaded.

`HomeScreenViewModel` now gets a reference to the `CanvasController` via
`attachCanvasController(...)` (called once by `HomeScreen` once both exist) —
simpler than a `State`/reactive event for this one-shot wiring action, and
avoids the gotcha of `State.set(...)` not notifying when the new value equals
the previous one (e.g. reloading the same file twice in a row).

### 2. Nickname instead of "onClick"

The original idea was a `name → handler` map pre-wired by the builder — the
user simplified it into something better: just a **nickname** on the widget;
on the Java side you look up the `Actor` by its nickname and use it normally
(`addListener`, swap style, disable, etc.), without the builder needing to
know anything about click logic.

- **`PlacedWidget`** gained a `nickname` field (nullable) — not part of
  `WidgetSpec` (which only describes "how to draw"), it lives at the
  placement level because it's interaction metadata, not visual.
- **`CanvasController`**: double-clicking an already-placed widget (a
  `MOUSE_CLICKED` event with `getClickCount() == 2`, coexists without
  conflict with the press/drag/release already used for moving) opens a
  `TextInputDialog` asking for the nickname. The logic itself
  (`setNickname(id, nicknameOrNull)`) is separate from the dialog — the same
  pattern as always, to keep the interaction-independent half testable.
- **`PlacedWidgetDto`**/**`UiLayoutAssembler`**: `nickname` field (omitted
  when null, like every optional field in the schema).

### 3. `HudView.get(nickname)` on the libGDX side

`HudLoader` now indexes each actor in an `ObjectMap<String, Actor>` (libGDX's
native collection, not `java.util.HashMap`) as it builds them, using each
`PlacedWidgetData`'s `nickname` when present. `HudView.get(String)` and
`HudView.get(String, Class<T>)` (typed version, avoids a manual cast) expose
that lookup.

```java
TextButton play = hud.get("play", TextButton.class);
if (play != null) {
    play.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) { ... }
    });
}
```

Wired up for real in `GameScreen` (which already had Phase 6's `HudLoader`).
In the process I found and fixed a real bug:
**`Gdx.input.setInputProcessor(stage)` was never called** — without it the
Stage never receives any click from the OS, so no listener on any button
would ever fire, no matter what else was done here.

**Check**: 4 new JUnit tests in scene2d-buider (full export→load round-trip
including nickname and the id-collision guard, `setNickname` directly, and
the inverse `fromDto`/`toDto`) — 18 total, all passing. On the libGDX side,
extended `HudSnapshotDemo`: re-exported `hud-demo.json` with the "Play"
button nicknamed `"play"` (`ExportDemo` updated), the demo looks up that
actor, registers a real `ClickListener`, and simulates a click via
`stage.touchDown`/`touchUp` (libGDX's own mechanism for synthesizing input,
no Robot/OS needed) at the button's center. The log confirmed
`playClicked=true` — the click went through the real Scene2D path (Stage
hit-test → listener), not just a direct call to the handler.

## Adjustments 2 — selection with a properties panel, alignment guides, Save fix

From here on I stopped generating/viewing a screenshot for every check
(expensive in tokens) — checks are compilation + JUnit only from now on.

- **Selection + "Properties" panel**: pressing a widget on the Canva selects
  it (`HomeScreenViewModel.selectedWidgetIdState()`), applies a highlighting
  `DropShadow` (`CanvasController.applySelectionHighlight`), and
  `SkinPalettePanel` shows a live-bound "Nickname" field
  (`Input.onChange` calls `CanvasController.setNickname` on every keystroke).
  Clicking an empty area of the Canva deselects. This replaced the previous
  round's double-click dialog (the user explicitly asked for the panel
  instead of the dialog). Important: `SkinPalettePanel` does **not** subscribe
  to `placedWidgets()` for rebuilds — only `selectedWidgetIdState()` —
  otherwise every keystroke would tear down and recreate the very field the
  user is typing into.
- **Center alignment guides**: two `Line`s (vertical/horizontal) added to the
  Canva's `Pane` from `CanvasController`'s constructor, invisible until the
  dragged widget gets close to the center (shows thin at ~15px away,
  thickens and *snaps* to the exact center at ~5px). Since the guides become
  children of the same `Pane` as the widgets, `clear()` now specifically
  preserves those two (`removeIf` instead of a plain `clear()`), and the
  tests swapped positional indexing into `pane.getChildren()` for
  `CanvasController.nodeFor(id)` (more robust anyway, regardless of how many
  overlays exist).
- **Fix: "Save" wasn't overwriting the loaded file.** `HomeScreenViewModel`
  gained `currentProjectFile`, set by `saveTo`/`loadLayoutFrom`;
  `handleSave()` now skips the dialog and overwrites directly once a known
  file exists (normal "Save" vs. "Save As" semantics — the first time still
  opens the dialog).

**Check**: 21 JUnit tests in total (4 new: center-guide snap+thickening,
selection with visual highlight moving between two widgets, `handleSave()`
overwriting without a dialog after a `loadLayoutFrom`).

## Adjustments 3 — grid, background image (persisted, ignored by the game)

- **Grid**: `showingGrid` (`State<Boolean>`, created by the user) + a "Grid"
  checkbox in `CanvasSizeToolbar`. `CanvasController` draws on a dedicated
  `javafx.scene.canvas.Canvas` (doesn't use `Line` — many individual lines
  would be more expensive), added to the Canva's `Pane` **before** the
  alignment guides (sits behind them and the widgets), redrawn on every
  change to `showingGrid` or the canvas size. `clear()` (used by "Load
  Layout") preserves the grid the same way it already preserved the guides.
- **Background image**: the initial request was purely visual ("Background
  Image (Canva)" in the menu, via `pane.setBackground(...)` with
  `BackgroundSize` in cover mode — same pattern `ContainerProps.bgImage`
  already used). The user corrected it mid-implementation: the path **needs**
  to go into the JSON (`UiLayout` gained `backgroundImagePath`,
  `@JsonInclude(NON_NULL)` to omit it when unset) — except the libGDX-side
  parser should ignore that field, since a game's background is
  `SpriteBatch`, not Scene2D. `HomeScreenViewModel` stores the **path**
  (`State<String>`), not the `Image` — `HomeScreen` builds the `Image` from
  the path when rendering. `saveTo`/`exportTo` relativize the path (reusing
  `UiLayoutAssembler.relativeSkinPath`, renamed under the hood to
  `relativePath` — generic now, no longer skin-specific); `loadLayoutFrom`
  resolves it back and restores the state.

**Check**: 25 tests in total (4 new: the generic `relativePath`,
`backgroundImagePath` surviving in the JSON when set and absent when not, a
full save→load round-trip of the background image, the grid tracking canvas
resize and not breaking when toggled on/off).

## Adjustments 4 — `AppStorage` (app preferences, not UI state)

New package `my_app.storage`: `AppSettings` (record: `showingGrid`,
`lastLayoutFile`) + `AppStorage` (reads/writes JSON at
`~/.scene2d-buider/settings.json` by default — independent of the folder the
app was launched from). Unlike `UiLayout`: this is a preference of the
**application** (survives across different projects), not part of an
exportable layout.

Motivation: the user had already added `HomeScreen.onMount() { ...
handleLoadLayout(); }` on their own — but that opens the "Load Layout"
dialog every time the app starts, which doesn't make sense for an automatic
restore. Swapped it for `viewModel.restoreFromAppStorage()` (no dialog):
reads `AppSettings`, applies `showingGrid`, and if `lastLayoutFile` actually
exists on disk, calls `loadLayoutFrom` directly. `saveTo`/`loadLayoutFrom`
call `persistAppStorage()` after updating `currentProjectFile`, so
"Save"/"Load Layout" also update what gets automatically reopened next time.

**Important care taken**: `AppStorage` is only used (enabled) after
`restoreFromAppStorage(...)` has run at least once — an `appStorageEnabled`
field guards this. Without that guard, the plain `subscribe` on
`showingGrid` (which fires immediately on subscription, the framework's
`State` behavior) would write to `~/.scene2d-buider/settings.json` **every
time** any test constructed a plain `HomeScreenViewModel()` — and the whole
suite does that constantly. Manually verified: deleted
`~/.scene2d-buider`, ran the full suite, and nothing was recreated.

**Check**: 31 tests in total (6 new: `AppStorage` alone — defaults when
missing/corrupted, save→read round-trip — and ViewModel integration —
restoring grid+layout, persisting when toggling the grid after the restore,
`saveTo` updating the remembered file).

## Adjustments 5 — removing a widget with Delete

`CanvasController.removeWidget(id)` (removes the node from the `Pane`, from
`nodesById` and from `placedWidgets()`; clears the selection if it was the
selected widget) + the Delete/Backspace key wired on the Canva's `Pane`.

Gotcha: keyboard events only reach whatever has focus, and a plain `Pane`
isn't focusable/focused by default (it's not a `Control`). Without this the
handler would never fire on a real Delete press. Solved with
`pane.setFocusTraversable(true)` + `pane.requestFocus()` called every time a
widget is selected (in the same `onMousePressed` that already handled
selection).

**Check**: 34 tests in total (3 new: `removeWidget` directly — disappears
from the model/selection, the other widget stays —, the Delete key removing
the selected widget via a synthetic event on the `Pane`'s handler, and
Delete with nothing selected doing nothing).
