package miui.hardware.input;

/**
 * Stand-in for MIUI's input manager. TouchService tells it which app is drawing so the input stack
 * can adapt; there is nothing to adapt here, so the calls do nothing.
 */
public class MiuiInputManager {

    private static final MiuiInputManager sInstance = new MiuiInputManager();

    private MiuiInputManager() {}

    public static MiuiInputManager getInstance() {
        return sInstance;
    }

    public void enableStylusTouchFilmForApp(String packageName, boolean enable) {}

    public void enableStylusPostureForApp(String packageName, boolean enable) {}
}
