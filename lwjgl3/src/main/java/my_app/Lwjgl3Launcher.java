package my_app;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import my_app.editor.HudEditorScreen;

/** Launches the desktop (LWJGL3) application — mirrors scene-game-2d-editor's Lwjgl3Launcher. */
public class Lwjgl3Launcher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("scene2d HUD Creator");
        config.useVsync(true);
        config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        config.setWindowedMode(
                HudEditorScreen.PALETTE_WIDTH + HudEditorScreen.MARGIN * 4 + HudEditorScreen.DEFAULT_CANVAS_WIDTH + HudEditorScreen.INSPECTOR_WIDTH,
                HudEditorScreen.DEFAULT_CANVAS_HEIGHT + HudEditorScreen.MARGIN * 2);

        new Lwjgl3Application(new HudEditorScreen(), config);
    }
}
