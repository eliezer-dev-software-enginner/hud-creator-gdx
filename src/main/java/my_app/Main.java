package my_app;

import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import megalodonte.ListenerManager;
import megalodonte.application.MegalodonteApp;
import megalodonte.base.components.Component;
import megalodonte.base.components.ScreenComponent;
import megalodonte.base.theme.ThemeManager;
import megalodonte.components.layout_components.Container;
import megalodonte.props.ContainerProps;
import my_app.gdx.BitmapFontRenderer;
import my_app.gdx.GdxFontLoader;
import my_app.storage.AppSettings;
import my_app.storage.AppStorage;

import java.nio.file.Path;

public class Main {

    static class ScreenTest implements ScreenComponent{

        @Override
        public Component render() {

            String skinDir = "/home/eliezer/Desktop/dev/megalodonte-world/scene2d-suite/gdx-skins/arcade/skin/";

            // Image quer URL -> precisa do "file:"
            Image atlasImage = new Image("file:" + skinDir + "arcade-ui.png");

            // GdxFontLoader usa FileHandle -> java.io.File -> path de sistema PURO, sem "file:"
            GdxFontLoader.LoadedFont title = GdxFontLoader.load(
                    Path.of(skinDir + "arcade-ui.atlas"),
                    Path.of(skinDir + "title-export.fnt"),
                    "title-export"
            );

            BitmapFontRenderer titleFont = new BitmapFontRenderer(atlasImage, title.atlasRegion(), title.fontData());

            Canvas canvas = new Canvas(500, 200);
            titleFont.drawText(canvas.getGraphicsContext2D(), "Hello Arcade", 20, 100);
            Pane wrapper = new Pane(canvas); // Pane é Parent, Canvas não é
            return new Container(new ContainerProps().paddingAll(10)).children(
                    Component.CreateFromJavaFxNode(wrapper)
            );
        }
    }

    static void main() {
        final String version = System.getProperty("megalodonte.appVersion", "");

        AppSettings settings = AppStorage.load();
        ThemeManager.setTheme(settings.isLightTheme() ? Themes.light : Themes.dark);

        MegalodonteApp.run(context ->
        {
            var stage = context.javafxStage();
            stage.setMinWidth(1000);
            stage.setMinHeight(600);

            stage.setTitle("Scene2d - UIBuilder " + version);

            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            //context.useView(new ScreenTest());

            ThemeManager.state().subscribe(currentTheme ->
                    context.updateView(new HomeScreen(viewModel, currentTheme))
            );


        }, ev->{
            if(ev == MegalodonteApp.Event.CloseRequest){
                System.out.println("Clicked on X - close application");
                ListenerManager.disposeAll();
            }
        });
    }
}
