# TODO

## Done

- [x] Native libGDX canvas + palette (real Scene2D `Skin` rendering, no
      reimplementation of drawables/fonts)
- [x] ImGui menu bar + Hierarchy/Inspector panels, single process, no
      JavaFX/megalodonte
- [x] Swing `JFileChooser` for Open/Save dialogs, backgrounded so the GLFW
      loop never freezes
- [x] Feature parity with the old JavaFX app: multi-select + group drag,
      snap-to-grid, in-app clipboard (copy/paste/duplicate), Save-vs-Export
      split, background-image load/clear, confirm-before-replacing-skin,
      palette section headers, thumbnail-capped previews,
      click-empty-clears-selection, centered+clamped palette drop, real
      canvas clipping, font-color hex field, alignment guides
- [x] Reserved window column for Hierarchy/Inspector (no more overlap with
      the palette or canvas)
- [x] Canvas top-anchored under the menu bar
- [x] Palette scroll-focus-on-hover, section-header padding, oversized-font
      thumbnail clamp
- [x] Resize floored at widget content minimum (no more text overflow from
      dragging a `Label`/`TextButton` smaller than its text needs)
- [x] Alignment-guide canvas centering fixed (was matching any edge, not
      the widget's actual center)
- [x] `nickname` field removed; `id` is the only, directly-editable
      identifier
- [x] Z-order decoupled from selection (selecting/inspecting a widget no
      longer silently reorders it)
- [x] `scene2d-hud-loader` re-synced with the `id`-only JSON schema (was
      still indexing lookups by the now-dead `nickname` field)
- [x] Light theme now recolors the Scene2D-rendered chrome too (window
      backdrop, status label), not just the ImGui panels
- [x] Shift-to-lock-aspect-ratio on resize (new, not in the old app)
- [x] Background image is now a real, loadable asset (`scene2d-hud-loader`
      renders it), not just an editor-only preview reference
- [x] Save/Export failures now print a stack trace to the console and pop
      up an ImGui modal (previously only tiny status-bar text, easy to
      miss — a reported "export does nothing" turned out unreproducible
      against the real logic, but the weak feedback was real regardless)
- [x] Anchoring — dock a widget to the canvas or another widget (edges/
      center + margin), resolved at *load* time by `scene2d-hud-loader`
      using the real device's canvas size, not baked into a fixed pixel
      position at export like scene-game-2d-editor's own anchor system is
- [x] Ctrl+G / Ctrl+Shift+G — persistent grouping. Only changes drag/
      delete/copy/duplicate (act on the whole group); click-select and the
      Inspector stay per-widget on purpose, so editing one grouped
      widget's own properties never requires ungrouping first

## Known gaps — deliberate, not forgotten

- **Startup auto-restore is a simplification, not a full port.** The old
  app continuously mirrored canvas state to a `canvas-cache.json` on every
  edit and fell back to it (with corruption handling) if the last explicit
  save couldn't be read. This rewrite just reopens whatever layout file
  was last explicitly saved/opened (`HudEditorScreen.restoreLastLayout`/
  `rememberLastLayout`) — simpler, and covers the same practical goal
  ("don't lose your session on restart"), but isn't a live continuous
  autosave, so work since the last Save/Export wouldn't survive a crash.
  Not building the full continuous-cache system unless asked — it's
  materially more infrastructure (debounced background writes, corruption
  recovery) for a personal dev tool where that risk is low.

## Best-effort fix — unconfirmed

The checkerboard/grid tiling was once reported to visually vary between
runs ("sometimes small, sometimes big"). Best hypothesis from code review:
some of `layoutChrome`'s position math depended on
`statusLabel.getHeight()` (font-metric-derived, not a whole number),
landing the canvas at a sub-pixel offset — with `Nearest` texture
filtering, that's enough to alias the checker/grid tiling inconsistently.
Fixed by rounding `layoutChrome`'s positions to whole pixels, but **this
was never confirmed as the actual root cause** — it couldn't be
reproduced/verified without live visual access. If it's still happening,
this hypothesis was wrong and it needs another look with more specific
repro details (does it correlate with a window resize? moving the window
between monitors? a specific skin?).
