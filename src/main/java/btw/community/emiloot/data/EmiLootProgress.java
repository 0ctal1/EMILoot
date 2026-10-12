package btw.community.emiloot.data;

import emi.dev.emi.emi.EmiPort;
import emi.dev.emi.emi.runtime.EmiReloadManager;

public final class EmiLootProgress {
    private EmiLootProgress() {}
    public static void step(String what) {
        try { EmiReloadManager.step(EmiPort.literal("EMI Loot: " + what), 60_000L); }
        catch (Throwable ignored) {}
    }
}
