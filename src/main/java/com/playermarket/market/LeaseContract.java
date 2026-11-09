package com.playermarket.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LeaseContract(
        String id,
        String assetId,
        double fraction,
        long payoutCr,
        long startTick,
        long endTick,
        int durationDays
) {
    public static final Codec<LeaseContract> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(LeaseContract::id),
            Codec.STRING.fieldOf("assetId").forGetter(LeaseContract::assetId),
            Codec.DOUBLE.fieldOf("fraction").forGetter(LeaseContract::fraction),
            Codec.LONG.fieldOf("payoutCr").forGetter(LeaseContract::payoutCr),
            Codec.LONG.fieldOf("startTick").forGetter(LeaseContract::startTick),
            Codec.LONG.fieldOf("endTick").forGetter(LeaseContract::endTick),
            Codec.INT.fieldOf("durationDays").forGetter(LeaseContract::durationDays)
    ).apply(instance, LeaseContract::new));
}
