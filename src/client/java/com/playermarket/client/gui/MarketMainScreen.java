package com.playermarket.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.playermarket.PlayerMarketMod;
import com.playermarket.client.ClientMarketState;
import com.playermarket.market.*;
import com.playermarket.network.MarketNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

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
        BLACK_MARKET("tab.playermarket.black_market", "ЧЁРНЫЙ РЫНОК", "tab_black_market"),
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
    private UpgradeType.Category upgradeFilter = UpgradeType.Category.BODY;
    private boolean resourceCatalogMode = false; // false = sell inventory, true = buy catalog
    private String selectedMailId = null;

    private int scrollOffset = 0;
    private int maxScroll = 0;

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
                    "biomech_expansion",
                    "CORP",
                    "БИОИНЖЕНЕРИЯ",
                    "BIOMECH CORP: МАССОВАЯ СКУПКА АКТИВОВ",
                    "Сводка #104",
                    "Фармацевтический конгломерат зафиксировал рекордные цены выкупа HP и скорости.",
                    List.of(
                            "Трансконтинентальный холдинг BioMech Corp опубликовал срочный запрос на закупку биоматериалов и нейронных связей для колониальных экспедиций.",
                            "В связи с критическим дефицитом доноров биржа установила максимальные коэффициенты выплат за продажу базового запаса здоровья (HP) и скорости передвижения.",
                            "Аналитики рекомендуют обратить внимание на временный лизинг: корпорация гарантирует повышенный процент дивидендов (+35 CR/мин) и 100% возврат всех характеристик по истечении контракта без затрат алмазов."
                    ),
                    "§aСОВЕТ ИНВЕСТОРУ: §fЛизинг здоровья на 1-5 дней даёт быстрый капитал и пассивный доход без риска навсегда лишиться HP.",
                    0xFF10B981
            ),
            new NewsArticle(
                    "diamond_syndicate",
                    "CORP",
                    "СИНДИКАТ АЛМАЗОВ",
                    "АЛМАЗНЫЙ КАРТЕЛЬ УЖЕСТОЧАЕТ ТРЕБОВАНИЯ",
                    "Сводка #105",
                    "Стоимость VIP-статуса и продвинутых приводов увеличена из-за дефицита углерода.",
                    List.of(
                            "Синдикат добычи алмазов глубокого залегания сообщил об истощении верхних пластов в секторе 7. Все технологические аугментации строго стандартизированы.",
                            "Для установки постоянных улучшений (VIP-клиент, Усиленный скелет, Кинетический амортизатор) принимаются только натуральные алмазы Minecraft.",
                            "VIP-статус открывает прямой доступ к 50% скидке на обратный выкуп проданных характеристик и повышает доход от дивидендов на 15-50%."
                    ),
                    "§bСОВЕТ ИНВЕСТОРУ: §fНе продавайте природные алмазы за бесценок. Инвестируйте их в VIP-статус и Премиум для максимальной выгоды.",
                    0xFF38BDF8
            ),
            new NewsArticle(
                    "wealth_tax_enforcement",
                    "CORP",
                    "ФИНАНСОВЫЙ НАДЗОР",
                    "ДЕПАРТАМЕНТ ЮСТИЦИИ: НАЛОГ НА БОГАТСТВО",
                    "Сводка #106",
                    "Все просроченные корпоративные контракты облагаются прогрессивным штрафом и налогами.",
                    List.of(
                            "Юридический департамент биржи утвердил антикризисный регламент контроля контрактов. Отныне за срыв дедлайна (1-5 дней) взимается не только базовый штраф.",
                            "Для предотвращения накопления мёртвой ликвидности введен прогрессивный 'Налог на богатство': с нарушителя списывается 5% от текущего баланса CR.",
                            "Дополнительно на оператора накладываются временные санкции: эффекты Слабости и Замедления на 30 секунд при срыве обязательств."
                    ),
                    "§cПРЕДУПРЕЖДЕНИЕ: §fСледите за дедлайнами на доске контрактов! Если на счёте крупная сумма CR, просрочка обойдётся очень дорого.",
                    0xFFEF4444
            ),
            new NewsArticle(
                    "shadow_protocol",
                    "DARK",
                    "ЧЁРНЫЙ РЫНОК",
                    "ТЁМНЫЙ СЕКТОР: ПРОТОКОЛ «ВТОРОЙ ШАНС»",
                    "Сводка #107",
                    "По неподтверждённым данным, брокеры получили доступ к закрытой бирже военных чипов.",
                    List.of(
                            "Анонимные источники в подпольной сети даркнета сообщают о продаже военных имплантов экстренной реанимации.",
                            "Чип «Второй Шанс» активируется при получении смертельного урона, предотвращая гибель оператора, снимая все негативные эффекты и даруя щит поглощения.",
                            "В отличие от официального магазина, сделки на чёрном рынке осуществляются без лицензий и облагаются рисковыми надбавками."
                    ),
                    "§dСОВЕТ ИНВЕСТОРУ: §fИспользуйте «Второй Шанс» перед опасными рейдами в Незер или битвой с боссами.",
                    0xFFC084FC
            ),
            new NewsArticle(
                    "dark_berserker",
                    "DARK",
                    "ЧЁРНЫЙ РЫНОК",
                    "ШТУРМОВОЙ ЧИП «БЕРСЕРК»: ВОЕННЫЙ ДОПИНГ",
                    "Сводка #108",
                    "На подпольных аукционах появился военный чип мгновенного усиления атаки и скорости.",
                    List.of(
                            "Протокол «Берсерк» изначально разрабатывался для элитных штурмовых отрядов корпоративной безопасности.",
                            "При активации на 60 секунд оператор получает +35% к урону и скорости передвижения, жертвуя защитой.",
                            "Чёрный Рынок открывается автоматически при достижении Этапа 4 Программы, покупке VIP III или совершении 12 любых сделок на бирже."
                    ),
                    "§dСОВЕТ: §fИспользуйте «Берсерк» для быстрого уничтожения элитных мобов и зачистки опасных подземелий.",
                    0xFFC084FC
            ),
            new NewsArticle(
                    "market_investigation",
                    "CORP",
                    "РАССЛЕДОВАНИЕ",
                    "КУДА ИСЧЕЗАЮТ ПРОДАННЫЕ ОРГАНЫ И СТАТЫ?",
                    "Сводка #109",
                    "Журналисты выяснили, что купленные у игроков характеристики упаковываются для колониальных батальонов.",
                    List.of(
                            "Независимый аудит подтвердил: выкупленные биологические данные упаковываются в нейро-капсулы и отправляются в закрытые военные лаборатории.",
                            "Корпорация отказывается комментировать слухи, заявляя, что 'любая сделка добровольна, а дивиденды выплачиваются строго в срок'.",
                            "Аналитики советуют трейдерам не продавать здоровье ниже 25%, чтобы избежать необратимой корпоративной зависимости."
                    ),
                    "§6ПРЕДУПРЕЖДЕНИЕ: §fСохраняйте баланс тела! Не продавайте статы ниже критического порога 20-25%.",
                    0xFFF59E0B
            ),
            new NewsArticle(
                    "resource_boom",
                    "CORP",
                    "ТОВАРНЫЙ РЫНОК",
                    "ГОРНОРУДНЫЙ БУМ: ВСПЛЕСК СПРОСА НА РУДУ",
                    "Сводка #110",
                    "Металлургические комбинаты подняли закупочные цены на руду, уголь и редстоун.",
                    List.of(
                            "Тяжёлая промышленность объявила о масштабной закупке меди, железа, угля и редстоуна для расширения производственных линий.",
                            "Операторы терминала могут мгновенно конвертировать добытые в шахтах ресурсы в ликвидные CR через раздел «РЕСУРСЫ».",
                            "Оптом сдавать сырьё выгоднее: специальная кнопка «Оптом всё» позволяет очистить инвентарь в один клик с начислением бонусов."
                    ),
                    "§eСОВЕТ ИНВЕСТОРУ: §fПеред продажей биологических характеристик проверьте инвентарь — сырьё приносит нужный объём CR без ослабления тела.",
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
                            "При оформлении лизинга вы получаете немедленный аванс CR, а также ежеминутные процентные выплаты на весь срок аренды (+35 CR/мин).",
                            "Главное преимущество: при истечении срока контракт закрывается АВТОМАТИЧЕСКИ, и 100% характеристики возвращаются вам абсолютно бесплатно!"
                    ),
                    "§2СОВЕТ ИНВЕСТОРУ: §fЛизинг — идеальный способ получать постоянный пассивный доход без затрат алмазов на выкуп.",
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
                            "Главное правило кризиса — НИЧЕГО НЕ ПРОДАВАТЬ и НЕ ВЫКУПАТЬ в этот период!",
                            "Вместо этого открывайте вкладку «КОНТРАКТЫ»: награды за корпоративные задания в кризис возрастают на 35-60% CR!",
                            "Выполняйте квоты на руду и мобов, чтобы заработать сверхприбыль, пока рынок лихорадит."
                    ),
                    "§aЗОЛОТОЕ ПРАВИЛО: §fВ кризис игнорируйте продажу статов — зарабатывайте на сверхвыплатах по контрактам!",
                    0xFF34D399
            ),
            new NewsArticle(
                    "diamond_earning_guide",
                    "GUIDES",
                    "РУКОВОДСТВО",
                    "ДОБЫЧА АЛМАЗОВ БЕЗ ШАХТ: КОНТРАКТНЫЙ БИЗНЕС",
                    "Инструкция #3",
                    "Где брать алмазы для покупки модификаций в магазине? Подробная сводка для новичков.",
                    List.of(
                            "Улучшения в Магазине требуют алмазы. Если вам не хочется часами копать недра, используйте доску контрактов.",
                            "Каждый корпоративный заказ теперь вознаграждает оператора не только крупной суммой CR, но и 10–30 настоящими алмазами!",
                            "Также от 5 до 30 алмазов выплачивается за завершение сюжетных этапов во вкладке «ПРОГРАММА».",
                            "Инвестируйте полученные алмазы в VIP-статус и Премиум-аккаунт, чтобы навсегда снизить комиссии биржи."
                    ),
                    "§bСОВЕТ: §fБерите по 2-3 контракта одновременно — это приносит до 60 алмазов за один игровой цикл!",
                    0xFF38BDF8
            )
    );

    private NewsArticle selectedNewsArticle = null;
    private String selectedNewsCategory = "ALL";
    private boolean finalEndingRevokeMode = false;
    private final List<ClickAction> modalClickActions = new ArrayList<>();
    private int currentClipTop = -1;
    private int currentClipBottom = -1;

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
        selectedNewsArticle = null;
        selectedNewsCategory = "ALL";
        finalEndingRevokeMode = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedNewsArticle != null) return true;
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
        if (selectedNewsArticle != null) {
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
        if (selectedNewsArticle != null) {
            if (event.key() == InputConstants.KEY_M || event.key() == InputConstants.KEY_ESCAPE) {
                selectedNewsArticle = null;
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
        MarketEvent event = ClientMarketState.getActiveEvent();
        int diamonds = (minecraft != null && minecraft.player != null)
                ? MarketAttributeApplier.countVanillaDiamonds(minecraft.player.getInventory()) : 0;

        // 4. Header Bar Content
        graphics.text(font, "§a§lPLAYER MARKET §8// §fФИНАНСОВЫЙ ТЕРМИНАЛ §8[§7v2.0§8]", left + 10, top + 10, 0xFFE2E8F0, false);

        String balanceStr = finalEndingRevokeMode
                ? "§4[ §cCR: §k99999§r §c(UNKNOWN) §8| §b♦: §f§l" + diamonds + " §4]"
                : "§2[ §aCR: §f§l" + formatNumber(account.getCrBalance()) + " §8| §b♦: §f§l" + diamonds + " §2]";
        int balWidth = font.width(balanceStr);
        graphics.text(font, balanceStr, left + guiWidth - balWidth - 10, top + 10, 0xFFFFFFFF, false);

        int bgMouseX = (selectedNewsArticle != null) ? -9999 : mouseX;
        int bgMouseY = (selectedNewsArticle != null) ? -9999 : mouseY;

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
                graphics.blit(RenderPipelines.GUI_TEXTURED, t.getIconId(), iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, 16, 16);
            } catch (Throwable ignored) {}

            String tabText = (isSelected ? "§l" : "") + t.getTitle();
            if (t == Tab.MAIL) {
                int unread = account.getStoryProgress().getUnreadMailCount();
                if (unread > 0) {
                    tabText += " §c(" + unread + ")";
                }
            } else if (t == Tab.BLACK_MARKET && !account.isBlackMarketUnlocked()) {
                tabText = "§8" + t.getTitle() + " §c🔒";
                textColor = 0xFF64748B;
            }
            drawClampedText(graphics, tabText, left + 22, ty + (tabHeight - 8) / 2, sidebarWidth - 26, textColor, false);

            final Tab clickedTab = t;
            registerClick(left + 2, ty, left + sidebarWidth, ty + tabHeight, () -> {
                activeTab = clickedTab;
                scrollOffset = 0;
                sendMarketAction("TAB_OPENED", clickedTab.name(), "", 0, 0.0);
            }, null);
        }

        // 6. Footer Content
        // Sidebar footer column
        graphics.text(font, "§8PM // v2.0", left + 8, footerTop + 6, 0xFF475569, false);

        // Footer main status starts to the right of the sidebar (left + sidebarWidth + 8)
        int footerLeft = left + sidebarWidth + 8;
        int footerMaxWidth = guiWidth - sidebarWidth - 190;
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

        String eventBadge = "§6[ " + event.getHeadline() + " ]";
        int ebWidth = font.width(eventBadge);
        graphics.text(font, eventBadge, left + guiWidth - ebWidth - 10, footerTop + 6, 0xFFFBBF24, false);

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

        // 8. News Article Interactive Modal Reader Popup
        if (selectedNewsArticle != null) {
            modalClickActions.clear();
            renderNewsModal(graphics, selectedNewsArticle, width, height, mouseX, mouseY);
        }

        // Commit frame clicks
        clickActions.clear();
        clickActions.addAll(frameClickBuffer);

        // Tooltips (clean multiline wrapped, only when modal is not open)
        if (selectedNewsArticle == null) {
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
        int cardHeight = 60;

        // Card 1: Balance & Liquidity
        drawCard(graphics, left, top, cardWidth, cardHeight, mouseX, mouseY);
        drawClampedText(graphics, "§7БАЛАНС СЧЁТА", left + 8, top + 6, cardWidth - 16, 0xFF94A3B8, false);
        drawClampedText(graphics, "§a§l" + formatNumber(account.getCrBalance()) + " CR", left + 8, top + 18, cardWidth - 16, 0xFF10B981, false);
        drawClampedText(graphics, "§8Доход: §7" + formatNumber(account.getTotalCrEarned()) + " CR", left + 8, top + 34, cardWidth - 16, 0xFF64748B, false);
        drawClampedText(graphics, "§8Расход: §7" + formatNumber(account.getTotalCrSpent()) + " CR", left + 8, top + 44, cardWidth - 16, 0xFF64748B, false);

        // Card 2: Sold Assets Valuation
        int c2Left = left + cardWidth + 6;
        drawCard(graphics, c2Left, top, cardWidth, cardHeight, mouseX, mouseY);
        drawClampedText(graphics, "§7АКТИВЫ", c2Left + 8, top + 6, cardWidth - 16, 0xFF94A3B8, false);
        drawClampedText(graphics, "§6§l" + formatNumber(account.getTotalSoldAssetsValuation()) + " CR", c2Left + 8, top + 18, cardWidth - 16, 0xFFF59E0B, false);
        drawClampedText(graphics, "§8Статов: §f" + account.getSoldAssetCount() + " шт.", c2Left + 8, top + 34, cardWidth - 16, 0xFF64748B, false);
        drawClampedText(graphics, "§8Лизинг: §d" + account.getActiveLeases().size() + " шт.", c2Left + 8, top + 44, cardWidth - 16, 0xFF64748B, false);

        // Card 3: Upgrades & Diamonds
        int c3Left = c2Left + cardWidth + 6;
        drawCard(graphics, c3Left, top, cardWidth, cardHeight, mouseX, mouseY);
        drawClampedText(graphics, "§7АЛМАЗЫ & VIP", c3Left + 8, top + 6, cardWidth - 16, 0xFF94A3B8, false);
        drawClampedText(graphics, "§b§l" + diamonds + " ♦ §7(инв.)", c3Left + 8, top + 18, cardWidth - 16, 0xFF38BDF8, false);
        drawClampedText(graphics, "§8Вложено: §b" + account.getTotalDiamondsSpent() + " ♦", c3Left + 8, top + 34, cardWidth - 16, 0xFF64748B, false);
        int vipLvl = account.getUpgradeLevel(UpgradeType.VIP_CLIENT);
        drawClampedText(graphics, "§8VIP: §e" + (vipLvl > 0 ? "Ур. " + vipLvl : "Базовый"), c3Left + 8, top + 44, cardWidth - 16, 0xFF64748B, false);

        // Market Event Banner
        int bannerY = top + cardHeight + 8;
        drawSprite(graphics, "hud_bg", left, bannerY, width, 52, 0xFF141A26);
        drawClampedText(graphics, "§6§lСВОДКА РЫНКА: §f" + event.getHeadline(), left + 8, bannerY + 6, width - 16, 0xFFFBBF24, false);
        drawClampedText(graphics, "§7" + event.getDescription(), left + 8, bannerY + 18, width - 16, 0xFF94A3B8, false);
        String eventMods = String.format(Locale.ROOT, "§8Множители: Тело x%.2f | Движение x%.2f | Ресурсы x%.2f | Выкуп x%.2f",
                event.getBodyMultiplier(), event.getMovementMultiplier(), event.getResourceBuyMultiplier(), event.getBuybackCostMultiplier());
        drawClampedText(graphics, eventMods, left + 8, bannerY + 34, width - 16, 0xFF64748B, false);

        // Quick Navigation Buttons
        int navY = bannerY + 58;
        int navW = (width - 18) / 4;
        int navH = 24;

        drawButton(graphics, "btn_amber", left, navY, navW, navH, "ПРОГРАММА", mouseX, mouseY, () -> {
            activeTab = Tab.STORY;
            scrollOffset = 0;
            sendMarketAction("TAB_OPENED", "STORY", "", 0, 0.0);
        });

        drawButton(graphics, "btn_green", left + (navW + 6), navY, navW, navH, "ПРОДАЖА", mouseX, mouseY, () -> {
            activeTab = Tab.SELL_ASSETS;
            scrollOffset = 0;
            sendMarketAction("TAB_OPENED", "SELL_ASSETS", "", 0, 0.0);
        });

        drawButton(graphics, "btn_blue", left + (navW + 6) * 2, navY, navW, navH, "МАГАЗИН", mouseX, mouseY, () -> {
            activeTab = Tab.UPGRADE_STORE;
            scrollOffset = 0;
            sendMarketAction("TAB_OPENED", "UPGRADE_STORE", "", 0, 0.0);
        });

        drawButton(graphics, "btn_gray", left + (navW + 6) * 3, navY, navW, navH, "ПОЧТА", mouseX, mouseY, () -> {
            activeTab = Tab.MAIL;
            scrollOffset = 0;
            sendMarketAction("TAB_OPENED", "MAIL", "", 0, 0.0);
        });

        // Archetypes & Story Mission Banner
        int archY = navY + navH + 8;
        int archH = height - (archY - top);
        drawSprite(graphics, "card", left, archY, width, archH, 0xFF101622);

        int curStage = account.getStoryStage();
        String stageTitle = (curStage >= 1 && curStage <= com.playermarket.story.StoryQuests.STAGES.size())
                ? com.playermarket.story.StoryQuests.STAGES.get(curStage - 1).name()
                : "ЭТАП " + curStage;

        int completedTasks = account.getStoryProgress().getCompletedTasks().size();
        drawClampedText(graphics, "§a🎭 ВАШ АРХЕТИП: §f" + determineArchetype(account), left + 8, archY + 6, width - 16, 0xFF10B981, false);
        drawClampedText(graphics, "§e📋 ЭТАП " + curStage + ": §f" + stageTitle + " §8(Выполнено задач: " + completedTasks + ")", left + 8, archY + 18, width - 16, 0xFFFBBF24, false);
        drawClampedText(graphics, "§7Продавайте характеристики, выполняйте квесты и читайте «ПОЧТУ» для продвижения.", left + 8, archY + 32, width - 16, 0xFFCBD5E1, false);
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
        int finalSectionH = (currentStage == 6) ? 140 : 0;
        int sideQuestsH = 26 + (com.playermarket.story.StoryQuests.SIDE_QUESTS.size() * 52);
        int totalStoryH = headerBannerH + tasksSectionH + finalSectionH + sideQuestsH + 20;

        maxScroll = Math.max(0, totalStoryH - height);
        setScissor(graphics, left, top, left + width, top + height);

        int cy = top - scrollOffset;

        // 1. Stage Info Banner
        drawSprite(graphics, "panel", left, cy, width, headerBannerH, 0xFF141926);
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

        // 2. Stage Tasks Header
        drawSprite(graphics, "hud_bg", left, cy, width, 18, 0xFF0D121B);
        drawClampedText(graphics, "§a§lОСНОВНЫЕ ЗАДАЧИ ЭТАПА (ВЫПОЛНИТЕ ДЛЯ ПРОДВИЖЕНИЯ ПО СЮЖЕТУ):",
                left + 8, cy + 5, width - 16, 0xFF10B981, false);
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
                drawClampedText(graphics, "§7Ортопедический фиксатор активен на правой ноге. Мобильность снижена.", left + 8, cy + 34, width - 16, 0xFFCBD5E1, false);
                drawClampedText(graphics, "§8«Вы больше не клиент. Вы — корпоративный актив.»", left + 8, cy + 48, width - 16, 0xFF64748B, false);
                cy += 76;
            } else {
                int cardH = 110;
                drawSprite(graphics, "card_amber", left, cy, width, cardH, 0xFF241A0A);
                drawClampedText(graphics, "§6§lФИНАНСОВЫЙ ДОГОВОР // ACCOUNT SETTLEMENT", left + 10, cy + 8, width - 20, 0xFFFBBF24, false);
                drawClampedText(graphics, "§fВсе этапы Программы Активов завершены. Корпорация производит окончательный расчёт.", left + 10, cy + 22, width - 20, 0xFFE2E8F0, false);
                drawClampedText(graphics, "§7Выплата +100 000 CR начисляется на счета всех зарегистрированных участников.", left + 10, cy + 34, width - 20, 0xFFCBD5E1, false);

                int bigBtnW = 280;
                int bigBtnH = 24;
                int bigBtnX = left + (width - bigBtnW) / 2;
                int bigBtnY = cy + 50;
                drawButton(graphics, "btn_green", bigBtnX, bigBtnY, bigBtnW, bigBtnH, "ACCEPT FINAL SETTLEMENT (+100 000 CR)", mouseX, mouseY, () -> {
                    sendMarketAction("CHOOSE_FINAL_TRANSACTION", "FINAL_SETTLEMENT", "", 0, 0.0);
                });

                String finePrint = "SECTION 17.4: RECOVERY PROTOCOL & BIOLOGICAL SURRENDER. Irrevocable assignment of biological assets.";
                drawClampedText(graphics, "§8" + finePrint, left + 10, cy + 82, width - 20, 0xFF475569, false);

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

        int listWidth = 190;
        int readerLeft = left + listWidth + 6;
        int readerWidth = width - listWidth - 6;

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
            drawClampedCenteredText(graphics, "§8Почтовый ящик пуст.", left + listWidth / 2, listTop + listHeight / 2, listWidth - 16, 0xFF64748B, false);
        } else {
            int itemH = 36;
            int spacing = 38;
            setScissor(graphics, left, listTop, left + listWidth, listTop + listHeight);

            for (int i = 0; i < mailbox.size(); i++) {
                com.playermarket.story.MailMessage msg = mailbox.get(i);
                int my = listTop + (i * spacing);
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
                    if (!fMsg.read()) {
                        sendMarketAction("MARK_MAIL_READ", fMsg.id(), "", 0, 0.0);
                    }
                }, null);
            }

            clearScissor(graphics);
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

        // Letter Body (Split paragraphs and wrap)
        int bodyY = top + 46;
        int bodyMaxY = top + height - (currentMsg.rewardType().equals("NONE") ? 10 : 40);
        int textMaxW = readerWidth - 20;

        for (String paragraph : currentMsg.body().split("\n")) {
            if (paragraph.trim().isEmpty()) {
                bodyY += 6;
                continue;
            }
            List<net.minecraft.network.chat.FormattedText> lines =
                    font.getSplitter().splitLines(Component.literal("§7" + paragraph), textMaxW, net.minecraft.network.chat.Style.EMPTY);
            for (var line : lines) {
                if (bodyY + 9 <= bodyMaxY) {
                    graphics.text(font, line.getString(), readerLeft + 10, bodyY, 0xFFCBD5E1, false);
                    bodyY += 10;
                }
            }
            bodyY += 4;
        }

        // Attachment / Reward Box at the bottom of the letter
        if (!currentMsg.rewardType().equals("NONE")) {
            int attachY = top + height - 34;
            drawSprite(graphics, "hud_bg", readerLeft + 8, attachY, readerWidth - 16, 28, 0xFF172030);

            if ("CONTRACT_OFFER".equalsIgnoreCase(currentMsg.rewardType())) {
                drawClampedText(graphics, "§e[!] Подпишите официальный договор на бумаге (Мятое письмо в инвентаре)",
                        readerLeft + 16, attachY + 9, readerWidth - 32, 0xFFFBBF24, false);
            } else if (currentMsg.rewardClaimed()) {
                drawClampedText(graphics, "§8✔ Корпоративное вложение получено (" + currentMsg.rewardAmount() + " " + currentMsg.rewardType() + ")",
                        readerLeft + 16, attachY + 9, readerWidth - 32, 0xFF64748B, false);
            } else {
                String attachTitle = "§eВЛОЖЕНИЕ: §a+" + currentMsg.rewardAmount() + " " + currentMsg.rewardType();
                drawClampedText(graphics, attachTitle, readerLeft + 16, attachY + 9, readerWidth - 150, 0xFFFBBF24, false);

                int claimW = 120;
                int claimH = 20;
                int claimX = readerLeft + readerWidth - claimW - 14;
                int claimY = attachY + 4;

                final String cId = currentMsg.id();
                drawSmallButton(graphics, "btn_green", claimX, claimY, claimW, claimH, "📎 ЗАБРАТЬ", mouseX, mouseY, () -> {
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
            String title = (unlocked ? "" : "🔒 ") + cat.getDisplayName();
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
        drawClampedText(graphics, "§a§lПРАВИЛА ТОРГОВЛИ: §fПродажа навсегда даёт разовый крупный капитал CR (выкуп платный).", left + 6, bannerY + 4, width - 12, 0xFF10B981, false);
        drawClampedText(graphics, "§eЛизинг (1-5 дн.) — вклад: доход +35 CR/мин + 100% авто-возврат стата бесплатно! §8[?] Подсказка", left + 6, bannerY + 14, width - 12, 0xFFFBBF24, false);

        // 3. Scrollable Card List
        int listY = bannerY + bannerH + 4;
        int listH = height - (listY - top);

        List<AssetType> assets = new ArrayList<>();
        for (AssetType a : AssetType.values()) {
            if (assetFilter == null || a.getCategory() == assetFilter) {
                assets.add(a);
            }
        }

        int cardHeight = 68;
        int spacing = 72;
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
                long cancelCost = Math.max(50L, Math.round(lc.payoutCr() * 1.25));
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
            int titleW = Math.max(80, Math.min(130, (int) (infoW * 0.52)));
            int statusW = infoW - titleW - 4;

            String assetTitle = "§f§l" + asset.getDisplayName();
            drawClampedText(graphics, assetTitle, left + 6, cy + 4, titleW, 0xFFFFFFFF, false);

            String statusStr = String.format(Locale.ROOT, "§7Продано: §e%.0f%% §8/ %.0f%%", totalSold * 100.0, maxAllowed * 100.0);
            drawClampedText(graphics, statusStr, left + 6 + titleW + 4, cy + 4, statusW, 0xFFFBBF24, false);

            // === ROW 2: 6 EQUAL NON-OVERLAPPING BUTTONS OR LOCKED NOTICE ===
            int actionW = width - 12;
            int btnCount = 6;
            int btnGap = 2;
            int singleBtnW = (actionW - (btnCount - 1) * btnGap) / btnCount;
            int row2Y = cy + 18;
            int row2H = 16;

            if (!catUnlocked) {
                drawSprite(graphics, "hud_bg", left + 6, row2Y, actionW, row2H, 0xFF1E1424);
                drawClampedText(graphics, "§c🔒 КАТЕГОРИЯ ЗАБЛОКИРОВАНА: §7Доступ открывается на §eЭтапе " + asset.getCategory().getRequiredStage() + " §7Программы Активов", left + 12, row2Y + 4, actionW - 16, 0xFFEF4444, false);
            } else {
                // 1. Permanent 10%
                long p10 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.10, 0);
                boolean a10 = canSellMore && (totalSold + 0.10 <= maxAllowed + 0.001);
                int b0X = left + 6 + 0 * (singleBtnW + btnGap);
                drawSmallButton(graphics, a10 ? "btn_green" : "btn_gray", b0X, row2Y, singleBtnW, row2H, "10%", mouseX, mouseY, () -> {
                    if (a10) sendMarketAction("SELL_ASSET", asset.getId(), "", 0, 0.10);
                }, "Продать 10% характеристики навсегда за +" + p10 + " CR");

                // 2. Permanent 25%
                long p25 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.25, 0);
                boolean a25 = canSellMore && (totalSold + 0.25 <= maxAllowed + 0.001);
                int b1X = left + 6 + 1 * (singleBtnW + btnGap);
                drawSmallButton(graphics, a25 ? "btn_green" : "btn_gray", b1X, row2Y, singleBtnW, row2H, "25%", mouseX, mouseY, () -> {
                    if (a25) sendMarketAction("SELL_ASSET", asset.getId(), "", 0, 0.25);
                }, "Продать 25% характеристики навсегда за +" + p25 + " CR");

                // 3. Permanent 50%
                long p50 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.50, 0);
                boolean a50 = canSellMore && (totalSold + 0.50 <= maxAllowed + 0.001);
                int b2X = left + 6 + 2 * (singleBtnW + btnGap);
                drawSmallButton(graphics, a50 ? "btn_green" : "btn_gray", b2X, row2Y, singleBtnW, row2H, "50%", mouseX, mouseY, () -> {
                    if (a50) sendMarketAction("SELL_ASSET", asset.getId(), "", 0, 0.50);
                }, "Продать 50% характеристики навсегда за +" + p50 + " CR (в 5 раз больше, чем за 10%)");

                // 4. Lease 1 Day (20%)
                long l1 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.20, 1);
                boolean al1 = canSellMore && (totalSold + 0.20 <= maxAllowed + 0.001);
                int b3X = left + 6 + 3 * (singleBtnW + btnGap);
                drawSmallButton(graphics, al1 ? "btn_blue" : "btn_gray", b3X, row2Y, singleBtnW, row2H, "Лизинг 1д", mouseX, mouseY, () -> {
                    if (al1) sendMarketAction("LEASE_ASSET", asset.getId(), "", 1, 0.20);
                }, "Лизинг 20% стата на 1 день: +" + l1 + " CR сразу + пассивный доход");

                // 5. Lease 3 Days (20%)
                long l3 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.20, 3);
                boolean al3 = canSellMore && (totalSold + 0.20 <= maxAllowed + 0.001);
                int b4X = left + 6 + 4 * (singleBtnW + btnGap);
                drawSmallButton(graphics, al3 ? "btn_amber" : "btn_gray", b4X, row2Y, singleBtnW, row2H, "Лизинг 3д", mouseX, mouseY, () -> {
                    if (al3) sendMarketAction("LEASE_ASSET", asset.getId(), "", 3, 0.20);
                }, "Лизинг 20% стата на 3 дня: +" + l3 + " CR сразу + повышенный процент");

                // 6. Lease 5 Days (20%)
                long l5 = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, 0.20, 5);
                boolean al5 = canSellMore && (totalSold + 0.20 <= maxAllowed + 0.001);
                int b5X = left + 6 + 5 * (singleBtnW + btnGap);
                drawSmallButton(graphics, al5 ? "btn_amber" : "btn_gray", b5X, row2Y, singleBtnW, row2H, "Лизинг 5д", mouseX, mouseY, () -> {
                    if (al5) sendMarketAction("LEASE_ASSET", asset.getId(), "", 5, 0.20);
                }, "Лизинг 20% стата на 5 дней: +" + l5 + " CR сразу + максимальный процент");
            }

            // === ROW 3: 2 EQUAL-WIDTH DIVIDEND BUTTONS ===
            int row3Y = cy + 38;
            int row3H = 24;
            int divW = (actionW - 4) / 2;

            String currentDiv = account.getSelectedDividends().getOrDefault(asset.getId(), asset.getDividendA().id());
            boolean selA = currentDiv.equals(asset.getDividendA().id());
            boolean selB = currentDiv.equals(asset.getDividendB().id());

            // Dividend A button
            int divAX = left + 6;
            drawSprite(graphics, selA ? "card_selected" : "card", divAX, row3Y, divW, row3H, selA ? 0xFF1B382D : 0xFF0D121B);
            String labelA = (selA ? "§a✔ " : "§7○ ") + asset.getDividendA().label();
            drawClampedText(graphics, labelA, divAX + 6, row3Y + 3, divW - 12, selA ? 0xFF34D399 : 0xFFCBD5E1, false);
            drawClampedText(graphics, "§8" + asset.getDividendA().desc(), divAX + 6, row3Y + 13, divW - 12, 0xFF64748B, false);

            registerClick(divAX, row3Y, divAX + divW, row3Y + row3H, () -> {
                sendMarketAction("SELECT_DIVIDEND", asset.getId(), asset.getDividendA().id(), 0, 0.0);
            }, "Выбрать: " + asset.getDividendA().label());

            // Dividend B button
            int divBX = divAX + divW + 4;
            drawSprite(graphics, selB ? "card_selected" : "card", divBX, row3Y, divW, row3H, selB ? 0xFF1B382D : 0xFF0D121B);
            String labelB = (selB ? "§a✔ " : "§7○ ") + asset.getDividendB().label();
            drawClampedText(graphics, labelB, divBX + 6, row3Y + 3, divW - 12, selB ? 0xFF34D399 : 0xFFCBD5E1, false);
            drawClampedText(graphics, "§8" + asset.getDividendB().desc(), divBX + 6, row3Y + 13, divW - 12, 0xFF64748B, false);

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
        drawClampedText(graphics, "§b§lОБРАТНЫЙ ВЫКУП ХАРАКТЕРИСТИК: §fВосстановление полной биологической нормы.", left + 8, top + 4, width - 16, 0xFF38BDF8, false);
        drawClampedText(graphics, "§7Выкуп снимает все постоянные дебаффы. Лицензия брокера снижает комиссию выкупа.", left + 8, top + 14, width - 16, 0xFF94A3B8, false);

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
            drawClampedCenteredText(graphics, "§aВсе характеристики в целости! Нет проданных постоянных долей или лизинга.", left + width / 2, listY + 20, width - 20, 0xFF10B981, false);
            maxScroll = 0;
            return;
        }

        int itemH = 46;
        int spacing = 50;
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

            drawClampedText(graphics, "§f§l" + asset.getDisplayName() + " §7[" + asset.getCategory().getDisplayName() + "]", left + 8, cy + 6, width / 2, 0xFFFFFFFF, false);
            drawClampedText(graphics, String.format(Locale.ROOT, "§eПродано навсегда: %.0f%% §8| Базис выплаты: %d CR",
                    permSold * 100.0, account.getPermanentSalePayoutBasis().getOrDefault(asset.getId(), 0L)), left + 8, cy + 20, width / 2, 0xFFFBBF24, false);

            int btnW = 130;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 12;

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
            long cancelCost = Math.max(50L, Math.round(lease.payoutCr() * 1.25));
            boolean canAfford = account.getCrBalance() >= cancelCost;

            drawSprite(graphics, "card_amber", left, cy, width, itemH, 0xFF181410);

            drawClampedText(graphics, "§6§lЛизинг: §f" + aName + " §c-" + Math.round(lease.fraction() * 100) + "% §8(Осталось: " + String.format(Locale.ROOT, "%.1f", daysLeft) + " дн.)",
                    left + 8, cy + 6, width - 150, 0xFFFBBF24, false);
            drawClampedText(graphics, "§7Досрочный возврат: " + formatNumber(cancelCost) + " CR §8(выплата + штраф 25%)",
                    left + 8, cy + 20, width - 150, 0xFFCBD5E1, false);

            int btnW = 140;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 12;

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

        int itemH = 50;
        int spacing = 54;
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

            drawSprite(graphics, "card", left, cy, width, itemH, 0xFF121622);

            String title = "§b§l" + upg.getTitle() + " §7[Ур. " + currentLvl + " / " + upg.getMaxLevel() + "]";
            drawClampedText(graphics, title, left + 8, cy + 6, width / 2, 0xFF38BDF8, false);

            String desc = isMax ? "§aМаксимальный уровень модификации достигнут." : upg.getSummaryForLevel(currentLvl + 1);
            drawClampedText(graphics, "§7" + desc, left + 8, cy + 20, width - 150, 0xFF94A3B8, false);

            if (!prereqMet) {
                drawClampedText(graphics, prereqText, left + 8, cy + 34, width - 150, 0xFFEF4444, false);
            }

            int btnW = 120;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 14;

            boolean canAfford = !isMax && prereqMet && (playerDiamonds >= nextCost);
            String btnText = isMax ? "МАКСИМУМ" : ("Купить (" + nextCost + " ♦)");

            drawButton(graphics, canAfford ? "btn_blue" : "btn_gray", btnX, btnY, btnW, btnH, btnText, mouseX, mouseY, () -> {
                if (canAfford) sendMarketAction("BUY_DIAMOND_UPGRADE", upg.getId(), "", 0, 0.0);
            });
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
        drawClampedText(graphics, "§e§lКОРПОРАТИВНЫЕ КОНТРАКТЫ §8(Срок: 1-5 игровых дней)", left + 8, top + 6, width - 155, 0xFFFBBF24, false);

        int refW = 145;
        int refH = 16;
        int refX = left + width - refW - 4;
        int refY = top + 3;
        boolean canRefresh = account.getCrBalance() >= 150L;
        drawSmallButton(graphics, canRefresh ? "btn_amber" : "btn_gray", refX, refY, refW, refH, "Обновить доску (150 CR)", mouseX, mouseY, () -> {
            if (canRefresh) sendMarketAction("REFRESH_CONTRACTS", "", "", 0, 0.0);
        });

        int listY = top + ctrlH + 6;
        int listH = height - (listY - top);

        List<ContractEntry> contracts = account.getContracts();
        int itemH = 50;
        int spacing = 54;
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

            String timeStatus;
            if (c.claimed()) {
                timeStatus = "§8ВЫПОЛНЕНО И ЗАКРЫТО";
            } else if (c.completed()) {
                timeStatus = "§aВЫПОЛНЕН! ЗАБЕРИТЕ НАГРАДУ";
            } else if (c.active()) {
                timeStatus = String.format(Locale.ROOT, "§eВ РАБОТЕ §8| §cОсталось: %.1f дн. §8| §a+%d CR §8| §b+%d ♦ §8| §cШтраф: -%d CR",
                        daysLeft, c.rewardCr(), c.rewardDiamonds(), c.penaltyCr());
            } else {
                timeStatus = String.format(Locale.ROOT, "§7Срок: %d дн. §8| §a+%d CR §8| §b+%d ♦ §8| §cШтраф: -%d CR",
                        c.durationDays(), c.rewardCr(), c.rewardDiamonds(), c.penaltyCr());
            }

            drawClampedText(graphics, "§e§l" + c.title() + "  " + timeStatus, left + 8, cy + 6, width - 125, 0xFFFBBF24, false);
            drawClampedText(graphics, "§7" + c.description(), left + 8, cy + 20, width - 125, 0xFF94A3B8, false);

            int barW = 160;
            int barH = 5;
            int barX = left + 8;
            int barY = cy + 36;
            double prog = (double) c.currentProgress() / (double) c.targetProgress();
            drawProgressBar(graphics, barX, barY, barW, barH, prog, c.completed() ? "bar_green" : (c.active() ? "bar_blue" : "bar_blue"));
            drawClampedText(graphics, String.format(Locale.ROOT, "§f%d / %d", c.currentProgress(), c.targetProgress()), barX + barW + 8, cy + 34, 75, 0xFFCBD5E1, false);

            int btnW = 105;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 14;

            if (c.claimed()) {
                drawButton(graphics, "btn_gray", btnX, btnY, btnW, btnH, "ЗАКРЫТ", mouseX, mouseY, () -> {});
            } else if (c.completed()) {
                long reward = Math.round(c.rewardCr() * event.getContractRewardMultiplier());
                String btnText = "+" + reward + (c.rewardDiamonds() > 0 ? " §b+" + c.rewardDiamonds() + "♦" : "");
                drawButton(graphics, "btn_amber", btnX, btnY, btnW, btnH, btnText, mouseX, mouseY, () -> {
                    sendMarketAction("CLAIM_CONTRACT", c.id(), "", 0, 0.0);
                });
            } else if (c.active()) {
                drawButton(graphics, "btn_blue", btnX, btnY, btnW, btnH, "В РАБОТЕ", mouseX, mouseY, () -> {});
            } else {
                drawButton(graphics, "btn_green", btnX, btnY, btnW, btnH, "ПРИНЯТЬ", mouseX, mouseY, () -> {
                    sendMarketAction("ACCEPT_CONTRACT", c.id(), "", 0, 0.0);
                });
            }
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, top, width, height, maxScroll, scrollOffset);
    }

    // ==========================================
    // TAB: BLACK_MARKET (Clean Multiline Text)
    // ==========================================
    private void renderBlackMarket(GuiGraphicsExtractor graphics, PlayerMarketAccount account, MarketEvent event, int playerDiamonds,
                                   int left, int top, int width, int height, int mouseX, int mouseY) {
        long gameTick = ClientMarketState.getCurrentGameTick();
        boolean unlocked = account.isBlackMarketUnlocked();

        if (!unlocked) {
            drawSprite(graphics, "hud_bg", left, top, width, height, 0xFF140810);

            int cardW = Math.min(420, width - 20);
            int cardH = 145;
            int cardX = left + (width - cardW) / 2;
            int cardY = top + Math.max(10, (height - cardH) / 2);

            drawSprite(graphics, "card_purple", cardX, cardY, cardW, cardH, 0xFF180E24);

            drawClampedCenteredText(graphics, "§c§l☣ СЕКТОР 7: ЧЁРНЫЙ РЫНОК ЗАБЛОКИРОВАН ☣", cardX + cardW / 2, cardY + 8, cardW - 16, 0xFFEF4444, true);
            drawClampedCenteredText(graphics, "§7У вас отсутствует допуск службы корпоративной безопасности.", cardX + cardW / 2, cardY + 22, cardW - 16, 0xFFCBD5E1, false);

            int stage = account.getStoryStage();
            int vip = account.getUpgradeLevel(UpgradeType.VIP_CLIENT);
            int deals = account.getTotalDealsCount();

            drawClampedText(graphics, "§6Условия допуска (выполните любое ОДНО):", cardX + 14, cardY + 40, cardW - 28, 0xFFFBBF24, false);

            String s1 = (stage >= 4 ? "§a✔ " : "§c○ ") + "§f1. Программа Активов: §eЭтап 4+ §7(Текущий: §e" + stage + "/4§7)";
            drawClampedText(graphics, s1, cardX + 14, cardY + 54, cardW - 28, 0xFFFFFFFF, false);

            String s2 = (vip >= 3 ? "§a✔ " : "§c○ ") + "§f2. VIP-Инвестиции: §bVIP III в Магазине §7(Текущий: §b" + vip + "/3§7)";
            drawClampedText(graphics, s2, cardX + 14, cardY + 68, cardW - 28, 0xFFFFFFFF, false);

            int progTasks = account.getStoryProgress().getTotalCompletedProgramTasks();
            String s3 = (progTasks >= 12 ? "§a✔ " : "§c○ ") + "§f3. Задания Программы: §a12 любых квестов §7(Выполнено: §a" + progTasks + "/12§7)";
            drawClampedText(graphics, s3, cardX + 14, cardY + 82, cardW - 28, 0xFFFFFFFF, false);

            drawClampedText(graphics, "§8* Засчитываются любые основные задачи этапов", cardX + 14, cardY + 104, cardW - 28, 0xFF64748B, false);
            drawClampedText(graphics, "§8  и дополнительные поручения (побочные квесты) во вкладке «ПРОГРАММА».", cardX + 14, cardY + 116, cardW - 28, 0xFF64748B, false);

            maxScroll = 0;
            return;
        }

        int itemH = 58;
        int spacing = 62;
        BlackMarketItem[] items = BlackMarketItem.values();
        maxScroll = Math.max(0, items.length * spacing - height);

        setScissor(graphics, left, top, left + width, top + height);

        for (int i = 0; i < items.length; i++) {
            BlackMarketItem bm = items[i];
            int cy = top + (i * spacing) - scrollOffset;
            if (cy + itemH < top || cy > top + height) continue;

            boolean active = account.isBlackMarketEffectActive(bm, gameTick);
            long cd = account.getBlackMarketCooldownRemainingTicks(bm, gameTick);
            boolean onCd = cd > 0L;

            drawSprite(graphics, "card_purple", left, cy, width, itemH, 0xFF180E24);

            String status = active ? "§a[АКТИВЕН]" : (onCd ? ("§c[Перезарядка " + (cd / 20L) + "с]") : "§d[ГОТОВ]");
            drawClampedText(graphics, "§d§l" + bm.getTitle() + "  " + status, left + 8, cy + 6, width - 140, 0xFFC084FC, false);

            int btnW = 120;
            int btnH = 22;
            int btnX = left + width - btnW - 8;
            int btnY = cy + 18;

            int textW = width - btnW - 24;
            String fullSummary = bm.getSummary();
            if (font.width(fullSummary) <= textW) {
                graphics.text(font, "§7" + fullSummary, left + 8, cy + 22, 0xFF94A3B8, false);
            } else {
                List<String> words = Arrays.asList(fullSummary.split(" "));
                StringBuilder line1 = new StringBuilder();
                StringBuilder line2 = new StringBuilder();
                boolean second = false;
                for (String w : words) {
                    if (!second) {
                        if (font.width(line1 + w + " ") < textW) {
                            line1.append(w).append(" ");
                        } else {
                            second = true;
                            line2.append(w).append(" ");
                        }
                    } else {
                        line2.append(w).append(" ");
                    }
                }
                graphics.text(font, "§7" + line1.toString().trim(), left + 8, cy + 20, 0xFF94A3B8, false);
                drawClampedText(graphics, "§7" + line2.toString().trim(), left + 8, cy + 32, textW, 0xFF94A3B8, false);
            }

            boolean canAfford = (account.getCrBalance() >= bm.getCostCr() && playerDiamonds >= bm.getCostDiamonds());
            boolean canBuy = canAfford && !onCd && (!active || bm == BlackMarketItem.SECOND_CHANCE || bm == BlackMarketItem.GOLDEN_CONTRACT);

            String costStr = bm.getCostCr() + " CR + " + bm.getCostDiamonds() + " ♦";
            drawButton(graphics, canBuy ? "btn_purple" : "btn_gray", btnX, btnY, btnW, btnH, costStr, mouseX, mouseY, () -> {
                if (canBuy) sendMarketAction("BUY_BLACK_MARKET", bm.getId(), "", 0, 0.0);
            });
        }

        clearScissor(graphics);
        renderVerticalScrollbar(graphics, left, top, width, height, maxScroll, scrollOffset);
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

        int listY = top + 22;
        int listH = height - (listY - top);

        if (!resourceCatalogMode) {
            Inventory inv = (minecraft != null && minecraft.player != null) ? minecraft.player.getInventory() : null;
            if (inv == null) return;

            List<Integer> validSlots = new ArrayList<>();
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && !stack.is(com.playermarket.item.MarketAccessCardItem.MARKET_ACCESS_CARD)) {
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
                long totalPayout = unitPrice * stack.getCount();

                drawSprite(graphics, "card", left, cy, width, itemH, 0xFF121622);

                drawClampedText(graphics, "§f" + stack.getCount() + "x §e" + stack.getHoverName().getString(), left + 8, cy + 6, width / 2, 0xFFFFFFFF, false);
                drawClampedText(graphics, "§7Цена: §a" + unitPrice + " CR/шт §8| Итого: §a+" + totalPayout + " CR", left + 8, cy + 18, width / 2, 0xFF94A3B8, false);

                int btnW = 76;
                int btnH = 18;
                int btnX1 = left + width - (btnW * 2) - 10;
                int btnX2 = left + width - btnW - 6;
                int btnY = cy + 8;

                drawSmallButton(graphics, "btn_green", btnX1, btnY, btnW, btnH, "Слот (+" + totalPayout + ")", mouseX, mouseY, () -> {
                    sendMarketAction("SELL_INVENTORY_SLOT", "", "", slot, 0.0);
                });

                drawSmallButton(graphics, "btn_amber", btnX2, btnY, btnW, btnH, "Оптом всё", mouseX, mouseY, () -> {
                    sendMarketAction("SELL_ALL_MATCHING_ITEM", "", "", slot, 0.0);
                });
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

                boolean canAfford = account.getCrBalance() >= offer.buyCostCr();

                drawSprite(graphics, "card", left, cy, width, itemH, 0xFF121622);

                drawClampedText(graphics, "§f" + offer.bundleSize() + "x §b" + offer.displayName(), left + 8, cy + 6, width / 2, 0xFFFFFFFF, false);
                drawClampedText(graphics, "§7Стоимость партии: §e" + offer.buyCostCr() + " CR", left + 8, cy + 18, width / 2, 0xFF94A3B8, false);

                int btnW = 100;
                int btnH = 18;
                int btnX = left + width - btnW - 8;
                int btnY = cy + 8;

                drawSmallButton(graphics, canAfford ? "btn_blue" : "btn_gray", btnX, btnY, btnW, btnH, "Купить (-" + offer.buyCostCr() + ")", mouseX, mouseY, () -> {
                    if (canAfford) sendMarketAction("BUY_CATALOG_RESOURCE", offer.itemId(), "", 0, 0.0);
                });
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
        int contentTotalH = 410; // Total height of all portfolio cards
        maxScroll = Math.max(0, contentTotalH - height);

        setScissor(graphics, left, top, left + width, top + height);

        int cy = top - scrollOffset;

        // Card 1: Executive Telemetry Overview
        drawSprite(graphics, "hud_bg", left, cy, width, 54, 0xFF111724);
        drawClampedText(graphics, "§a§lФИНАНСОВЫЙ СТАТУС ПЕРСОНАЖА", left + 8, cy + 6, width / 2, 0xFF10B981, false);
        drawClampedText(graphics, "§b§lПРИОБРЕТЁННЫЕ АПГРЕЙДЫ", left + width / 2 + 8, cy + 6, width / 2 - 16, 0xFF38BDF8, false);

        drawClampedText(graphics, "• Баланс счёта: §e" + formatNumber(account.getCrBalance()) + " CR", left + 8, cy + 20, width / 2 - 16, 0xFFCBD5E1, false);
        drawClampedText(graphics, "• VIP Клиент: §bУр. " + account.getUpgradeLevel(UpgradeType.VIP_CLIENT), left + width / 2 + 8, cy + 20, width / 2 - 16, 0xFFCBD5E1, false);

        drawClampedText(graphics, "• Сделок закрыто: §f" + account.getTotalDealsCount() + " шт. §8| Алмазов: §b" + diamonds + " ♦", left + 8, cy + 34, width / 2 - 16, 0xFFCBD5E1, false);
        drawClampedText(graphics, "• Премиум Аккаунт: §bУр. " + account.getUpgradeLevel(UpgradeType.PREMIUM_ACCOUNT) + " §8| Страховка: §bУр. " + account.getUpgradeLevel(UpgradeType.MARKET_INSURANCE), left + width / 2 + 8, cy + 34, width / 2 - 16, 0xFFCBD5E1, false);

        cy += 60;

        // Card 2: Legal & Sanctions Status (Wealth Tax & Penalties)
        drawSprite(graphics, "card", left, cy, width, 58, 0xFF141926);
        drawClampedText(graphics, "§c§lЮРИДИЧЕСКИЙ СТАТУС, НАЛОГИ И САНКЦИИ", left + 8, cy + 6, width - 16, 0xFFEF4444, false);
        long wealthTaxEst = (long) (account.getCrBalance() * 0.05);
        drawClampedText(graphics, "• Налог на капитал: §c5% от баланса CR §7при срыве дедлайна", left + 8, cy + 20, width - 16, 0xFFCBD5E1, false);
        drawClampedText(graphics, "• Оценка штрафа: §e200-350 CR + налог (~" + formatNumber(wealthTaxEst) + " CR) §7| Дебаффы: Слабость/Замедл. 30с", left + 8, cy + 32, width - 16, 0xFFCBD5E1, false);
        long leaseYield = account.calculateLeasePassiveInterestPerMinuteCr();
        drawClampedText(graphics, "• Доход по вкладам: §a+" + leaseYield + " CR/мин §8| Возврат характеристик без затрат алмазов", left + 8, cy + 44, width - 16, 0xFF34D399, false);

        cy += 64;

        // Card 3: Active Leases (Deposits) with Early Cancellation
        int leaseCardH = account.getActiveLeases().isEmpty() ? 52 : (26 + account.getActiveLeases().size() * 24);
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
                long cancelCost = Math.max(50L, Math.round(lease.payoutCr() * 1.25));
                boolean canAfford = account.getCrBalance() >= cancelCost;

                drawClampedText(graphics, String.format(Locale.ROOT, "• §f%s: §c-%.0f%% §8(Осталось: §d%.1f дн.§8)", aName, lease.fraction() * 100.0, daysLeft),
                        left + 8, lRowY + 4, width - 160, 0xFFCBD5E1, false);

                int cBtnW = 140;
                int cBtnH = 18;
                int cBtnX = left + width - cBtnW - 8;
                drawSmallButton(graphics, canAfford ? "btn_amber" : "btn_gray", cBtnX, lRowY, cBtnW, cBtnH,
                        "✕ Снять (-" + formatNumber(cancelCost) + " CR)", mouseX, mouseY, () -> {
                            if (canAfford) sendMarketAction("CANCEL_LEASE", lease.id(), "", 0, 0.0);
                        });
            }
        }

        cy += leaseCardH + 6;

        // Card 4: Active Dividends in 2-column Grid
        drawSprite(graphics, "card", left, cy, width, 180, 0xFF0D121B);
        drawClampedText(graphics, "§d§lВЫБРАННЫЕ ДИВИДЕНДЫ И ПАССИВНЫЕ ДОХОДЫ:", left + 8, cy + 6, width - 16, 0xFFC084FC, false);

        Map<String, String> divs = account.getSelectedDividends();
        if (divs.isEmpty()) {
            drawClampedText(graphics, "§7Вы пока не выбрали ни одного дивиденда (продайте стат в разделе «ПРОДАЖА»).", left + 8, cy + 24, width - 16, 0xFF64748B, false);
        } else {
            int dColW = (width - 24) / 2;
            int idx = 0;
            for (Map.Entry<String, String> entry : divs.entrySet()) {
                Optional<AssetType> assetOpt = AssetType.byId(entry.getKey());
                if (assetOpt.isEmpty()) continue;
                AssetType a = assetOpt.get();
                if (account.getTotalSoldFraction(a) <= 0.001) continue;

                int col = idx % 2;
                int row = idx / 2;
                int dx = left + 8 + col * (dColW + 8);
                int dy = cy + 22 + row * 13;

                String divSummary = entry.getValue().equals(a.getDividendA().id()) ? a.getDividendA().label() : a.getDividendB().label();
                drawClampedText(graphics, "• §f" + a.getShortName() + ": §a" + divSummary, dx, dy, dColW, 0xFFCBD5E1, false);
                idx++;
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

            drawClampedText(graphics, "§e" + rec.category() + " §8| §f" + rec.title(), left + 8, cy + 4, width - 130, 0xFFFFFFFF, false);
            drawClampedText(graphics, "§8" + rec.detail(), left + 8, cy + 14, width - 130, 0xFF64748B, false);

            int dw = font.width(deltaStr);
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

        long nextTick = ClientMarketState.getNextEventSwitchTick();
        long currentTick = (minecraft != null && minecraft.level != null) ? minecraft.level.getGameTime() : 0;
        long remainingTicks = Math.max(0, nextTick - currentTick);
        String timerStr;
        if (nextTick > 0 && remainingTicks > 0) {
            double mins = remainingTicks / 1200.0;
            timerStr = String.format(java.util.Locale.US, "§8[Смена цикла: §e~%.1f мин.§8]", mins);
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

        // 2. Category Filter Pills Row
        int pillY = top + heroH + 4;
        int pillH = 16;
        int pillW = 76;
        int px = left;

        drawPill(graphics, px, pillY, pillW, pillH, "ВСЕ", selectedNewsCategory.equals("ALL"), mouseX, mouseY, () -> {
            selectedNewsCategory = "ALL";
            scrollOffset = 0;
        });
        px += pillW + 4;
        drawPill(graphics, px, pillY, pillW, pillH, "⚡ СРОЧНО", selectedNewsCategory.equalsIgnoreCase("BREAKING"), mouseX, mouseY, () -> {
            selectedNewsCategory = "BREAKING";
            scrollOffset = 0;
        });
        px += pillW + 4;
        drawPill(graphics, px, pillY, pillW, pillH, "🏢 КОРП", selectedNewsCategory.equalsIgnoreCase("CORP"), mouseX, mouseY, () -> {
            selectedNewsCategory = "CORP";
            scrollOffset = 0;
        });
        px += pillW + 4;
        drawPill(graphics, px, pillY, pillW, pillH, "☣ ЧЁРНЫЙ", selectedNewsCategory.equalsIgnoreCase("DARK"), mouseX, mouseY, () -> {
            selectedNewsCategory = "DARK";
            scrollOffset = 0;
        });
        px += pillW + 4;
        drawPill(graphics, px, pillY, pillW, pillH, "📚 ГАЙДЫ", selectedNewsCategory.equalsIgnoreCase("GUIDE"), mouseX, mouseY, () -> {
            selectedNewsCategory = "GUIDE";
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

        int cardH = 46;
        int spacing = 50;
        int contentTotalH = filteredArticles.size() * spacing;
        maxScroll = Math.max(0, contentTotalH - listHeight);

        setScissor(graphics, left, listTop, left + width, listTop + listHeight);

        for (int i = 0; i < filteredArticles.size(); i++) {
            NewsArticle article = filteredArticles.get(i);
            int cy = listTop + (i * spacing) - scrollOffset;
            if (cy + cardH < listTop || cy > listTop + listHeight) continue;

            boolean hovered = (mouseX >= left && mouseX <= left + width && mouseY >= cy && mouseY <= cy + cardH);
            drawSprite(graphics, hovered ? "card_hover" : "card", left, cy, width, cardH, hovered ? 0xFF1E2838 : 0xFF121622);

            drawClampedText(graphics, "§8[" + article.category() + "] §f" + article.title(), left + 8, cy + 6, width - 110, article.categoryColor(), true);
            drawClampedText(graphics, "§7" + article.teaser(), left + 8, cy + 19, width - 110, 0xFFCBD5E1, false);
            drawClampedText(graphics, "§8" + article.date() + " | Нажмите для открытия полного отчёта", left + 8, cy + 31, width - 110, 0xFF64748B, false);

            int readBtnW = 92;
            int readBtnH = 18;
            int readBtnX = left + width - readBtnW - 8;
            int readBtnY = cy + 14;
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
        boolean hovered = (selectedNewsArticle == null) && (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h);
        drawSprite(graphics, hovered ? "card_hover" : "card", x, y, w, h, hovered ? 0xFF1E2838 : 0xFF121622);
    }

    private void drawButton(GuiGraphicsExtractor graphics, String baseSprite, int x, int y, int w, int h, String text,
                            int mouseX, int mouseY, Runnable onClick) {
        boolean hovered = (selectedNewsArticle == null) && (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h);
        String sprite = hovered ? (baseSprite + "_hover") : baseSprite;
        drawSprite(graphics, sprite, x, y, w, h, hovered ? 0xFF2A4365 : 0xFF1A2B42);
        drawClampedCenteredText(graphics, text, x + (w / 2), y + (h - 8) / 2, w - 8, hovered ? 0xFFFFFFFF : 0xFFE2E8F0, false);
        registerClick(x, y, x + w, y + h, onClick, null);
    }

    private void drawSmallButton(GuiGraphicsExtractor graphics, String baseSprite, int x, int y, int w, int h, String text,
                                 int mouseX, int mouseY, Runnable onClick, String tooltip) {
        boolean hovered = (selectedNewsArticle == null) && (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h);
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
        boolean hovered = (selectedNewsArticle == null) && (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h);
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
        if (selectedNewsArticle != null) {
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

    private void sendMarketAction(String action, String targetId, String secondaryId, int intValue, double doubleValue) {
        ClientPlayNetworking.send(new MarketNetworking.MarketActionPayload(action, targetId, secondaryId, intValue, doubleValue));
    }

    private void playClickSound() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
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
