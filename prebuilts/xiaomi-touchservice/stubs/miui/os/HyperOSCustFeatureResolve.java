package miui.os;

/**
 * Stand-in for Xiaomi's feature flag resolver. TouchService looks this class up by name and calls
 * getBoolean("support_pencil_engine", false); without a true answer it refuses every client.
 */
public class HyperOSCustFeatureResolve {

    public static boolean getBoolean(String key, boolean defValue) {
        return "support_pencil_engine".equals(key) || defValue;
    }
}
