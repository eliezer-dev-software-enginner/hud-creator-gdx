package my_app.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;

import java.nio.file.Path;

/**
 * O pulo do gato é que tanto TextureAtlas quanto BitmapFont têm uma classe interna de dados puros, sem dependência de GPU/OpenGL — só texto parseado em campos públicos:
 *
 * TextureAtlas.TextureAtlasData → parseia o .atlas
 * BitmapFont.BitmapFontData → parseia o .fnt
 *
 * Ambas usam com.badlogic.gdx.files.FileHandle, que não precisa de Gdx.app/Gdx.files inicializado — o construtor new FileHandle(File) cria um handle do tipo Absolute e funciona standalone, puro Java. Ou seja: dá pra usar isso dentro do JavaFX sem subir nenhum contexto LibGDX (nem HeadlessApplication).
 */
public class GdxFontLoader {

    public record LoadedFont(Region atlasRegion, BitmapFontData fontData) {}

    public static LoadedFont load(Path atlasPath, Path fontPath, String regionName) {
        FileHandle atlasFile = new FileHandle(atlasPath.toFile());
        TextureAtlasData atlasData = new TextureAtlasData(atlasFile, atlasFile.parent(), false);

        Region region = null;
        for (Region r : atlasData.getRegions()) {
            if (r.name.equals(regionName)) {
                region = r;
                break;
            }
        }
        if (region == null) {
            throw new IllegalArgumentException("Região não encontrada no atlas: " + regionName);
        }

        FileHandle fontFile = new FileHandle(fontPath.toFile());
        // flip=true: por javadoc do próprio libgdx ("glyphs will be flipped for
        // use with a perspective where 0,0 is the upper left corner"), é essa
        // opção - não flip=false - que deixa Glyph.yoffset como "distância do
        // topo da linha até o topo do glyph", pronto pra usar como coordenada Y
        // de um Canvas (0,0 no canto superior esquerdo). Com flip=false (o
        // padrão usado pelo SpriteBatch, coordenadas OpenGL Y-up), yoffset sai
        // negativo e não bate com o desenho feito aqui.
        BitmapFontData fontData = new BitmapFontData(fontFile, true);

        return new LoadedFont(region, fontData);
    }
}