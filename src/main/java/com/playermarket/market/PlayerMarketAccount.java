package com.playermarket.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.playermarket.config.MarketConfig;

import java.util.*;

public class PlayerMarketAccount {

    public static final int MAX_DAILY_DIAMOND_EXCHANGE = 10;

    public record AccountOverview(
            long crBalance,
            long totalCrEarned,
            long totalCrSpent,
            int totalDiamondsSpent,
            int totalDealsCount,
            boolean blackMarketUnlocked,
            boolean secondChanceArmed,
            boolean goldenContractArmed,
            String endingState,
            boolean accountClosed,
            boolean receivedCrumpledLetter,
            boolean readCrumpledLetter,
            boolean marketCardBlocked,
            boolean permanentImpaired,
            long lastDiamondExchangeDay,
            int diamondsExchangedToday
    ) {
        public static final Codec<AccountOverview> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("crBalance").forGetter(AccountOverview::crBalance),
                Codec.LONG.fieldOf("totalCrEarned").forGetter(AccountOverview::totalCrEarned),
                Codec.LONG.fieldOf("totalCrSpent").forGetter(AccountOverview::totalCrSpent),
                Codec.INT.fieldOf("totalDiamondsSpent").forGetter(AccountOverview::totalDiamondsSpent),
                Codec.INT.fieldOf("totalDealsCount").forGetter(AccountOverview::totalDealsCount),
                Codec.BOOL.fieldOf("blackMarketUnlocked").forGetter(AccountOverview::blackMarketUnlocked),
                Codec.BOOL.fieldOf("secondChanceArmed").forGetter(AccountOverview::secondChanceArmed),
                Codec.BOOL.fieldOf("goldenContractArmed").forGetter(AccountOverview::goldenContractArmed),
                Codec.STRING.optionalFieldOf("endingState", "NONE").forGetter(AccountOverview::endingState),
                Codec.BOOL.optionalFieldOf("accountClosed", false).forGetter(AccountOverview::accountClosed),
                Codec.BOOL.optionalFieldOf("receivedCrumpledLetter", false).forGetter(AccountOverview::receivedCrumpledLetter),
                Codec.BOOL.optionalFieldOf("readCrumpledLetter", false).forGetter(AccountOverview::readCrumpledLetter),
                Codec.BOOL.optionalFieldOf("marketCardBlocked", false).forGetter(AccountOverview::marketCardBlocked),
                Codec.BOOL.optionalFieldOf("permanentImpaired", false).forGetter(AccountOverview::permanentImpaired),
                Codec.LONG.optionalFieldOf("lastDiamondExchangeDay", -1L).forGetter(AccountOverview::lastDiamondExchangeDay),
                Codec.INT.optionalFieldOf("diamondsExchangedToday", 0).forGetter(AccountOverview::diamondsExchangedToday)
        ).apply(instance, AccountOverview::new));
    }

    public static final Codec<PlayerMarketAccount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AccountOverview.CODEC.fieldOf("overview").forGetter(PlayerMarketAccount::getOverview),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("permanentSoldFractions").forGetter(PlayerMarketAccount::getPermanentSoldFractions),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("permanentSalePayoutBasis").forGetter(PlayerMarketAccount::getPermanentSalePayoutBasis),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("selectedDividends").forGetter(PlayerMarketAccount::getSelectedDividends),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("upgradeLevels").forGetter(PlayerMarketAccount::getUpgradeLevels),
            LeaseContract.CODEC.listOf().fieldOf("activeLeases").forGetter(PlayerMarketAccount::getActiveLeases),
            ContractEntry.CODEC.listOf().fieldOf("contracts").forGetter(PlayerMarketAccount::getContracts),
            TransactionRecord.CODEC.listOf().fieldOf("history").forGetter(PlayerMarketAccount::getHistory),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("blackMarketActiveUntil").forGetter(PlayerMarketAccount::getBlackMarketActiveUntil),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("blackMarketCooldownUntil").forGetter(PlayerMarketAccount::getBlackMarketCooldownUntil),
            com.playermarket.story.StoryProgress.CODEC.optionalFieldOf("story", new com.playermarket.story.StoryProgress()).forGetter(PlayerMarketAccount::getStoryProgress)
    ).apply(instance, PlayerMarketAccount::new));

    private long crBalance;
    private long totalCrEarned;
    private long totalCrSpent;
    private int totalDiamondsSpent;
    private int totalDealsCount;
    private boolean blackMarketUnlocked;
    private boolean secondChanceArmed;
    private boolean goldenContractArmed;
    private String endingState = "NONE";
    private boolean accountClosed = false;
    private boolean receivedCrumpledLetter = false;
    private boolean readCrumpledLetter = false;
    private boolean marketCardBlocked = false;
    private boolean permanentImpaired = false;
    private long lastDiamondExchangeDay = -1L;
    private int diamondsExchangedToday = 0;

    private final Map<String, Double> permanentSoldFractions = new LinkedHashMap<>();
    private final Map<String, Long> permanentSalePayoutBasis = new LinkedHashMap<>();
    private final Map<String, String> selectedDividends = new LinkedHashMap<>();
    private final Map<String, Integer> upgradeLevels = new LinkedHashMap<>();
    private final List<LeaseContract> activeLeases = new ArrayList<>();
    private final List<ContractEntry> contracts = new ArrayList<>();
    private final List<TransactionRecord> history = new ArrayList<>();
    private final Map<String, Long> blackMarketActiveUntil = new LinkedHashMap<>();
    private final Map<String, Long> blackMarketCooldownUntil = new LinkedHashMap<>();
    private com.playermarket.story.StoryProgress storyProgress = new com.playermarket.story.StoryProgress();

    public PlayerMarketAccount() {
        this.crBalance = MarketConfig.get().startingCr;
        this.totalCrEarned = this.crBalance;
        ensureDefaultContracts();
    }

    public PlayerMarketAccount(
            AccountOverview overview,
            Map<String, Double> permanentSoldFractions,
            Map<String, Long> permanentSalePayoutBasis,
            Map<String, String> selectedDividends,
            Map<String, Integer> upgradeLevels,
            List<LeaseContract> activeLeases,
            List<ContractEntry> contracts,
            List<TransactionRecord> history,
            Map<String, Long> blackMarketActiveUntil,
            Map<String, Long> blackMarketCooldownUntil,
            com.playermarket.story.StoryProgress storyProgress
    ) {
        this.crBalance = overview.crBalance();
        this.totalCrEarned = overview.totalCrEarned();
        this.totalCrSpent = overview.totalCrSpent();
        this.totalDiamondsSpent = overview.totalDiamondsSpent();
        this.totalDealsCount = overview.totalDealsCount();
        this.blackMarketUnlocked = overview.blackMarketUnlocked();
        this.secondChanceArmed = overview.secondChanceArmed();
        this.goldenContractArmed = overview.goldenContractArmed();
        this.endingState = overview.endingState() != null ? overview.endingState() : "NONE";
        this.accountClosed = overview.accountClosed();
        this.receivedCrumpledLetter = overview.receivedCrumpledLetter();
        this.readCrumpledLetter = overview.readCrumpledLetter();
        this.marketCardBlocked = overview.marketCardBlocked();
        this.permanentImpaired = overview.permanentImpaired();
        this.lastDiamondExchangeDay = overview.lastDiamondExchangeDay();
        this.diamondsExchangedToday = Math.max(0, overview.diamondsExchangedToday());

        this.permanentSoldFractions.putAll(permanentSoldFractions);
        this.permanentSalePayoutBasis.putAll(permanentSalePayoutBasis);
        this.selectedDividends.putAll(selectedDividends);
        this.upgradeLevels.putAll(upgradeLevels);
        this.activeLeases.addAll(activeLeases);
        this.contracts.addAll(contracts);
        this.history.addAll(history);
        this.blackMarketActiveUntil.putAll(blackMarketActiveUntil);
        this.blackMarketCooldownUntil.putAll(blackMarketCooldownUntil);
        if (storyProgress != null) this.storyProgress = storyProgress;
        ensureDefaultContracts();
    }

    public AccountOverview getOverview() {
        return new AccountOverview(
                crBalance, totalCrEarned, totalCrSpent, totalDiamondsSpent,
                totalDealsCount, blackMarketUnlocked, secondChanceArmed, goldenContractArmed,
                getEndingState(), isAccountClosed(),
                isReceivedCrumpledLetter(), isReadCrumpledLetter(),
                isMarketCardBlocked(), isPermanentImpaired(),
                lastDiamondExchangeDay, diamondsExchangedToday
        );
    }

    public long getLastDiamondExchangeDay() {
        return lastDiamondExchangeDay;
    }

    public int getDiamondsExchangedToday() {
        return diamondsExchangedToday;
    }

    public int getDiamondsExchangedOnDay(long dayIndex) {
        return (dayIndex == lastDiamondExchangeDay) ? diamondsExchangedToday : 0;
    }

    public int getDailyDiamondExchangeLimit() {
        int vipLvl = getUpgradeLevel(UpgradeType.VIP_CLIENT);
        int vipBonus = switch (vipLvl) {
            case 1 -> 5;
            case 2 -> 12;
            case 3 -> 25;
            default -> vipLvl > 3 ? 25 : 0;
        };
        return MAX_DAILY_DIAMOND_EXCHANGE + vipBonus;
    }

    public int getRemainingDailyDiamondExchange(long dayIndex) {
        return Math.max(0, getDailyDiamondExchangeLimit() - getDiamondsExchangedOnDay(dayIndex));
    }

    public boolean recordDiamondExchange(long dayIndex, int count) {
        if (count <= 0) return true;
        if (lastDiamondExchangeDay != dayIndex) {
            lastDiamondExchangeDay = dayIndex;
            diamondsExchangedToday = 0;
        }
        int limit = getDailyDiamondExchangeLimit();
        if (diamondsExchangedToday + count > limit) {
            return false;
        }
        diamondsExchangedToday += count;
        return true;
    }

    public void ensureDefaultContracts() {
        ContractBoardCatalog.ensurePopulated(contracts);
    }

    public void refreshContracts() {
        ContractBoardCatalog.refreshBoard(contracts);
    }

    public void refreshContracts(long currentGameTick) {
        ContractBoardCatalog.refreshBoard(contracts);
    }

    public long getCrBalance() {
        return crBalance;
    }

    public void setCrBalance(long crBalance) {
        this.crBalance = Math.max(0L, crBalance);
    }

    public void addCr(long amount) {
        if (amount <= 0) return;
        this.crBalance += amount;
        this.totalCrEarned += amount;
        checkBlackMarketUnlock();
    }

    public boolean spendCr(long amount) {
        if (amount < 0 || this.crBalance < amount) return false;
        this.crBalance -= amount;
        this.totalCrSpent += amount;
        checkBlackMarketUnlock();
        return true;
    }

    public long forcePenaltyCr(long amount) {
        if (amount <= 0) return 0L;
        long actual = Math.min(this.crBalance, amount);
        this.crBalance -= actual;
        this.totalCrSpent += actual;
        return actual;
    }

    public void recordDiamondsSpent(int count) {
        if (count > 0) {
            this.totalDiamondsSpent += count;
            checkBlackMarketUnlock();
        }
    }

    public void incrementDealsCount() {
        this.totalDealsCount++;
        checkBlackMarketUnlock();
    }

    public void checkBlackMarketUnlock() {
        if (storyProgress.getCurrentStage() >= 4
                || getUpgradeLevel(UpgradeType.VIP_CLIENT) >= 3
                || storyProgress.getTotalCompletedProgramTasks() >= 12) {
            this.blackMarketUnlocked = true;
        }
    }

    public long getTotalCrEarned() {
        return totalCrEarned;
    }

    public long getTotalCrSpent() {
        return totalCrSpent;
    }

    public int getTotalDiamondsSpent() {
        return totalDiamondsSpent;
    }

    public int getTotalDealsCount() {
        return totalDealsCount;
    }

    public boolean isBlackMarketUnlocked() {
        checkBlackMarketUnlock();
        return (storyProgress.getCurrentStage() >= 4)
                || (getUpgradeLevel(UpgradeType.VIP_CLIENT) >= 3)
                || (storyProgress.getTotalCompletedProgramTasks() >= 12)
                || blackMarketUnlocked;
    }

    public void setBlackMarketUnlocked(boolean blackMarketUnlocked) {
        this.blackMarketUnlocked = blackMarketUnlocked;
    }

    public String getEndingState() {
        return endingState != null ? endingState : "NONE";
    }

    public void setEndingState(String endingState) {
        this.endingState = endingState;
    }

    public boolean isAccountClosed() {
        return accountClosed;
    }

    public void setAccountClosed(boolean accountClosed) {
        this.accountClosed = accountClosed;
    }

    public com.playermarket.story.StoryProgress getStoryProgress() {
        return storyProgress;
    }

    public int getStoryStage() {
        return storyProgress.getCurrentStage();
    }

    public void setStoryStage(int stage) {
        storyProgress.setCurrentStage(stage);
        if (stage >= 4) {
            this.blackMarketUnlocked = true;
        }
    }

    public List<com.playermarket.story.MailMessage> getMailbox() {
        return storyProgress.getMailbox();
    }

    public int getUnreadMailCount() {
        return storyProgress.getUnreadMailCount();
    }

    public boolean isSecondChanceArmed() {
        return secondChanceArmed;
    }

    public void setSecondChanceArmed(boolean secondChanceArmed) {
        this.secondChanceArmed = secondChanceArmed;
    }

    public boolean isGoldenContractArmed() {
        return goldenContractArmed;
    }

    public void setGoldenContractArmed(boolean goldenContractArmed) {
        this.goldenContractArmed = goldenContractArmed;
    }

    public Map<String, Double> getPermanentSoldFractions() {
        return permanentSoldFractions;
    }

    public Map<String, Long> getPermanentSalePayoutBasis() {
        return permanentSalePayoutBasis;
    }

    public Map<String, String> getSelectedDividends() {
        return selectedDividends;
    }

    public Map<String, Integer> getUpgradeLevels() {
        return upgradeLevels;
    }

    public List<LeaseContract> getActiveLeases() {
        return activeLeases;
    }

    public List<ContractEntry> getContracts() {
        return contracts;
    }

    public List<TransactionRecord> getHistory() {
        return history;
    }

    public Map<String, Long> getBlackMarketActiveUntil() {
        return blackMarketActiveUntil;
    }

    public Map<String, Long> getBlackMarketCooldownUntil() {
        return blackMarketCooldownUntil;
    }

    public double getPermanentSoldFraction(AssetType type) {
        return permanentSoldFractions.getOrDefault(type.getId(), 0.0);
    }

    public double getLeasedFraction(AssetType type) {
        double sum = 0.0;
        for (LeaseContract lease : activeLeases) {
            if (lease.assetId().equals(type.getId())) {
                sum += lease.fraction();
            }
        }
        return sum;
    }

    public double getTotalSoldFraction(AssetType type) {
        return Math.min(0.95, getPermanentSoldFraction(type) + getLeasedFraction(type));
    }

    public int getUpgradeLevel(UpgradeType type) {
        return upgradeLevels.getOrDefault(type.getId(), 0);
    }

    public void setUpgradeLevel(UpgradeType type, int level) {
        if (level <= 0) {
            upgradeLevels.remove(type.getId());
        } else {
            upgradeLevels.put(type.getId(), Math.min(level, type.getMaxLevel()));
        }
        checkBlackMarketUnlock();
    }

    public boolean hasDividend(String dividendId) {
        for (Map.Entry<String, String> entry : selectedDividends.entrySet()) {
            Optional<AssetType> assetOpt = AssetType.byId(entry.getKey());
            if (assetOpt.isPresent() && getTotalSoldFraction(assetOpt.get()) > 0.001) {
                if (entry.getValue().equalsIgnoreCase(dividendId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public long calculateLeasePassiveInterestPerMinuteCr() {
        double sum = 0.0;
        for (LeaseContract lease : activeLeases) {
            double durationMult = lease.durationDays() >= 3 ? 1.25 : 1.0;
            sum += 35.0 * (lease.fraction() / 0.20) * durationMult; // Deposit yield scaled to 35 CR/min per tier
        }
        return Math.round(sum);
    }

    public long calculatePassiveDividendPerMinuteCr() {
        double sum = 0.0;
        for (Map.Entry<String, String> entry : selectedDividends.entrySet()) {
            Optional<AssetType> assetOpt = AssetType.byId(entry.getKey());
            if (assetOpt.isEmpty()) continue;
            double frac = getTotalSoldFraction(assetOpt.get());
            if (frac <= 0.001) continue;
            String divId = entry.getValue();
            double baseRate = switch (divId) {
                case "passive_cr_high" -> 25.0;
                case "passive_cr_med" -> 18.0;
                case "passive_cr_low" -> 12.0;
                default -> 0.0;
            };
            sum += baseRate * frac;
        }
        sum += calculateLeasePassiveInterestPerMinuteCr();
        int vipLvl = getUpgradeLevel(UpgradeType.VIP_CLIENT);
        if (vipLvl == 1) sum *= 1.15;
        else if (vipLvl == 2) sum *= 1.30;
        else if (vipLvl >= 3) sum *= 1.50;
        if (storyProgress.getFinalChoice().equals("PREMIUM_SHAREHOLDER")) {
            sum *= 1.50; // +50% dividend yield for executive shareholder!
        }
        return Math.round(sum);
    }

    public boolean isBlackMarketEffectActive(BlackMarketItem item, long currentGameTick) {
        Long until = blackMarketActiveUntil.get(item.getId());
        return until != null && currentGameTick < until;
    }

    public long getBlackMarketRemainingTicks(BlackMarketItem item, long currentGameTick) {
        Long until = blackMarketActiveUntil.get(item.getId());
        if (until == null || currentGameTick >= until) return 0L;
        return until - currentGameTick;
    }

    public long getBlackMarketCooldownRemainingTicks(BlackMarketItem item, long currentGameTick) {
        Long until = blackMarketCooldownUntil.get(item.getId());
        if (until == null || currentGameTick >= until) return 0L;
        return until - currentGameTick;
    }

    public void addTransaction(long gameTick, String category, String title, String detail, long deltaCr, int deltaDiamonds) {
        history.addFirst(new TransactionRecord(gameTick, category, title, detail, deltaCr, deltaDiamonds));
        while (history.size() > 60) {
            history.removeLast();
        }
    }

    public List<String> progressContracts(String typeId, int amount) {
        if (amount <= 0) return List.of();
        List<String> newlyCompleted = new ArrayList<>();
        for (int i = 0; i < contracts.size(); i++) {
            ContractEntry entry = contracts.get(i);
            boolean matches = entry.typeId().equals(typeId);
            int delta = amount;
            if (!matches && "SURVIVE_SEC".equals(typeId) && "SURVIVE_TICKS".equals(entry.typeId())) {
                matches = true;
                delta = amount * 20;
            } else if (!matches && "BUYBACK_ASSET".equals(typeId) && "BUYBACK_OR_LEASE".equals(entry.typeId())) {
                matches = true;
            }
            if (matches && entry.active() && !entry.completed()) {
                ContractEntry updated = entry.withProgress(delta);
                contracts.set(i, updated);
                if (updated.completed()) {
                    newlyCompleted.add(updated.title());
                }
            }
        }
        return newlyCompleted;
    }

    public long getTotalSoldAssetsValuation() {
        long sum = 0L;
        for (AssetType type : AssetType.values()) {
            double frac = getTotalSoldFraction(type);
            if (frac > 0.001) {
                sum += Math.round(type.getFullValueCr() * frac);
            }
        }
        return sum;
    }

    public int getSoldAssetCount() {
        int count = 0;
        for (AssetType type : AssetType.values()) {
            if (getTotalSoldFraction(type) > 0.001) count++;
        }
        return count;
    }

    public void resetAll() {
        this.crBalance = MarketConfig.get().startingCr;
        this.totalCrEarned = this.crBalance;
        this.totalCrSpent = 0L;
        this.totalDiamondsSpent = 0;
        this.totalDealsCount = 0;
        this.blackMarketUnlocked = false;
        this.secondChanceArmed = false;
        this.goldenContractArmed = false;
        this.permanentSoldFractions.clear();
        this.permanentSalePayoutBasis.clear();
        this.selectedDividends.clear();
        this.upgradeLevels.clear();
        this.activeLeases.clear();
        this.contracts.clear();
        this.history.clear();
        this.blackMarketActiveUntil.clear();
        this.blackMarketCooldownUntil.clear();
        this.receivedCrumpledLetter = false;
        this.readCrumpledLetter = false;
        this.marketCardBlocked = false;
        this.permanentImpaired = false;
        this.lastDiamondExchangeDay = -1L;
        this.diamondsExchangedToday = 0;
        ensureDefaultContracts();
    }

    public boolean isReceivedCrumpledLetter() {
        return receivedCrumpledLetter;
    }

    public boolean hasReceivedCrumpledLetter() {
        return receivedCrumpledLetter;
    }

    public void setReceivedCrumpledLetter(boolean receivedCrumpledLetter) {
        this.receivedCrumpledLetter = receivedCrumpledLetter;
    }

    public boolean isReadCrumpledLetter() {
        return readCrumpledLetter;
    }

    public boolean hasReadCrumpledLetter() {
        return readCrumpledLetter;
    }

    public void setReadCrumpledLetter(boolean readCrumpledLetter) {
        this.readCrumpledLetter = readCrumpledLetter;
    }

    public boolean isMarketCardBlocked() {
        return marketCardBlocked;
    }

    public void setMarketCardBlocked(boolean marketCardBlocked) {
        this.marketCardBlocked = marketCardBlocked;
    }

    public boolean isPermanentImpaired() {
        return permanentImpaired;
    }

    public void setPermanentImpaired(boolean permanentImpaired) {
        this.permanentImpaired = permanentImpaired;
    }
}
