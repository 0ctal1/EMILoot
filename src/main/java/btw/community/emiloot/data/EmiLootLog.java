package btw.community.emiloot.data;

import api.AddonHandler;

public final class EmiLootLog {
    private EmiLootLog() {}
    public static void info(String msg) {
        AddonHandler.logMessage("[EMI Loot] " + msg);
    }
    public static void warn(String msg) {
        AddonHandler.logMessage("[EMI Loot] WARNING: " + msg);
    }
}
