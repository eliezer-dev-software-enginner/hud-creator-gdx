package my_app;

import megalodonte.ListenerManager;
import megalodonte.application.MegalodonteApp;
import megalodonte.base.theme.ThemeManager;

public class Main {

    static void main() {
        ThemeManager.setTheme(Themes.light);

        MegalodonteApp.run(context ->
        {
            var stage = context.javafxStage();
            stage.setMinWidth(1000);
            stage.setMinHeight(600);
            stage.setTitle("Scene2d - UIBuilder");

            HomeScreenViewModel viewModel = new HomeScreenViewModel();

            ThemeManager.state().subscribe(currentTheme -> {
                HomeScreen screen = new HomeScreen(viewModel, currentTheme);
                if (stage.getScene() == null) {
                    context.useView(screen);
                } else {
                    context.updateView(screen);
                }
            });

        }, ev->{
            if(ev == MegalodonteApp.Event.CloseRequest){
                System.out.println("Clicked on X - close application");
                ListenerManager.disposeAll();
            }
        });
    }
}
