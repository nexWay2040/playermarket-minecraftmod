package com.playermarket.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TransactionRecord(
        long gameTick,
        String category,
        String title,
        String detail,
        long deltaCr,
        int deltaDiamonds
) {
    public static final Codec<TransactionRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("gameTick").forGetter(TransactionRecord::gameTick),
            Codec.STRING.fieldOf("category").forGetter(TransactionRecord::category),
            Codec.STRING.fieldOf("title").forGetter(TransactionRecord::title),
            Codec.STRING.fieldOf("detail").forGetter(TransactionRecord::detail),
            Codec.LONG.fieldOf("deltaCr").forGetter(TransactionRecord::deltaCr),
            Codec.INT.fieldOf("deltaDiamonds").forGetter(TransactionRecord::deltaDiamonds)
    ).apply(instance, TransactionRecord::new));
}
