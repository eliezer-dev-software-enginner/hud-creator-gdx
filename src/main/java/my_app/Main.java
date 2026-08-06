package my_app;

import megalodonte.ListenerManager;
import megalodonte.application.MegalodonteApp;
import megalodonte.base.theme.ThemeManager;
import my_app.storage.AppSettings;
import my_app.storage.AppStorage;

public class Main {


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
