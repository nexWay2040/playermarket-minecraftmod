package com.playermarket.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MailMessage(
        String id,
        long timestampTick,
        String sender,
        String subject,
        String body,
        boolean read,
        String rewardType,
        long rewardAmount,
        boolean rewardClaimed
) {
    public static final Codec<MailMessage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(MailMessage::id),
            Codec.LONG.fieldOf("timestampTick").forGetter(MailMessage::timestampTick),
            Codec.STRING.fieldOf("sender").forGetter(MailMessage::sender),
            Codec.STRING.fieldOf("subject").forGetter(MailMessage::subject),
            Codec.STRING.fieldOf("body").forGetter(MailMessage::body),
            Codec.BOOL.fieldOf("read").forGetter(MailMessage::read),
            Codec.STRING.fieldOf("rewardType").forGetter(MailMessage::rewardType),
            Codec.LONG.fieldOf("rewardAmount").forGetter(MailMessage::rewardAmount),
            Codec.BOOL.fieldOf("rewardClaimed").forGetter(MailMessage::rewardClaimed)
    ).apply(instance, MailMessage::new));

    public MailMessage withRead(boolean read) {
        return new MailMessage(id, timestampTick, sender, subject, body, read, rewardType, rewardAmount, rewardClaimed);
    }

    public MailMessage withRewardClaimed() {
        return new MailMessage(id, timestampTick, sender, subject, body, read, rewardType, rewardAmount, true);
    }
}
