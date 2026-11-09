package com.playermarket.market;

import java.util.ArrayList;
import java.util.List;

public enum CharacterBuild {
    GLASS_CANNON(
            "glass_cannon",
            "THE GLASS CANNON",
            "High combat output sacrificed for extreme biological fragility."
    ),
    INDUSTRIAL_WORKER(
            "industrial_worker",
            "CHIEF INDUSTRIAL EXTRACTOR",
            "Optimized for high-speed mining, extended reach, and resource extraction."
    ),
    SPEED_DEMON(
            "speed_demon",
            "KINETIC ARBITRAGEUR",
            "Hyper-accelerated mobility and reflexes at the expense of heavy armor."
    ),
    CORPORATE_MACHINE(
            "corporate_machine",
            "BOARDROOM SOVEREIGN",
            "Executive privileges, broker licenses, and maximum market dividend efficiency."
    ),
    SURVIVOR(
            "survivor",
            "APEX JUGGERNAUT",
            "Fortified vitality, reinforced dermis, and unyielding physical resilience."
    ),
    FRAGILE_MILLIONAIRE(
            "fragile_millionaire",
            "HOLLOW BILLIONAIRE",
            "Stripped of physical humanity in exchange for immense liquid CR capital."
    );

    private final String id;
    private final String title;
    private final String description;

    CharacterBuild(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return "build.playermarket." + id;
    }

    public String getTitleKey() {
        return "build.playermarket." + id + ".title";
    }

    public String getDescriptionKey() {
        return "build.playermarket." + id + ".desc";
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public static List<CharacterBuild> evaluateUnlockedBuilds(PlayerMarketAccount account) {
        List<CharacterBuild> list = new ArrayList<>();

        double healthSold = account.getTotalSoldFraction(AssetType.MAX_HEALTH);
        double armorSold = account.getTotalSoldFraction(AssetType.ARMOR);
        int muscleLvl = account.getUpgradeLevel(UpgradeType.MUSCLE_OVERCLOCK);
        int critLvl = account.getUpgradeLevel(UpgradeType.COMBAT_FOCUS);
        int reflexLvl = account.getUpgradeLevel(UpgradeType.FAST_REFLEXES);

        // 1. GLASS CANNON: High damage/combat upgrades + sold health or armor
        if ((healthSold >= 0.25 || armorSold >= 0.25) && (muscleLvl + critLvl + reflexLvl >= 2 || account.hasDividend("berserker_edge") || account.hasDividend("thermal_overload"))) {
            list.add(GLASS_CANNON);
        }

        // 2. INDUSTRIAL WORKER: Mining protocol / reach / scanner or mining dividends
        int miningLvl = account.getUpgradeLevel(UpgradeType.MINING_PROTOCOL);
        int scannerLvl = account.getUpgradeLevel(UpgradeType.ORE_SCANNER);
        int reachLvl = account.getUpgradeLevel(UpgradeType.PRECISION_HANDS);
        if (miningLvl + scannerLvl + reachLvl >= 2 || (miningLvl >= 1 && (account.hasDividend("industrial_legs") || account.hasDividend("precision_hands")))) {
            list.add(INDUSTRIAL_WORKER);
        }

        // 3. SPEED DEMON: Adrenaline system / strong legs + low armor or speed dividends
        int speedLvl = account.getUpgradeLevel(UpgradeType.ADRENALINE_SYSTEM);
        int legsLvl = account.getUpgradeLevel(UpgradeType.STRONG_LEGS);
        if ((speedLvl >= 1 || legsLvl >= 2 || account.hasDividend("unburdened")) && (armorSold >= 0.10 || speedLvl + reflexLvl >= 2)) {
            list.add(SPEED_DEMON);
        }

        // 4. CORPORATE MACHINE: Corporate privileges levels >= 3
        int corpTotal = account.getUpgradeLevel(UpgradeType.PREMIUM_ACCOUNT)
                + account.getUpgradeLevel(UpgradeType.BROKER_LICENSE)
                + account.getUpgradeLevel(UpgradeType.MARKET_INSURANCE)
                + account.getUpgradeLevel(UpgradeType.VIP_CLIENT)
                + account.getUpgradeLevel(UpgradeType.PRICE_LOCK)
                + account.getUpgradeLevel(UpgradeType.DIAMOND_CASHBACK);
        if (corpTotal >= 2) {
            list.add(CORPORATE_MACHINE);
        }

        // 5. SURVIVOR: Reinforced heart + skin + recovery + low health sold
        int heartLvl = account.getUpgradeLevel(UpgradeType.REINFORCED_HEART);
        int skinLvl = account.getUpgradeLevel(UpgradeType.REINFORCED_SKIN);
        int recoveryLvl = account.getUpgradeLevel(UpgradeType.RAPID_RECOVERY);
        if (healthSold <= 0.10 && (heartLvl + skinLvl + recoveryLvl >= 2)) {
            list.add(SURVIVOR);
        }

        // 6. FRAGILE MILLIONAIRE: Many sold assets + high CR balance
        double totalSoldSum = 0.0;
        for (AssetType type : AssetType.values()) {
            totalSoldSum += account.getTotalSoldFraction(type);
        }
        if (totalSoldSum >= 1.25 && account.getCrBalance() >= 2000L) {
            list.add(FRAGILE_MILLIONAIRE);
        }

        return list;
    }
}
