package my_app.skin;

/**
 * A {@code com.badlogic.gdx.scenes.scene2d.ui.Skin$TintedDrawable} declaration
 * — a named alias that reuses an existing atlas region ({@code regionName}),
 * tinted with {@code tint}. Lets a skin derive several colored variants (e.g.
 * a button's normal/pressed/hover states) from one base region instead of
 * needing separate atlas art for each — real skins (Skin Composer exports,
 * the gdx-skins pack, ...) use this routinely. {@code tint} is already fully
 * resolved at parse time, since libGDX's {@code color} field accepts either
 * a name reference (looked up against the skin's declared colors) or an
 * inline {@code {r, g, b, a}} literal — either way, by the time this record
 * exists there's nothing left to resolve.
 */
public record TintedDrawableRef(String regionName, SkinColor tint) {
}
