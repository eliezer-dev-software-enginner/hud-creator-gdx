package my_app.gdx;

import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

public class BitmapFontRenderer {
    private final Image atlasImage;
    private final Region fontRegion;
    private final BitmapFontData fontData;

    public BitmapFontRenderer(Image atlasImage, Region fontRegion, BitmapFontData fontData) {
        this.atlasImage = atlasImage;
        this.fontRegion = fontRegion;
        this.fontData = fontData;
    }

    public void drawText(GraphicsContext gc, String text, double startX, double startY) {
        double cursorX = startX;

        // offsetX/offsetY/originalWidth/originalHeight são float nesta versão do libgdx
        float offsetLeft = fontRegion.offsetX;
        float offsetTop = fontRegion.originalHeight - fontRegion.offsetY - fontRegion.height;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Glyph g = fontData.getGlyph(c);
            if (g == null) continue;

            double srcX = fontRegion.left + (g.srcX - offsetLeft);
            double srcY = fontRegion.top + (g.srcY - offsetTop);

            gc.drawImage(
                    atlasImage,
                    srcX, srcY, g.width, g.height,
                    cursorX + g.xoffset, startY + g.yoffset,
                    g.width, g.height
            );

            cursorX += g.xadvance;
        }
    }
}