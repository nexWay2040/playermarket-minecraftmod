package com.playermarket.story;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Хелпер безопасных экранных баннеров (Title / Subtitle + подробности в чат).
 * <p>
 * Исключает вылезание длинных заголовков за границы экрана:
 * Title строго <= 14 видимых символов, Subtitle <= 28 видимых символов (без учёта §-кодов),
 * а полное описание всегда уходит в чат.
 */
public final class ScreenAlertHelper {

    public static final int MAX_TITLE_VISIBLE_CHARS = 14;
    public static final int MAX_SUBTITLE_VISIBLE_CHARS = 28;

    private static final int DEFAULT_FADE_IN_TICKS = 8;
    private static final int DEFAULT_STAY_TICKS = 50;
    private static final int DEFAULT_FADE_OUT_TICKS = 12;

    private ScreenAlertHelper() {
    }

    /**
     * Отправляет безопасный баннер (Title <= 14 символов, Subtitle <= 28 символов с сохранением §-кодов)
     * и выводит полное сообщение в чат игрока.
     */
    public static void sendSafeBanner(ServerPlayer player, String rawTitle, String rawSubtitle, String chatMessage) {
        if (player == null) {
            return;
        }

        String safeTitle = clampPreservingFormatting(sanitizeSingleLine(rawTitle), MAX_TITLE_VISIBLE_CHARS);
        String safeSubtitle = clampPreservingFormatting(sanitizeSingleLine(rawSubtitle), MAX_SUBTITLE_VISIBLE_CHARS);

        player.connection.send(new ClientboundSetTitlesAnimationPacket(
                DEFAULT_FADE_IN_TICKS,
                DEFAULT_STAY_TICKS,
                DEFAULT_FADE_OUT_TICKS
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                safeSubtitle.isEmpty() ? Component.empty() : Component.literal(safeSubtitle)
        ));
        player.connection.send(new ClientboundSetTitleTextPacket(
                safeTitle.isEmpty() ? Component.empty() : Component.literal(safeTitle)
        ));

        if (chatMessage != null && !chatMessage.isBlank()) {
            player.sendSystemMessage(Component.literal(chatMessage));
        }
    }

    /**
     * Безопасный баннер для рыночных событий:
     * Title строго 1–2 слова, Subtitle — короткий заголовок события (<= 28 символов),
     * полное описание отправляется только в чат.
     */
    public static void sendMarketEventBanner(ServerPlayer player, boolean isCrash, String headline, String description) {
        if (player == null) {
            return;
        }

        String title = isCrash ? "§c§l📉 ОБВАЛ" : "§a§l📈 БУМ БИРЖИ";
        String colorPrefix = isCrash ? "§c" : "§a";
        String cleanHeadline = sanitizeSingleLine(headline);
        String subtitle = cleanHeadline.isEmpty()
                ? ""
                : (startsWithFormatting(cleanHeadline) ? cleanHeadline : colorPrefix + cleanHeadline);

        String prefix = isCrash ? "§c§l[БИРЖА // ОБВАЛ] §r" : "§a§l[БИРЖА // БУМ] §r";
        String chatLine;
        if (cleanHeadline.isEmpty() && (description == null || description.isBlank())) {
            chatLine = null;
        } else if (description == null || description.isBlank()) {
            chatLine = prefix + colorPrefix + cleanHeadline;
        } else if (cleanHeadline.isEmpty()) {
            chatLine = prefix + "§7" + description.trim();
        } else {
            chatLine = prefix + colorPrefix + cleanHeadline + " §8— §7" + description.trim();
        }

        sendSafeBanner(player, title, subtitle, chatLine);
    }

    /**
     * Безопасный баннер для корпоративного хаоса / директив:
     * Title <= 14 символов (короткий тег), Subtitle <= 28 символов (название директивы),
     * подробное описание отправляется только в чат.
     */
    public static void sendChaosBanner(ServerPlayer player, String shortTag, String directiveTitle, String fullDescription) {
        if (player == null) {
            return;
        }

        String cleanTag = sanitizeSingleLine(shortTag);
        String title;
        if (cleanTag.isEmpty()) {
            title = "§6§l⚡ ИНЦИДЕНТ";
        } else {
            title = startsWithFormatting(cleanTag) ? cleanTag : "§6§l" + cleanTag;
        }

        String cleanDirective = sanitizeSingleLine(directiveTitle);
        String subtitle = cleanDirective.isEmpty()
                ? ""
                : (startsWithFormatting(cleanDirective) ? cleanDirective : "§e" + cleanDirective);

        StringBuilder chat = new StringBuilder();
        chat.append(title);
        if (!cleanDirective.isEmpty()) {
            chat.append(" §8| §e").append(stripFormattingCodes(cleanDirective));
        }
        if (fullDescription != null && !fullDescription.isBlank()) {
            chat.append("\n§7   ↳ ").append(fullDescription.trim());
        }

        sendSafeBanner(player, title, subtitle, chat.toString());
    }

    /**
     * Удаляет все коды форматирования §x и обрезает видимый текст до {@code maxVisibleChars}
     * (добавляя «…» при усечении).
     */
    public static String stripAndClamp(String text, int maxVisibleChars) {
        if (text == null || text.isEmpty() || maxVisibleChars <= 0) {
            return "";
        }
        String plain = stripFormattingCodes(sanitizeSingleLine(text)).trim();
        if (plain.length() <= maxVisibleChars) {
            return plain;
        }
        if (maxVisibleChars == 1) {
            return "…";
        }
        String cut = plain.substring(0, maxVisibleChars - 1).stripTrailing();
        return cut + "…";
    }

    /**
     * Обрезает строку до {@code maxVisibleChars} видимых символов, корректно игнорируя
     * §x-коды при подсчёте длины и сохраняя цветовое форматирование.
     */
    public static String clampPreservingFormatting(String text, int maxVisibleChars) {
        if (text == null || text.isEmpty() || maxVisibleChars <= 0) {
            return "";
        }
        int totalVisible = countVisibleChars(text);
        if (totalVisible <= maxVisibleChars) {
            return text;
        }
        if (maxVisibleChars == 1) {
            return extractLeadingFormatting(text) + "…";
        }

        int targetVisible = maxVisibleChars - 1;
        StringBuilder out = new StringBuilder(text.length());
        int visibleCount = 0;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < len) {
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            if (visibleCount >= targetVisible) {
                break;
            }
            out.append(c);
            visibleCount++;
        }

        // Убираем случайный хвостовой пробел перед многоточием, не затрагивая §-коды
        while (!out.isEmpty() && out.charAt(out.length() - 1) == ' ') {
            out.deleteCharAt(out.length() - 1);
        }
        out.append('…');
        return out.toString();
    }

    public static int countVisibleChars(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < len) {
                i++;
                continue;
            }
            count++;
        }
        return count;
    }

    public static String stripFormattingCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < len) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String extractLeadingFormatting(String text) {
        StringBuilder codes = new StringBuilder();
        int len = text.length();
        for (int i = 0; i + 1 < len; i += 2) {
            if (text.charAt(i) == '§') {
                codes.append('§').append(text.charAt(i + 1));
            } else {
                break;
            }
        }
        return codes.toString();
    }

    private static boolean startsWithFormatting(String text) {
        return text.length() >= 2 && text.charAt(0) == '§';
    }

    private static String sanitizeSingleLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ').replace('\n', ' ').trim();
    }
}