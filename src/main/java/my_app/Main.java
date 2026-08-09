package my_app;

import javafx.scene.image.Image;
import megalodonte.ListenerManager;
import megalodonte.application.MegalodonteApp;
import megalodonte.application.MegalodonteApplication;
import megalodonte.base.theme.ThemeManager;
import my_app.storage.AppSettings;
import my_app.storage.AppStorage;

import java.util.Objects;

public class Main {

    public static final String ICON_PATH = "/assets/app_ico.png";

    public static Image loadIcon() {
        return new Image(Objects.requireNonNull(Main.class.getResourceAsStream(ICON_PATH)));
    }

    public static class AppHost extends MegalodonteApplication {}
    static void main() {
        final String version = System.getProperty("megalodonte.appVersion", "");

        AppSettings settings = AppStorage.load();
        ThemeManager.setTheme(settings.isLightTheme() ? Themes.light : Themes.dark);

        MegalodonteApp.appName("Scene2d - UIBuilder");
        MegalodonteApp.appIcon(ICON_PATH);

        MegalodonteApp.run(AppHost.class, context -> {
            var stage = context.javafxStage();
            stage.setMinWidth(1000);
            stage.setMinHeight(600);

            stage.setTitle("Scene2d - UIBuilder " + version);

            final String[] images = {"/logo_32x32.png", "/logo_256x256.png"};

            for (String image : images) {
                stage.getIcons().add(new Image(Objects.requireNonNull(Main.class.getResourceAsStream(image))));
            }

            stage.getIcons().add(Main.loadIcon());

            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            //context.useView(new ScreenTest());

            ThemeManager.state().subscribe(currentTheme ->
                    context.updateView(new HomeScreen(viewModel, currentTheme))
            );
        }, event -> {
            if(event == MegalodonteApp.Event.CloseRequest){
                System.out.println("Clicked on X - close application");
                ListenerManager.disposeAll();
            }
        });
    }
}


/*
class ScreenTest implements ScreenComponent{

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
 */