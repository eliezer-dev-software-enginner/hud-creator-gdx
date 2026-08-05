package my_app.widget;

/**
 * Same-process side-channel for the {@link WidgetSpec} currently being
 * dragged. JavaFX's {@code Dragboard} only carries standard content types
 * (string/image/files) — no arbitrary Java object — so the spec itself
 * travels via this single-slot holder instead, set in the palette entry's
 * {@code onDragDetected} and read back in the Canva's {@code onDragDropped}.
 * Safe because drag sources and targets both live in the same window/process;
 * this wouldn't work for drags between separate app instances.
 */
public final class DragPayload {

    private static WidgetSpec current;

    private DragPayload() {
    }

    public static void set(WidgetSpec spec) {
        current = spec;
    }

    public static WidgetSpec get() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
