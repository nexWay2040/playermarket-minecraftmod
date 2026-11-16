package com.playermarket.story;

import java.util.Arrays;
import java.util.Optional;

/**
 * 5 Rare Modifiers with an 18% chance of triggering on active Corporate Chaos events.
 */
public enum ChaosModifier {
    NONE("none", "Стандарт"),
    HOT_POTATO("hot_potato", "ГОРЯЧАЯ КАРТОШКА HR"),
    MAGNET_PULSE("magnet_pulse", "МАГНИТНЫЙ ПЫЛЕСОС"),
    BLACKOUT("blackout", "ОТКЛЮЧЕНИЕ СВЕТА"),
    SWAP_ROULETTE("swap_roulette", "ТЕЛЕПОРТ-РОКИРОВКА"),
    FLOOR_PANIC("floor_panic", "ПОЛ — ЭТО АУДИТ-ЛАВА");

    private final String id;
    private final String title;

    ChaosModifier(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }

    public static Optional<ChaosModifier> byId(String id) {
        if (id == null) return Optional.empty();
        return Arrays.stream(values()).filter(m -> m.id.equalsIgnoreCase(id)).findFirst();
    }
}
