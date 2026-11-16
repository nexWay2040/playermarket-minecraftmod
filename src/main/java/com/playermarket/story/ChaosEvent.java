package com.playermarket.story;

import java.util.Arrays;
import java.util.Optional;

/**
 * 8 Golden Events of Corporate Chaos 2.0.
 * Designed to provide high-stakes cooperative and solo corporate satire gameplay.
 */
public enum ChaosEvent {
    SUPPLY_OR_MIMIC("supply_crate", "ГРУЗ С НЕБА 3D", "Сброшен 3D Контейнер Asset Corp! Открой ПКМ/ударом — алмазы или пранк HR!", 5),
    VAULT_HEIST("vault_heist", "ВЗЛОМ БАНКОМАТА 3D", "Сброшен 3D Банкомат! Пробей 6 раз (ПКМ/удар) за 22 сек — иначе штраф -10%!", 4),
    DRONE_STRIKE("drone_strike", "ОРБИТАЛЬНЫЙ ДРОН HR", "3D Дрон с вращающимся винтом навёл красный лазер! Выбеги из круга до разряда (x3)!", 4),
    BOUNTY_HUNT("bounty_hunt", "ОХОТА ЗА КОРОНОЙ 3D", "3D Корона на лидере (отбери ПКМ/ударом!) или лови Кот в Мешке в соло!", 3),
    PIGGY_BANK("piggy_bank", "БЕГЛАЯ СВИНКА-КОПИЛКА", "Пугливая Свинка-Копилка убегает! Догоните (ПКМ/удар) до побега!", 5),
    COLLECTOR_RAID("collector_raid", "ВИЗИТ КОЛЛЕКТОРА", "Одиночный рейд Коллектора Asset Corp! Победите ради CR, иначе штраф -10%!", 3),
    RAINBOW_PINATA("rainbow_pinata", "РАДУЖНАЯ ОВЦА-ПИНЬЯТА", "Шустрая овца jeb_! Стукните 3 раза за печенье, конфетти и награду!", 2),
    BRIBE_INSPECTOR("bribe_inspector", "ГОЛОДНЫЙ ИНСПЕКТОР", "Ревизор просит перекус! Кликните ПКМ с ЛЮБОЙ едой или изумрудом!", 3);

    private final String id;
    private final String title;
    private final String description;
    private final int weight;

    ChaosEvent(String id, String title, String description, int weight) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.weight = weight;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getWeight() { return weight; }

    public static Optional<ChaosEvent> byId(String id) {
        if (id == null) return Optional.empty();
        return Arrays.stream(values()).filter(e -> e.id.equalsIgnoreCase(id)).findFirst();
    }
}
