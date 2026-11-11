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
                    "parkour_clutch", "PARKOUR_CLUTCH",
                    "Гравитация — актив фирмы",
                    "Упадите минимум с 10 блоков (в воду, сено, слизь или на ноги) и выживите.",
                    1, 520L, 12, 2, 200L
            ),
            new Template(
                    "paranoid_spin", "PARANOID_SPIN",
                    "Датчик паранойи",
                    "Проверяйте углы как в Lethal Company: накрутите 1800° камерой (5 оборотов).",
                    1800, 450L, 10, 2, 180L
            ),
            new Template(
                    "drg_dance", "DANCE_SQUATS",
                    "Перекур по уставу DRG",
                    "Пока начальство отвернулось, присядьте (Shift) 12 раз подряд.",
                    12, 420L, 10, 1, 160L
            ),
            new Template(
                    "risk_no_armor", "RISK_SURVIVAL",
                    "Работа без страховки",
                    "Продержитесь 15 сек без брони рядом с враждебным мобом (≤8 блоков).",
                    15, 620L, 15, 2, 240L
            ),
            new Template(
                    "altitude_cloud", "ALTITUDE_CLOUD",
                    "Миграция в Облако",
                    "Заберитесь высоко в небо (Y ≥ 160) и проведите там 10 секунд.",
                    10, 520L, 12, 2, 200L
            ),
            new Template(
                    "hot_spring", "HOT_SPRING",
                    "Курорт у лавы",
                    "Проведите 8 секунд рядом с открытой лавой (≤4 бл.), не сгорев заживо.",
                    8, 560L, 14, 2, 220L
            ),
            new Template(
                    "bare_brawl", "BARE_HAND_HITS",
                    "Переговоры на кулаках",
                    "Нанесите 5 ударов враждебным монстрам абсолютно пустыми руками.",
                    5, 560L, 14, 2, 220L
            ),
            new Template(
                    "stunt_kills", "STUNT_KILLS",
                    "Эффектная ликвидация",
                    "Победите 2 врагов критическим ударом в прыжке, в огне или в воде.",
                    2, 620L, 15, 3, 240L
            ),
            new Template(
                    "pain_threshold", "SURVIVE_DAMAGE",
                    "Аудит болевого порога",
                    "Получите и переживите суммарно 12 ед. урона на смене без гибели.",
                    12, 500L, 12, 2, 200L
            ),
            new Template(
                    "ficsit_sink", "SELL_RESOURCES",
                    "Утилизатор FICSIT",
                    "Сдайте на «Рынке Ресурсов» любые 12 ненужных или добытых предметов.",
                    12, 480L, 10, 2, 180L
            ),
            new Template(
                    "sprint_courier", "SPRINT_COURIER",
                    "Сверхсрочный курьер",
                    "Пробегите спринтом 120 блоков по делам логистики корпорации.",
                    120, 480L, 11, 2, 180L
            ),
            new Template(
                    "creep_downsize", "DOWNSIZE_ACT",
                    "Управляемый даунсайзинг",
                    "Подожгите Крипера огнивом, переживите взрыв или победите горящего моба.",
                    1, 660L, 16, 3, 260L
            ),
            new Template(
                    "weird_lunch", "TOXIC_LUNCH",
                    "Сомнительный бизнес-ланч",
                    "Съешьте гнилую плоть, паучий глаз, ядовитый картофель или сырое мясо.",
                    1, 480L, 12, 2, 180L
            ),
            new Template(
                    "debt_restructure", "BUYBACK_OR_LEASE",
                    "Реструктуризация долга",
                    "Оформите 1 лизинг стата или выкупите обратно проданную характеристику.",
                    1, 780L, 20, 4, 300L
            )
    );

    public static final int BOARD_SIZE = 6;

    private ContractBoardCatalog() {
    }

    public static boolean needsMigration(List<ContractEntry> existing) {
        if (existing == null || existing.isEmpty()) return true;
        if (existing.getFirst().title().startsWith("INDUSTRIAL")) return true;
        if (existing.stream().anyMatch(c -> c.rewardDiamonds() <= 0)) return true;
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
            board.clear();
            fillBoard(board, true);
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
