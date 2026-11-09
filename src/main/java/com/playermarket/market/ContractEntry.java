package com.playermarket.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ContractEntry(
        String id,
        String typeId,
        String title,
        String description,
        int targetProgress,
        int currentProgress,
        long rewardCr,
        int rewardDiamonds,
        boolean completed,
        boolean claimed,
        int durationDays,
        long deadlineTick,
        long penaltyCr,
        boolean active
) {
    public static final Codec<ContractEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(ContractEntry::id),
            Codec.STRING.fieldOf("typeId").forGetter(ContractEntry::typeId),
            Codec.STRING.fieldOf("title").forGetter(ContractEntry::title),
            Codec.STRING.fieldOf("description").forGetter(ContractEntry::description),
            Codec.INT.fieldOf("targetProgress").forGetter(ContractEntry::targetProgress),
            Codec.INT.fieldOf("currentProgress").forGetter(ContractEntry::currentProgress),
            Codec.LONG.fieldOf("rewardCr").forGetter(ContractEntry::rewardCr),
            Codec.INT.optionalFieldOf("rewardDiamonds", 10).forGetter(ContractEntry::rewardDiamonds),
            Codec.BOOL.fieldOf("completed").forGetter(ContractEntry::completed),
            Codec.BOOL.fieldOf("claimed").forGetter(ContractEntry::claimed),
            Codec.INT.optionalFieldOf("durationDays", 3).forGetter(ContractEntry::durationDays),
            Codec.LONG.optionalFieldOf("deadlineTick", 0L).forGetter(ContractEntry::deadlineTick),
            Codec.LONG.optionalFieldOf("penaltyCr", 250L).forGetter(ContractEntry::penaltyCr),
            Codec.BOOL.optionalFieldOf("active", false).forGetter(ContractEntry::active)
    ).apply(instance, ContractEntry::new));

    public ContractEntry(String id, String typeId, String title, String description, int targetProgress, int currentProgress, long rewardCr, boolean completed, boolean claimed, int durationDays, long deadlineTick, long penaltyCr) {
        this(id, typeId, title, description, targetProgress, currentProgress, rewardCr, 10, completed, claimed, durationDays, deadlineTick, penaltyCr, false);
    }

    public ContractEntry(String id, String typeId, String title, String description, int targetProgress, int currentProgress, long rewardCr, int rewardDiamonds, boolean completed, boolean claimed, int durationDays, long deadlineTick, long penaltyCr) {
        this(id, typeId, title, description, targetProgress, currentProgress, rewardCr, rewardDiamonds, completed, claimed, durationDays, deadlineTick, penaltyCr, false);
    }

    public ContractEntry withProgress(int added) {
        if (!active || claimed) return this;
        int next = Math.min(targetProgress, currentProgress + added);
        boolean done = next >= targetProgress;
        return new ContractEntry(id, typeId, title, description, targetProgress, next, rewardCr, rewardDiamonds, done, claimed, durationDays, deadlineTick, penaltyCr, active);
    }

    public ContractEntry accept(long currentTick) {
        return new ContractEntry(id, typeId, title, description, targetProgress, 0, rewardCr, rewardDiamonds, false, false, durationDays, currentTick + durationDays * 24000L, penaltyCr, true);
    }

    public ContractEntry markClaimed() {
        return new ContractEntry(id, typeId, title, description, targetProgress, targetProgress, rewardCr, rewardDiamonds, true, true, durationDays, deadlineTick, penaltyCr, false);
    }

    public ContractEntry withDeadline(long newDeadlineTick) {
        return new ContractEntry(id, typeId, title, description, targetProgress, currentProgress, rewardCr, rewardDiamonds, completed, claimed, durationDays, newDeadlineTick, penaltyCr, active);
    }

    public ContractEntry resetForNewCycle(long currentGameTick) {
        return new ContractEntry(id, typeId, title, description, targetProgress, 0, rewardCr, rewardDiamonds, false, false, durationDays, 0L, penaltyCr, false);
    }
}
