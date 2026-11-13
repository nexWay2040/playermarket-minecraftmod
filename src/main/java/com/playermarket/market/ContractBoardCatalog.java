package com.playermarket.market;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pool and rotating generator of fun, non-grindy corporate contracts.
 */
public final class ContractBoardCatalog {

    private record Template(
            String key,
            String typeId,
            String title,
            String description,
            int targetProgress,
            long rewardCr,
            int rewardDiamonds,
            int durationDays,
            long penaltyCr
    ) {}

    private static final List<Template> TEMPLATES = List.of(
            new Template(
                    "courier_loot", "COURIER_LOOT",
                    "Охота на курьеров и пиньяту",
                    "Поймайте Свинку-Копилку, Курицу-Курьера, Радужную Пиньяту, Верити-Колобка или отбейтесь от Коллектора.",
                    1, 280L, 3, 2, 360L
            ),
            new Template(
                    "roulette_spins", "ROULETTE_SPINS",
                    "Магия 50/50 (Казино биржи)",
                    "Сыграйте 2 раунда в «Инвест-Рулетку 50/50» на бирже (любая ставка).",
                    2, 220L, 2, 2, 300L
            ),
            new Template(
                    "bribe_pet", "BRIBE_PET",
                    "Подкуп и тимбилдинг",
                    "Покормите или погладьте едой/изумрудом Жителя, Инспектора или любое животное (ПКМ 2 раза).",
                    2, 200L, 2, 2, 280L
            ),
            new Template(
                    "throw_ballistics", "THROW_BALLISTICS",
                    "Артподготовка корпорации",
                    "Запустите 6 любых снарядов: снежки, яйца, стрелы, жемчуг или фейерверк.",
                    6, 180L, 1, 2, 260L
            ),
            new Template(
                    "parkour_clutch", "PARKOUR_CLUTCH",
                    "Гравитация — актив фирмы",
                    "Упадите минимум с 8 блоков высоты (в воду, сено, слизь или на ноги) и выживите.",
                    1, 200L, 2, 2, 280L
            ),
            new Template(
                    "paranoid_spin", "PARANOID_SPIN",
                    "Датчик паранойи",
                    "Проверяйте углы как в Lethal Company: накрутите 1080° камерой (3 оборота).",
                    1080, 170L, 1, 2, 260L
            ),
            new Template(
                    "drg_dance", "DANCE_SQUATS",
                    "Перекур по уставу DRG",
                    "Пока начальство отвернулось, присядьте (Shift) 10 раз подряд.",
                    10, 160L, 1, 1, 240L
            ),
            new Template(
                    "stunt_kills", "STUNT_KILLS",
                    "Эффектная ликвидация",
                    "Победите 2 врагов критическим ударом в прыжке, в огне или во время события.",
                    2, 260L, 3, 3, 350L
            ),
            new Template(
                    "ficsit_sink", "SELL_RESOURCES",
                    "Утилизатор FICSIT",
                    "Сдайте на «Рынке Ресурсов» любые 12 ненужных или добытых предметов.",
                    12, 180L, 1, 2, 260L
            ),
            new Template(
                    "sprint_courier", "SPRINT_COURIER",
                    "Сверхсрочный курьер",
                    "Пробегите спринтом 80 блоков по делам логистики корпорации.",
                    80, 180L, 1, 2, 260L
            ),
            new Template(
                    "creep_downsize", "DOWNSIZE_ACT",
                    "Управляемый даунсайзинг",
                    "Подожгите Крипера огнивом, переживите взрыв или победите горящего моба.",
                    1, 260L, 2, 3, 330L
            ),
            new Template(
                    "weird_lunch", "TOXIC_LUNCH",
                    "Сомнительный бизнес-ланч",
                    "Съешьте гнилую плоть, сырое мясо, печенье, ягоды или выпейте молоко.",
                    1, 170L, 1, 2, 260L
            ),
            new Template(
                    "debt_restructure", "BUYBACK_OR_LEASE",
                    "Реструктуризация долга",
                    "Оформите 1 лизинг стата или выкупите обратно проданную характеристику.",
                    1, 340L, 3, 4, 450L
            ),
            new Template(
                    "market_trades", "MARKET_TRADES",
                    "Биржевая активность",
                    "Закройте 2 любые сделки на бирже (продажа стата, сырьё или рулетка).",
                    2, 220L, 2, 2, 300L
            )
    );

    public static final int BOARD_SIZE = 6;

    private static final Set<String> DEPRECATED_TEDIOUS_TYPES = Set.of(
            "RISK_SURVIVAL", "HOT_SPRING", "ALTITUDE_CLOUD", "SURVIVE_DAMAGE"
    );

    private ContractBoardCatalog() {
    }

    public static boolean needsMigration(List<ContractEntry> existing) {
        if (existing == null || existing.isEmpty()) return true;
        if (existing.getFirst().title().startsWith("INDUSTRIAL")) return true;
        if (existing.stream().anyMatch(c -> c.rewardDiamonds() <= 0 || (!c.active() && !c.completed() && c.rewardDiamonds() > 4))) return true;
        if (existing.stream().anyMatch(c -> !c.active() && !c.completed() && DEPRECATED_TEDIOUS_TYPES.contains(c.typeId()))) return true;
        // Legacy unaccepted default 7-item static list without creative types -> upgrade unaccepted slots smoothly
        boolean allLegacyIds = existing.stream().allMatch(c ->
                c.id().equals("mining_iron")
                        || c.id().equals("resource_delivery")
                        || c.id().equals("experience_yield")
                        || c.id().equals("survival_shift")
                        || c.id().equals("combat_security")
                        || c.id().equals("market_volume")
                        || c.id().equals("asset_repayment")
        );
        boolean noneActive = existing.stream().noneMatch(c -> c.active() || c.completed());
        return allLegacyIds && noneActive;
    }

    public static void ensurePopulated(List<ContractEntry> board) {
        if (needsMigration(board)) {
            board.removeIf(c -> !c.active() && !c.completed() && (DEPRECATED_TEDIOUS_TYPES.contains(c.typeId()) || c.rewardDiamonds() <= 0 || c.rewardDiamonds() > 4));
            fillBoard(board, false);
            return;
        }
        // Keep active & unclaimed completed contracts, ensure enough non-claimed offers exist
        long openCount = board.stream().filter(c -> !c.claimed()).count();
        if (openCount < 4) {
            fillBoard(board, false);
        }
    }

    public static void refreshBoard(List<ContractEntry> board) {
        fillBoard(board, true);
    }

    public static void fillBoard(List<ContractEntry> board, boolean replaceUnaccepted) {
        List<ContractEntry> preserved = new ArrayList<>();
        Set<String> usedTypeIds = new HashSet<>();

        for (ContractEntry c : board) {
            boolean keep = c.active() || (c.completed() && !c.claimed());
            if (!replaceUnaccepted && !c.claimed()) {
                keep = true;
            }
            if (keep) {
                preserved.add(c);
                usedTypeIds.add(c.typeId());
            }
        }

        List<Template> pool = new ArrayList<>();
        for (Template t : TEMPLATES) {
            if (!usedTypeIds.contains(t.typeId())) {
                pool.add(t);
            }
        }
        Collections.shuffle(pool, ThreadLocalRandom.current());

        board.clear();
        board.addAll(preserved);

        int idx = 0;
        while (board.size() < BOARD_SIZE && idx < pool.size()) {
            Template t = pool.get(idx++);
            String uniqueId = t.key() + "_" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
            board.add(new ContractEntry(
                    uniqueId,
                    t.typeId(),
                    t.title(),
                    t.description(),
                    t.targetProgress(),
                    0,
                    t.rewardCr(),
                    t.rewardDiamonds(),
                    false,
                    false,
                    t.durationDays(),
                    0L,
                    t.penaltyCr(),
                    false
            ));
        }
    }

    public static String formatProgress(ContractEntry c) {
        return switch (c.typeId()) {
            case "SURVIVE_TICKS" -> (c.currentProgress() / 20) + " / " + (c.targetProgress() / 20) + " сек";
            case "SURVIVE_SEC", "RISK_SURVIVAL", "ALTITUDE_CLOUD", "HOT_SPRING" -> c.currentProgress() + " / " + c.targetProgress() + " сек";
            case "PARANOID_SPIN" -> c.currentProgress() + " / " + c.targetProgress() + "°";
            case "SPRINT_COURIER" -> c.currentProgress() + " / " + c.targetProgress() + " м";
            case "SURVIVE_DAMAGE" -> c.currentProgress() + " / " + c.targetProgress() + " HP";
            default -> c.currentProgress() + " / " + c.targetProgress();
        };
    }
}
