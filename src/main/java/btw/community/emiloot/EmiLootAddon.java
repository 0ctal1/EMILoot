package btw.community.emiloot;

import api.AddonHandler;
import api.BTWAddon;
import api.config.AddonConfig;

public class EmiLootAddon extends BTWAddon {
    public EmiLootAddon() { super(); }
    @Override public void initialize() { AddonHandler.logMessage(this.getName() + " Version " + this.getVersionString() + " Initializing..."); }

    @Override
    public void registerConfigProperties(AddonConfig config) {
        config.registerBoolean("ChestLoot", EmiLootConfig.chestLoot);
        config.registerBoolean("MobDrops", EmiLootConfig.mobDrops);
        config.registerBoolean("BlockDrops", EmiLootConfig.blockDrops);
        config.registerBoolean("OreDistribution", EmiLootConfig.oreDistribution);
        config.registerBoolean("HideSelfDrops", EmiLootConfig.hideSelfDrops);
        config.registerString("HiddenMobs", EmiLootConfig.hiddenMobs, "e.g. Giant,PigZombie");
        config.registerBoolean("AccurateOreGraphs", EmiLootConfig.accurateOreGraphs);
        config.registerInt("BlockSamples", EmiLootConfig.blockSamples, 16, 10000);
        config.registerInt("MobSamples", EmiLootConfig.mobSamples, 16, 10000);
        config.registerInt("OreSamples", EmiLootConfig.oreSamples, 16, 100000);
    }

    @Override
    public void handleConfigProperties(AddonConfig config) {
        EmiLootConfig.chestLoot = config.getBoolean("ChestLoot");
        EmiLootConfig.mobDrops = config.getBoolean("MobDrops");
        EmiLootConfig.blockDrops = config.getBoolean("BlockDrops");
        EmiLootConfig.oreDistribution = config.getBoolean("OreDistribution");
        EmiLootConfig.hideSelfDrops = config.getBoolean("HideSelfDrops");
        EmiLootConfig.hiddenMobs = config.getString("HiddenMobs");
        EmiLootConfig.accurateOreGraphs = config.getBoolean("AccurateOreGraphs");
        EmiLootConfig.blockSamples = config.getInt("BlockSamples");
        EmiLootConfig.mobSamples = config.getInt("MobSamples");
        EmiLootConfig.oreSamples = config.getInt("OreSamples");
    }
}