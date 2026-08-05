package my_app;

import megalodonte.base.components.Component;
import megalodonte.base.state.State;
import megalodonte.components.Checkbox;
import megalodonte.components.SpacerHorizontal;
import megalodonte.components.Text;
import megalodonte.components.inputs.OnChangeResult;
import megalodonte.components.layout_components.Row;
import megalodonte.components.v2.Input;
import megalodonte.props.v2.InputProps;

/**
 * Small toolbar above the Canva letting the user set its pixel size. The
 * Canva represents a fixed target screen/HUD resolution (e.g. a libGDX
 * viewport), not an auto-sizing container, so its size needs to be explicit
 * and user-controllable for the preview to match the real game.
 */
final class CanvasSizeToolbar {

    private static final int MIN_EDGE = 16;
    private static final int MAX_EDGE = 4096;

    private CanvasSizeToolbar() {
    }

    static Component build(HomeScreenViewModel viewModel) {
        return new Row().children(
                new Text("Canvas:"),
                new SpacerHorizontal(8),
                dimensionInput(viewModel.canvasWidthState()),
                new SpacerHorizontal(4),
                new Text("×"),
                new SpacerHorizontal(4),
                dimensionInput(viewModel.canvasHeightState()),
                new SpacerHorizontal(16),
                new Checkbox("Grid", viewModel.showingGridState())
        );
    }

    private static Component dimensionInput(State<Integer> dimension) {
        State<String> text = State.of(String.valueOf(dimension.get()));

        return new Input(text, new InputProps().width(56))
                .onChange(value -> {
                    try {
                        int parsed = Integer.parseInt(value.trim());
                        if (parsed >= MIN_EDGE && parsed <= MAX_EDGE) {
                            dimension.set(parsed);
                        }
                    } catch (NumberFormatException ignored) {
                        // still typing (empty, partial number) - leave dimension untouched
                    }
                    return OnChangeResult.same(value);
                });
    }
}
