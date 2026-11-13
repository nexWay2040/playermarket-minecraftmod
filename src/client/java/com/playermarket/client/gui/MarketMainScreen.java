package com.playermarket.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.playermarket.PlayerMarketMod;
import com.playermarket.client.ClientMarketState;
import com.playermarket.market.*;
import com.playermarket.network.MarketNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class MarketMainScreen extends Screen {

    public enum Tab {
        DASHBOARD("tab.playermarket.dashboard", "ОБЗОР", "tab_overview"),
        STORY("tab.playermarket.story", "ПРОГРАММА", "tab_program"),
        MAIL("tab.playermarket.mail", "ПОЧТА", "tab_mail"),
        SELL_ASSETS("tab.playermarket.sell_assets", "ПРОДАЖА", "tab_sell"),
        BUY_BACK("tab.playermarket.buyback", "ВЫКУП", "tab_buyback"),
        RESOURCE_MARKET("tab.playermarket.resource_market", "РЕСУРСЫ", "tab_resources"),
        UPGRADE_STORE("tab.playermarket.upgrade_store", "МАГАЗИН", "tab_upgrades"),
        BLACK_MARKET("tab.playermarket.black_market", "ЧЁРНЫЙ РЫНОК", "tab_blackmarket"),
        CONTRACTS("tab.playermarket.contracts", "КОНТРАКТЫ", "tab_contracts"),
        PORTFOLIO("tab.playermarket.portfolio", "ПОРТФЕЛЬ", "tab_portfolio"),
        LEDGER("tab.playermarket.history", "ИСТОРИЯ", "tab_history"),
        NEWS("tab.playermarket.news", "НОВОСТИ", "tab_news");

        private final String translationKey;
        private final String fallbackTitle;
        private final Identifier iconId;

        Tab(String translationKey, String fallbackTitle, String iconName) {
            this.translationKey = translationKey;
            this.fallbackTitle = fallbackTitle;
            this.iconId = PlayerMarketMod.id("textures/gui/icons/" + iconName + ".png");
        }

        public String getTitle() {
            String trans = Component.translatable(translationKey).getString();
            return (trans != null && !trans.isEmpty() && !trans.equals(translationKey)) ? trans : fallbackTitle;
        }

        public Identifier getIconId() {
            return iconId;
        }
    }

    private Tab activeTab = Tab.DASHBOARD;
    private AssetType.Category assetFilter = null; // null = all
    private static final Identifier ICON_CR = PlayerMarketMod.id("textures/gui/icons/currency_cr.png");
    private static final Identifier ICON_DIAMOND = PlayerMarketMod.id("textures/gui/icons/currency_diamond.png");
    private static final Identifier ICON_LOCK_CLOSED = PlayerMarketMod.id("textures/gui/icons/lock_closed.png");
    private static final Identifier ICON_BLACKMARKET = PlayerMarketMod.id("textures/gui/icons/tab_blackmarket.png");
    private static final Identifier BODY_HEAD = PlayerMarketMod.id("textures/gui/body/body_head.png");
    private static final Identifier BODY_TORSO = PlayerMarketMod.id("textures/gui/body/body_torso.png");
    private static final Identifier BODY_HEART = PlayerMarketMod.id("textures/gui/body/body_heart.png");
    private static final Identifier BODY_ARMS = PlayerMarketMod.id("textures/gui/body/body_arms.png");
    private static final Identifier BODY_LEGS = PlayerMarketMod.id("textures/gui/body/body_legs.png");
    private static final Identifier BODY_BRACE = PlayerMarketMod.id("textures/gui/body/body_brace.png");

    private UpgradeType.Category upgradeFilter = UpgradeType.Category.BODY;
    private boolean resourceCatalogMode = false; // false = sell inventory, true = buy catalog
    private boolean bioViewMode3D = true; // true = 3D live player model + zone reticles, false = 2D detailed anatomy
    private static volatile String hoveredBioZone = null;
    private String selectedMailId = null;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int mailBodyScroll = 0;
    private int mailBodyMaxScroll = 0;
    private int lastReaderLeft = Integer.MAX_VALUE;

    public record NewsArticle(
            String id,
            String filterGroup,
            String category,
            String title,
            String date,
            String teaser,
            List<String> bodyParagraphs,
            String impactTip,
            int categoryColor
    ) {}

    private static final List<NewsArticle> NEWS_ARTICLES = List.of(
            new NewsArticle(
                    "leafxup_3d_drop_protocol",
                    "BREAKING",
                    "ОБНОВЛЕНИЕ LX-1.3.1",
                    "ДЕСАНТ 3D БАНКОМАТОВ, ДРОНОВ И БОМБ С ФИТИЛЁМ",
                    "Лификсап-вестник #5",
                    "Обычные вагонетки устарели: в мир падают 3D-сейфы, крутится Дрон HR, а на голове горит 3D Корона!",
                    List.of(
                            "Инженерный отдел Asset Corp перешёл на объёмные 3D-модели: теперь над носителем Горячей Картошки вращается светящаяся 3D-бомба, в небе висит двухвинтовый Дрон-Аудитор с красным лазером, в мир падают Банкоматы на 6 ударов, а по земле катится Верити-Колобок!",
                            "А во время цикла «БУМ НА ХЛАМ x8» цветочки, перья, фугу, гниль и даже окна-стёкла взлетают в цене в 8 раз — обчистите весь огород пока не поздно!"
                    ),
                    "§aСОВЕТ: §fЦены чипов Чёрного Рынка снижены с 8–16 ♦ до §b3–6 ♦ §f— юзай протоколы в бою!",
                    0xFF22D3EE
            ),
            new NewsArticle(
                    "leafxup_upgrader_mandate",
                    "BREAKING",
                    "ДИРЕКТИВА 50/50",
                    "УКАЗ №404: АПГРЕЙДЕР И РУЛЕТКА ОБЯЗАТЕЛЬНЫ",
                    "Лификсап-вестник #1",
                    "Попытка выжить без «казика» 50/50 и рулетки признана нарушением корпоративной культуры.",
                    List.of(
                            "Объединённый департамент развлечений постановил: без регулярных ставок 50/50 игровой день считается прожитым зря. Разрешено закладывать не только здоровье, но и последний булыжник.",
                            "Внимание: при сливе баланса в ноль система всё ещё присылает утешительную субсидию от Mr. Ledger (но второй раз на ту же удочку лучше не попадаться)."
                    ),
                    "§dСОВЕТ КАЗИНО: §fВкладка «ОБЗОР -> РУЛЕТКА 50/50» открыта всегда — даже до Этапа 2!",
                    0xFFC084FC
            ),
            new NewsArticle(
                    "leafxup_owl_bear_audit",
                    "CORP",
                    "СВОДКА ЛИФИКСАПА",
                    "СИНДИКАТ СОВЫ И МЕДВЕДЯ ОФОРМИЛ МИКРОЗАЙМ",
                    "Лификсап-вестник #2",
                    "Главные испытатели сектора заложили полверстака ради шанса x5. Никто не жалеет.",
                    List.of(
                            "Служба микрозаймов подтвердила: теперь алмазный кредит выдаётся под честное слово зелёной Совы и справку о харизме от Медведя.",
                            "Отдельное достижение дня — правильно прочитать ник «Ли-фикс-ап» с первого раза без споров в чате. Нарушителям грозит принудительная зарядка 8x Shift."
                    ),
                    "§eЗАМЕТКА: §fПеревести CR тиммейту, когда он всё проиграл в рулетку, можно командой /market pay <ник> <сумма>!",
                    0xFFFBBF24
            ),
            new NewsArticle(
                    "leafxup_korol_edict",
                    "BREAKING",
                    "УКАЗ /KOROL",
                    "НОВАЯ ДИРЕКТИВА: СВИНКА-КОПИЛКА И КУРЬЕРЫ",
                    "Лификсап-вестник #3",
                    "Надоело стоять 5 сек у мобов? Теперь в мир летят свинки-копилки, радужные пиньяты и посылки!",
                    List.of(
                            "Совет Директоров отменил скучные замеры дистанции до криперов и стояние в лаве! Вместо них прямо в мир регулярно десантируются интерактивные курьеры и овцы jeb_.",
                            "А ещё где-то бродит голодный Инспектор, который закрывает глаза на любую недостачу за один кусочек хлеба или яблочко."
                    ),
                    "§aСОВЕТ: §fПоймать Свинку, Курьера или Верити-Колобка можно и правым кликом (ПКМ), и лёгким шлепком!",
                    0xFF10B981
            ),
            new NewsArticle(
                    "leafxup_patch_note",
                    "DARK",
                    "ПАТЧНОУТ 1.1",
                    "РЕЛИЗ LEAFXUP EDITION: МОГУТ БЫТЬ СЮРПРИЗЫ",
                    "Лификсап-вестник #4",
                    "«Там могут быть весёлые баги и сбегающий верстак — зато точно реклама нашей общей тупости».",
                    List.of(
                            "Техподдержка выпустила экстренное обновление терминала: каждые ~80 секунд на весь сервер включается общая Директива Хаоса (мини-рост, батутный пол, гигантизм или рейд коллектора).",
                            "Даже если на секунду показалось, что всё под контролем — подождите полторы минуты."
                    ),
                    "§bКОМАНДА: §f/market chaos list и /market chaos trigger позволяют устроить корпоративный беспредел вручную.",
                    0xFF38BDF8
            ),
            new NewsArticle(
                    "humor_creeper_window",
                    "DARK",
                    "ЧЁРНЫЙ ЮМОР",
                    "КРИПЕР У СУНДУКА ПРИЗНАН «ПАНОРАМНЫМ ОСТЕКЛЕНИЕМ»",
                    "Мир // ЖКХ",
                    "Неожиданный взрыв на базе оформлен как срочная перепланировка и тимбилдинг.",
                    List.of(
                            "Страховая комиссия отказала в компенсации за взорванный склад: согласно договору, визит зелёного специалиста считается бесплатным улучшением вентиляции вашего дома.",
                            "Выжившая кровать и два блока земли повышены в статусе до «лофт-интерьера»."
                    ),
                    "§7ПРИМЕЧАНИЕ: §fКорпорация не страхует сундуки от любви криперов к объятиям.",
                    0xFFEF4444
            ),
            new NewsArticle(
                    "humor_villager_bread",
                    "CORP",
                    "НОВОСТИ ДЕРЕВНИ",
                    "ЖИТЕЛИ УТВЕРДИЛИ ИПОТЕКУ НА БУХАНКУ ХЛЕБА",
                    "Мир // Экономика",
                    "За 14 изумрудов фермер выдаёт один батон и сочувственное «Хмм».",
                    List.of(
                            "Местная деревенская биржа ввела антикризисный курс выпечки. Ипотека на грядку пшеницы выдаётся сроком на 300 игровых дней под залог вашей мотыги.",
                            "Впрочем, корпоративные Инспекторы до сих пор берут взятки обычной булкой с картошкой — пользуйтесь, пока они не узнали курс деревни."
                    ),
                    "§aЛАЙФХАК: §fУгостите заезжего Инспектора любой едой через ПКМ — он тут же выдаст 3 алмаза и CR!",
                    0xFF10B981
            ),
            new NewsArticle(
                    "humor_nether_vacation",
                    "DARK",
                    "КОРП. ТУРИЗМ",
                    "ГОРЯЩАЯ ПУТЁВКА В НЕЗЕР: БИЛЕТ В ОДИН КОНЕЦ",
                    "Мир // Отдых",
                    "В тариф «Эконом» включены лавовое джакузи и бодрящий кросс от гастов.",
                    List.of(
                            "Отдел заботы о сотрудниках разыгрывает бесплатные туры в Багровый лес. Обещаны незабываемые впечатления, тепло и полное отсутствие назойливых звонков менеджера.",
                            "Зелье огнестойкости, элитры и обратный портал приобретаются туристом отдельно по коммерческому курсу."
                    ),
                    "§eСОВЕТ: §fПеред входом в портал лучше оставить лишние алмазы дома — гасты не принимают возврат.",
                    0xFFF59E0B
            ),
            new NewsArticle(
                    "humor_golem_hr",
                    "DARK",
                    "ОХРАНА ТРУДА",
                    "ЖЕЛЕЗНЫЙ ГОЛЕМ ПОЛУЧИЛ ПРЕМИЮ ЗА ДЕЭСКАЛАЦИЮ",
                    "Мир // Кадры",
                    "После случайного мисклика по библиотекарю сотрудник охраны отправил нарушителя на орбиту.",
                    List.of(
                            "Начальник службы безопасности деревни продемонстрировал эталонную скорость урегулирования споров: конфликт с торговцем был исчерпан за 0.4 секунды на высоте 12 метров.",
                            "Деревенский профсоюз вручил голему красный мак за преданность профессии."
                    ),
                    "§7ТБ: §fВыбирайте пустую руку перед диалогом с жителями — железный юрист не принимает извинения.",
                    0xFF94A3B8
            ),
            new NewsArticle(
                    "humor_mining_safety",
                    "DARK",
                    "ТЕХНИКА БЕЗОПАСНОСТИ",
                    "ИНСТРУКЦИЯ ДЛЯ ШАХТЁРОВ СОКРАЩЕНА ДО 1 СТРОКИ",
                    "Мир // Шахты",
                    "«Если копаете строго под себя — снимите казённую алмазную броню».",
                    List.of(
                            "Юридический отдел упростил 400-страничный устав горнодобычи, потому что сотрудники всё равно читают только первую строчку.",
                            "Освещение глубоких пещер факелами также признано избыточной роскошью: звуки в темноте отлично повышают скорость добычи руды."
                    ),
                    "§bСОВЕТ: §fУлучшение «Сканер руд» в Магазине подсвечивает алмазы и обломки сквозь камень!",
                    0xFF38BDF8
            ),
            new NewsArticle(
                    "humor_enderman_wall",
                    "DARK",
                    "ПРОИСШЕСТВИЯ",
                    "ЭНДЕРМЕН УНЁС НЕСУЩУЮ СТЕНУ СТОЛОВОЙ",
                    "Мир // Логистика",
                    "Администрация просит не смотреть новому грузчику в глаза во время перерыва.",
                    List.of(
                            "Высококвалифицированный грузчик без объяснения причин демонтировал три блока дёрна и исчез с корпоративным ланчем.",
                            "HR просит уважать личные границы сотрудника и носить тыкву на голове при обсуждении квартальных планов."
                    ),
                    "§dПОДСКАЗКА: §fУбейте пару эндерменов и сдайте жемчуг в разделе «РЕСУРСЫ» по выгодному курсу!",
                    0xFFC084FC
            ),
            new NewsArticle(
                    "humor_phantom_overtime",
                    "DARK",
                    "ТАЙМ-МЕНЕДЖМЕНТ",
                    "ФАНТОМЫ ВЫШЛИ НА НОЧНОЙ КОНТРОЛЬ ВЫГОРАНИЯ",
                    "Мир // HR",
                    "Не спали три ночи ради плана? Небесный HR сам прилетит напомнить про кровать.",
                    List.of(
                            "Если оператор игнорирует режим отдыха, синие супервайзеры спускаются прямо с ночного неба для мягкой профилактической беседы.",
                            "Статистика показывает: 9 из 10 трудоголиков ложатся спать сразу после третьего пике сверху."
                    ),
                    "§7СОВЕТ: §fВ коопе достаточно поспать всем одновременно — супервайзеры исчезнут на рассвете.",
                    0xFF60A5FA
            ),
            new NewsArticle(
                    "humor_end_resignation",
                    "DARK",
                    "АРХИВ ЭНДА",
                    "ПАДЕНИЕ В БЕЗДНУ ЗАСЧИТАНО КАК ОТПУСК",
                    "Мир // Архив",
                    "Весь инвентарь перенесён в бесконечное облачное хранилище корпорации.",
                    List.of(
                            "Руководство поздравляет с переходом в безлимитный отпуск за пределами карты! Ваши алмазы надёжно заархивированы на уровне Y = -65.",
                            "Застрявший в левой руке тотем бессмертия передал искреннее сочувствие и попрощался."
                    ),
                    "§cНАПОМИНАНИЕ: §fПри смерти карты биржи обнуляются — но баланс CR в банке сохраняется!",
                    0xFFEF4444
            ),
            new NewsArticle(
                    "biomech_expansion",
                    "CORP",
                    "БИОИНЖЕНЕРИЯ",
                    "BIOMECH CORP: МАССОВАЯ СКУПКА АКТИВОВ",
                    "Сводка #104",
                    "Фармацевтический конгломерат зафиксировал рекордные цены выкупа HP и скорости.",
                    List.of(
                            "Трансконтинентальный холдинг BioMech Corp опубликовал срочный запрос на закупку биоматериалов и нейронных связей для колониальных экспедиций.",
                            "Аналитики рекомендуют временный лизинг: корпорация гарантирует повышенный процент дивидендов (+35 CR/мин) и 100% возврат характеристик по истечении контракта без затрат алмазов."
                    ),
                    "§aСОВЕТ ИНВЕСТОРУ: §fЛизинг здоровья на 1-5 дней даёт быстрый капитал без риска навсегда лишиться HP.",
                    0xFF10B981
            ),
            new NewsArticle(
                    "diamond_syndicate",
                    "CORP",
                    "СИНДИКАТ АЛМАЗОВ",
                    "АЛМАЗНЫЙ КАРТЕЛЬ ОБНОВИЛ ПРИВИЛЕГИИ VIP",
                    "Сводка #105",
                    "Каждый уровень VIP-клиента повышает дневной лимит обмена алмазов и даёт скидки.",
                    List.of(
                            "Для установки постоянных улучшений (VIP-клиент, Усиленный скелет, Сканер руд) принимаются натуральные алмазы Minecraft (включая полученные из Upgrader).",
                            "VIP-статус открывает Чёрный Рынок уже с 1-го уровня, снижает цены на чипы на 10-20% и поднимает доход с дивидендов на 15-50%."
                    ),
                    "§bСОВЕТ ИНВЕСТОРУ: §fЛовите Свинок-Копилок, Пиньят и Курьеров — каждый приносит по 2-3 алмаза!",
                    0xFF38BDF8
            ),
            new NewsArticle(
                    "dark_berserker",
                    "DARK",
                    "ЧЁРНЫЙ РЫНОК",
                    "ПРОТОКОЛЫ ТЁМНОГО СЕКТОРА: «БЕРСЕРК» И «ВТОРОЙ ШАНС»",
                    "Сводка #108",
                    "Подпольные чипы мгновенной реанимации и боевого разгона доступны уже с 2-го этапа!",
                    List.of(
                            "Чёрный Рынок автоматически снимает замок при достижении Этапа 2, покупке VIP I, 3 сделках на бирже (даже в Рулетку!) или выполнении 6 заданий Программы.",
                            "Внутри вы найдёте «Второй Шанс» (спасение от смерти), «Берсерк» (+40% урона) и «Золотой Контракт» (x2 выплата за следующую сделку)."
                    ),
                    "§dСОВЕТ: §fКрутаните Рулетку 3 раза по 150 CR — и доступ к нелегальным чипам откроется мгновенно!",
                    0xFFC084FC
            ),
            new NewsArticle(
                    "resource_boom",
                    "CORP",
                    "ТОВАРНЫЙ РЫНОК",
                    "ПЕРЕОЦЕНКА НЕЗЕРИТА И ЭКИПИРОВКИ В РЕСУРСАХ",
                    "Сводка #110",
                    "Незеритовые вещи и редкие артефакты теперь оцениваются в тысячи CR без багов крафта.",
                    List.of(
                            "Тяжёлая промышленность обновила формулы оценки: мечи, броня, незерит, тотемы и трезубцы больше не оцениваются в копейки.",
                            "Специальная кнопка «Оптом всё» во вкладке «РЕСУРСЫ» позволяет продать лишний лут в один клик."
                    ),
                    "§eСОВЕТ ИНВЕСТОРУ: §fНенужную броню мобов или лишнюю руду всегда можно сдать в «РЕСУРСЫ» за CR.",
                    0xFFF59E0B
            ),
            new NewsArticle(
                    "lease_deposit_guide",
                    "GUIDES",
                    "ОБУЧАЮЩИЙ РАЗДЕЛ",
                    "ЛИЗИНГ И ВКЛАДЫ: ФИНАНСОВЫЙ ДЕПОЗИТ",
                    "Инструкция #1",
                    "Корпоративные аудиторы разъясняют разницу между продажей навсегда и лизингом.",
                    List.of(
                            "В отличие от безвозвратной продажи, лизинг на 1, 3 или 5 игровых дней является финансовым вкладом (депозитом).",
                            "При оформлении лизинга вы получаете немедленный аванс CR и +35 CR/мин процентов. По истечении срока 100% стата возвращается бесплатно!"
                    ),
                    "§2СОВЕТ ИНВЕСТОРУ: §fЛизинг — лучший способ заработать стартовые CR без траты алмазов на выкуп.",
                    0xFF14B8A6
            ),
            new NewsArticle(
                    "crisis_survival_guide",
                    "GUIDES",
                    "РУКОВОДСТВО",
                    "КАК ВЫЖИТЬ ПРИ ОБВАЛЕ БИРЖИ: СТРАТЕГИЯ",
                    "Инструкция #2",
                    "Полный разбор действий трейдера при возникновении рыночной паники и краха котировок.",
                    List.of(
                            "Когда наступает «Паника на бирже» или «Крах рынка», котировки продаж падают до -35%, а выкуп дорожает.",
                            "Главное правило кризиса — НИЧЕГО НЕ ПРОДАВАТЬ в минус! Открывайте вкладку «КОНТРАКТЫ»: награды за задания в кризис взлетают на +35%..+60% CR!"
                    ),
                    "§aЗОЛОТОЕ ПРАВИЛО: §fВ кризис зарабатывайте на контрактах, курьерах и Свинках-Копилках!",
                    0xFF34D399
            ),
            new NewsArticle(
                    "diamond_earning_guide",
                    "GUIDES",
                    "РУКОВОДСТВО",
                    "ГДЕ БРАТЬ АЛМАЗЫ БЕЗ СКУЧНОЙ ШАХТЫ?",
                    "Инструкция #3",
                    "Контракты, задания Программы, Рулетка, Курьеры и Инспекторы — полный гайд.",
                    List.of(
                            "1) Выполняйте контракты в [M] -> КОНТРАКТЫ (по 10–20 ♦ каждый). 2) Ловите Свинок-Копилок, Пиньят, Курьеров и кормите Инспекторов (по 2–3 ♦!).",
                            "3) Выбивайте x5 джекпот в Рулетке 50/50 или перерабатывайте хлам в алмазы через установленный комплекс Upgrader Items!"
                    ),
                    "§bСОВЕТ: §fИдеальное комбо: берите 3 контракта сразу и ловите каждого Курьера с неба!",
                    0xFF38BDF8
            )
    );

    private NewsArticle selectedNewsArticle = null;
    private boolean section17ModalOpen = false;
    private String selectedNewsCategory = "ALL";
    private boolean finalEndingRevokeMode = false;
    private boolean howToPlayModalOpen = false;
    private final List<ClickAction> modalClickActions = new ArrayList<>();
    private int currentClipTop = -1;
    private int currentClipBottom = -1;

    private boolean isModalOpen() {
        return selectedNewsArticle != null || section17ModalOpen || howToPlayModalOpen;
    }

    public static String getHoveredBioZone() {
        return hoveredBioZone;
    }

    public static double getZoneLoss(PlayerMarketAccount account, AssetType... types) {
        return avgSoldFraction(account, types);
    }

    public static int getZoneRgb(double soldFrac) {
        return zoneColor(soldFrac);
    }

    private record ClickAction(int minX, int minY, int maxX, int maxY, Runnable action, String tooltip) {}
    private final List<ClickAction> clickActions = new CopyOnWriteArrayList<>();
    private final List<ClickAction> frameClickBuffer = new ArrayList<>();

    public MarketMainScreen() {
        super(Component.literal("PLAYER MARKET // EQUITY TERMINAL"));
    }

    @Override
    protected void init() {
        super.init();
        scrollOffset = 0;
        mailBodyScroll = 0;
        mailBodyMaxScroll = 0;
        selectedNewsArticle = null;
        section17ModalOpen = false;
        howToPlayModalOpen = false;
        hoveredBioZone = null;
        selectedNewsCategory = "ALL";
        finalEndingRevokeMode = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isModalOpen()) return true;
        if (activeTab == Tab.MAIL && mouseX >= lastReaderLeft && mailBodyMaxScroll > 0) {
            mailBodyScroll = Math.clamp(mailBodyScroll - (int) (verticalAmount * 20), 0, mailBodyMaxScroll);
            return true;
        }
        if (maxScroll > 0) {
            scrollOffset = Math.clamp(scrollOffset - (int) (verticalAmount * 28), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int getGuiWidth() {
        int target = (int) (width * 0.82);
        return Math.clamp(target, Math.min(width - 16, 520), 650);
    }

    private int getGuiHeight() {
        int target = (int) (height * 0.86);
        return Math.clamp(target, Math.min(height - 16, 290), 380);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (isModalOpen()) {
            int mx = (int) event.x();
            int my = (int) event.y();
            for (ClickAction ca : modalClickActions) {
                if (mx >= ca.minX && mx <= ca.maxX && my >= ca.minY && my <= ca.maxY) {
                    playClickSound();
                    ca.action().run();
                    return true;
                }
            }
            selectedNewsArticle = null;
            section17ModalOpen = false;
            howToPlayModalOpen = false;
            return true;
        }

        if (event.button() == 1 || event.button() == 0) {
            int mx = (int) event.x();
            int my = (int) event.y();

            int guiWidth = getGuiWidth();
            int guiHeight = getGuiHeight();
            int left = (width - guiWidth) / 2;
            int top = (height - guiHeight) / 2;
            int footerTop = top + guiHeight - 22;

            for (ClickAction ca : clickActions) {
                if (mx >= ca.minX && mx <= ca.maxX && my >= ca.minY && my <= ca.maxY) {
                    playClickSound();
                    ca.action().run();
                    return true;
                }
            }

            // Click sink on footer area outside any buttons to prevent click-through
            if (mx >= left && mx <= left + guiWidth && my >= footerTop && my <= footerTop + 22) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isModalOpen()) {
            if (event.key() == InputConstants.KEY_M || event.key() == InputConstants.KEY_ESCAPE) {
                selectedNewsArticle = null;
                section17ModalOpen = false;
                howToPlayModalOpen = false;
                return true;
            }
        }
        if (event.key() == InputConstants.KEY_M || event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        frameClickBuffer.clear();
        hoveredBioZone = null;

        // 1. Transparent background blur
        this.extractTransparentBackground(graphics);

        // 2. Main Window Dimensions (Compact ~70% screen area)
        int guiWidth = getGuiWidth();
        int guiHeight = getGuiHeight();
        int left = (width - guiWidth) / 2;
        int top = (height - guiHeight) / 2;

        int headerHeight = 30;
        int footerHeight = 22;
        int sidebarWidth = 132;

        int sidebarTop = top + headerHeight;
        int footerTop = top + guiHeight - footerHeight;

        int contentLeft = left + sidebarWidth + 6;
        int contentTop = sidebarTop + 6;
        int contentRight = left + guiWidth - 6;
        int contentBottom = footerTop - 4;
        int contentWidth = contentRight - contentLeft;
        int contentHeight = contentBottom - contentTop;

        // 3. Render Nine-Slice Window Background
        drawSprite(graphics, "panel", left, top, guiWidth, guiHeight, 0xF50A0E17);
        drawSprite(graphics, "header", left, top, guiWidth, headerHeight, 0xFF121824);
        drawSprite(graphics, "footer", left, footerTop, guiWidth, footerHeight, 0xFF10141D);
        drawSprite(graphics, "sidebar", left, sidebarTop, sidebarWidth, footerTop - sidebarTop, 0xFF0D111A);

        // Click-sink boundaries to prevent clicking through header and footer to scrolled items behind
        registerClick(left, top, left + guiWidth, sidebarTop, () -> {}, null);
        registerClick(left, footerTop, left + guiWidth, footerTop + footerHeight, () -> {}, null);

        PlayerMarketAccount account = ClientMarketState.getAccount();
        var campaign = ClientMarketState.getSharedCampaign();
        if (account.isMarketCardBlocked() || account.isAccountClosed() || account.isPermanentImpaired()
                || campaign.isEndingStarted() || "SETTLED".equals(campaign.getEndingState())) {
            this.onClose();
            return;
        }
        MarketEvent event = ClientMarketState.getActiveEvent();
        int diamonds = (minecraft != null && minecraft.player != null)
                ? MarketAttributeApplier.countVanillaDiamonds(minecraft.player.getInventory()) : 0;

        // 4. Header Bar Content
        // Right-aligned Currency Badge with crisp 10x10 CR & Diamond icons + live cycle countdown
        String crText = finalEndingRevokeMode ? "§k99999§r §c(REVOKED)" : ("§f§l" + formatNumber(account.getCrBalance()) + " §aCR");
        String diaText = "§b§l" + diamonds;
        int balPillW = 16 + font.width(crText) + 10 + 14 + font.width(diaText) + 8;
        int balX = left + guiWidth - balPillW - 8;

        String fullHeaderTitle = "§a§lPLAYER MARKET §8// §e§lLEAFXUP EDITION §8[§7v1.4.0§8]";
        String compactHeaderTitle = "§a§lPLAYER MARKET §8[§7v1.4.0§8]";
        String headerTitle = font.width(fullHeaderTitle) <= (balX - left - 18) ? fullHeaderTitle : compactHeaderTitle;
        drawClampedText(graphics, headerTitle, left + 10, top + 10, Math.max(70, balX - left - 18), 0xFFE2E8F0, false);

        drawSprite(graphics, "hud_bg", balX, top + 5, balPillW, 15, finalEndingRevokeMode ? 0xFF280D12 : 0xFF0E1622);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_CR, balX + 5, top + 7, 0.0f, 0.0f, 10, 10, 16, 16, 16, 16);
        graphics.text(font, crText, balX + 17, top + 9, 0xFFFFFFFF, false);
        int sepX = balX + 17 + font.width(crText) + 4;
        graphics.text(font, "§8|", sepX, top + 9, 0xFF475569, false);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_DIAMOND, sepX + 6, top + 7, 0.0f, 0.0f, 10, 10, 16, 16, 16, 16);
        graphics.text(font, diaText, sepX + 18, top + 9, 0xFFFFFFFF, false);

        // Live market cycle countdown pill if space allows cleanly between title and balance pill
        int titleEndX = left + 10 + font.width(headerTitle) + 8;
        boolean campaignAccepted = ClientMarketState.getSharedCampaign().isCampaignAccepted();
        long nextEvTick = ClientMarketState.getNextEventSwitchTick();
        long curWorldTick = ClientMarketState.getCurrentGameTick();
        String cycleBadge;
        if (!campaignAccepted) {
            cycleBadge = "§8● §7ЖДЁТ ДОГОВОР";
        } else {
            long remEvTicks = Math.max(0L, nextEvTick - curWorldTick);
            int remSecTotal = (int) (remEvTicks / 20L);
            String cycleStr = nextEvTick > 0L
                    ? String.format(Locale.ROOT, "§e%02d:%02d", remSecTotal / 60, remSecTotal % 60)
                    : "§7--:--";
            cycleBadge = "§8● §7ЦИКЛ " + cycleStr;
        }
        int cycleW = font.width(cycleBadge) + 12;
        if (balX - titleEndX >= cycleW + 8) {
            int cycleX = balX - cycleW - 6;
            drawSprite(graphics, "hud_bg", cycleX, top + 5, cycleW, 15, 0xFF0D1420);
            graphics.text(font, cycleBadge, cycleX + 6, top + 9, 0xFFE2E8F0, false);
        }

        int bgMouseX = isModalOpen() ? -9999 : mouseX;
        int bgMouseY = isModalOpen() ? -9999 : mouseY;

        // 5. Sidebar Navigation
        Tab[] tabs = Tab.values();
        int availableSidebarH = footerTop - sidebarTop;
        int tabHeight = Math.max(16, availableSidebarH / tabs.length);
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int ty = sidebarTop + (i * tabHeight);
            if (ty + tabHeight > footerTop) {
                ty = footerTop - tabHeight;
            }
            boolean isSelected = (t == activeTab);
            boolean isHovered = (bgMouseX >= left + 2 && bgMouseX <= left + sidebarWidth && bgMouseY >= ty && bgMouseY < ty + tabHeight);

            if (isSelected) {
                drawSprite(graphics, "tab_active", left + 2, ty, sidebarWidth - 2, tabHeight, 0xFF1A332C);
            } else if (isHovered) {
                drawSprite(graphics, "tab_hover", left + 2, ty, sidebarWidth - 2, tabHeight, 0xFF1E2838);
            }

            int textColor = isSelected ? 0xFF34D399 : (isHovered ? 0xFFFFFFFF : 0xFF94A3B8);

            // Draw custom 16x16 icon texture (rendered at 12x12)
            int iconSize = 12;
            int iconX = left + 6;
            int iconY = ty + (tabHeight - iconSize) / 2;
            try {
                graphics.blit(RenderPipelines.GUI_TEXTURED, t.getIconId(), iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, 16, 16, 16, 16);
            } catch (Throwable ignored) {}

            String tabText = (isSelected ? "§l" : "") + t.getTitle();
            boolean isLockedBlackMarket = (t == Tab.BLACK_MARKET && !account.isBlackMarketUnlocked());
            if (t == Tab.MAIL) {
                int unread = account.getStoryProgress().getUnreadMailCount();
                if (unread > 0) {
                    tabText += " §c(" + unread + ")";
                }
            }
            int textMaxW = isLockedBlackMarket ? (sidebarWidth - 38) : (sidebarWidth - 26);
            drawClampedText(graphics, tabText, left + 22, ty + (tabHeight - 8) / 2, textMaxW, textColor, false);
            if (isLockedBlackMarket) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_LOCK_CLOSED, left + sidebarWidth - 14, ty + (tabHeight - 9) / 2, 0.0f, 0.0f, 9, 9, 16, 16, 16, 16);
            }

            final Tab clickedTab = t;
            registerClick(left + 2, ty, left + sidebarWidth, ty + tabHeight, () -> {
                activeTab = clickedTab;
                scrollOffset = 0;
                mailBodyScroll = 0;
                sendMarketAction("TAB_OPENED", clickedTab.name(), "", 0, 0.0);
            }, isLockedBlackMarket ? "Рулетка уже открыта! Нелегальные чипы откроются с Этапа 2 / 3 сделок" : null);
        }

        // 6. Footer Content
        // Sidebar footer column
        graphics.text(font, "§8PM // LX-1.3.1", left + 8, footerTop + 6, 0xFF475569, false);

        // Footer main status starts to the right of the sidebar (left + sidebarWidth + 8)
        String eventBadge = "§6[ " + event.getHeadline() + " ]";
        int maxEbWidth = Math.max(75, (guiWidth - sidebarWidth) / 2 - 12);
        int ebWidth = Math.min(font.width(eventBadge), maxEbWidth);
        int footerLeft = left + sidebarWidth + 8;
        int footerMaxWidth = Math.max(60, guiWidth - sidebarWidth - ebWidth - 26);
        String activeMsg = ClientMarketState.getActiveStatusMessage();
        if (!activeMsg.isEmpty()) {
            drawClampedText(graphics, "§e✦ " + activeMsg, footerLeft, footerTop + 6, footerMaxWidth, 0xFFFFFFFF, false);
        } else {
            String scanner = ClientMarketState.getOreScannerReport();
            String footerText = (!scanner.isEmpty() && !scanner.equals("OFFLINE"))
                    ? "§bСКАНЕР РУД: §f" + scanner
                    : "§8[M / ESC: Закрыть] §7ВСЁ ИМЕЕТ СВОЮ ЦЕНУ.";
            drawClampedText(graphics, footerText, footerLeft, footerTop + 6, footerMaxWidth, 0xFF94A3B8, false);
        }

        drawClampedText(graphics, eventBadge, left + guiWidth - ebWidth - 10, footerTop + 6, ebWidth, 0xFFFBBF24, false);

        // 7. Render Active Tab Content
        switch (activeTab) {
            case DASHBOARD -> renderDashboard(graphics, account, event, diamonds, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case STORY -> renderStory(graphics, account, event, diamonds, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case MAIL -> renderMail(graphics, account, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case SELL_ASSETS -> renderSellAssets(graphics, account, event, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case BUY_BACK -> renderBuyback(graphics, account, event, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case RESOURCE_MARKET -> renderResourceMarket(graphics, account, event, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case UPGRADE_STORE -> renderUpgradeStore(graphics, account, event, diamonds, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case BLACK_MARKET -> renderBlackMarket(graphics, account, event, diamonds, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case CONTRACTS -> renderContracts(graphics, account, event, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case PORTFOLIO -> renderPortfolio(graphics, account, event, diamonds, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case LEDGER -> renderLedger(graphics, account, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
            case NEWS -> renderNews(graphics, event, contentLeft, contentTop, contentWidth, contentHeight, bgMouseX, bgMouseY);
        }

        // 8. Active Modal Popups (How To Play, News Article, or Hidden Section 17.4 Disclosure)
        if (section17ModalOpen) {
            modalClickActions.clear();
            renderSection17Modal(graphics, width, height, mouseX, mouseY);
        } else if (howToPlayModalOpen) {
            modalClickActions.clear();
            renderHowToPlayModal(graphics, width, height, mouseX, mouseY);
        } else if (selectedNewsArticle != null) {
            modalClickActions.clear();
            renderNewsModal(graphics, selectedNewsArticle, width, height, mouseX, mouseY);
        }

        // Commit frame clicks
        clickActions.clear();
        clickActions.addAll(frameClickBuffer);

        // Tooltips (clean multiline wrapped, only when modal is not open)
        if (!isModalOpen()) {
            for (ClickAction ca : clickActions) {
                if (ca.tooltip != null && mouseX >= ca.minX && mouseX <= ca.maxX && mouseY >= ca.minY && mouseY <= ca.maxY) {
                    renderCustomMultilineTooltip(graphics, ca.tooltip, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    // ==========================================
    // TAB: DASHBOARD
    // ==========================================
    private void renderDashboard(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event, int diamonds,
                                 int left, int top, int width, int height, int mouseX, int mouseY) {
        maxScroll = 0;
        int cardWidth = (width - 12) / 3;
        int cardHeight = 56;

        // Card 1: Balance & Liquidity
        drawCard(graphics, left, top, cardWidth, cardHeight, mouseX, mouseY);
        drawClampedText(graphics, "§7БАЛАНС СЧЁТА", left + 8, top + 5, cardWidth - 16, 0xFF94A3B8, false);
        drawClampedText(graphics, "§a§l" + formatNumber(account.getCrBalance()) + " CR", left + 8, top + 17, cardWidth - 16, 0xFF10B981, false);
        drawClampedText(graphics, "§8Доход: §7" + formatNumber(account.getTotalCrEarned()) + " CR", left + 8, top + 29, cardWidth - 16, 0xFF64748B, false);
        drawClampedText(graphics, "§8Расход: §7" + formatNumber(account.getTotalCrSpent()) + " CR", left + 8, top + 41, cardWidth - 16, 0xFF64748B, false);

        // Card 2: Sold Assets Valuation & Bio-Contribution Multiplier
        int c2Left = left + cardWidth + 6;
        drawCard(graphics, c2Left, top, cardWidth, cardHeight, mouseX, mouseY);
        drawClampedText(graphics, "§7АКТИВЫ & СТАТУС", c2Left + 8, top + 5, cardWidth - 16, 0xFF94A3B8, false);
        drawClampedText(graphics, "§6§l" + formatNumber(account.getTotalSoldAssetsValuation()) + " CR", c2Left + 8, top + 17, cardWidth - 16, 0xFFF59E0B, false);
        int soldCount = account.getSoldAssetCount();
        drawClampedText(graphics, "§8Статов: §f" + soldCount + " §8| Лиз: §d" + account.getActiveLeases().size(), c2Left + 8, top + 29, cardWidth - 16, 0xFF64748B, false);
        String bioStatusLine = soldCount <= 0
                ? "§cШтраф: -30% & налог!"
                : (soldCount >= 3 ? "§aVIP: +35% CR & +1♦" : "§eНорма: ещё до VIP");
        drawClampedText(graphics, bioStatusLine, c2Left + 8, top + 41, cardWidth - 16, 0xFF94A3B8, false);

        // Card 3: Upgrades & Diamonds
        int c3Left = c2Left + cardWidth + 6;
        drawCard(graphics, c3Left, top, cardWidth, cardHeight, mouseX, mouseY);
        drawClampedText(graphics, "§7АЛМАЗЫ & VIP", c3Left + 8, top + 5, cardWidth - 16, 0xFF94A3B8, false);
        drawClampedText(graphics, "§b§l" + diamonds + " ♦ §7(инв.)", c3Left + 8, top + 17, cardWidth - 16, 0xFF38BDF8, false);
        long dayIdx = (minecraft != null && minecraft.level != null) ? (minecraft.level.getOverworldClockTime() / 24000L) : 0L;
        int dailyLim = account.getDailyDiamondExchangeLimit();
        int remExch = account.getRemainingDailyDiamondExchange(dayIdx);
        drawClampedText(graphics, "§8Обм: §b" + remExch + "/" + dailyLim + " §8| Вл: §b" + account.getTotalDiamondsSpent() + " ♦", c3Left + 8, top + 29, cardWidth - 16, 0xFF64748B, false);
        int vipLvl = account.getUpgradeLevel(UpgradeType.VIP_CLIENT);
        drawClampedText(graphics, "§8VIP: §e" + (vipLvl > 0 ? "Ур. " + vipLvl : "Базовый"), c3Left + 8, top + 41, cardWidth - 16, 0xFF64748B, false);

        // Market Event & Synchronized Co-op / Chaos Banner
        int bannerY = top + cardHeight + 6;
        int bannerH = 54;
        drawSprite(graphics, "hud_bg", left, bannerY, width, bannerH, 0xFF141A26);

        int helpW = 88;
        int helpH = 14;
        int helpX = left + width - helpW - 6;
        int helpY = bannerY + 4;
        drawSmallButton(graphics, "btn_blue", helpX, helpY, helpW, helpH, "? КАК ИГРАТЬ", mouseX, mouseY, () -> {
            howToPlayModalOpen = true;
        }, "Открыть памятку игрока: как зарабатывать, штрафы и 3D-ивенты");

        long curTick = ClientMarketState.getCurrentGameTick();
        String chaosId = ClientMarketState.getActiveChaosId();
        long chaosEnds = ClientMarketState.getActiveChaosEndsTick();
        var activeChaosOpt = (chaosId != null && !"NONE".equals(chaosId) && chaosEnds > curTick)
                ? com.playermarket.story.CorporateChaosDirector.ChaosDirective.byId(chaosId)
                : java.util.Optional.<com.playermarket.story.CorporateChaosDirector.ChaosDirective>empty();

        int headMaxW = Math.max(80, helpX - (left + 8) - 8);
        String bannerDescText;
        int bannerDescColor;
        if (activeChaosOpt.isPresent()) {
            var cd = activeChaosOpt.get();
            long remSec = Math.max(0L, (chaosEnds - curTick) / 20L);
            drawClampedText(graphics, "§c§l⚡ ДИРЕКТИВА ХАОСА: §e" + cd.getTitle() + " §8(" + remSec + "с)",
                    left + 8, bannerY + 6, headMaxW, 0xFFFBBF24, false);
            bannerDescText = "§f" + cd.getDescription();
            bannerDescColor = 0xFFE2E8F0;
        } else {
            drawClampedText(graphics, "§6§lСВОДКА РЫНКА: §f" + event.getHeadline(), left + 8, bannerY + 6, headMaxW, 0xFFFBBF24, false);
            bannerDescText = "§7" + event.getDescription();
            bannerDescColor = 0xFF94A3B8;
        }

        List<net.minecraft.util.FormattedCharSequence> bannerDescLines =
                font.split(Component.literal(bannerDescText), width - 16);
        for (int d = 0; d < Math.min(2, bannerDescLines.size()); d++) {
            graphics.text(font, bannerDescLines.get(d), left + 8, bannerY + 19 + (d * 9), bannerDescColor, false);
        }

        String coopRaw = ClientMarketState.getCoopSummary();
        if (coopRaw != null && !coopRaw.isEmpty()) {
            StringBuilder coopLine = new StringBuilder("§bКООП: ");
            String[] parts = coopRaw.split("\\|");
            for (int pi = 0; pi < parts.length; pi++) {
                String[] fields = parts[pi].split(":");
                if (fields.length >= 3) {
                    if (pi > 0) coopLine.append(" §8| ");
                    coopLine.append("§f").append(fields[0])
                            .append(" §a").append(fields[1]).append("%")
                            .append(" §8(§e").append(fields[2]).append(" CR§8)");
                }
            }
            drawClampedText(graphics, coopLine.toString(), left + 8, bannerY + 40, width - 16, 0xFF94A3B8, false);
        } else {
            String eventMods = String.format(Locale.ROOT, "§8Мнж: Тело x%.2f | Движ x%.2f | Рес x%.2f | Вык x%.2f",
                    event.getBodyMultiplier(), event.getMovementMultiplier(), event.getResourceBuyMultiplier(), event.getBuybackCostMultiplier());
            drawClampedText(graphics, eventMods, left + 8, bannerY + 40, width - 16, 0xFF64748B, false);
        }

        // Quick Navigation Buttons (5 compact buttons incl. Roulette Casino!)
        int navY = bannerY + bannerH + 5;
        int navGap = 5;
        int navW = (width - navGap * 4) / 5;
        int navLastW = width - (navW + navGap) * 4;
        int navH = 20;

        drawButton(graphics, "btn_amber", left, navY, navW, navH, "ПРОГРАММА", mouseX, mouseY, () -> {
            activeTab = Tab.STORY;
            scrollOffset = 0;
            sendMarketAction("TAB_OPENED", "STORY", "", 0, 0.0);
        });

        drawButton(graphics, "btn_green", left + (navW + navGap), navY, navW, navH, "ПРОДАЖА", mouseX, mouseY, () -> {
            activeTab = Tab.SELL_ASSETS;
            scrollOffset = 0;
            sendMarketAction("TAB_OPENED", "SELL_ASSETS", "", 0, 0.0);
        });

        drawButton(graphics, "btn_blue", left + (navW + navGap) * 2, navY, navW, navH, "МАГАЗИН", mouseX, mouseY, () -> {
            activeTab = Tab.UPGRADE_STORE;
            scrollOffset = 0;
            sendMarketAction("TAB_OPENED", "UPGRADE_STORE", "", 0, 0.0);
        });

        drawButton(graphics, "btn_purple", left + (navW + navGap) * 3, navY, navW, navH, "РУЛЕТКА ♦", mouseX, mouseY, () -> {
            activeTab = Tab.BLACK_MARKET;
            scrollOffset = 0;
            sendMarketAction("TAB_OPENED", "BLACK_MARKET", "", 0, 0.0);
        });

        drawButton(graphics, "btn_gray", left + (navW + navGap) * 4, navY, navLastW, navH, "ПОЧТА", mouseX, mouseY, () -> {
            activeTab = Tab.MAIL;
            scrollOffset = 0;
            sendMarketAction("TAB_OPENED", "MAIL", "", 0, 0.0);
        });

        // Telemetry Deck: Hybrid 3D/2D Cyber Bio-Scanner + Live Market Pulse Sparkline
        int archY = navY + navH + 6;
        int archH = height - (archY - top);
        renderTelemetryDeck(graphics, account, event, left, archY, width, archH, mouseX, mouseY);
    }

    // ==========================================
    // TAB: STORY (The Asset Program - 6 Stages, 5 Tasks each, 12 Side Quests, Final Transaction)
    // ==========================================
    private void renderStory(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event, int diamonds,
                             int left, int top, int width, int height, int mouseX, int mouseY) {
        com.playermarket.story.StoryProgress story = account.getStoryProgress();
        int currentStage = story.getCurrentStage();
        com.playermarket.story.StoryQuests.StageInfo stageInfo =
                com.playermarket.story.StoryQuests.STAGES.get(Math.clamp(currentStage - 1, 0, com.playermarket.story.StoryQuests.STAGES.size() - 1));

        List<com.playermarket.story.StoryQuests.StageTask> tasks = com.playermarket.story.StoryQuests.getTasksForStage(currentStage);
        int completedTasks = 0;
        for (var t : tasks) {
            if (story.isTaskCompleted(t.id())) completedTasks++;
        }

        // Calculate total content height for scrolling
        int headerBannerH = 68;
        int tasksSectionH = 22 + (tasks.size() * 54);
        int finalSectionH = (currentStage == 6) ? 162 : 0;
        int sideQuestsH = 26 + (com.playermarket.story.StoryQuests.SIDE_QUESTS.size() * 52);
        int totalStoryH = headerBannerH + tasksSectionH + finalSectionH + sideQuestsH + 20;

        maxScroll = Math.max(0, totalStoryH - height);
        setScissor(graphics, left, top, left + width, top + height);

        int cy = top - scrollOffset;

        // 1. Stage Info Banner
        drawSprite(graphics, "hud_bg", left, cy, width, headerBannerH, 0xFF141926);
        drawClampedText(graphics, "§e§lЭТАП " + currentStage + ": §f" + stageInfo.name() + " §8// §7" + stageInfo.subtitle(),
                left + 10, cy + 6, width - 20, 0xFFFBBF24, false);
        drawClampedText(graphics, "§7Статус допуска: §a" + stageInfo.corporateStanding(),
                left + 10, cy + 20, width - 20, 0xFF34D399, false);
        drawClampedText(graphics, "§8" + stageInfo.description(),
                left + 10, cy + 34, width - 20, 0xFF94A3B8, false);

        int barW = width - 140;
        int barH = 6;
        int barX = left + 10;
        int barY = cy + 50;
        double stageProgressRatio = tasks.isEmpty() ? 1.0 : ((double) completedTasks / tasks.size());
        drawProgressBar(graphics, barX, barY, barW, barH, stageProgressRatio, "bar_amber");
        drawClampedText(graphics, "§fЗаданий: §e" + completedTasks + " / " + tasks.size() + (stageProgressRatio >= 1.0 ? " §a[ЭТАП ЗАКРЫТ]" : ""),
                barX + barW + 8, cy + 49, 120, 0xFFFFFFFF, false);

        cy += headerBannerH + 8;

        // 2. Stage Tasks Header + Anti-abuse Manual Event Mob Spawner Button
        drawSprite(graphics, "hud_bg", left, cy, width, 18, 0xFF0D121B);
        int spawnBtnW = 104;
        int spawnBtnH = 14;
        int spawnBtnX = left + width - spawnBtnW - 4;
        int spawnBtnY = cy + 2;
        drawClampedText(graphics, "§a§lОСНОВНЫЕ ЗАДАЧИ ЭТАПА (ВЫПОЛНИТЕ ДЛЯ ПРОДВИЖЕНИЯ ПО СЮЖЕТУ):",
                left + 8, cy + 5, Math.max(80, width - spawnBtnW - 18), 0xFF10B981, false);

        boolean chaosBusy = !"NONE".equals(ClientMarketState.getActiveChaosId());
        boolean spawnLocked = finalEndingRevokeMode || !ClientMarketState.getSharedCampaign().isCampaignAccepted() || chaosBusy;
        boolean spawnHover = mouseX >= spawnBtnX && mouseX <= spawnBtnX + spawnBtnW
                && mouseY >= spawnBtnY && mouseY <= spawnBtnY + spawnBtnH;
        int spawnBg = spawnLocked ? 0xFF1E293B : (spawnHover ? 0xFF059669 : 0xFF065F46);
        drawSprite(graphics, spawnHover && !spawnLocked ? "btn_green_hover" : "btn_green",
                spawnBtnX, spawnBtnY, spawnBtnW, spawnBtnH, spawnBg);
        String spawnLabel = finalEndingRevokeMode ? "§8✦ ЗАКРЫТО" : (chaosBusy ? "§7✦ ХАОС ИДЁТ" : "§e✦ ИВЕНТ-МОБ");
        drawClampedCenteredText(graphics, spawnLabel, spawnBtnX + spawnBtnW / 2, spawnBtnY + 3, spawnBtnW - 6, 0xFFFFFFFF, false);
        registerClick(spawnBtnX, spawnBtnY, spawnBtnX + spawnBtnW, spawnBtnY + spawnBtnH, () -> {
            playClickSound();
            sendMarketAction(MarketNetworking.ActionType.SPAWN_EVENT_MOB.name(), "stage_auto", "", 0, 0.0);
        }, "§e§lПризвать ивент-моба этапа\n§7Свинка, Пиньята, Инспектор, Курьер, Верити 3D или Груз 3D.\n§8Защита от фарма: макс. 1 в мире, пауза во время хаоса, КД 35 сек.");
        cy += 22;

        // Stage Tasks List (Expanded multiline cards with full text visibility)
        int taskItemH = 50;
        int taskSpacing = 54;
        for (com.playermarket.story.StoryQuests.StageTask task : tasks) {
            boolean isDone = story.isTaskCompleted(task.id());
            boolean isClaimed = story.isTaskRewardClaimed(task.id());

            drawSprite(graphics, isDone ? "card_selected" : "card", left, cy, width, taskItemH, isDone ? 0xFF13221C : 0xFF10141D);

            int btnW = 105;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 14;

            int textMaxW = width - btnW - 24;

            String titlePrefix = isClaimed ? "§8✔ " : (isDone ? "§a✦ " : "§7○ ");
            drawClampedText(graphics, titlePrefix + "§f§l" + task.title(), left + 8, cy + 4, textMaxW, 0xFFFFFFFF, false);

            List<net.minecraft.util.FormattedCharSequence> descLines =
                    font.split(Component.literal("§7" + task.description()), textMaxW);
            int dY = cy + 16;
            for (int d = 0; d < Math.min(2, descLines.size()); d++) {
                graphics.text(font, descLines.get(d), left + 8, dY, 0xFF94A3B8, false);
                dY += 10;
            }

            String rewardStr = "§eНаграда: §a+" + task.rewardCr() + " CR" + (task.rewardDiamonds() > 0 ? " §8| §b+" + task.rewardDiamonds() + " ♦" : "");
            graphics.text(font, rewardStr, left + 8, cy + 37, 0xFFCBD5E1, false);

            if (isClaimed) {
                drawButton(graphics, "btn_gray", btnX, btnY, btnW, btnH, "ПОЛУЧЕНО", mouseX, mouseY, () -> {});
            } else if (isDone) {
                drawButton(graphics, "btn_green", btnX, btnY, btnW, btnH, "ЗАБРАТЬ", mouseX, mouseY, () -> {
                    sendMarketAction("CLAIM_TASK_REWARD", task.id(), "", currentStage, 0.0);
                });
            } else {
                drawButton(graphics, "btn_gray", btnX, btnY, btnW, btnH, "В ПРОЦЕССЕ", mouseX, mouseY, () -> {});
            }

            cy += taskSpacing;
        }

        // 3. Stage 6: The Final Transaction & Endings
        if (currentStage == 6) {
            cy += 6;

            if (account.isPermanentImpaired() || account.isMarketCardBlocked()) {
                drawSprite(graphics, "hud_bg", left, cy, width, 70, 0xFF200A0E);
                drawClampedText(graphics, "§4§lФИНАНСОВЫЙ РАСЧЁТ ЗАВЕРШЁН // СТАТЬЯ 17.4 ВСТУПИЛА В СИЛУ", left + 8, cy + 6, width - 16, 0xFFEF4444, false);
                drawClampedText(graphics, "§cПрава на биологические активы переданы корпорации. Доступ к бирже аннулирован.", left + 8, cy + 20, width - 16, 0xFFEF4444, false);
                drawClampedText(graphics, "§7Ортопедический фиксатор активен на правой ноге. Скорость -12%, добыча -22% (прыжок в норме).", left + 8, cy + 34, width - 16, 0xFFCBD5E1, false);
                drawClampedText(graphics, "§8«Вы больше не клиент. Вы — корпоративный актив.»", left + 8, cy + 48, width - 16, 0xFF64748B, false);
                cy += 76;
            } else {
                int cardH = 136;
                drawSprite(graphics, "card_amber", left, cy, width, cardH, 0xFF241A0A);
                drawClampedText(graphics, "§6§lФИНАНСОВЫЙ ДОГОВОР // ОКОНЧАТЕЛЬНЫЙ РАСЧЁТ", left + 10, cy + 8, width - 20, 0xFFFBBF24, false);
                drawClampedText(graphics, "§fВсе этапы Программы Активов завершены. Корпорация производит окончательный расчёт.", left + 10, cy + 22, width - 20, 0xFFE2E8F0, false);
                drawClampedText(graphics, "§7Выплата +1 000 000 CR начисляется на счета всех зарегистрированных участников.", left + 10, cy + 34, width - 20, 0xFFCBD5E1, false);

                int bigBtnW = 290;
                int bigBtnH = 24;
                int bigBtnX = left + (width - bigBtnW) / 2;
                int bigBtnY = cy + 49;
                drawButton(graphics, "btn_green", bigBtnX, bigBtnY, bigBtnW, bigBtnH, "ПОДПИСАТЬ ФИНАЛЬНЫЙ РАСЧЁТ (+1 000 000 CR)", mouseX, mouseY, () -> {
                    sendMarketAction("CHOOSE_FINAL_TRANSACTION", "FINAL_SETTLEMENT", "", 0, 0.0);
                    this.onClose();
                });

                // Dense ultra-small muted legal filler text burying clickable Clause 17.4 inside
                float fpScale = 0.62f;
                int fpX = left + 10;
                int fpY = cy + 79;
                int fpMaxUnscaledW = Math.max(120, Math.round((width - 20) / fpScale));
                String boiler1 = "Настоящий акт урегулирования составлен в соответствии с Регламентом Корпоративного Реестра (ред. IV), политикой конфиденциальности,";
                String boiler2 = "учётом курсовой волатильности CR/♦, стандартным согласием на телеметрический аудит транзакций, архивирование логов и ";
                String p3Pre = "процедурное сопровождение закрытия портфеля, в том числе с применением положений ";
                String clause17Link = "п. 17.4 (протокол отчуждения и фиксации)";
                String p3Post = ", действующих с момента подписания без дополнительных уведомлений оператора.";

                List<net.minecraft.util.FormattedCharSequence> l1 = font.split(Component.literal(boiler1), fpMaxUnscaledW);
                List<net.minecraft.util.FormattedCharSequence> l2 = font.split(Component.literal(boiler2), fpMaxUnscaledW);

                int l3Y = fpY + Math.round((l1.size() + l2.size()) * 10 * fpScale);
                int cPreW = Math.round(font.width(p3Pre) * fpScale);
                int cLinkW = Math.round(font.width(clause17Link) * fpScale);
                boolean inlineFits = (font.width(p3Pre + clause17Link) <= fpMaxUnscaledW);
                int linkHitX = inlineFits ? (fpX + cPreW) : fpX;
                int linkHitY = inlineFits ? l3Y : (l3Y + Math.round(10 * fpScale));
                int linkHitW = cLinkW;
                int linkHitH = 8;

                boolean clauseHovered = !isModalOpen() && (mouseX >= linkHitX && mouseX <= linkHitX + linkHitW && mouseY >= linkHitY && mouseY <= linkHitY + linkHitH);
                int baseMutedColor = 0xFF333D4D;
                int linkColor = clauseHovered ? 0xFF94A3B8 : 0xFF354052;

                graphics.pose().pushMatrix();
                graphics.pose().translate((float) fpX, (float) fpY);
                graphics.pose().scale(fpScale, fpScale);
                int uy = 0;
                for (var s : l1) {
                    graphics.text(font, s, 0, uy, baseMutedColor, false);
                    uy += 10;
                }
                for (var s : l2) {
                    graphics.text(font, s, 0, uy, baseMutedColor, false);
                    uy += 10;
                }
                if (inlineFits) {
                    graphics.text(font, p3Pre, 0, uy, baseMutedColor, false);
                    int preW = font.width(p3Pre);
                    int lnW = font.width(clause17Link);
                    graphics.text(font, clause17Link, preW, uy, linkColor, false);
                    if (clauseHovered) {
                        graphics.fill(preW, uy + 9, preW + lnW, uy + 10, linkColor);
                    }
                    uy += 10;
                    for (var s : font.split(Component.literal(p3Post.substring(2)), fpMaxUnscaledW)) {
                        if (fpY + uy * fpScale + 6 <= cy + cardH - 3) {
                            graphics.text(font, s, 0, uy, baseMutedColor, false);
                            uy += 10;
                        }
                    }
                } else {
                    graphics.text(font, p3Pre, 0, uy, baseMutedColor, false);
                    uy += 10;
                    int lnW = font.width(clause17Link);
                    graphics.text(font, clause17Link, 0, uy, linkColor, false);
                    if (clauseHovered) {
                        graphics.fill(0, uy + 9, lnW, uy + 10, linkColor);
                    }
                }
                graphics.pose().popMatrix();

                registerClick(linkHitX, linkHitY, linkHitX + linkHitW, linkHitY + linkHitH,
                        () -> section17ModalOpen = true,
                        "Открыть полный текст п. 17.4");

                cy += cardH + 10;
            }
        }

        // 4. Side Quests Header
        cy += 8;
        int completedSq = 0;
        for (var sq : com.playermarket.story.StoryQuests.SIDE_QUESTS) {
            if (story.isSideQuestCompleted(sq.id())) completedSq++;
        }

        drawSprite(graphics, "hud_bg", left, cy, width, 20, 0xFF121927);
        drawClampedText(graphics, "§b§lДОПОЛНИТЕЛЬНЫЕ ПОРУЧЕНИЯ (ПОБОЧНЫЕ КВЕСТЫ) §8| §fВыполнено: §e"
                        + completedSq + " / " + com.playermarket.story.StoryQuests.SIDE_QUESTS.size(),
                left + 8, cy + 5, width - 16, 0xFF38BDF8, false);
        cy += 24;

        // Side Quests List (Expanded multiline cards with full text visibility)
        int sqItemH = 48;
        int sqSpacing = 52;
        for (com.playermarket.story.StoryQuests.SideQuest sq : com.playermarket.story.StoryQuests.SIDE_QUESTS) {
            boolean isDone = story.isSideQuestCompleted(sq.id());
            boolean isClaimed = story.isSideQuestRewardClaimed(sq.id());

            drawSprite(graphics, isDone ? "card_selected" : "card", left, cy, width, sqItemH, isDone ? 0xFF102024 : 0xFF0E131C);

            int btnW = 95;
            int btnH = 20;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 14;

            int textMaxW = width - btnW - 24;

            String titlePrefix = isClaimed ? "§8✔ " : (isDone ? "§b✦ " : "§7○ ");
            drawClampedText(graphics, titlePrefix + "§f§l" + sq.title(), left + 8, cy + 4, textMaxW, 0xFFFFFFFF, false);

            List<net.minecraft.util.FormattedCharSequence> sqDescLines =
                    font.split(Component.literal("§7" + sq.description()), textMaxW);
            int sY = cy + 15;
            for (int d = 0; d < Math.min(2, sqDescLines.size()); d++) {
                graphics.text(font, sqDescLines.get(d), left + 8, sY, 0xFF94A3B8, false);
                sY += 10;
            }

            String rewStr = "§eНаграда: " + (sq.rewardCr() > 0 ? "§a+" + sq.rewardCr() + " CR" : "")
                    + (sq.rewardDiamonds() > 0 ? (sq.rewardCr() > 0 ? " §8| " : "") + "§b+" + sq.rewardDiamonds() + " ♦" : "");
            graphics.text(font, rewStr, left + 8, cy + 35, 0xFFCBD5E1, false);

            if (isClaimed) {
                drawSmallButton(graphics, "btn_gray", btnX, btnY, btnW, btnH, "ПОЛУЧЕНО", mouseX, mouseY, () -> {});
            } else if (isDone) {
                drawSmallButton(graphics, "btn_blue", btnX, btnY, btnW, btnH, "ЗАБРАТЬ", mouseX, mouseY, () -> {
                    sendMarketAction("CLAIM_SIDE_QUEST_REWARD", sq.id(), "", 0, 0.0);
                });
            } else {
                drawSmallButton(graphics, "btn_gray", btnX, btnY, btnW, btnH, "В процессе", mouseX, mouseY, () -> {});
            }

            cy += sqSpacing;
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, top, width, height, maxScroll, scrollOffset);
    }

    // ==========================================
    // TAB: MAIL (Corporate Mailbox, Mr. Ledger Letters, Attachments)
    // ==========================================
    private void renderMail(GuiGraphicsExtractor graphics, PlayerMarketAccount account,
                            int left, int top, int width, int height, int mouseX, int mouseY) {
        List<com.playermarket.story.MailMessage> mailbox = account.getStoryProgress().getMailbox();

        int listWidth = Math.min(176, Math.max(145, (int) (width * 0.37)));
        int readerLeft = left + listWidth + 6;
        int readerWidth = width - listWidth - 6;
        lastReaderLeft = readerLeft;

        // Auto-select first email if none selected
        if ((selectedMailId == null || selectedMailId.isEmpty()) && !mailbox.isEmpty()) {
            selectedMailId = mailbox.getFirst().id();
        }

        // LEFT COLUMN: Mailbox List
        drawSprite(graphics, "sidebar", left, top, listWidth, height, 0xFF0D111A);
        drawClampedText(graphics, "§7ВХОДЯЩИЕ (" + mailbox.size() + ")", left + 8, top + 6, listWidth - 16, 0xFF94A3B8, false);

        int listTop = top + 18;
        int listHeight = height - 18;

        if (mailbox.isEmpty()) {
            maxScroll = 0;
            drawClampedCenteredText(graphics, "§8Почтовый ящик пуст.", left + listWidth / 2, listTop + listHeight / 2, listWidth - 16, 0xFF64748B, false);
        } else {
            int itemH = 36;
            int spacing = 38;
            maxScroll = Math.max(0, mailbox.size() * spacing - listHeight);
            setScissor(graphics, left, listTop, left + listWidth, listTop + listHeight);

            for (int i = 0; i < mailbox.size(); i++) {
                com.playermarket.story.MailMessage msg = mailbox.get(i);
                int my = listTop + (i * spacing) - scrollOffset;
                if (my + itemH < listTop || my > listTop + listHeight) continue;
                boolean isSelected = msg.id().equals(selectedMailId);
                boolean isHovered = (mouseX >= left + 4 && mouseX <= left + listWidth - 4 && mouseY >= my && mouseY < my + itemH);

                String cardSprite = isSelected ? "tab_active" : (isHovered ? "tab_hover" : "card");
                drawSprite(graphics, cardSprite, left + 4, my, listWidth - 8, itemH, isSelected ? 0xFF1C3026 : 0xFF10141D);

                String senderDisplay = msg.sender();
                if (senderDisplay.contains("//")) {
                    senderDisplay = senderDisplay.substring(0, senderDisplay.indexOf("//")).trim();
                }
                String unreadGlyph = msg.read() ? "§8○ " : "§c● ";
                drawClampedText(graphics, unreadGlyph + "§f" + senderDisplay, left + 8, my + 4, listWidth - 16, 0xFFFFFFFF, false);

                String subjectDisplay = msg.subject();
                if (subjectDisplay.contains("—")) {
                    subjectDisplay = subjectDisplay.substring(0, subjectDisplay.indexOf('—')).trim();
                } else if (subjectDisplay.contains("//")) {
                    subjectDisplay = subjectDisplay.substring(0, subjectDisplay.indexOf("//")).trim();
                }
                String subjPrefix = (!msg.rewardType().equals("NONE") && !msg.rewardClaimed()) ? "§e[📎] " : "";
                drawClampedText(graphics, subjPrefix + (msg.read() ? "§7" : "§e§l") + subjectDisplay, left + 8, my + 15, listWidth - 16, 0xFFCBD5E1, false);

                final com.playermarket.story.MailMessage fMsg = msg;
                registerClick(left + 4, my, left + listWidth - 4, my + itemH, () -> {
                    selectedMailId = fMsg.id();
                    mailBodyScroll = 0;
                    if (!fMsg.read()) {
                        sendMarketAction("MARK_MAIL_READ", fMsg.id(), "", 0, 0.0);
                    }
                }, null);
            }

            clearScissor(graphics);
            renderVerticalScrollbar(graphics, left, listTop, listWidth, listHeight, maxScroll, scrollOffset);
        }

        // RIGHT COLUMN: Letter Reader
        drawSprite(graphics, "panel", readerLeft, top, readerWidth, height, 0xFF0E131E);

        com.playermarket.story.MailMessage currentMsg = null;
        for (var m : mailbox) {
            if (m.id().equals(selectedMailId)) {
                currentMsg = m;
                break;
            }
        }

        if (currentMsg == null) {
            mailBodyMaxScroll = 0;
            drawClampedCenteredText(graphics, "§7Выберите письмо из списка слева для прочтения.",
                    readerLeft + readerWidth / 2, top + height / 2, readerWidth - 20, 0xFF64748B, false);
            return;
        }

        // Letter Header
        drawSprite(graphics, "header", readerLeft, top, readerWidth, 36, 0xFF141A28);
        String senderClean = currentMsg.sender();
        if (senderClean.contains("//")) {
            senderClean = senderClean.substring(0, senderClean.indexOf("//")).trim();
        }
        drawClampedText(graphics, "§8От: §f" + senderClean, readerLeft + 8, top + 5, readerWidth - 16, 0xFFE2E8F0, false);
        drawClampedText(graphics, "§8Тема: §e§l" + currentMsg.subject(), readerLeft + 8, top + 18, readerWidth - 16, 0xFFFBBF24, false);

        // Letter Body (Split paragraphs, wrap and allow vertical scroll on tall letters / big GUI scale)
        int bodyTop = top + 42;
        int bodyMaxY = top + height - (currentMsg.rewardType().equals("NONE") ? 8 : 42);
        int bodyAvailH = Math.max(20, bodyMaxY - bodyTop);
        int textMaxW = readerWidth - 22;

        List<String> renderedRows = new ArrayList<>();
        for (String paragraph : currentMsg.body().split("\n")) {
            if (paragraph.trim().isEmpty()) {
                renderedRows.add("");
                continue;
            }
            List<net.minecraft.network.chat.FormattedText> lines =
                    font.getSplitter().splitLines(Component.literal("§7" + paragraph), textMaxW, net.minecraft.network.chat.Style.EMPTY);
            for (var line : lines) {
                renderedRows.add(line.getString());
            }
        }
        int totalBodyH = 0;
        for (String r : renderedRows) totalBodyH += r.isEmpty() ? 6 : 10;
        mailBodyMaxScroll = Math.max(0, totalBodyH - bodyAvailH);
        mailBodyScroll = Math.clamp(mailBodyScroll, 0, mailBodyMaxScroll);

        setScissor(graphics, readerLeft + 4, bodyTop, readerLeft + readerWidth - 4, bodyMaxY);
        int bodyY = bodyTop - mailBodyScroll;
        for (String r : renderedRows) {
            if (r.isEmpty()) {
                bodyY += 6;
            } else {
                if (bodyY + 10 >= bodyTop && bodyY <= bodyMaxY) {
                    graphics.text(font, r, readerLeft + 10, bodyY, 0xFFCBD5E1, false);
                }
                bodyY += 10;
            }
        }
        clearScissor(graphics);
        if (mailBodyMaxScroll > 0) {
            renderVerticalScrollbar(graphics, readerLeft, bodyTop, readerWidth - 2, bodyAvailH, mailBodyMaxScroll, mailBodyScroll);
        }

        // Attachment / Reward Box at the bottom of the letter (multiline, never truncated to "ВЛОЖ...")
        if (!currentMsg.rewardType().equals("NONE")) {
            int attachH = 32;
            int attachY = top + height - 38;
            drawSprite(graphics, "hud_bg", readerLeft + 8, attachY, readerWidth - 16, attachH, 0xFF172030);

            if ("CONTRACT_OFFER".equalsIgnoreCase(currentMsg.rewardType())) {
                List<net.minecraft.util.FormattedCharSequence> offerLines = font.split(
                        Component.literal("§e[!] Подпишите договор на бумаге (Мятое письмо в инвентаре)"),
                        readerWidth - 28
                );
                int oy = attachY + (offerLines.size() <= 1 ? 11 : 6);
                for (int oi = 0; oi < Math.min(2, offerLines.size()); oi++) {
                    graphics.text(font, offerLines.get(oi), readerLeft + 14, oy + oi * 10, 0xFFFBBF24, false);
                }
            } else if (currentMsg.rewardClaimed()) {
                drawClampedText(graphics, "§8✔ Вложение получено: +" + currentMsg.rewardAmount() + " " + currentMsg.rewardType(),
                        readerLeft + 14, attachY + 11, readerWidth - 28, 0xFF64748B, false);
            } else {
                int claimW = Math.min(96, Math.max(76, readerWidth / 2 - 10));
                int claimH = 20;
                int claimX = readerLeft + readerWidth - claimW - 14;
                int claimY = attachY + 6;
                int infoMaxW = Math.max(40, claimX - (readerLeft + 14) - 6);

                drawClampedText(graphics, "§eВЛОЖЕНИЕ:", readerLeft + 14, attachY + 5, infoMaxW, 0xFFFBBF24, false);
                drawClampedText(graphics, "§a+" + currentMsg.rewardAmount() + " " + currentMsg.rewardType(), readerLeft + 14, attachY + 16, infoMaxW, 0xFF10B981, false);

                final String cId = currentMsg.id();
                drawSmallButton(graphics, "btn_green", claimX, claimY, claimW, claimH, "ЗАБРАТЬ", mouseX, mouseY, () -> {
                    sendMarketAction("CLAIM_MAIL_REWARD", cId, "", 0, 0.0);
                });
            }
        }
    }

    private void renderVerticalScrollbar(GuiGraphicsExtractor graphics, int left, int top, int width, int height, int max, int offset) {
        if (max > 0) {
            int barW = 3;
            int barX = left + width - barW - 1;
            int trackH = height - 4;
            int thumbH = Math.max(16, (int) ((float) height / (height + max) * trackH));
            int thumbY = top + 2 + (int) ((float) offset / max * (trackH - thumbH));
            graphics.fill(barX, top + 2, barX + barW, top + 2 + trackH, 0x33FFFFFF);
            graphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, 0x9938BDF8);
        }
    }

    // ==========================================
    // TAB: SELL_ASSETS (Fixed Grid, Help [?] Tooltips, Clean Bounds)
    // ==========================================
    private void renderSellAssets(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event,
                                  int left, int top, int width, int height, int mouseX, int mouseY) {
        // 1. Category Filter Pills
        int filterY = top;
        int count = 1 + AssetType.Category.values().length;
        int pillW = (width - 16 - (count - 1) * 4) / count;
        int pillH = 16;
        int pillX = left;

        boolean allActive = (assetFilter == null);
        drawPill(graphics, pillX, filterY, pillW, pillH, "ВСЕ", allActive, mouseX, mouseY, () -> {
            assetFilter = null;
            scrollOffset = 0;
        });
        pillX += pillW + 4;

        for (AssetType.Category cat : AssetType.Category.values()) {
            boolean unlocked = cat.isUnlockedForStage(account.getStoryStage());
            boolean active = (assetFilter == cat);
            String title = (unlocked ? "" : "[X] ") + cat.getDisplayName();
            drawPill(graphics, pillX, filterY, pillW, pillH, title, active, mouseX, mouseY, () -> {
                assetFilter = cat;
                scrollOffset = 0;
            });
            pillX += pillW + 4;
        }

        // 2. Explanatory Banner
        int bannerY = top + 20;
        int bannerH = 26;
        drawSprite(graphics, "hud_bg", left, bannerY, width, bannerH, 0xFF121927);
        drawClampedText(graphics, "§a§lТОРГОВЛЯ: §fПродажа навсегда даёт разовый крупный CR (выкуп платный).", left + 6, bannerY + 4, width - 12, 0xFF10B981, false);
        drawClampedText(graphics, "§eЛизинг (1-5 дн.) — вклад +35 CR/мин и 100% авто-возврат бесплатно! §8[?]", left + 6, bannerY + 14, width - 12, 0xFFFBBF24, false);

        // 3. Scrollable Card List
        int listY = bannerY + bannerH + 4;
        int listH = height - (listY - top);

        List<AssetType> assets = new ArrayList<>();
        for (AssetType a : AssetType.values()) {
            if (assetFilter == null || a.getCategory() == assetFilter) {
                assets.add(a);
            }
        }

        int cardHeight = 80;
        int spacing = 84;
        int totalContentHeight = assets.size() * spacing;
        maxScroll = Math.max(0, totalContentHeight - listH);

        setScissor(graphics, left, listY, left + width, listY + listH);

        for (int i = 0; i < assets.size(); i++) {
            AssetType asset = assets.get(i);
            int cy = listY + (i * spacing) - scrollOffset;
            if (cy + cardHeight < listY || cy > listY + listH) continue;

            int playerStage = account.getStoryStage();
            boolean catUnlocked = asset.getCategory().isUnlockedForStage(playerStage);
            double maxAllowed = asset.getMaxAllowedFractionForStage(playerStage);
            double permSold = account.getPermanentSoldFraction(asset);
            double totalSold = account.getTotalSoldFraction(asset);
            boolean canSellMore = catUnlocked && (totalSold + 0.10 <= maxAllowed + 0.001);

            // Card background
            drawSprite(graphics, "card", left, cy, width, cardHeight, 0xFF10141D);

            // === ROW 1: Name, Status, Progress Bar, [?] Help Tooltip, [⟲ Выкуп], [✕ Снять лиз.] ===
            // Help [?] button in top right
            int helpW = 14;
            int helpH = 12;
            int helpX = left + width - helpW - 4;
            int helpY = cy + 3;
            drawSmallButton(graphics, "btn_blue", helpX, helpY, helpW, helpH, "?", mouseX, mouseY, () -> {}, asset.getHelpTooltip());

            int curRightX = helpX - 4;

            // Direct early lease cancellation if currently in lease
            Optional<LeaseContract> activeLease = account.getActiveLeases().stream()
                    .filter(l -> l.assetId().equals(asset.getId()))
                    .findFirst();
            if (activeLease.isPresent()) {
                LeaseContract lc = activeLease.get();
                long cancelCost = MarketAttributeApplier.calculateLeaseCancelCostCr(lc);
                int clW = 74;
                int clH = 12;
                curRightX -= clW;
                int clX = curRightX;
                curRightX -= 4;
                int clY = cy + 3;
                boolean canAfford = account.getCrBalance() >= cancelCost;
                drawSmallButton(graphics, canAfford ? "btn_amber" : "btn_gray", clX, clY, clW, clH, "✕ Снять лиз.", mouseX, mouseY, () -> {
                    if (canAfford) sendMarketAction("CANCEL_LEASE", lc.id(), "", 0, 0.0);
                });
            }

            // Direct Buyback button if permanently sold
            if (permSold > 0.001) {
                long buyCost = MarketAttributeApplier.calculateBuybackCostCr(account, event, asset);
                int bbW = 58;
                int bbH = 12;
                curRightX -= bbW;
                int bbX = curRightX;
                curRightX -= 4;
                int bbY = cy + 3;
                boolean canAfford = account.getCrBalance() >= buyCost;
                drawSmallButton(graphics, canAfford ? "btn_blue" : "btn_gray", bbX, bbY, bbW, bbH, "⟲ Выкуп", mouseX, mouseY, () -> {
                    sendMarketAction("BUYBACK_ASSET", asset.getId(), "", 0, 0.0);
                });
            }

            // Title and Status cleanly fitting into available left space
            int infoW = Math.max(100, curRightX - (left + 6));
            String assetTitle = "§f§l" + asset.getDisplayName();
            String statusStr = String.format(Locale.ROOT, "§7Прод: §e%.0f%%§8/%.0f%%", totalSold * 100.0, maxAllowed * 100.0);
            int statusW = Math.min(infoW / 2, font.width(statusStr));
            int titleW = Math.max(50, infoW - statusW - 6);

            drawClampedText(graphics, assetTitle, left + 6, cy + 4, titleW, 0xFFFFFFFF, false);
            drawClampedText(graphics, statusStr, curRightX - statusW, cy + 4, statusW, 0xFFFBBF24, false);

            // === ROW 2: 18x18 ITEM SLOT + 6 ACTION BUTTONS OR LOCKED NOTICE ===
            int fullW = width - 12;
            int row2Y = cy + 18;
            int row2H = 16;
            drawItemSlot(graphics, left + 6, row2Y - 1, getAssetIconStack(asset));
            int row2Left = left + 27;
            int actionW = width - 33;
            int btnCount = 6;
            int btnGap = 2;
            int singleBtnW = (actionW - (btnCount - 1) * btnGap) / btnCount;

            if (!catUnlocked) {
                drawSprite(graphics, "hud_bg", row2Left, row2Y, actionW, row2H, 0xFF1E1424);
                graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_LOCK_CLOSED, row2Left + 4, row2Y + 3, 0.0f, 0.0f, 10, 10, 16, 16, 16, 16);
                drawClampedText(graphics, "§cКАТЕГОРИЯ ЗАБЛОКИРОВАНА: §7Доступ с §eЭтапа " + asset.getCategory().getRequiredStage(), row2Left + 17, row2Y + 4, actionW - 22, 0xFFEF4444, false);
            } else {
                boolean compactLease = singleBtnW < 58;
                // 1. Permanent 10%
                long p10 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.10, 0);
                boolean a10 = canSellMore && (totalSold + 0.10 <= maxAllowed + 0.001);
                int b0X = row2Left + 0 * (singleBtnW + btnGap);
                drawSmallButton(graphics, a10 ? "btn_green" : "btn_gray", b0X, row2Y, singleBtnW, row2H, "10%", mouseX, mouseY, () -> {
                    if (a10) sendMarketAction("SELL_ASSET", asset.getId(), "", 0, 0.10);
                }, "Продать 10% характеристики навсегда за +" + p10 + " CR");

                // 2. Permanent 25%
                long p25 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.25, 0);
                boolean a25 = canSellMore && (totalSold + 0.25 <= maxAllowed + 0.001);
                int b1X = row2Left + 1 * (singleBtnW + btnGap);
                drawSmallButton(graphics, a25 ? "btn_green" : "btn_gray", b1X, row2Y, singleBtnW, row2H, "25%", mouseX, mouseY, () -> {
                    if (a25) sendMarketAction("SELL_ASSET", asset.getId(), "", 0, 0.25);
                }, "Продать 25% характеристики навсегда за +" + p25 + " CR");

                // 3. Permanent 50%
                long p50 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.50, 0);
                boolean a50 = canSellMore && (totalSold + 0.50 <= maxAllowed + 0.001);
                int b2X = row2Left + 2 * (singleBtnW + btnGap);
                drawSmallButton(graphics, a50 ? "btn_green" : "btn_gray", b2X, row2Y, singleBtnW, row2H, "50%", mouseX, mouseY, () -> {
                    if (a50) sendMarketAction("SELL_ASSET", asset.getId(), "", 0, 0.50);
                }, "Продать 50% характеристики навсегда за +" + p50 + " CR (в 5 раз больше, чем за 10%)");

                // 4. Lease 1 Day (20%)
                long l1 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.20, 1);
                boolean al1 = canSellMore && (totalSold + 0.20 <= maxAllowed + 0.001);
                int b3X = row2Left + 3 * (singleBtnW + btnGap);
                drawSmallButton(graphics, al1 ? "btn_blue" : "btn_gray", b3X, row2Y, singleBtnW, row2H, compactLease ? "Лиз. 1д" : "Лизинг 1д", mouseX, mouseY, () -> {
                    if (al1) sendMarketAction("LEASE_ASSET", asset.getId(), "", 1, 0.20);
                }, "Лизинг 20% стата на 1 день: +" + l1 + " CR сразу + пассивный доход");

                // 5. Lease 3 Days (20%)
                long l3 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.20, 3);
                boolean al3 = canSellMore && (totalSold + 0.20 <= maxAllowed + 0.001);
                int b4X = row2Left + 4 * (singleBtnW + btnGap);
                drawSmallButton(graphics, al3 ? "btn_amber" : "btn_gray", b4X, row2Y, singleBtnW, row2H, compactLease ? "Лиз. 3д" : "Лизинг 3д", mouseX, mouseY, () -> {
                    if (al3) sendMarketAction("LEASE_ASSET", asset.getId(), "", 3, 0.20);
                }, "Лизинг 20% стата на 3 дня: +" + l3 + " CR сразу + повышенный процент");

                // 6. Lease 5 Days (20%)
                long l5 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.20, 5);
                boolean al5 = canSellMore && (totalSold + 0.20 <= maxAllowed + 0.001);
                int b5X = row2Left + 5 * (singleBtnW + btnGap);
                drawSmallButton(graphics, al5 ? "btn_amber" : "btn_gray", b5X, row2Y, singleBtnW, row2H, compactLease ? "Лиз. 5д" : "Лизинг 5д", mouseX, mouseY, () -> {
                    if (al5) sendMarketAction("LEASE_ASSET", asset.getId(), "", 5, 0.20);
                }, "Лизинг 20% стата на 5 дней: +" + l5 + " CR сразу + максимальный процент");
            }

            // === ROW 3: 2 EQUAL-WIDTH DIVIDEND BUTTONS (2-line wrapped descriptions) ===
            int row3Y = cy + 39;
            int row3H = 35;
            int divW = (fullW - 4) / 2;

            String currentDiv = account.getSelectedDividends().getOrDefault(asset.getId(), asset.getDividendA().id());
            boolean selA = currentDiv.equals(asset.getDividendA().id());
            boolean selB = currentDiv.equals(asset.getDividendB().id());

            // Dividend A button
            int divAX = left + 6;
            drawSprite(graphics, selA ? "card_selected" : "card", divAX, row3Y, divW, row3H, selA ? 0xFF1B382D : 0xFF0D121B);
            String labelA = (selA ? "§a✔ " : "§7○ ") + asset.getDividendA().label();
            drawClampedText(graphics, labelA, divAX + 6, row3Y + 3, divW - 12, selA ? 0xFF34D399 : 0xFFCBD5E1, false);
            List<net.minecraft.util.FormattedCharSequence> descLinesA =
                    font.split(Component.literal("§8" + asset.getDividendA().desc()), divW - 12);
            for (int d = 0; d < Math.min(2, descLinesA.size()); d++) {
                graphics.text(font, descLinesA.get(d), divAX + 6, row3Y + 13 + (d * 9), 0xFF64748B, false);
            }

            registerClick(divAX, row3Y, divAX + divW, row3Y + row3H, () -> {
                sendMarketAction("SELECT_DIVIDEND", asset.getId(), asset.getDividendA().id(), 0, 0.0);
            }, "Выбрать: " + asset.getDividendA().label());

            // Dividend B button
            int divBX = divAX + divW + 4;
            drawSprite(graphics, selB ? "card_selected" : "card", divBX, row3Y, divW, row3H, selB ? 0xFF1B382D : 0xFF0D121B);
            String labelB = (selB ? "§a✔ " : "§7○ ") + asset.getDividendB().label();
            drawClampedText(graphics, labelB, divBX + 6, row3Y + 3, divW - 12, selB ? 0xFF34D399 : 0xFFCBD5E1, false);
            List<net.minecraft.util.FormattedCharSequence> descLinesB =
                    font.split(Component.literal("§8" + asset.getDividendB().desc()), divW - 12);
            for (int d = 0; d < Math.min(2, descLinesB.size()); d++) {
                graphics.text(font, descLinesB.get(d), divBX + 6, row3Y + 13 + (d * 9), 0xFF64748B, false);
            }

            registerClick(divBX, row3Y, divBX + divW, row3Y + row3H, () -> {
                sendMarketAction("SELECT_DIVIDEND", asset.getId(), asset.getDividendB().id(), 0, 0.0);
            }, "Выбрать: " + asset.getDividendB().label());
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, top, width, height, maxScroll, scrollOffset);
    }

    // ==========================================
    // TAB: BUY_BACK
    // ==========================================
    private void renderBuyback(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event,
                               int left, int top, int width, int height, int mouseX, int mouseY) {
        int infoH = 26;
        drawSprite(graphics, "hud_bg", left, top, width, infoH, 0xFF131A26);
        drawClampedText(graphics, "§b§lОБРАТНЫЙ ВЫКУП: §fВосстановление проданных характеристик.", left + 8, top + 4, width - 16, 0xFF38BDF8, false);
        drawClampedText(graphics, "§7Выкуп дороже продажи (+75%..+135%). Лицензия брокера снижает цену.", left + 8, top + 14, width - 16, 0xFF94A3B8, false);

        int listY = top + infoH + 6;
        int listH = height - (listY - top);

        List<AssetType> soldList = new ArrayList<>();
        for (AssetType a : AssetType.values()) {
            if (account.getPermanentSoldFraction(a) > 0.001) {
                soldList.add(a);
            }
        }
        List<LeaseContract> leases = account.getActiveLeases();

        if (soldList.isEmpty() && leases.isEmpty()) {
            drawSprite(graphics, "card", left, listY, width, 50, 0xFF10141D);
            drawClampedCenteredText(graphics, "§aВсе характеристики в целости! Нет проданных долей или лизинга.", left + width / 2, listY + 20, width - 20, 0xFF10B981, false);
            maxScroll = 0;
            return;
        }

        int itemH = 50;
        int spacing = 54;
        int totalItems = soldList.size() + leases.size();
        maxScroll = Math.max(0, totalItems * spacing - listH);

        setScissor(graphics, left, listY, left + width, listY + listH);

        int renderIdx = 0;

        // 1. Permanent sold positions
        for (int i = 0; i < soldList.size(); i++) {
            AssetType asset = soldList.get(i);
            int cy = listY + (renderIdx * spacing) - scrollOffset;
            renderIdx++;
            if (cy + itemH < listY || cy > listY + listH) continue;

            double permSold = account.getPermanentSoldFraction(asset);
            long cost = MarketAttributeApplier.calculateBuybackCostCr(account, event, asset);
            boolean canAfford = account.getCrBalance() >= cost;

            drawSprite(graphics, "card", left, cy, width, itemH, 0xFF121622);

            int btnW = 128;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 14;
            int textMaxW = Math.max(80, btnX - (left + 28) - 8);

            drawItemSlot(graphics, left + 6, cy + 16, getAssetIconStack(asset));
            drawClampedText(graphics, "§f§l" + asset.getDisplayName() + " §7[" + asset.getCategory().getDisplayName() + "]", left + 28, cy + 6, textMaxW, 0xFFFFFFFF, false);
            String basisLine = String.format(Locale.ROOT, "§eПродано навсегда: %.0f%% §8| Базис: %d CR",
                    permSold * 100.0, account.getPermanentSalePayoutBasis().getOrDefault(asset.getId(), 0L));
            List<net.minecraft.util.FormattedCharSequence> bLines = font.split(Component.literal(basisLine), textMaxW);
            for (int d = 0; d < Math.min(2, bLines.size()); d++) {
                graphics.text(font, bLines.get(d), left + 28, cy + 20 + (d * 10), 0xFFFBBF24, false);
            }

            drawButton(graphics, canAfford ? "btn_blue" : "btn_gray", btnX, btnY, btnW, btnH,
                    "Выкупить: " + formatNumber(cost) + " CR", mouseX, mouseY, () -> {
                        if (canAfford) sendMarketAction("BUYBACK_ASSET", asset.getId(), "", 0, 0.0);
                    });
        }

        // 2. Active leases with early termination option
        long gameTick = ClientMarketState.getCurrentGameTick();
        for (int i = 0; i < leases.size(); i++) {
            LeaseContract lease = leases.get(i);
            int cy = listY + (renderIdx * spacing) - scrollOffset;
            renderIdx++;
            if (cy + itemH < listY || cy > listY + listH) continue;

            String aName = AssetType.byId(lease.assetId()).map(AssetType::getDisplayName).orElse(lease.assetId());
            double daysLeft = Math.max(0.0, (lease.endTick() - gameTick) / 24000.0);
            long cancelCost = MarketAttributeApplier.calculateLeaseCancelCostCr(lease);
            boolean canAfford = account.getCrBalance() >= cancelCost;

            drawSprite(graphics, "card_amber", left, cy, width, itemH, 0xFF181410);

            int btnW = 136;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 14;
            int textMaxW = Math.max(80, btnX - (left + 8) - 8);

            drawClampedText(graphics, "§6§lЛизинг: §f" + aName + " §c-" + Math.round(lease.fraction() * 100) + "% §8(§d" + String.format(Locale.ROOT, "%.1f", daysLeft) + " дн.§8)",
                    left + 8, cy + 6, textMaxW, 0xFFFBBF24, false);
            String cancelDesc = "§7Досрочный возврат: §e" + formatNumber(cancelCost) + " CR §8(выплата + штраф 50%)";
            List<net.minecraft.util.FormattedCharSequence> cLines = font.split(Component.literal(cancelDesc), textMaxW);
            for (int d = 0; d < Math.min(2, cLines.size()); d++) {
                graphics.text(font, cLines.get(d), left + 8, cy + 20 + (d * 10), 0xFFCBD5E1, false);
            }

            drawButton(graphics, canAfford ? "btn_amber" : "btn_gray", btnX, btnY, btnW, btnH,
                    "Расторгнуть (-" + formatNumber(cancelCost) + ")", mouseX, mouseY, () -> {
                        if (canAfford) sendMarketAction("CANCEL_LEASE", lease.id(), "", 0, 0.0);
                    });
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, top, width, height, maxScroll, scrollOffset);
    }

    // ==========================================
    // TAB: UPGRADE_STORE
    // ==========================================
    private void renderUpgradeStore(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event, int playerDiamonds,
                                    int left, int top, int width, int height, int mouseX, int mouseY) {
        int uCount = UpgradeType.Category.values().length;
        int pillW = (width - 16 - (uCount - 1) * 4) / uCount;
        int pillH = 16;
        int pillX = left;

        for (UpgradeType.Category cat : UpgradeType.Category.values()) {
            boolean active = (upgradeFilter == cat);
            drawPill(graphics, pillX, top, pillW, pillH, cat.getDisplayName(), active, mouseX, mouseY, () -> {
                upgradeFilter = cat;
                scrollOffset = 0;
            });
            pillX += pillW + 4;
        }

        int listY = top + 22;
        int listH = height - (listY - top);

        List<UpgradeType> list = new ArrayList<>();
        for (UpgradeType u : UpgradeType.values()) {
            if (u.getCategory() == upgradeFilter) {
                list.add(u);
            }
        }

        int itemH = 60;
        int spacing = 64;
        maxScroll = Math.max(0, list.size() * spacing - listH);

        setScissor(graphics, left, listY, left + width, listY + listH);

        for (int i = 0; i < list.size(); i++) {
            UpgradeType upg = list.get(i);
            int cy = listY + (i * spacing) - scrollOffset;
            if (cy + itemH < listY || cy > listY + listH) continue;

            int currentLvl = account.getUpgradeLevel(upg);
            boolean isMax = currentLvl >= upg.getMaxLevel();
            int nextCost = isMax ? 0 : MarketAttributeApplier.calculateUpgradeDiamondCost(account, event, upg, currentLvl + 1);

            boolean prereqMet = true;
            String prereqText = "";
            if (upg.getPrerequisite() != null) {
                int pLvl = account.getUpgradeLevel(upg.getPrerequisite());
                if (pLvl < upg.getPrerequisiteLevel()) {
                    prereqMet = false;
                    prereqText = "§cТребуется: " + upg.getPrerequisite().getTitle() + " Ур. " + upg.getPrerequisiteLevel();
                }
            }

            drawSprite(graphics, isMax ? "card_selected" : "card", left, cy, width, itemH, 0xFF121622);

            int btnW = 112;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 19;
            int textW = width - btnW - 42;

            String title = "§b§l" + upg.getTitle() + " §7[Ур. " + currentLvl + " / " + upg.getMaxLevel() + "]";
            drawItemSlot(graphics, left + 6, cy + 7, getUpgradeIconStack(upg));
            drawClampedText(graphics, title, left + 28, cy + 5, textW, 0xFF38BDF8, false);

            String activeLine = currentLvl > 0
                    ? ("§aТекущее: §f" + upg.getSummaryForLevel(currentLvl))
                    : ("§7Ур. 1 даст: §f" + upg.getSummaryForLevel(1));
            drawClampedText(graphics, activeLine, left + 28, cy + 16, textW, 0xFFE2E8F0, false);

            String subInfo;
            if (!prereqMet) {
                subInfo = prereqText;
            } else if (!isMax && currentLvl > 0) {
                subInfo = "§eУр. " + (currentLvl + 1) + ": §f" + upg.getSummaryForLevel(currentLvl + 1);
            } else {
                subInfo = "§7" + upg.getMechanicDescription();
            }
            List<net.minecraft.util.FormattedCharSequence> subLines = font.split(Component.literal(subInfo), textW);
            int sy = cy + 27;
            for (int lineIdx = 0; lineIdx < Math.min(2, subLines.size()); lineIdx++) {
                graphics.text(font, subLines.get(lineIdx), left + 28, sy, 0xFF94A3B8, false);
                sy += 9;
            }

            boolean canAfford = !isMax && prereqMet && (playerDiamonds >= nextCost);
            String btnText = isMax ? "МАКСИМУМ" : ("Купить (" + nextCost + " ♦)");
            String tip = (currentLvl > 0 ? "§aАктивно сейчас: §f" + upg.getSummaryForLevel(currentLvl) + "\n" : "")
                    + (!isMax ? "§eСледующий ур.: §f" + upg.getSummaryForLevel(currentLvl + 1) + "\n" : "§aПрокачано на максимум!\n")
                    + "§7" + upg.getMechanicDescription();

            drawSmallButton(graphics, canAfford ? "btn_blue" : "btn_gray", btnX, btnY, btnW, btnH, btnText, mouseX, mouseY, () -> {
                if (canAfford) sendMarketAction("BUY_DIAMOND_UPGRADE", upg.getId(), "", 0, 0.0);
            }, tip);
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, top, width, height, maxScroll, scrollOffset);
    }

    // ==========================================
    // TAB: CONTRACTS (Fixed Hover, No Missing Texture, Clean Text)
    // ==========================================
    private void renderContracts(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event,
                                 int left, int top, int width, int height, int mouseX, int mouseY) {
        long gameTick = ClientMarketState.getCurrentGameTick();

        int ctrlH = 22;
        drawSprite(graphics, "hud_bg", left, top, width, ctrlH, 0xFF141926);
        drawClampedText(graphics, "§e§lКОРПОРАТИВНЫЕ КОНТРАКТЫ", left + 8, top + 6, width - 130, 0xFFFBBF24, false);

        int refW = 115;
        int refH = 16;
        int refX = left + width - refW - 4;
        int refY = top + 3;
        boolean canRefresh = account.getCrBalance() >= 100L;
        drawSmallButton(graphics, canRefresh ? "btn_amber" : "btn_gray", refX, refY, refW, refH, "Обновить (100 CR)", mouseX, mouseY, () -> {
            if (canRefresh) sendMarketAction("REFRESH_CONTRACTS", "", "", 0, 0.0);
        }, "Обновить свободные слоты (активные и готовые контракты сохранятся)");

        int listY = top + ctrlH + 6;
        int listH = height - (listY - top);

        List<ContractEntry> contracts = account.getContracts();
        int itemH = 66;
        int spacing = 70;
        maxScroll = Math.max(0, contracts.size() * spacing - listH);

        setScissor(graphics, left, listY, left + width, listY + listH);

        for (int i = 0; i < contracts.size(); i++) {
            ContractEntry c = contracts.get(i);
            int cy = listY + (i * spacing) - scrollOffset;
            if (cy + itemH < listY || cy > listY + listH) continue;

            drawSprite(graphics, c.completed() ? "card_selected" : "card", left, cy, width, itemH, 0xFF121622);

            double daysLeft = (c.deadlineTick() > gameTick)
                    ? Math.max(0.0, (c.deadlineTick() - gameTick) / 24000.0)
                    : (double) c.durationDays();

            int btnW = 96;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 22;
            int textMaxW = width - btnW - 22;

            String badge;
            if (c.claimed()) {
                badge = "§8[ЗАКРЫТ]";
            } else if (c.completed()) {
                badge = "§a[ГОТОВО]";
            } else if (c.active()) {
                badge = String.format(Locale.ROOT, "§e[Ост: %.1f дн.]", daysLeft);
            } else {
                badge = String.format(Locale.ROOT, "§7[Срок: %d дн.]", c.durationDays());
            }
            int badgeW = font.width(badge);
            drawClampedText(graphics, "§e§l" + c.title(), left + 8, cy + 5, Math.max(60, textMaxW - badgeW - 8), 0xFFFBBF24, false);
            graphics.text(font, badge, left + 8 + textMaxW - badgeW, cy + 5, 0xFFFFFFFF, false);

            List<net.minecraft.util.FormattedCharSequence> descLines = font.split(Component.literal("§7" + c.description()), textMaxW);
            int dY = cy + 16;
            for (int d = 0; d < Math.min(2, descLines.size()); d++) {
                graphics.text(font, descLines.get(d), left + 8, dY, 0xFF94A3B8, false);
                dY += 9;
            }

            long eventPayout = Math.round(c.rewardCr() * event.getContractRewardMultiplier());
            String payoutLine = String.format(Locale.ROOT,
                    "§a+%d CR  §b+%d ♦  §8|  §cШтраф: -%d CR",
                    eventPayout, c.rewardDiamonds(), c.penaltyCr());
            drawClampedText(graphics, payoutLine, left + 8, cy + 37, textMaxW, 0xFFCBD5E1, false);

            int barW = Math.min(155, Math.max(60, textMaxW - 96));
            int barH = 5;
            int barX = left + 8;
            int barY = cy + 51;
            double prog = c.targetProgress() > 0 ? ((double) c.currentProgress() / (double) c.targetProgress()) : 1.0;
            drawProgressBar(graphics, barX, barY, barW, barH, prog, c.completed() ? "bar_green" : "bar_blue");
            drawClampedText(graphics, "§f" + ContractBoardCatalog.formatProgress(c), barX + barW + 8, cy + 49, textMaxW - barW - 8, 0xFFCBD5E1, false);

            if (c.claimed()) {
                drawButton(graphics, "btn_gray", btnX, btnY, btnW, btnH, "ЗАКРЫТ", mouseX, mouseY, () -> {});
            } else if (c.completed()) {
                String claimTip = String.format(Locale.ROOT, "§aЗабрать награду: §f+%d CR §8| §b+%d ♦", eventPayout, c.rewardDiamonds());
                boolean bHov = !isModalOpen() && (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH);
                drawSprite(graphics, bHov ? "btn_amber_hover" : "btn_amber", btnX, btnY, btnW, btnH, 0xFF92400E);
                drawClampedCenteredText(graphics, "§lЗАБРАТЬ", btnX + btnW / 2, btnY + (btnH - 8) / 2, btnW - 8, 0xFFFFFFFF, false);
                registerClick(btnX, btnY, btnX + btnW, btnY + btnH, () -> {
                    sendMarketAction("CLAIM_CONTRACT", c.id(), "", 0, 0.0);
                }, claimTip);
            } else if (c.active()) {
                String activeTip = String.format(Locale.ROOT, "§eКонтракт в работе\n§7Осталось: §f%.1f дн.\n§cШтраф за просрочку: -%d CR (+15%% налог)", daysLeft, c.penaltyCr());
                drawSmallButton(graphics, "btn_blue", btnX, btnY, btnW, btnH, "В РАБОТЕ", mouseX, mouseY, () -> {}, activeTip);
            } else {
                String acceptTip = String.format(Locale.ROOT, "§aВзять контракт в работу\n§7Срок: §f%d дн. §8| §a+%d CR §b+%d ♦\n§cШтраф при срыве: -%d CR (+15%% налог)",
                        c.durationDays(), eventPayout, c.rewardDiamonds(), c.penaltyCr());
                drawSmallButton(graphics, "btn_green", btnX, btnY, btnW, btnH, "ПРИНЯТЬ", mouseX, mouseY, () -> {
                    sendMarketAction("ACCEPT_CONTRACT", c.id(), "", 0, 0.0);
                }, acceptTip);
            }
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, top, width, height, maxScroll, scrollOffset);
    }

    // ==========================================
    // TAB: BLACK_MARKET (Always-open Invest Roulette + Early Contraband Unlock)
    // ==========================================
    private void renderBlackMarket(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event, int playerDiamonds,
                                   int left, int top, int width, int height, int mouseX, int mouseY) {
        long gameTick = ClientMarketState.getCurrentGameTick();
        boolean unlocked = account.isBlackMarketUnlocked();

        // 1. Always-available High-Voltage Invest Roulette ("ОТЫГРАТЬСЯ 50/50!")
        int casinoH = 66;
        drawSprite(graphics, "card_amber", left, top, width, casinoH, 0xFF1F1224);
        drawClampedText(graphics, "§d§lИНВЕСТ-РУЛЕТКА БИРЖИ §8// §eКАЗИНО (50/50 ОТЫГРАТЬСЯ!)",
                left + 8, top + 5, width - 16, 0xFFFBBF24, false);
        drawClampedText(graphics, "§7Шансы: §c46% слив §8| §a34% x2 §8| §e15% x3 §8| §65% ДЖЕКПОТ x5 + ♦",
                left + 8, top + 16, width - 16, 0xFFCBD5E1, false);

        int rGap = 5;
        int rBtnW = (width - 16 - rGap * 3) / 4;
        int rLastW = (width - 16) - (rBtnW + rGap) * 3;
        int rBtnY = top + 31;
        int rBtnH = 26;
        boolean compactR = rBtnW < 88;
        String rTip = "§7Шансы: §c46% слив §8| §a34% x2 §8| §e15% x3 §8| §65% ДЖЕКПОТ x5 + ♦";

        boolean can150 = account.getCrBalance() >= 150L;
        drawSmallButton(graphics, can150 ? "btn_green" : "btn_gray", left + 8, rBtnY, rBtnW, rBtnH,
                compactR ? "150 CR" : "150 CR (x2..5)", mouseX, mouseY, () -> {
                    if (can150) sendMarketAction("SPIN_ROULETTE", "CR_150", "", 0, 0.0);
                }, "Ставка 150 CR\n" + rTip);

        boolean can500 = account.getCrBalance() >= 500L;
        drawSmallButton(graphics, can500 ? "btn_amber" : "btn_gray", left + 8 + (rBtnW + rGap), rBtnY, rBtnW, rBtnH,
                compactR ? "500 CR" : "500 CR (x2..5)", mouseX, mouseY, () -> {
                    if (can500) sendMarketAction("SPIN_ROULETTE", "CR_500", "", 0, 0.0);
                }, "Ставка 500 CR\n" + rTip);

        boolean canAllIn = account.getCrBalance() >= 100L;
        drawSmallButton(graphics, canAllIn ? "btn_red" : "btn_gray", left + 8 + (rBtnW + rGap) * 2, rBtnY, rBtnW, rBtnH,
                compactR ? "ВА-БАНК!" : "ВА-БАНК (ВСЁ!)", mouseX, mouseY, () -> {
                    if (canAllIn) sendMarketAction("SPIN_ROULETTE", "CR_ALL", "", 0, 0.0);
                }, "Поставить весь баланс CR!\n" + rTip);

        boolean canDia = playerDiamonds >= 2;
        drawSmallButton(graphics, canDia ? "btn_blue" : "btn_gray", left + 8 + (rBtnW + rGap) * 3, rBtnY, rLastW, rBtnH,
                "РИСК 2 ♦", mouseX, mouseY, () -> {
                    if (canDia) sendMarketAction("SPIN_ROULETTE", "DIA_2", "", 0, 0.0);
                }, "Ставка 2 ♦\n" + rTip);

        int listTop = top + casinoH + 6;
        int listH = Math.max(40, height - (listTop - top));

        if (!unlocked) {
            int cardW = Math.min(420, width - 16);
            int cardH = Math.min(listH - 8, 134);
            int cardX = left + (width - cardW) / 2;
            int cardY = listTop + Math.max(4, (listH - cardH) / 2);

            drawSprite(graphics, "card_purple", cardX, cardY, cardW, cardH, 0xFF180E24);

            graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_BLACKMARKET, cardX + 10, cardY + 7, 0.0f, 0.0f, 12, 12, 16, 16, 16, 16);
            drawClampedCenteredText(graphics, "§c§lСЕКТОР 7: НЕЛЕГАЛЬНЫЕ ЧИПЫ ЗАКРЫТЫ", cardX + cardW / 2, cardY + 8, cardW - 40, 0xFFEF4444, true);

            int stage = account.getStoryStage();
            int vip = account.getUpgradeLevel(UpgradeType.VIP_CLIENT);
            int deals = account.getTotalDealsCount();
            int progTasks = account.getStoryProgress().getTotalCompletedProgramTasks();

            drawClampedText(graphics, "§6Для допуска к нелегальным чипам выполните ЛЮБОЕ ОДНО:", cardX + 14, cardY + 28, cardW - 28, 0xFFFBBF24, false);
            drawClampedText(graphics, (stage >= 2 ? "§a✔ " : "§c○ ") + "§f1. Этап Программы: §eЭтап 2+ §7(" + stage + "/2)", cardX + 14, cardY + 44, cardW - 28, 0xFFFFFFFF, false);
            drawClampedText(graphics, (vip >= 1 ? "§a✔ " : "§c○ ") + "§f2. VIP-Статус: §bVIP I+ §7(" + vip + "/1)", cardX + 14, cardY + 58, cardW - 28, 0xFFFFFFFF, false);
            drawClampedText(graphics, (deals >= 3 ? "§a✔ " : "§c○ ") + "§f3. Активность: §a3+ сделки (можно рулеткой!) §7(" + deals + "/3)", cardX + 14, cardY + 72, cardW - 28, 0xFFFFFFFF, false);
            drawClampedText(graphics, (progTasks >= 6 ? "§a✔ " : "§c○ ") + "§f4. Задания Программы: §a6 квестов §7(" + progTasks + "/6)", cardX + 14, cardY + 86, cardW - 28, 0xFFFFFFFF, false);

            maxScroll = 0;
            return;
        }

        int itemH = 62;
        int spacing = 66;
        BlackMarketItem[] items = BlackMarketItem.values();
        maxScroll = Math.max(0, items.length * spacing - listH);

        setScissor(graphics, left, listTop, left + width, listTop + listH);

        for (int i = 0; i < items.length; i++) {
            BlackMarketItem bm = items[i];
            int cy = listTop + (i * spacing) - scrollOffset;
            if (cy + itemH < listTop || cy > listTop + listH) continue;

            boolean active = account.isBlackMarketEffectActive(bm, gameTick);
            long cd = account.getBlackMarketCooldownRemainingTicks(bm, gameTick);
            boolean onCd = cd > 0L;

            drawSprite(graphics, "card_purple", left, cy, width, itemH, 0xFF180E24);

            int btnW = 120;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 20;
            int textW = Math.max(80, btnX - (left + 28) - 8);

            String status = active ? "§a[АКТИВЕН]" : (onCd ? ("§c[КД " + (cd / 20L) + "с]") : "§d[ГОТОВ]");
            drawItemSlot(graphics, left + 6, cy + 8, getBlackMarketIconStack(bm));
            drawClampedText(graphics, "§d§l" + bm.getTitle() + "  " + status, left + 28, cy + 6, textW, 0xFFC084FC, false);

            List<net.minecraft.util.FormattedCharSequence> sumLines =
                    font.split(Component.literal("§7" + bm.getSummary()), textW);
            for (int d = 0; d < Math.min(3, sumLines.size()); d++) {
                graphics.text(font, sumLines.get(d), left + 28, cy + 19 + (d * 10), 0xFF94A3B8, false);
            }

            boolean canAfford = (account.getCrBalance() >= bm.getCostCr() && playerDiamonds >= bm.getCostDiamonds());
            boolean canBuy = canAfford && !onCd && (!active || bm == BlackMarketItem.SECOND_CHANCE || bm == BlackMarketItem.GOLDEN_CONTRACT);

            String costStr = bm.getCostCr() + " CR + " + bm.getCostDiamonds() + " ♦";
            drawButton(graphics, canBuy ? "btn_purple" : "btn_gray", btnX, btnY, btnW, btnH, costStr, mouseX, mouseY, () -> {
                if (canBuy) sendMarketAction("BUY_BLACK_MARKET", bm.getId(), "", 0, 0.0);
            });
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, listTop, width, listH, maxScroll, scrollOffset);
    }

    // ==========================================
    // TAB: RESOURCE_MARKET
    // ==========================================
    private void renderResourceMarket(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event,
                                      int left, int top, int width, int height, int mouseX, int mouseY) {
        int toggleW = 100;
        int toggleH = 16;

        drawPill(graphics, left, top, toggleW, toggleH, "ПРОДАЖА ИНВЕНТАРЯ", !resourceCatalogMode, mouseX, mouseY, () -> {
            resourceCatalogMode = false;
            scrollOffset = 0;
        });

        drawPill(graphics, left + toggleW + 4, top, toggleW, toggleH, "КАТАЛОГ СЫРЬЯ", resourceCatalogMode, mouseX, mouseY, () -> {
            resourceCatalogMode = true;
            scrollOffset = 0;
        });

        long dayIndex = (minecraft != null && minecraft.level != null) ? (minecraft.level.getOverworldClockTime() / 24000L) : 0L;
        int dailyDiamondLimit = account.getDailyDiamondExchangeLimit();
        int remainingDiamondQuota = account.getRemainingDailyDiamondExchange(dayIndex);
        String quotaBadge = String.format(Locale.ROOT, "Лимит алмазов: §b%d/%d§7/дн.",
                remainingDiamondQuota, dailyDiamondLimit);
        int quotaBadgeW = font.width(quotaBadge) + 22;
        if (left + (toggleW * 2) + 12 + quotaBadgeW <= left + width) {
            int qx = left + width - quotaBadgeW;
            drawSprite(graphics, "hud_bg", qx, top, quotaBadgeW, toggleH, remainingDiamondQuota > 0 ? 0xFF0E1826 : 0xFF281016);
            graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_DIAMOND, qx + 4, top + 3, 0.0f, 0.0f, 10, 10, 16, 16, 16, 16);
            drawClampedText(graphics, quotaBadge, qx + 17, top + 4, quotaBadgeW - 20, 0xFFCBD5E1, false);
        }

        int listY = top + 22;
        int listH = height - (listY - top);

        if (!resourceCatalogMode) {
            Inventory inv = (minecraft != null && minecraft.player != null) ? minecraft.player.getInventory() : null;
            if (inv == null) return;

            List<Integer> validSlots = new ArrayList<>();
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                        .getNamespace().equals(PlayerMarketMod.MOD_ID)) {
                    validSlots.add(i);
                }
            }

            int itemH = 34;
            int spacing = 38;
            maxScroll = Math.max(0, validSlots.size() * spacing - listH);

            setScissor(graphics, left, listY, left + width, listY + listH);

            for (int idx = 0; idx < validSlots.size(); idx++) {
                int slot = validSlots.get(idx);
                int cy = listY + (idx * spacing) - scrollOffset;
                if (cy + itemH < listY || cy > listY + listH) continue;

                ItemStack stack = inv.getItem(slot);
                long unitPrice = ResourceValuationEngine.evaluateUnitSellCr(stack, account, event);
                int eqPerUnit = stack.is(Items.DIAMOND_BLOCK) ? 9 : (stack.is(Items.DIAMOND) ? 1 : 0);
                int sellableUnits = eqPerUnit > 0 ? Math.min(stack.getCount(), remainingDiamondQuota / eqPerUnit) : stack.getCount();
                long totalPayout = unitPrice * Math.max(1, sellableUnits);

                drawSprite(graphics, "card", left, cy, width, itemH, 0xFF121622);

                int btnW = 76;
                int btnH = 18;
                int btnX1 = left + width - (btnW * 2) - 10;
                int btnX2 = left + width - btnW - 6;
                int btnY = cy + 8;
                int sellTextMaxW = Math.max(80, btnX1 - (left + 28) - 8);

                drawItemSlot(graphics, left + 6, cy + 8, stack);
                String nameLine = "§f" + stack.getCount() + "x §e" + stack.getHoverName().getString();
                if (eqPerUnit > 0) {
                    nameLine += " §8(§bост. " + (remainingDiamondQuota / eqPerUnit) + "§8)";
                }
                drawClampedText(graphics, nameLine, left + 28, cy + 6, sellTextMaxW, 0xFFFFFFFF, false);
                drawClampedText(graphics, "§7Цена: §a" + unitPrice + " CR/шт §8| Итого: §a+" + (sellableUnits > 0 ? totalPayout : 0L) + " CR", left + 28, cy + 18, sellTextMaxW, 0xFF94A3B8, false);

                boolean canSellItem = (eqPerUnit == 0) || (sellableUnits > 0);
                String slotTip = canSellItem ? null : "Дневной лимит обмена алмазов (" + dailyDiamondLimit + "/день) исчерпан!";
                drawSmallButton(graphics, canSellItem ? "btn_green" : "btn_gray", btnX1, btnY, btnW, btnH,
                        canSellItem ? ("Слот (+" + totalPayout + ")") : ("Лимит " + dailyDiamondLimit + "/дн."), mouseX, mouseY, () -> {
                    if (canSellItem) sendMarketAction("SELL_INVENTORY_SLOT", "", "", slot, 0.0);
                }, slotTip);

                drawSmallButton(graphics, canSellItem ? "btn_amber" : "btn_gray", btnX2, btnY, btnW, btnH,
                        eqPerUnit > 0 ? (canSellItem ? "Обм. до " + (remainingDiamondQuota / eqPerUnit) : "Лимит") : "Оптом всё", mouseX, mouseY, () -> {
                    if (canSellItem) sendMarketAction("SELL_ALL_MATCHING_ITEM", "", "", slot, 0.0);
                }, slotTip);
            }

            clearScissor(graphics);
        } else {
            List<ResourceValuationEngine.CatalogItem> catalog = ResourceValuationEngine.getCatalogOffers(account, event);
            int itemH = 34;
            int spacing = 38;
            maxScroll = Math.max(0, catalog.size() * spacing - listH);

            setScissor(graphics, left, listY, left + width, listY + listH);

            for (int i = 0; i < catalog.size(); i++) {
                ResourceValuationEngine.CatalogItem offer = catalog.get(i);
                int cy = listY + (i * spacing) - scrollOffset;
                if (cy + itemH < listY || cy > listY + listH) continue;

                int eqPerUnit = offer.item() == Items.DIAMOND_BLOCK ? 9 : (offer.item() == Items.DIAMOND ? 1 : 0);
                boolean withinQuota = (eqPerUnit == 0) || (remainingDiamondQuota >= eqPerUnit * offer.bundleSize());
                boolean canAfford = withinQuota && (account.getCrBalance() >= offer.buyCostCr());

                drawSprite(graphics, "card", left, cy, width, itemH, 0xFF121622);

                int btnW = 100;
                int btnH = 18;
                int btnX = left + width - btnW - 8;
                int btnY = cy + 8;
                int buyTextMaxW = Math.max(80, btnX - (left + 28) - 8);

                drawItemSlot(graphics, left + 6, cy + 8, new ItemStack(offer.item()));
                String titleLine = "§f" + offer.bundleSize() + "x §b" + offer.displayName();
                if (eqPerUnit > 0) {
                    titleLine += " §8(§bост. " + remainingDiamondQuota + "/" + dailyDiamondLimit + "§8)";
                }
                drawClampedText(graphics, titleLine, left + 28, cy + 6, buyTextMaxW, 0xFFFFFFFF, false);
                drawClampedText(graphics, "§7Стоимость партии: §e" + offer.buyCostCr() + " CR", left + 28, cy + 18, buyTextMaxW, 0xFF94A3B8, false);

                String buyBtnText = !withinQuota ? ("Лимит " + dailyDiamondLimit + "/дн.") : ("Купить (-" + offer.buyCostCr() + ")");
                String buyTip = !withinQuota ? "Дневной лимит обмена алмазов (" + dailyDiamondLimit + "/день) исчерпан!" : null;
                drawSmallButton(graphics, canAfford ? "btn_blue" : "btn_gray", btnX, btnY, btnW, btnH, buyBtnText, mouseX, mouseY, () -> {
                    if (canAfford) sendMarketAction("BUY_CATALOG_RESOURCE", offer.itemId(), "", 0, 0.0);
                }, buyTip);
            }

            clearScissor(graphics);
        }
    }

    // ==========================================
    // TAB: PORTFOLIO (Fully Scrollable, Modular Grid & Wealth Tax Status)
    // ==========================================
    private void renderPortfolio(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event, int diamonds,
                                int left, int top, int width, int height, int mouseX, int mouseY) {
        long gameTick = ClientMarketState.getCurrentGameTick();

        List<Map.Entry<AssetType, String>> activeDivs = new ArrayList<>();
        for (Map.Entry<String, String> entry : account.getSelectedDividends().entrySet()) {
            Optional<AssetType> assetOpt = AssetType.byId(entry.getKey());
            if (assetOpt.isEmpty()) continue;
            AssetType a = assetOpt.get();
            if (account.getTotalSoldFraction(a) <= 0.001) continue;
            String divSummary = entry.getValue().equals(a.getDividendA().id()) ? a.getDividendA().label() : a.getDividendB().label();
            activeDivs.add(Map.entry(a, divSummary));
        }

        int card1H = 56;
        int card2H = 64;
        int leaseCardH = account.getActiveLeases().isEmpty() ? 52 : (26 + account.getActiveLeases().size() * 24);
        int divRows = (activeDivs.size() + 1) / 2;
        int divCardH = activeDivs.isEmpty() ? 46 : (28 + divRows * 14);
        int contentTotalH = card1H + 6 + card2H + 6 + leaseCardH + 6 + divCardH + 4;
        maxScroll = Math.max(0, contentTotalH - height);

        setScissor(graphics, left, top, left + width, top + height);

        int cy = top - scrollOffset;
        int halfW = (width - 24) / 2;

        // Card 1: Executive Telemetry Overview
        drawSprite(graphics, "hud_bg", left, cy, width, card1H, 0xFF111724);
        drawClampedText(graphics, "§a§lФИНАНСОВЫЙ СТАТУС", left + 8, cy + 6, halfW, 0xFF10B981, false);
        drawClampedText(graphics, "§b§lАПГРЕЙДЫ АККАУНТА", left + width / 2 + 6, cy + 6, halfW, 0xFF38BDF8, false);

        drawClampedText(graphics, "• Баланс: §e" + formatNumber(account.getCrBalance()) + " CR", left + 8, cy + 20, halfW, 0xFFCBD5E1, false);
        drawClampedText(graphics, "• VIP: §bУр. " + account.getUpgradeLevel(UpgradeType.VIP_CLIENT)
                + " §8| Прем: §bУр. " + account.getUpgradeLevel(UpgradeType.PREMIUM_ACCOUNT), left + width / 2 + 6, cy + 20, halfW, 0xFFCBD5E1, false);

        drawClampedText(graphics, "• Сделок: §f" + account.getTotalDealsCount() + " шт. §8| Алм: §b" + diamonds + " ♦", left + 8, cy + 34, halfW, 0xFFCBD5E1, false);
        drawClampedText(graphics, "• Страховка: §bУр. " + account.getUpgradeLevel(UpgradeType.MARKET_INSURANCE), left + width / 2 + 6, cy + 34, halfW, 0xFFCBD5E1, false);

        cy += card1H + 6;

        // Card 2: Legal & Sanctions Status (Wealth Tax & Penalties)
        drawSprite(graphics, "card", left, cy, width, card2H, 0xFF141926);
        drawClampedText(graphics, "§c§lЮРИДИЧЕСКИЙ СТАТУС, НАЛОГИ И САНКЦИИ", left + 8, cy + 6, width - 16, 0xFFEF4444, false);
        long wealthTaxEst = Math.max(260L, Math.round(account.getCrBalance() * 0.15));
        long greedTaxEst = Math.max(250L, Math.round(account.getCrBalance() * 0.18));
        drawClampedText(graphics, "• Просрочка контракта: §cот 260 CR / 15% CR §7(~" + formatNumber(wealthTaxEst) + " CR) + дебаффы", left + 8, cy + 20, width - 16, 0xFFCBD5E1, false);
        drawClampedText(graphics, "• Налог 0-статов (жадность): §c18% CR §7(~" + formatNumber(greedTaxEst) + " CR) §8| Смерть: §c10% CR", left + 8, cy + 33, width - 16, 0xFFCBD5E1, false);
        long leaseYield = account.calculateLeasePassiveInterestPerMinuteCr();
        drawClampedText(graphics, "• Доход по вкладам: §a+" + leaseYield + " CR/мин §8| Авто-возврат характеристик бесплатно", left + 8, cy + 46, width - 16, 0xFF34D399, false);

        cy += card2H + 6;

        // Card 3: Active Leases (Deposits) with Early Cancellation
        drawSprite(graphics, "card", left, cy, width, leaseCardH, 0xFF121622);
        drawClampedText(graphics, "§e§lАКТИВНЫЕ ВКЛАДЫ (ЛИЗИНГ ХАРАКТЕРИСТИК): " + account.getActiveLeases().size() + " шт.", left + 8, cy + 6, width - 16, 0xFFFBBF24, false);
        if (account.getActiveLeases().isEmpty()) {
            drawClampedText(graphics, "§7Нет действующих договоров вклада. Оформите лизинг в разделе «ПРОДАЖА».", left + 8, cy + 22, width - 16, 0xFF64748B, false);
            drawClampedText(graphics, "§8(Лизинг приносит процент и возвращает статы автоматически по окончании срока)", left + 8, cy + 34, width - 16, 0xFF64748B, false);
        } else {
            for (int li = 0; li < account.getActiveLeases().size(); li++) {
                LeaseContract lease = account.getActiveLeases().get(li);
                int lRowY = cy + 22 + li * 24;
                double daysLeft = Math.max(0.0, (lease.endTick() - gameTick) / 24000.0);
                String aName = AssetType.byId(lease.assetId()).map(AssetType::getDisplayName).orElse(lease.assetId());
                long cancelCost = MarketAttributeApplier.calculateLeaseCancelCostCr(lease);
                boolean canAfford = account.getCrBalance() >= cancelCost;

                int cBtnW = 140;
                int cBtnH = 18;
                int cBtnX = left + width - cBtnW - 8;
                int leaseTextW = Math.max(70, cBtnX - (left + 8) - 8);
                drawClampedText(graphics, String.format(Locale.ROOT, "• §f%s: §c-%.0f%% §8(Ост: §d%.1f дн.§8)", aName, lease.fraction() * 100.0, daysLeft),
                        left + 8, lRowY + 4, leaseTextW, 0xFFCBD5E1, false);

                drawSmallButton(graphics, canAfford ? "btn_amber" : "btn_gray", cBtnX, lRowY, cBtnW, cBtnH,
                        "✕ Снять (-" + formatNumber(cancelCost) + " CR)", mouseX, mouseY, () -> {
                            if (canAfford) sendMarketAction("CANCEL_LEASE", lease.id(), "", 0, 0.0);
                        });
            }
        }

        cy += leaseCardH + 6;

        // Card 4: Active Dividends in 2-column Grid
        drawSprite(graphics, "card", left, cy, width, divCardH, 0xFF0D121B);
        drawClampedText(graphics, "§d§lВЫБРАННЫЕ ДИВИДЕНДЫ И ПАССИВНЫЕ ДОХОДЫ:", left + 8, cy + 6, width - 16, 0xFFC084FC, false);

        if (activeDivs.isEmpty()) {
            drawClampedText(graphics, "§7Вы пока не выбрали ни одного дивиденда (продайте стат в разделе «ПРОДАЖА»).", left + 8, cy + 24, width - 16, 0xFF64748B, false);
        } else {
            int dColW = (width - 24) / 2;
            for (int idx = 0; idx < activeDivs.size(); idx++) {
                var entry = activeDivs.get(idx);
                int col = idx % 2;
                int row = idx / 2;
                int dx = left + 8 + col * (dColW + 8);
                int dy = cy + 22 + row * 14;
                drawClampedText(graphics, "• §f" + entry.getKey().getShortName() + ": §a" + entry.getValue(), dx, dy, dColW, 0xFFCBD5E1, false);
            }
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, top, width, height, maxScroll, scrollOffset);
    }

    // ==========================================
    // TAB: LEDGER
    // ==========================================
    private void renderLedger(GuiGraphicsExtractor graphics, PlayerMarketAccount account,
                             int left, int top, int width, int height, int mouseX, int mouseY) {
        List<TransactionRecord> hist = account.getHistory();
        if (hist.isEmpty()) {
            drawSprite(graphics, "hud_bg", left, top, width, height, 0xFF121622);
            drawClampedCenteredText(graphics, "§7История финансовых операций пуста.", left + width / 2, top + height / 2, width - 20, 0xFF94A3B8, false);
            maxScroll = 0;
            return;
        }

        int itemH = 26;
        int spacing = 28;
        maxScroll = Math.max(0, hist.size() * spacing - height);

        setScissor(graphics, left, top, left + width, top + height);

        for (int i = 0; i < hist.size(); i++) {
            TransactionRecord rec = hist.get(i);
            int cy = top + (i * spacing) - scrollOffset;
            if (cy + itemH < top || cy > top + height) continue;

            drawSprite(graphics, "card", left, cy, width, itemH, 0xFF10141D);

            String deltaStr = (rec.deltaCr() > 0 ? "§a+" : (rec.deltaCr() < 0 ? "§c" : "§7")) + rec.deltaCr() + " CR";
            if (rec.deltaDiamonds() != 0) {
                deltaStr += " §8| §b" + (rec.deltaDiamonds() > 0 ? "+" : "") + rec.deltaDiamonds() + " ♦";
            }

            int dw = font.width(deltaStr);
            int ledgerTextW = Math.max(80, width - dw - 24);
            drawClampedText(graphics, "§e" + rec.category() + " §8| §f" + rec.title(), left + 8, cy + 4, ledgerTextW, 0xFFFFFFFF, false);
            drawClampedText(graphics, "§8" + rec.detail(), left + 8, cy + 14, ledgerTextW, 0xFF64748B, false);

            graphics.text(font, deltaStr, left + width - dw - 8, cy + 9, 0xFFFFFFFF, false);
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, top, width, height, maxScroll, scrollOffset);
    }

    // ==========================================
    // TAB: NEWS (Creative Financial Dispatch, Interactive Modal Reader)
    // ==========================================
    private NewsArticle buildEventArticle(MarketEvent event) {
        return switch (event) {
            case MARKET_CRASH -> new NewsArticle(
                    "event_crash", "BREAKING", "КРАХ БИРЖИ", "ЧЁРНЫЙ ДЕНЬ: ОБВАЛ КОТИРОВОК И ПАНИКА", "Текущий цикл",
                    "Глобальный обвал цен! Продажа статов -35%, комиссии выкупа +30%, но антикризисные контракты платят +60% CR!",
                    List.of(
                            "На бирже зафиксирован масштабный обвал котировок! В результате переизбытка предложений продажа характеристик обесценена на 35%.",
                            "Комиссия за досрочный обратный выкуп выросла на 30%. Однако корпорации ввели экстренный пакет спасения: выплаты за любые контракты увеличены на 60%!"
                    ),
                    "§cТРЕВОГА: §fВоздержитесь от продажи характеристик и выкупа. Выполняйте контракты — это принесёт рекордные +60% CR!",
                    0xFFEF4444
            );
            case BULL_MARKET -> new NewsArticle(
                    "event_bull", "BREAKING", "БЫЧИЙ ТРЕНД", "АЖИОТАЖНЫЙ РОСТ: ИСТОРИЧЕСКИЙ МАКСИМУМ", "Текущий цикл",
                    "Биржевой бум! Выплаты за любые характеристики +40%, сдача сырья +35%, контракты +30%.",
                    List.of(
                            "Инвестиционные фонды вливают триллионы в скупку биологических и двигательных активов. Цены на продажу и лизинг статов достигли рекордных отметок (+40%).",
                            "Также взлетели цены на приём полезных ископаемых (+35%). Идеальный момент для фиксации сверхприбыли."
                    ),
                    "§aСОВЕТ: §fСдавайте характеристики и ресурсы! Цены на историческом пике (+40%).",
                    0xFF10B981
            );
            case TECH_BREAKTHROUGH -> new NewsArticle(
                    "event_tech", "BREAKING", "ИННОВАЦИИ", "ПРОРЫВ БИОИНЖЕНЕРИИ: СКИДКА 30% НА АУГМЕНТАЦИИ", "Текущий цикл",
                    "Технологический бум! Стоимость улучшений в Магазине снижена на 30%, а обратный выкуп дешевле на 25%.",
                    List.of(
                            "Лаборатории совершили фундаментальный прорыв в синтезе имплантов. Стоимость прокачки VIP-статуса и покупки модификаций упала на 30%.",
                            "Кроме того, биржа субсидирует 25% стоимости возврата проданных характеристик. Прекрасный шанс вернуть своё здоровье со скидкой."
                    ),
                    "§aСОВЕТ: §fТратьте алмазы на улучшения (-30%) и выкупайте проданные статы с экономией 25%!",
                    0xFF38BDF8
            );
            case DEBT_CRISIS -> new NewsArticle(
                    "event_debt", "BREAKING", "КРИЗИС ДОЛГА", "ДОЛГОВОЙ КРИЗИС: ВЫСОКИЕ СТАВКИ ПО ВКЛАДАМ", "Текущий цикл",
                    "Дефицит ликвидности! Ставки по вкладам удвоены (+100%), выкуп со скидкой 30%, но сырьё дешевеет (-20%).",
                    List.of(
                            "Биржа испытывает острый кризис наличных средств и готова платить двойные проценты за временные вклады (лизинг).",
                            "Чтобы стабилизировать балансы участников, центробанк биржи субсидирует 30% расходов на выкуп проданных статов."
                    ),
                    "§eСОВЕТ: §fОформляйте лизинг характеристик под удвоенный пассивный доход и возвращайте статы с 30% скидкой!",
                    0xFFF59E0B
            );
            case HEALTH_DEMAND -> new NewsArticle(
                    "event_health", "BREAKING", "СРОЧНЫЙ ВЫПУСК", "БИОИНЖЕНЕРИЯ: ДЕФИЦИТ БИОМАТЕРИАЛА", "Текущий цикл",
                    "Фармацевтический конгломерат взвинтил выплаты за характеристики ТЕЛА (+35%).",
                    List.of(
                            "Спрос на биологические активы превысил все ожидания. Корпорации готовы платить повышенную премию за сдачу здоровья и регенерации.",
                            "Аналитики биржи прогнозируют кратковременный пик ликвидности. Рекомендуется использовать лизинг или частичную ликвидацию HP."
                    ),
                    "§aСОВЕТ: §fВыплаты за раздел ТЕЛО увеличены на 35%. Самое время открыть вклад!",
                    0xFF10B981
            );
            case SPEED_SHORTAGE -> new NewsArticle(
                    "event_speed", "BREAKING", "СРОЧНЫЙ ВЫПУСК", "ТРАНСПОРТНЫЙ КОМИТЕТ: КИНЕТИЧЕСКИЙ БУМ", "Текущий цикл",
                    "Логистические синдикаты скупают скорость и прыгучесть с надбавкой +40%.",
                    List.of(
                            "В связи с открытием новых торговых коридоров биржа фиксирует рекордную стоимость характеристик перемещения.",
                            "Продажа мобильности сейчас даёт максимальный начальный капитал, но помните о снижении скорости в опасных шахтах."
                    ),
                    "§aСОВЕТ: §fВыплаты за раздел ДВИЖЕНИЕ увеличены на 40%!",
                    0xFF38BDF8
            );
            case DIAMOND_DEMAND -> new NewsArticle(
                    "event_diamond", "BREAKING", "СРОЧНЫЙ ВЫПУСК", "СЫРЬЕВАЯ БИРЖА: АЛМАЗНОЕ РАЛЛИ", "Текущий цикл",
                    "Курс кристаллического углерода бьёт рекорды (+35%).",
                    List.of(
                            "Дефицит алмазов на мировом рынке вынуждает регулятора повысить закупочные цены.",
                            "Если у вас есть лишние алмазы или вы используете Upgrader, продавайте их в терминале с максимальной выгодой."
                    ),
                    "§aСОВЕТ: §fСдача алмазов на бирже приносит на 35% больше CR!",
                    0xFF38BDF8
            );
            case RESOURCE_OVERSUPPLY -> new NewsArticle(
                    "event_oversupply", "BREAKING", "ТОВАРНЫЙ РЫНОК", "ПЕРЕИЗБЫТОК СЫРЬЯ: СКЛАДСКОЙ ЗАТОР", "Текущий цикл",
                    "Склады переполнены. Сдача ресурсов -25%, но покупка в каталоге на 25% дешевле.",
                    List.of(
                            "Всплеск добычи руды привёл к перепроизводству. Приём сырья временно оплачивается по сниженному тарифу (-25%).",
                            "Однако для строителей и крафтеров это золотое время: покупка материалов в каталоге терминала стала на 25% дешевле."
                    ),
                    "§eСОВЕТ: §fПридержите ресурсы до Промышленного бума или докупите нужные блоки со скидкой 25%.",
                    0xFFF59E0B
            );
            case MARKET_PANIC -> new NewsArticle(
                    "event_panic", "BREAKING", "ТРЕВОГА", "ОБВАЛ РЫНКА: ПАНИКА НА БИРЖЕ", "Текущий цикл",
                    "Котировки статов обвалились (-20%), но контракты платят +35%!",
                    List.of(
                            "Внезапный сброс токсичных активов обрушил котировки характеристик. Продавать статы сейчас крайне невыгодно.",
                            "Однако корпорации срочно заказывают антикризисную работу: награды за корпоративные контракты повышены на 35%."
                    ),
                    "§cВНИМАНИЕ: §fНе продавайте статы! Сосредоточьтесь на выполнении контрактов (+35% CR).",
                    0xFFEF4444
            );
            case DIVIDEND_DAY -> new NewsArticle(
                    "event_div", "BREAKING", "АКЦИОНЕРЫ", "КВАРТАЛЬНЫЙ ОТЧЁТ: ДЕНЬ ДИВИДЕНДОВ", "Текущий цикл",
                    "Все выплаты за любые характеристики повышены на 25%, контракты +15%.",
                    List.of(
                            "Финансовый год завершается с рекордной прибылью. Биржа выплачивает дивидендные надбавки всем операторам.",
                            "Любая сделка продажи или лизинга сегодня приносит на четверть больше капитала."
                    ),
                    "§aСОВЕТ: §fСдавайте статы в лизинг или на продажу с надбавкой +25%!",
                    0xFF10B981
            );
            case CORPORATE_AUDIT -> new NewsArticle(
                    "event_audit", "BREAKING", "ПРОВЕРКА РЕГУЛЯТОРА", "СЛУЖБА БЕЗОПАСНОСТИ: КОРПОРАТИВНЫЙ АУДИТ", "Текущий цикл",
                    "Строгий аудит счетов: комиссия выкупа +20%, но улучшения со скидкой 15%.",
                    List.of(
                            "Комиссия по ценным бумагам проводит аудит операторов. Попытки выкупа статов сейчас облагаются штрафной наценкой.",
                            "В то же время лаборатории аугментаций снизили стоимость постоянных улучшений за алмазы на 15%."
                    ),
                    "§cВНИМАНИЕ: §fВоздержитесь от выкупа характеристик! Покупайте аугментации со скидкой 15%.",
                    0xFFF59E0B
            );
            case PREMIUM_WEEK -> new NewsArticle(
                    "event_premium", "BREAKING", "САММИТ ДИРЕКТОРОВ", "СОВЕТ ДИРЕКТОРОВ: ПРЕМИАЛЬНАЯ НЕДЕЛЯ", "Текущий цикл",
                    "Все выплаты в CR +20%, выкуп на 10% дешевле, апгрейды -15%!",
                    List.of(
                            "В честь ежегодного саммита директоров активирован льготный режим во всех секторах биржи.",
                            "Это идеальное окно для крупных сделок, погашения обязательств и масштабной прокачки персонажа."
                    ),
                    "§aСОВЕТ: §fЗолотое время биржи! Выполняйте контракты и улучшайте персонажа.",
                    0xFFFBBF24
            );
            case BUYBACK_DISCOUNT -> new NewsArticle(
                    "event_buyback", "BREAKING", "РЕГУЛЯТОР", "ГОСУДАРСТВЕННАЯ ПРОГРАММА: АМНИСТИЯ ВЫКУПА", "Текущий цикл",
                    "Центробанк снижает неустойку! Обратный выкуп характеристик на 20% дешевле.",
                    List.of(
                            "Центробанк биржи запустил программу социальной реабилитации операторов. Запретительные штрафы на возврат статов снижены.",
                            "Стоимость выкупа любых ранее проданных характеристик уменьшена на 20%."
                    ),
                    "§aСОВЕТ: §fВоспользуйтесь амнистией, чтобы вернуть проданное здоровье и силу по минимальной цене!",
                    0xFF38BDF8
            );
            case MINING_BOOM -> new NewsArticle(
                    "event_mining", "BREAKING", "ИНДУСТРИЯ", "ПРОМЫШЛЕННЫЙ БУМ: СВЕРХСПРОС НА РУДУ", "Текущий цикл",
                    "Тяжёлая индустрия наращивает мощности! Сдача ресурсов +30% и выплаты за ПРОФЕССИЮ +30%.",
                    List.of(
                            "Масштабное строительство орбитальных станций требует миллионов тонн сплавов и редких ископаемых.",
                            "Сдача ресурсов в терминале оплачивается с 30% надбавкой. Также повышены награды за выполнение квот добычи."
                    ),
                    "§aСОВЕТ: §fПродавайте железо, медь, уголь и золото с наценкой +30%!",
                    0xFFF59E0B
            );
            default -> new NewsArticle(
                    "event_stable", "BREAKING", "МАКРОЭКОНОМИКА", "БИРЖЕВОЙ ВЕСТНИК: РАВНОВЕСИЕ РЫНКА", "Текущий цикл",
                    "Стабильная ликвидность и базовые комиссии во всех секторах биржи.",
                    List.of(
                            "Рынок находится в фазе стабильного равновесия. Колебания цен отсутствуют, базовые тарифы зафиксированы.",
                            "Изучайте контракты, следите за почтой от Mr. Ledger и готовьтесь к следующим рыночным циклам."
                    ),
                    "§aСОВЕТ: §fПланируйте портфель и выполняйте текущие контракты.",
                    0xFF94A3B8
            );
        };
    }

    private void renderNews(GuiGraphicsExtractor graphics, MarketEvent event,
                           int left, int top, int width, int height, int mouseX, int mouseY) {
        // 1. Top Hero Card: Current Macroeconomic Broadcast & Timer
        int heroH = 48;
        drawSprite(graphics, "hud_bg", left, top, width, heroH, 0xFF111724);

        boolean campaignAccepted = ClientMarketState.getSharedCampaign().isCampaignAccepted();
        long nextTick = ClientMarketState.getNextEventSwitchTick();
        long currentTick = ClientMarketState.getCurrentGameTick();
        String timerStr;
        if (!campaignAccepted) {
            timerStr = "§8[Ждёт подписания договора]";
        } else if (nextTick > 0) {
            long remainingTicks = Math.max(0L, nextTick - currentTick);
            int sec = (int) (remainingTicks / 20L);
            timerStr = remainingTicks > 0L
                    ? String.format(Locale.ROOT, "§8[Смена цикла: §e%02d:%02d§8]", sec / 60, sec % 60)
                    : "§8[Смена цикла: §eскоро§8]";
        } else {
            timerStr = "§8[Смена цикла: §eскоро§8]";
        }
        int timerW = font.width(timerStr);

        drawClampedText(graphics, "§6§l● В ЭФИРЕ: §e" + event.getHeadline(), left + 8, top + 6, width - timerW - 16, 0xFFFBBF24, false);
        graphics.text(font, timerStr, left + width - timerW - 8, top + 6, 0xFFE2E8F0, false);
        drawClampedText(graphics, "§7" + event.getDescription(), left + 8, top + 19, width - 16, 0xFFCBD5E1, false);

        String multSummary = String.format(java.util.Locale.US,
                "§8Тело: §a%+.0f%% §8| Движение: §b%+.0f%% §8| Сырьё: §e%+.0f%% §8| Контракты: §6%+.0f%% §8| Выкуп: §d%+.0f%%",
                (event.getBodyMultiplier() - 1.0) * 100.0,
                (event.getMovementMultiplier() - 1.0) * 100.0,
                (event.getResourcePriceMultiplier() - 1.0) * 100.0,
                (event.getContractRewardMultiplier() - 1.0) * 100.0,
                (event.getBuybackCostMultiplier() - 1.0) * 100.0
        );
        drawClampedText(graphics, multSummary, left + 8, top + 32, width - 16, 0xFF94A3B8, false);

        // 2. Category Filter Pills Row (flush within [left .. left + width])
        int pillY = top + heroH + 4;
        int pillH = 16;
        int pillGap = 4;
        int pillW = (width - pillGap * 4) / 5;
        int lastPillW = width - (pillW + pillGap) * 4;
        int px = left;

        drawPill(graphics, px, pillY, pillW, pillH, "ВСЕ", selectedNewsCategory.equals("ALL"), mouseX, mouseY, () -> {
            selectedNewsCategory = "ALL";
            scrollOffset = 0;
        });
        px += pillW + pillGap;
        drawPill(graphics, px, pillY, pillW, pillH, "СРОЧНО", selectedNewsCategory.equalsIgnoreCase("BREAKING"), mouseX, mouseY, () -> {
            selectedNewsCategory = "BREAKING";
            scrollOffset = 0;
        });
        px += pillW + pillGap;
        drawPill(graphics, px, pillY, pillW, pillH, "РЫНОК", selectedNewsCategory.equalsIgnoreCase("CORP"), mouseX, mouseY, () -> {
            selectedNewsCategory = "CORP";
            scrollOffset = 0;
        });
        px += pillW + pillGap;
        drawPill(graphics, px, pillY, pillW, pillH, "ЧЁРНЫЙ", selectedNewsCategory.equalsIgnoreCase("DARK"), mouseX, mouseY, () -> {
            selectedNewsCategory = "DARK";
            scrollOffset = 0;
        });
        px += pillW + pillGap;
        drawPill(graphics, px, pillY, lastPillW, pillH, "ГАЙДЫ", selectedNewsCategory.equalsIgnoreCase("GUIDES"), mouseX, mouseY, () -> {
            selectedNewsCategory = "GUIDES";
            scrollOffset = 0;
        });

        // 3. Filtered Articles Scrollable List
        int listTop = pillY + pillH + 6;
        int listHeight = height - (listTop - top);

        List<NewsArticle> allArticles = new ArrayList<>();
        allArticles.add(buildEventArticle(event));
        allArticles.addAll(NEWS_ARTICLES);

        List<NewsArticle> filteredArticles = new ArrayList<>();
        for (NewsArticle art : allArticles) {
            if (selectedNewsCategory.equals("ALL") || art.filterGroup().equalsIgnoreCase(selectedNewsCategory)) {
                filteredArticles.add(art);
            }
        }

        int cardH = 56;
        int spacing = 60;
        int contentTotalH = filteredArticles.size() * spacing;
        maxScroll = Math.max(0, contentTotalH - listHeight);

        setScissor(graphics, left, listTop, left + width, listTop + listHeight);

        for (int i = 0; i < filteredArticles.size(); i++) {
            NewsArticle article = filteredArticles.get(i);
            int cy = listTop + (i * spacing) - scrollOffset;
            if (cy + cardH < listTop || cy > listTop + listHeight) continue;

            boolean hovered = (mouseX >= left && mouseX <= left + width && mouseY >= cy && mouseY <= cy + cardH);
            drawSprite(graphics, hovered ? "card_hover" : "card", left, cy, width, cardH, hovered ? 0xFF1E2838 : 0xFF121622);

            int readBtnW = 92;
            int readBtnH = 18;
            int readBtnX = left + width - readBtnW - 8;
            int readBtnY = cy + 19;
            int newsTextW = Math.max(90, readBtnX - (left + 8) - 8);

            drawClampedText(graphics, "§8[" + article.category() + "] §f" + article.title(), left + 8, cy + 5, newsTextW, article.categoryColor(), true);
            List<net.minecraft.util.FormattedCharSequence> teaserLines =
                    font.split(Component.literal("§7" + article.teaser()), newsTextW);
            for (int d = 0; d < Math.min(2, teaserLines.size()); d++) {
                graphics.text(font, teaserLines.get(d), left + 8, cy + 17 + (d * 10), 0xFFCBD5E1, false);
            }
            drawClampedText(graphics, "§8" + article.date() + " | Нажмите для открытия отчёта", left + 8, cy + 41, newsTextW, 0xFF64748B, false);

            drawSmallButton(graphics, hovered ? "btn_blue" : "btn_gray", readBtnX, readBtnY, readBtnW, readBtnH, "📖 Читать", mouseX, mouseY, () -> {
                selectedNewsArticle = article;
            });

            registerClick(left, cy, left + width, cy + cardH, () -> {
                selectedNewsArticle = article;
            }, "Открыть отчёт: " + article.title());
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, listTop, width, listHeight, maxScroll, scrollOffset);
    }

    // ==========================================
    // RENDERING HELPERS & WIDGETS
    // ==========================================

    private void drawSprite(GuiGraphicsExtractor graphics, String spriteName, int x, int y, int w, int h, int fallbackColor) {
        try {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PlayerMarketMod.id(spriteName), x, y, w, h);
        } catch (Throwable t) {
            graphics.fill(x, y, x + w, y + h, fallbackColor);
        }
    }

    private void drawCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = !isModalOpen() && (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h);
        drawSprite(graphics, hovered ? "card_hover" : "card", x, y, w, h, hovered ? 0xFF1E2838 : 0xFF121622);
    }

    private void drawButton(GuiGraphicsExtractor graphics, String baseSprite, int x, int y, int w, int h, String text,
                            int mouseX, int mouseY, Runnable onClick) {
        boolean hovered = !isModalOpen() && (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h);
        String sprite = hovered ? (baseSprite + "_hover") : baseSprite;
        drawSprite(graphics, sprite, x, y, w, h, hovered ? 0xFF2A4365 : 0xFF1A2B42);
        drawClampedCenteredText(graphics, text, x + (w / 2), y + (h - 8) / 2, w - 8, hovered ? 0xFFFFFFFF : 0xFFE2E8F0, false);
        registerClick(x, y, x + w, y + h, onClick, null);
    }

    private void drawSmallButton(GuiGraphicsExtractor graphics, String baseSprite, int x, int y, int w, int h, String text,
                                 int mouseX, int mouseY, Runnable onClick, String tooltip) {
        boolean hovered = !isModalOpen() && (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h);
        String sprite = hovered ? (baseSprite + "_hover") : baseSprite;
        drawSprite(graphics, sprite, x, y, w, h, hovered ? 0xFF2A4365 : 0xFF1A2B42);
        drawClampedCenteredText(graphics, text, x + (w / 2), y + (h - 7) / 2, w - 4, hovered ? 0xFFFFFFFF : 0xFFE2E8F0, false);
        registerClick(x, y, x + w, y + h, onClick, tooltip);
    }

    private void drawSmallButton(GuiGraphicsExtractor graphics, String baseSprite, int x, int y, int w, int h, String text,
                                 int mouseX, int mouseY, Runnable onClick) {
        drawSmallButton(graphics, baseSprite, x, y, w, h, text, mouseX, mouseY, onClick, null);
    }

    private void drawPill(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String text, boolean active,
                          int mouseX, int mouseY, Runnable onClick) {
        boolean hovered = !isModalOpen() && (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h);
        String sprite = active ? "pill_active" : (hovered ? "pill_hover" : "pill");
        drawSprite(graphics, sprite, x, y, w, h, active ? 0xFF10B981 : 0xFF1A2232);
        drawClampedCenteredText(graphics, text, x + (w / 2), y + (h - 8) / 2, w - 6, active ? 0xFF052E16 : (hovered ? 0xFFFFFFFF : 0xFF94A3B8), false);
        registerClick(x, y, x + w, y + h, onClick, null);
    }

    private void drawProgressBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, double progress, String barSprite) {
        drawSprite(graphics, "bar_bg", x, y, w, h, 0xFF0B0E14);
        int fillW = (int) Math.clamp(Math.round(w * progress), 0, w);
        if (fillW > 0) {
            drawSprite(graphics, barSprite, x, y, fillW, h, 0xFF10B981);
        }
    }

    private void drawClampedText(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color, boolean shadow) {
        if (text == null) return;
        if (font.width(text) <= maxWidth) {
            graphics.text(font, text, x, y, color, shadow);
            return;
        }
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (font.width(sb.toString() + c) + ellipsisWidth > maxWidth) break;
            sb.append(c);
        }
        sb.append(ellipsis);
        graphics.text(font, sb.toString(), x, y, color, shadow);
    }

    private void drawClampedCenteredText(GuiGraphicsExtractor graphics, String text, int centerX, int y, int maxWidth, int color, boolean shadow) {
        if (text == null) return;
        String toDraw = text;
        if (font.width(text) > maxWidth) {
            int ellipsisWidth = font.width("…");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (font.width(sb.toString() + c) + ellipsisWidth > maxWidth) break;
                sb.append(c);
            }
            sb.append("…");
            toDraw = sb.toString();
        }
        int textWidth = font.width(toDraw);
        graphics.text(font, toDraw, centerX - (textWidth / 2), y, color, shadow);
    }

    private void setScissor(GuiGraphicsExtractor graphics, int x, int y, int x2, int y2) {
        this.currentClipTop = y;
        this.currentClipBottom = y2;
        graphics.enableScissor(x, y, x2, y2);
    }

    private void clearScissor(GuiGraphicsExtractor graphics) {
        this.currentClipTop = -1;
        this.currentClipBottom = -1;
        graphics.disableScissor();
    }

    private void registerClick(int minX, int minY, int maxX, int maxY, Runnable action, String tooltip) {
        if (isModalOpen()) {
            // When modal popup is open, suppress all background clickable registrations
            return;
        }
        if (currentClipTop >= 0 && currentClipBottom >= 0) {
            // Discard clickable elements that are scrolled outside the scissor viewport
            if (minY < currentClipTop || maxY > currentClipBottom) {
                return;
            }
        }
        frameClickBuffer.add(new ClickAction(minX, minY, maxX, maxY, action, tooltip));
    }

    private void renderCustomMultilineTooltip(GuiGraphicsExtractor graphics, String tooltipText, int mouseX, int mouseY) {
        if (tooltipText == null || tooltipText.isEmpty()) return;
        String clean = tooltipText.replace("\r", "");
        List<net.minecraft.util.FormattedCharSequence> wrappedLines = new ArrayList<>();
        int maxLinePx = 220;
        for (String paragraph : clean.split("\n")) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                wrappedLines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            } else {
                wrappedLines.addAll(font.split(Component.literal(p), maxLinePx));
            }
        }
        graphics.setTooltipForNextFrame(font, wrappedLines, mouseX, mouseY);
    }

    private void renderNewsModal(GuiGraphicsExtractor graphics, NewsArticle article, int screenW, int screenH, int mouseX, int mouseY) {
        int modalW = Math.min(500, screenW - 24);
        int modalH = Math.min(270, screenH - 24);
        int mx = (screenW - modalW) / 2;
        int my = (screenH - modalH) / 2;

        // Dark dim backdrop over entire terminal
        graphics.fill(0, 0, screenW, screenH, 0xCC000000);

        // Modal Frame
        drawSprite(graphics, "panel", mx, my, modalW, modalH, 0xFF0B0F19);
        drawSprite(graphics, "header", mx, my, modalW, 30, 0xFF141A28);
        drawSprite(graphics, "footer", mx, my + modalH - 32, modalW, 32, 0xFF10141E);

        // Header
        drawClampedText(graphics, "§8[" + article.category() + "] §f" + article.title(), mx + 10, my + 10, modalW - 90, article.categoryColor(), true);
        drawClampedText(graphics, "§8" + article.date(), mx + modalW - 85, my + 10, 75, 0xFF64748B, false);

        // Content Area
        int bodyY = my + 38;
        int contentW = modalW - 24;

        // Market impact callout box (multiline wrapped, never truncated)
        List<net.minecraft.util.FormattedCharSequence> tipLines =
                font.split(Component.literal(article.impactTip()), modalW - 32);
        int calloutH = Math.max(26, 8 + tipLines.size() * 11);
        int calloutY = my + modalH - 32 - calloutH - 4;

        for (String paragraph : article.bodyParagraphs()) {
            List<net.minecraft.util.FormattedCharSequence> lines =
                    font.split(Component.literal("§7" + paragraph), contentW);
            for (var line : lines) {
                if (bodyY + 10 <= calloutY - 4) {
                    graphics.text(font, line, mx + 12, bodyY, 0xFFCBD5E1, false);
                    bodyY += 10;
                }
            }
            bodyY += 4;
        }

        drawSprite(graphics, "hud_bg", mx + 10, calloutY, modalW - 20, calloutH, 0xFF162032);
        for (int i = 0; i < tipLines.size(); i++) {
            graphics.text(font, tipLines.get(i), mx + 16, calloutY + 6 + i * 11, 0xFFFBBF24, false);
        }

        // Close Button in footer
        int closeW = 140;
        int closeH = 18;
        int closeX = mx + (modalW - closeW) / 2;
        int closeY = my + modalH - 25;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH;
        String btnSprite = closeHovered ? "btn_blue_hover" : "btn_blue";
        drawSprite(graphics, btnSprite, closeX, closeY, closeW, closeH, closeHovered ? 0xFF2A4365 : 0xFF1A2B42);
        drawClampedCenteredText(graphics, "✕ Закрыть отчёт", closeX + closeW / 2, closeY + (closeH - 7) / 2, closeW - 4, closeHovered ? 0xFFFFFFFF : 0xFFE2E8F0, false);

        modalClickActions.add(new ClickAction(closeX, closeY, closeX + closeW, closeY + closeH, () -> {
            selectedNewsArticle = null;
        }, null));
    }

    private void renderSection17Modal(GuiGraphicsExtractor graphics, int screenW, int screenH, int mouseX, int mouseY) {
        int modalW = Math.min(440, screenW - 24);
        int modalH = Math.min(242, screenH - 24);
        int mx = (screenW - modalW) / 2;
        int my = (screenH - modalH) / 2;

        graphics.fill(0, 0, screenW, screenH, 0xCC020408);
        drawSprite(graphics, "panel", mx, my, modalW, modalH, 0xFF0B0F19);
        drawSprite(graphics, "header", mx, my, modalW, 30, 0xFF200C12);
        drawSprite(graphics, "footer", mx, my + modalH - 32, modalW, 32, 0xFF10141E);

        drawClampedText(graphics, "§c§lПРИЛОЖЕНИЕ №17.4 // ПРОТОКОЛ ПОЛНОГО ОТЧУЖДЕНИЯ", mx + 12, my + 10, modalW - 24, 0xFFEF4444, true);

        int bodyY = my + 38;
        int contentW = modalW - 24;
        String[] clauses = {
                "§fПодписывая Финальный Расчёт, оператор безоговорочно соглашается со следующими последствиями:",
                "§e1. Финансовый расчёт: §7На ваш счёт начисляется +1 000 000 CR, после чего лицевой счёт закрывается.",
                "§c2. Ликвидация активов: §7Все биологические характеристики переходят в исключительную собственность Корпорации.",
                "§c3. Аннулирование доступа: §7Карта Доступа [M] превращается в заблокированный носитель, а терминал отключается навсегда.",
                "§64. Механическая фиксация: §7На правую ногу устанавливается лёгкий несъёмный ортез (скорость -12%, добыча блоков -22%, прыжок полностью сохранён).",
                "§45. Разрыв сессии: §7После истечения 10-секундной процедуры происходит принудительное отключение от сервера без возможности отмены."
        };

        for (String p : clauses) {
            for (var line : font.split(Component.literal(p), contentW)) {
                if (bodyY + 10 <= my + modalH - 38) {
                    graphics.text(font, line, mx + 12, bodyY, 0xFFE2E8F0, false);
                    bodyY += 10;
                }
            }
            bodyY += 4;
        }

        int closeW = 140;
        int closeH = 18;
        int closeX = mx + (modalW - closeW) / 2;
        int closeY = my + modalH - 25;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH;
        drawSprite(graphics, closeHovered ? "btn_red_hover" : "btn_red", closeX, closeY, closeW, closeH, closeHovered ? 0xFF7F1D1D : 0xFF450A0A);
        drawClampedCenteredText(graphics, "Понятно // Закрыть", closeX + closeW / 2, closeY + (closeH - 7) / 2, closeW - 6, 0xFFFFFFFF, false);

        modalClickActions.add(new ClickAction(closeX, closeY, closeX + closeW, closeY + closeH, () -> {
            section17ModalOpen = false;
        }, null));
    }

    private void renderHowToPlayModal(GuiGraphicsExtractor graphics, int screenW, int screenH, int mouseX, int mouseY) {
        int modalW = Math.min(460, screenW - 24);
        int modalH = Math.min(266, screenH - 24);
        int mx = (screenW - modalW) / 2;
        int my = (screenH - modalH) / 2;

        graphics.fill(0, 0, screenW, screenH, 0xCC020408);
        drawSprite(graphics, "panel", mx, my, modalW, modalH, 0xFF0B0F19);
        drawSprite(graphics, "header", mx, my, modalW, 30, 0xFF102033);
        drawSprite(graphics, "footer", mx, my + modalH - 32, modalW, 32, 0xFF10141E);

        drawClampedText(graphics, "§b§lПАМЯТКА ИГРОКА // КАК ИГРАТЬ В LEAFXUP EDITION", mx + 12, my + 10, modalW - 24, 0xFF38BDF8, true);

        int bodyY = my + 38;
        int maxBodyBottom = my + modalH - 36;
        int contentW = modalW - 24;
        String[] tips = {
                "§e1. Терминал [M] и старт: §7Подпиши договор и открывай биржу на клавишу §f[M]§7. Главный заработок — продажа статов, лизинг, контракты и квесты Программы.",
                "§c2. Налог за жадность (0 статов): §7Если не продавать характеристики и копить §f600+ CR §7или §b4+ ♦§7, инспекция спишет §c18% налог§7! Сдай стат в §eЛизинг (1–5 дн.)§7 — он приносит CR/мин и сам вернётся.",
                "§b3. 3D-ивенты Хаоса и маяки: §7Каждые ~20–30 сек падают §aЯщик снабжения§7, §cСейф-Взлом§7, §6Банкомат (бей 6 раз!) §7и §dКапсула§7 — они стоят прямо перед тобой со световым лучом!",
                "§64. Верити-Колобок и 3D-Бомба: §7Жёлтый §eВерити-Колобок §7катится по земле — поймай его ПКМ или ударом! Если включилась §cГорячая Картошка§7, скинь бомбу §fПКМ по любому мобу/игроку§7 до взрыва!",
                "§a5. Крупные штрафы и спасение: §7Провал контракта (-15% CR), смерть с лизингом (-10%), Дрон (-8%) и Коллекторы опасны. Покупай §dЧипы Чёрного Рынка §7и крути §eРулетку§7!"
        };

        for (String tip : tips) {
            for (var line : font.split(Component.literal(tip), contentW)) {
                if (bodyY + 9 <= maxBodyBottom) {
                    graphics.text(font, line, mx + 12, bodyY, 0xFFE2E8F0, false);
                    bodyY += 10;
                }
            }
            bodyY += 4;
        }

        int closeW = 148;
        int closeH = 18;
        int closeX = mx + (modalW - closeW) / 2;
        int closeY = my + modalH - 25;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH;
        drawSprite(graphics, closeHovered ? "btn_blue_hover" : "btn_blue", closeX, closeY, closeW, closeH, closeHovered ? 0xFF2563EB : 0xFF1D4ED8);
        drawClampedCenteredText(graphics, "Понятно! К делу", closeX + closeW / 2, closeY + (closeH - 7) / 2, closeW - 6, 0xFFFFFFFF, false);

        modalClickActions.add(new ClickAction(closeX, closeY, closeX + closeW, closeY + closeH, () -> {
            howToPlayModalOpen = false;
        }, null));
    }

    private void sendMarketAction(String action, String targetId, String secondaryId, int intValue, double doubleValue) {
        ClientPlayNetworking.send(new MarketNetworking.MarketActionPayload(action, targetId, secondaryId, intValue, doubleValue));
    }

    private void playClickSound() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void drawItemSlot(GuiGraphicsExtractor graphics, int x, int y, ItemStack stack) {
        drawSprite(graphics, "slot_dark", x, y, 18, 18, 0xFF0A0F18);
        if (stack != null && !stack.isEmpty()) {
            graphics.item(stack, x + 1, y + 1);
        }
    }

    private void renderTelemetryDeck(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event,
                                     int left, int top, int width, int height, int mouseX, int mouseY) {
        if (height < 42) {
            drawSprite(graphics, "card", left, top, width, height, 0xFF101622);
            drawClampedText(graphics, "§aАРХЕТИП: §f" + determineArchetype(account), left + 8, top + 6, width - 16, 0xFF10B981, false);
            return;
        }

        int halfGap = 6;
        int leftW = (width - halfGap) / 2;
        int rightX = left + leftW + halfGap;
        int rightW = width - leftW - halfGap;

        // === LEFT MODULE: HYBRID 3D LIVE PLAYER / 2D HOLO-BODY SCANNER ===
        drawSprite(graphics, "card", left, top, leftW, height, 0xFF101622);

        AssetType[] headAssets = {AssetType.OXYGEN_CAPACITY, AssetType.SATURATION_EFFICIENCY, AssetType.HUNGER_EFFICIENCY, AssetType.XP_GAIN, AssetType.LUCK};
        AssetType[] torsoAssets = {AssetType.MAX_HEALTH, AssetType.NATURAL_REGENERATION, AssetType.ARMOR, AssetType.ARMOR_TOUGHNESS, AssetType.FIRE_RESISTANCE, AssetType.MAX_ABSORPTION, AssetType.POTION_EFFECTIVENESS};
        AssetType[] armAssets = {AssetType.ATTACK_DAMAGE, AssetType.ATTACK_SPEED, AssetType.MINING_SPEED, AssetType.UNDERWATER_MINING, AssetType.BLOCK_REACH, AssetType.ENTITY_REACH, AssetType.KNOCKBACK_POWER, AssetType.CRITICAL_DAMAGE};
        AssetType[] legAssets = {AssetType.MOVEMENT_SPEED, AssetType.JUMP_STRENGTH, AssetType.STEP_HEIGHT, AssetType.SPRINT_EFFICIENCY, AssetType.SWIM_SPEED, AssetType.FALL_RESISTANCE, AssetType.KNOCKBACK_RESISTANCE};

        double headSold = avgSoldFraction(account, headAssets);
        double torsoSold = avgSoldFraction(account, torsoAssets);
        double armsSold = avgSoldFraction(account, armAssets);
        double legsSold = avgSoldFraction(account, legAssets);
        int bioIntegrity = (int) Math.max(0, Math.min(100, Math.round((1.0 - (headSold + torsoSold + armsSold + legsSold) / 4.0) * 100.0)));

        int bayW = Math.min(68, Math.max(48, (int) (leftW * 0.34)));
        int bayH = Math.max(52, height - 14);
        int bayX = left + 5;
        int bayY = top + (height - bayH) / 2;
        boolean braced = account.isPermanentImpaired() || account.getStoryStage() >= 5 || legsSold >= 0.40;

        int infoX = bayX + bayW + 8;
        int infoW = Math.max(40, left + leftW - infoX - 6);
        int zoneListTop = top + 36;
        int rowStep = height >= 96 ? 11 : 9;

        // Precompute hovered zone from BOTH body hitboxes AND zone rows so 3D player highlights on the exact same frame
        int midX = bayX + bayW / 2;
        boolean use3D = bioViewMode3D && minecraft != null && minecraft.player != null;
        int[] headBox;
        int[] torsoBox;
        int[] armLeftBox;
        int[] armRightBox;
        int[] legBox;
        if (use3D) {
            int halfTorso = 7;
            int zH0 = bayY + Math.round(bayH * 0.08f);
            int zH1 = bayY + Math.round(bayH * 0.30f);
            int zT1 = bayY + Math.round(bayH * 0.58f);
            int zA1 = bayY + Math.round(bayH * 0.62f);
            int zL1 = bayY + bayH - 3;
            headBox = new int[]{midX - 10, zH0, midX + 10, zH1};
            torsoBox = new int[]{midX - halfTorso, zH1 + 1, midX + halfTorso, zT1};
            armLeftBox = new int[]{bayX + 2, zH1 + 1, midX - halfTorso - 1, zA1};
            armRightBox = new int[]{midX + halfTorso + 1, zH1 + 1, bayX + bayW - 2, zA1};
            legBox = new int[]{midX - 11, zT1 + 1, midX + 11, zL1};
        } else {
            int drawH = Math.max(48, Math.min(bayH - 4, 96));
            int drawW = Math.round(drawH * (64.0f / 96.0f));
            if (drawW > bayW - 4) {
                drawW = bayW - 4;
                drawH = Math.round(drawW * (96.0f / 64.0f));
            }
            int hx = bayX + (bayW - drawW) / 2;
            int hy = bayY + (bayH - drawH) / 2;
            int splitA1 = hx + Math.round(drawW * 0.31f);
            int splitA2 = hx + Math.round(drawW * 0.69f);
            int yHeadEnd = hy + Math.round(drawH * 0.25f);
            int yTorsoEnd = hy + Math.round(drawH * 0.60f);
            int yArmEnd = hy + Math.round(drawH * 0.63f);
            headBox = new int[]{hx + Math.round(drawW * 0.26f), hy, hx + Math.round(drawW * 0.74f), yHeadEnd};
            torsoBox = new int[]{splitA1, yHeadEnd + 1, splitA2, yTorsoEnd};
            armLeftBox = new int[]{hx, yHeadEnd + 1, splitA1 - 1, yArmEnd};
            armRightBox = new int[]{splitA2 + 1, yHeadEnd + 1, hx + drawW, yArmEnd};
            legBox = new int[]{hx + Math.round(drawW * 0.22f), yTorsoEnd + 1, hx + Math.round(drawW * 0.78f), hy + drawH};
        }

        if (!isModalOpen()) {
            if (inBox(mouseX, mouseY, headBox) || inRect(mouseX, mouseY, infoX - 2, zoneListTop - 1, infoW + 2, 9)) {
                hoveredBioZone = "HEAD";
            } else if (inBox(mouseX, mouseY, torsoBox) || inRect(mouseX, mouseY, infoX - 2, zoneListTop + rowStep - 1, infoW + 2, 9)) {
                hoveredBioZone = "TORSO";
            } else if (inBox(mouseX, mouseY, armLeftBox) || inBox(mouseX, mouseY, armRightBox)
                    || inRect(mouseX, mouseY, infoX - 2, zoneListTop + 2 * rowStep - 1, infoW + 2, 9)) {
                hoveredBioZone = "ARMS";
            } else if (inBox(mouseX, mouseY, legBox) || inRect(mouseX, mouseY, infoX - 2, zoneListTop + 3 * rowStep - 1, infoW + 2, 9)) {
                hoveredBioZone = "LEGS";
            }
        }

        drawBioBodyDiagram(graphics, account, bayX, bayY, bayW, bayH,
                headSold, torsoSold, armsSold, legsSold, braced,
                headAssets, torsoAssets, armAssets, legAssets,
                headBox, torsoBox, armLeftBox, armRightBox, legBox,
                mouseX, mouseY);

        int toggleBtnW = 24;
        int toggleBtnH = 12;
        drawSmallButton(graphics, "btn_blue", infoX + infoW - toggleBtnW, top + 4, toggleBtnW, toggleBtnH,
                bioViewMode3D ? "3D" : "2D", mouseX, mouseY,
                () -> bioViewMode3D = !bioViewMode3D,
                bioViewMode3D ? "Переключить на детализированную 2D-голограмму тела" : "Переключить на живую 3D-модель игрока");

        String intColor = bioIntegrity >= 80 ? "§a" : (bioIntegrity >= 50 ? "§e" : "§c");
        drawClampedText(graphics, "§b§lБИО-МОНИТОР", infoX, top + 6, Math.max(30, infoW - toggleBtnW - 4), 0xFF38BDF8, false);
        drawClampedText(graphics, "§7НОРМА: " + intColor + "§l" + bioIntegrity + "%", infoX, top + 17, infoW, 0xFFE2E8F0, false);

        String barSprite = bioIntegrity >= 80 ? "bar_green" : (bioIntegrity >= 50 ? "bar_amber" : "bar_red");
        drawProgressBar(graphics, infoX, top + 28, Math.min(infoW, 145), 4, bioIntegrity / 100.0, barSprite);

        // 4 Interactive Tarkov-style body zone health rows connected to scanner bay
        drawZoneRow(graphics, bayX + bayW - 1, (headBox[1] + headBox[3]) / 2, infoX, zoneListTop + 0 * rowStep, infoW,
                "ГОЛОВА", "ГОЛОВА // РАЗУМ И ВОСПРИЯТИЕ", headSold, account, AssetType.Category.PROFESSIONAL, headAssets, mouseX, mouseY);
        drawZoneRow(graphics, bayX + bayW - 1, (torsoBox[1] + torsoBox[3]) / 2, infoX, zoneListTop + 1 * rowStep, infoW,
                "ГРУДЬ", "ГРУДЬ // СЕРДЦЕ И ОРГАНЫ", torsoSold, account, AssetType.Category.BODY, torsoAssets, mouseX, mouseY);
        drawZoneRow(graphics, bayX + bayW - 1, (armRightBox[1] + armRightBox[3]) / 2, infoX, zoneListTop + 2 * rowStep, infoW,
                "РУКИ", "РУКИ // БОЙ И ИНСТРУМЕНТ", armsSold, account, AssetType.Category.COMBAT, armAssets, mouseX, mouseY);
        drawZoneRow(graphics, bayX + bayW - 1, (legBox[1] + legBox[3]) / 2, infoX, zoneListTop + 3 * rowStep, infoW,
                "НОГИ", "НОГИ // МОБИЛЬНОСТЬ И ШАГ", legsSold, account, AssetType.Category.MOVEMENT, legAssets, mouseX, mouseY);

        int footerStart = zoneListTop + 4 * rowStep;
        if ((top + height) - footerStart >= 16) {
            int curStage = account.getStoryStage();
            String stageTitle = (curStage >= 1 && curStage <= com.playermarket.story.StoryQuests.STAGES.size())
                    ? com.playermarket.story.StoryQuests.STAGES.get(curStage - 1).name()
                    : ("ЭТАП " + curStage);
            drawClampedText(graphics, "§eЭт." + curStage + ": §7" + stageTitle,
                    infoX, top + height - 13, infoW, 0xFF94A3B8, false);
        }

        // === RIGHT MODULE: ANIMATED LIVE MARKET PULSE SPARKLINE ===
        drawSprite(graphics, "card", rightX, top, rightW, height, 0xFF101622);
        double marketIdx = (event.getBodyMultiplier() + event.getMovementMultiplier()
                + event.getResourcePriceMultiplier() + event.getContractRewardMultiplier()) / 4.0;
        String trendTag = marketIdx >= 1.06 ? "§a▲ РОСТ " : (marketIdx <= 0.94 ? "§c▼ СПАД " : "§b● СТАБ ");
        drawClampedText(graphics, "§a§lПУЛЬС БИРЖИ", rightX + 8, top + 6, rightW - 72, 0xFF10B981, false);
        String idxLabel = String.format(Locale.ROOT, "%s%.0f%%", trendTag, marketIdx * 100.0);
        int idxW = font.width(idxLabel);
        graphics.text(font, idxLabel, rightX + rightW - idxW - 8, top + 6, 0xFFFFFFFF, false);

        int plotX = rightX + 7;
        int plotY = top + 18;
        int plotW = Math.max(40, rightW - 14);
        int plotH = Math.max(22, height - 34);
        drawMarketSparkline(graphics, event, marketIdx, plotX, plotY, plotW, plotH);

        String footerChips = String.format(Locale.ROOT,
                "§8Т:§f%.2f §8| Д:§f%.2f §8| С:§f%.2f §8| К:§f%.2f",
                event.getBodyMultiplier(), event.getMovementMultiplier(),
                event.getResourcePriceMultiplier(), event.getContractRewardMultiplier());
        drawClampedText(graphics, footerChips, rightX + 8, top + height - 12, rightW - 16, 0xFF94A3B8, false);
    }

    private void drawBioBodyDiagram(GuiGraphicsExtractor graphics, PlayerMarketAccount account,
                                    int bx, int by, int bw, int bh,
                                    double headSold, double torsoSold, double armsSold, double legsSold,
                                    boolean showLegBrace,
                                    AssetType[] headAssets, AssetType[] torsoAssets,
                                    AssetType[] armAssets, AssetType[] legAssets,
                                    int[] headBox, int[] torsoBox, int[] armLeftBox, int[] armRightBox, int[] legBox,
                                    int mouseX, int mouseY) {
        graphics.fill(bx, by, bx + bw, by + bh, 0xFF050911);

        // Subtle diagnostic grid lines inside bay
        for (int gy = by + 10; gy < by + bh - 4; gy += 12) {
            graphics.fill(bx + 2, gy, bx + bw - 2, gy + 1, 0xFF0D1726);
        }
        int midX = bx + bw / 2;
        graphics.fill(midX, by + 3, midX + 1, by + bh - 3, 0xFF0D1726);

        // Corner medical scanner brackets
        int brkCol = 0xFF22D3EE;
        graphics.fill(bx, by, bx + 6, by + 1, brkCol);
        graphics.fill(bx, by, bx + 1, by + 6, brkCol);
        graphics.fill(bx + bw - 6, by, bx + bw, by + 1, brkCol);
        graphics.fill(bx + bw - 1, by, bx + bw, by + 6, brkCol);
        graphics.fill(bx, by + bh - 1, bx + 6, by + bh, brkCol);
        graphics.fill(bx, by + bh - 6, bx + 1, by + bh, brkCol);
        graphics.fill(bx + bw - 6, by + bh - 1, bx + bw, by + bh, brkCol);
        graphics.fill(bx + bw - 1, by + bh - 6, bx + bw, by + bh, brkCol);

        int headCol = zoneColor(headSold);
        int torsoCol = zoneColor(torsoSold);
        int armsCol = zoneColor(armsSold);
        int legsCol = zoneColor(legsSold);

        // Soft pulsing hazard highlight inside each tight anatomical hitbox (renders in 2D mode only;
        // 3D mode renders glowing overlays directly onto the player's 3D skin via PlayerBraceRenderLayer)
        boolean render3DModel = bioViewMode3D && minecraft != null && minecraft.player != null;
        if (!render3DModel) {
            int pulseAlpha = (int) (0x1E + Math.abs(Math.sin(System.currentTimeMillis() / 320.0)) * 0x20);
            if (headSold > 0.01 || "HEAD".equals(hoveredBioZone)) {
                int c = "HEAD".equals(hoveredBioZone) ? 0x3838BDF8 : ((pulseAlpha << 24) | (headCol & 0x00FFFFFF));
                graphics.fill(headBox[0], headBox[1], headBox[2], headBox[3], c);
            }
            if (torsoSold > 0.01 || "TORSO".equals(hoveredBioZone)) {
                int c = "TORSO".equals(hoveredBioZone) ? 0x3838BDF8 : ((pulseAlpha << 24) | (torsoCol & 0x00FFFFFF));
                graphics.fill(torsoBox[0], torsoBox[1], torsoBox[2], torsoBox[3], c);
            }
            if (armsSold > 0.01 || "ARMS".equals(hoveredBioZone)) {
                int c = "ARMS".equals(hoveredBioZone) ? 0x3838BDF8 : ((pulseAlpha << 24) | (armsCol & 0x00FFFFFF));
                graphics.fill(armLeftBox[0], armLeftBox[1], armLeftBox[2], armLeftBox[3], c);
                graphics.fill(armRightBox[0], armRightBox[1], armRightBox[2], armRightBox[3], c);
            }
            if (legsSold > 0.01 || "LEGS".equals(hoveredBioZone)) {
                int c = "LEGS".equals(hoveredBioZone) ? 0x3838BDF8 : ((pulseAlpha << 24) | (legsCol & 0x00FFFFFF));
                graphics.fill(legBox[0], legBox[1], legBox[2], legBox[3], c);
            }
        }

        if (render3DModel) {
            // Render real animated 3D player skin following cursor (+ glowing 3D bio-overlay & leg brace via PlayerBraceRenderLayer!)
            int entityScale = Math.max(18, Math.min(40, (int) (bh * 0.42)));
            try {
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                        graphics, bx + 2, by + 2, bx + bw - 2, by + bh - 2,
                        entityScale, 0.0625f, (float) mouseX, (float) mouseY, minecraft.player);
            } catch (Throwable ignored) {
                render2DHoloBody(graphics, bx, by, bw, bh, headCol, torsoCol, armsCol, legsCol, torsoSold, showLegBrace);
            }
        } else {
            render2DHoloBody(graphics, bx, by, bw, bh, headCol, torsoCol, armsCol, legsCol, torsoSold, showLegBrace);
        }

        // Left-side anatomical slice markers matching exact boxes
        graphics.fill(bx + 2, (headBox[1] + headBox[3]) / 2, bx + 4, (headBox[1] + headBox[3]) / 2 + 2, headCol);
        graphics.fill(bx + 2, (torsoBox[1] + torsoBox[3]) / 2, bx + 4, (torsoBox[1] + torsoBox[3]) / 2 + 2, torsoCol);
        graphics.fill(bx + 2, (armLeftBox[1] + armLeftBox[3]) / 2, bx + 4, (armLeftBox[1] + armLeftBox[3]) / 2 + 2, armsCol);
        graphics.fill(bx + 2, (legBox[1] + legBox[3]) / 2, bx + 4, (legBox[1] + legBox[3]) / 2 + 2, legsCol);

        // Register tight clickable/hoverable hitboxes directly on Head / Chest / Left+Right Arms / Legs
        registerClick(headBox[0], headBox[1], headBox[2], headBox[3], () -> {
            assetFilter = AssetType.Category.PROFESSIONAL;
            activeTab = Tab.SELL_ASSETS;
            scrollOffset = 0;
        }, buildZoneTooltip("ГОЛОВА // РАЗУМ И ВОСПРИЯТИЕ", headSold, account, headAssets));

        registerClick(torsoBox[0], torsoBox[1], torsoBox[2], torsoBox[3], () -> {
            assetFilter = AssetType.Category.BODY;
            activeTab = Tab.SELL_ASSETS;
            scrollOffset = 0;
        }, buildZoneTooltip("ГРУДЬ // СЕРДЦЕ И ОРГАНЫ", torsoSold, account, torsoAssets));

        registerClick(armLeftBox[0], armLeftBox[1], armLeftBox[2], armLeftBox[3], () -> {
            assetFilter = AssetType.Category.COMBAT;
            activeTab = Tab.SELL_ASSETS;
            scrollOffset = 0;
        }, buildZoneTooltip("РУКИ // БОЙ И ИНСТРУМЕНТ", armsSold, account, armAssets));

        registerClick(armRightBox[0], armRightBox[1], armRightBox[2], armRightBox[3], () -> {
            assetFilter = AssetType.Category.COMBAT;
            activeTab = Tab.SELL_ASSETS;
            scrollOffset = 0;
        }, buildZoneTooltip("РУКИ // БОЙ И ИНСТРУМЕНТ", armsSold, account, armAssets));

        registerClick(legBox[0], legBox[1], legBox[2], legBox[3], () -> {
            assetFilter = AssetType.Category.MOVEMENT;
            activeTab = Tab.SELL_ASSETS;
            scrollOffset = 0;
        }, buildZoneTooltip("НОГИ // МОБИЛЬНОСТЬ И ШАГ", legsSold, account, legAssets));

        // Sweeping laser scanline
        int scanOffset = (int) ((System.currentTimeMillis() / 55L) % Math.max(1, bh - 4));
        graphics.fill(bx + 2, by + 2 + scanOffset, bx + bw - 2, by + 3 + scanOffset, 0x5522D3EE);
    }

    private static boolean inBox(int mx, int my, int[] b) {
        return mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3];
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void render2DHoloBody(GuiGraphicsExtractor graphics, int bx, int by, int bw, int bh,
                                  int headCol, int torsoCol, int armsCol, int legsCol,
                                  double torsoSold, boolean showLegBrace) {
        int drawH = Math.max(48, Math.min(bh - 4, 96));
        int drawW = Math.round(drawH * (64.0f / 96.0f));
        if (drawW > bw - 4) {
            drawW = bw - 4;
            drawH = Math.round(drawW * (96.0f / 64.0f));
        }
        int hx = bx + (bw - drawW) / 2;
        int hy = by + (bh - drawH) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, BODY_ARMS, hx, hy, 0.0f, 0.0f, drawW, drawH, 64, 96, 64, 96, armsCol);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BODY_LEGS, hx, hy, 0.0f, 0.0f, drawW, drawH, 64, 96, 64, 96, legsCol);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BODY_TORSO, hx, hy, 0.0f, 0.0f, drawW, drawH, 64, 96, 64, 96, torsoCol);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BODY_HEAD, hx, hy, 0.0f, 0.0f, drawW, drawH, 64, 96, 64, 96, headCol);

        long beatPeriod = torsoSold > 0.25 ? 260L : 520L;
        boolean heartFlash = ((System.currentTimeMillis() / beatPeriod) % 2L) == 0L;
        int heartTint = torsoSold > 0.01
                ? (heartFlash ? 0xFFFF3333 : 0xFFCC1111)
                : (heartFlash ? 0xFF34D399 : 0xFF10B981);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BODY_HEART, hx, hy, 0.0f, 0.0f, drawW, drawH, 64, 96, 64, 96, heartTint);

        if (showLegBrace) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, BODY_BRACE, hx, hy, 0.0f, 0.0f, drawW, drawH, 64, 96, 64, 96, 0xFFE2E8F0);
        }
    }

    private void drawZoneRow(GuiGraphicsExtractor graphics, int bayEdgeX, int bayAnchorY,
                             int rx, int ry, int rw, String label, String tooltipTitle, double soldFrac,
                             PlayerMarketAccount account, AssetType.Category jumpCat,
                             AssetType[] zoneAssets, int mouseX, int mouseY) {
        int pct = (int) Math.max(0, Math.min(100, Math.round((1.0 - soldFrac) * 100.0)));
        int col = zoneColor(soldFrac);
        boolean hovered = !isModalOpen() && (mouseX >= rx - 2 && mouseX <= rx + rw && mouseY >= ry - 1 && mouseY <= ry + 9);

        // Tactical thin connector dot + bridge line from scanner bay into the row
        graphics.fill(bayEdgeX, bayAnchorY, bayEdgeX + 3, bayAnchorY + 1, col);
        graphics.fill(rx - 4, ry + 4, rx - 1, ry + 5, col);

        if (hovered) {
            graphics.fill(rx - 1, ry - 1, rx + rw, ry + 9, 0x3338BDF8);
        }

        int pctW = font.width(pct + "%");
        graphics.text(font, label, rx, ry + 1, hovered ? 0xFFFFFFFF : 0xFFCBD5E1, false);
        graphics.text(font, pct + "%", rx + rw - pctW, ry + 1, col, false);

        int barX = rx + 42;
        int barW = rw - 42 - pctW - 4;
        if (barW >= 18) {
            String spr = soldFrac < 0.01 ? "bar_green" : (soldFrac < 0.45 ? "bar_amber" : "bar_red");
            drawProgressBar(graphics, barX, ry + 2, barW, 4, Math.max(0.0, 1.0 - soldFrac), spr);
        }

        registerClick(rx - 1, ry - 1, rx + rw, ry + 9, () -> {
            assetFilter = jumpCat;
            activeTab = Tab.SELL_ASSETS;
            scrollOffset = 0;
        }, buildZoneTooltip(tooltipTitle, soldFrac, account, zoneAssets));
    }

    private static String buildZoneTooltip(String title, double soldFrac, PlayerMarketAccount account, AssetType... assets) {
        int intact = (int) Math.max(0, Math.min(100, Math.round((1.0 - soldFrac) * 100.0)));
        StringBuilder sb = new StringBuilder();
        sb.append(intact >= 99 ? "§a§l" : (intact >= 55 ? "§e§l" : "§c§l"))
                .append(title).append(" — ").append(intact).append("% нормы\n");
        int shown = 0;
        for (AssetType a : assets) {
            double total = account.getTotalSoldFraction(a);
            if (total > 0.001) {
                sb.append("§c• ").append(a.getShortName()).append(": -").append(Math.round(total * 100)).append("%\n");
                shown++;
            }
        }
        if (shown == 0) {
            sb.append("§7Потерь нет. Биологические параметры стабильны.\n");
        }
        sb.append("§8Нажмите для перехода к характеристикам этой зоны");
        return sb.toString().trim();
    }

    private void drawMarketSparkline(GuiGraphicsExtractor graphics, MarketEvent event, double marketIdx,
                                     int px, int py, int pw, int ph) {
        graphics.fill(px, py, px + pw, py + ph, 0xFF060A12);
        graphics.fill(px, py, px + pw, py + 1, 0xFF1E293B);
        graphics.fill(px, py + ph - 1, px + pw, py + ph, 0xFF1E293B);
        graphics.fill(px, py, px + 1, py + ph, 0xFF1E293B);
        graphics.fill(px + pw - 1, py, px + pw, py + ph, 0xFF1E293B);

        // Reference grid lines (baseline 1.0x near center)
        int midY = py + ph / 2;
        graphics.fill(px + 1, midY, px + pw - 1, midY + 1, 0xFF162032);
        for (int g = 1; g <= 3; g++) {
            int gx = px + (pw * g) / 4;
            graphics.fill(gx, py + 1, gx + 1, py + ph - 1, 0xFF0F172A);
        }

        int lineCol = marketIdx >= 1.08 ? 0xFF10B981
                : (marketIdx <= 0.92 ? 0xFFEF4444
                : (Math.abs(event.getBodyMultiplier() - event.getMovementMultiplier()) > 0.25 ? 0xFFF59E0B : 0xFF38BDF8));
        int fillCol = (0x38 << 24) | (lineCol & 0x00FFFFFF);

        int samples = 22;
        double phase = (System.currentTimeMillis() / 240.0);
        int innerW = Math.max(10, pw - 4);
        int innerH = Math.max(10, ph - 6);
        int prevX = -1;
        int prevY = -1;

        for (int s = 0; s < samples; s++) {
            double normX = (double) s / (samples - 1);
            int sx = px + 2 + (int) Math.round(normX * innerW);
            double wave = Math.sin(phase + s * 0.55 + event.ordinal() * 1.1) * 0.14
                    + Math.cos(phase * 0.6 + s * 1.15) * 0.08;
            double targetNorm = Math.max(0.12, Math.min(0.92, ((marketIdx - 0.65) / 0.75) + wave));
            int sy = py + ph - 3 - (int) Math.round(targetNorm * innerH);

            // Soft shaded bar column below curve
            if (sy < py + ph - 2) {
                graphics.fill(sx, sy, Math.min(px + pw - 2, sx + Math.max(1, innerW / samples)), py + ph - 2, fillCol);
            }
            if (prevX >= 0) {
                int yMin = Math.min(prevY, sy);
                int yMax = Math.max(prevY, sy);
                graphics.fill(sx, yMin, sx + 1, yMax + 1, lineCol);
                graphics.fill(prevX, prevY, sx + 1, prevY + 1, lineCol);
            }
            if (s == samples - 1) {
                graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFFFFFFFF);
            }
            prevX = sx;
            prevY = sy;
        }
    }

    private static double avgSoldFraction(PlayerMarketAccount account, AssetType... types) {
        if (types.length == 0) return 0.0;
        double sum = 0.0;
        double max = 0.0;
        for (AssetType t : types) {
            double v = account.getTotalSoldFraction(t);
            sum += v;
            if (v > max) max = v;
        }
        double avg = sum / types.length;
        // Blend peak stat loss with zone average so even a single sold stat lights up the zone clearly
        return Math.min(1.0, max * 0.55 + avg * 0.45);
    }

    private static int zoneColor(double soldFrac) {
        if (soldFrac >= 0.35) return 0xFFEF4444;
        if (soldFrac >= 0.14) return 0xFFF59E0B;
        if (soldFrac > 0.005) return 0xFFFACC15;
        return 0xFF10B981;
    }

    private static ItemStack getAssetIconStack(AssetType asset) {
        return new ItemStack(switch (asset) {
            case MAX_HEALTH -> Items.GOLDEN_APPLE;
            case NATURAL_REGENERATION -> Items.GLISTERING_MELON_SLICE;
            case SATURATION_EFFICIENCY -> Items.COOKED_BEEF;
            case HUNGER_EFFICIENCY -> Items.BREAD;
            case OXYGEN_CAPACITY -> Items.TURTLE_HELMET;
            case FIRE_RESISTANCE -> Items.MAGMA_CREAM;
            case FALL_RESISTANCE -> Items.FEATHER;
            case MAX_ABSORPTION -> Items.GOLDEN_CHESTPLATE;
            case MOVEMENT_SPEED -> Items.SUGAR;
            case JUMP_STRENGTH -> Items.RABBIT_FOOT;
            case SPRINT_EFFICIENCY -> Items.LEATHER_BOOTS;
            case SWIM_SPEED -> Items.COD;
            case STEP_HEIGHT -> Items.LADDER;
            case ATTACK_DAMAGE -> Items.DIAMOND_SWORD;
            case ATTACK_SPEED -> Items.IRON_SWORD;
            case ARMOR -> Items.IRON_CHESTPLATE;
            case ARMOR_TOUGHNESS -> Items.NETHERITE_CHESTPLATE;
            case KNOCKBACK_RESISTANCE -> Items.SHIELD;
            case ENTITY_REACH -> Items.TRIDENT;
            case PROJECTILE_RESISTANCE -> Items.ARROW;
            case EXPLOSION_RESISTANCE -> Items.TNT;
            case CRITICAL_DAMAGE -> Items.NETHERITE_AXE;
            case KNOCKBACK_POWER -> Items.PISTON;
            case MINING_SPEED -> Items.DIAMOND_PICKAXE;
            case UNDERWATER_MINING -> Items.PRISMARINE_CRYSTALS;
            case LUCK -> Items.EMERALD;
            case XP_GAIN -> Items.EXPERIENCE_BOTTLE;
            case BLOCK_REACH -> Items.BRICKS;
            case POTION_EFFECTIVENESS -> Items.BREWING_STAND;
            case PROJECTILE_ACCURACY -> Items.SPECTRAL_ARROW;
        });
    }

    private static ItemStack getUpgradeIconStack(UpgradeType upg) {
        return new ItemStack(switch (upg) {
            case REINFORCED_HEART -> Items.GOLDEN_APPLE;
            case RAPID_RECOVERY -> Items.GLISTERING_MELON_SLICE;
            case ADRENALINE_SYSTEM -> Items.REDSTONE;
            case ENHANCED_LUNGS -> Items.TURTLE_HELMET;
            case STRONG_LEGS -> Items.RABBIT_FOOT;
            case METABOLISM_UPGRADE -> Items.GOLDEN_CARROT;
            case REINFORCED_BONES -> Items.BONE;
            case THERMAL_ADAPTATION -> Items.MAGMA_CREAM;
            case MUSCLE_OVERCLOCK -> Items.BLAZE_POWDER;
            case FAST_REFLEXES -> Items.FEATHER;
            case REINFORCED_SKIN -> Items.IRON_CHESTPLATE;
            case IMPACT_DAMPENER -> Items.SLIME_BALL;
            case PRECISION_TARGETING -> Items.SPYGLASS;
            case PROJECTILE_CALIBRATION -> Items.BOW;
            case COMBAT_FOCUS -> Items.DIAMOND_SWORD;
            case MINING_PROTOCOL -> Items.DIAMOND_PICKAXE;
            case LUCKY_CHIP -> Items.EMERALD;
            case XP_ACCELERATOR -> Items.EXPERIENCE_BOTTLE;
            case ORE_SCANNER -> Items.COMPASS;
            case EFFICIENT_METABOLISM -> Items.APPLE;
            case AQUATIC_PROPULSION -> Items.HEART_OF_THE_SEA;
            case PRECISION_HANDS -> Items.SHEARS;
            case PREMIUM_ACCOUNT -> Items.GOLD_INGOT;
            case BROKER_LICENSE -> Items.PAPER;
            case MARKET_INSURANCE -> Items.SHIELD;
            case VIP_CLIENT -> Items.NETHER_STAR;
            case PRICE_LOCK -> Items.AMETHYST_SHARD;
            case DIAMOND_CASHBACK -> Items.DIAMOND;
        });
    }

    private static ItemStack getBlackMarketIconStack(BlackMarketItem bm) {
        return new ItemStack(switch (bm) {
            case SECOND_CHANCE -> Items.TOTEM_OF_UNDYING;
            case BERSERKER_PROTOCOL -> Items.NETHERITE_SWORD;
            case PHANTOM_STEP -> Items.ENDER_PEARL;
            case SYNTHETIC_HEART -> Items.ENCHANTED_GOLDEN_APPLE;
            case OVERCLOCKED_MIND -> Items.CLOCK;
            case EMERGENCY_BATTERY -> Items.REDSTONE_BLOCK;
            case GOLDEN_CONTRACT -> Items.GOLD_BLOCK;
            case ASSET_INSURANCE -> Items.SHIELD;
        });
    }

    private String determineArchetype(PlayerMarketAccount account) {
        int bodySold = (account.getTotalSoldFraction(AssetType.MAX_HEALTH) > 0.3 ? 1 : 0)
                + (account.getTotalSoldFraction(AssetType.NATURAL_REGENERATION) > 0.3 ? 1 : 0);
        int moveSold = (account.getTotalSoldFraction(AssetType.MOVEMENT_SPEED) > 0.3 ? 1 : 0)
                + (account.getTotalSoldFraction(AssetType.JUMP_STRENGTH) > 0.3 ? 1 : 0);

        if (bodySold >= 1 && moveSold >= 1) return "Кибернетический Брокер (Высокий риск, максимум ликвидности)";
        if (bodySold >= 1) return "Стеклянная Пушка (Проданное здоровье, упор на урон и скорость)";
        if (moveSold >= 1) return "Тяжёлый Бункер (Снижена подвижность, усилена защита)";
        return "Стандартный Биологический Гражданин (Чистый баланс)";
    }

    private static String formatNumber(long num) {
        if (num >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", num / 1_000_000.0);
        } else if (num >= 10_000L) {
            return String.format(Locale.ROOT, "%.1fK", num / 1_000.0);
        }
        return String.format(Locale.ROOT, "%,d", num);
    }
}
