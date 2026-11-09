package com.playermarket.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

public class StoryProgress {

    public static final Codec<StoryProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("currentStage", 1).forGetter(StoryProgress::getCurrentStage),
            Codec.STRING.optionalFieldOf("finalChoice", "NONE").forGetter(StoryProgress::getFinalChoice),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("completedTasks", Collections.emptyMap()).forGetter(StoryProgress::getCompletedTasks),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("taskProgress", Collections.emptyMap()).forGetter(StoryProgress::getTaskProgress),
            Codec.STRING.listOf().optionalFieldOf("claimedTaskRewards", Collections.emptyList()).forGetter(StoryProgress::getClaimedTaskRewards),
            Codec.STRING.listOf().optionalFieldOf("completedSideQuests", Collections.emptyList()).forGetter(StoryProgress::getCompletedSideQuests),
            Codec.STRING.listOf().optionalFieldOf("claimedSideQuestRewards", Collections.emptyList()).forGetter(StoryProgress::getClaimedSideQuestRewards),
            MailMessage.CODEC.listOf().optionalFieldOf("mailbox", Collections.emptyList()).forGetter(StoryProgress::getMailbox),
            Codec.STRING.listOf().optionalFieldOf("triggeredEvents", Collections.emptyList()).forGetter(StoryProgress::getTriggeredEvents),
            Codec.LONG.optionalFieldOf("lastEventTick", 0L).forGetter(StoryProgress::getLastEventTick)
    ).apply(instance, StoryProgress::new));

    private int currentStage = 1;
    private String finalChoice = "NONE";
    private final Map<String, Boolean> completedTasks = new LinkedHashMap<>();
    private final Map<String, Long> taskProgress = new LinkedHashMap<>();
    private final Set<String> claimedTaskRewards = new LinkedHashSet<>();
    private final Set<String> completedSideQuests = new LinkedHashSet<>();
    private final Set<String> claimedSideQuestRewards = new LinkedHashSet<>();
    private final List<MailMessage> mailbox = new ArrayList<>();
    private final Set<String> triggeredEvents = new LinkedHashSet<>();
    private long lastEventTick = 0L;

    public StoryProgress() {}

    public StoryProgress(
            int currentStage,
            String finalChoice,
            Map<String, Boolean> completedTasks,
            Map<String, Long> taskProgress,
            List<String> claimedTaskRewards,
            List<String> completedSideQuests,
            List<String> claimedSideQuestRewards,
            List<MailMessage> mailbox,
            List<String> triggeredEvents,
            long lastEventTick
    ) {
        this.currentStage = Math.clamp(currentStage, 1, 6);
        this.finalChoice = finalChoice != null ? finalChoice : "NONE";
        if (completedTasks != null) this.completedTasks.putAll(completedTasks);
        if (taskProgress != null) this.taskProgress.putAll(taskProgress);
        if (claimedTaskRewards != null) this.claimedTaskRewards.addAll(claimedTaskRewards);
        if (completedSideQuests != null) this.completedSideQuests.addAll(completedSideQuests);
        if (claimedSideQuestRewards != null) this.claimedSideQuestRewards.addAll(claimedSideQuestRewards);
        if (mailbox != null) this.mailbox.addAll(mailbox);
        if (triggeredEvents != null) this.triggeredEvents.addAll(triggeredEvents);
        this.lastEventTick = lastEventTick;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(int currentStage) {
        this.currentStage = Math.clamp(currentStage, 1, 6);
    }

    public String getFinalChoice() {
        return finalChoice;
    }

    public void setFinalChoice(String finalChoice) {
        this.finalChoice = finalChoice != null ? finalChoice : "NONE";
    }

    public Map<String, Boolean> getCompletedTasks() {
        return completedTasks;
    }

    public Map<String, Long> getTaskProgress() {
        return taskProgress;
    }

    public List<String> getClaimedTaskRewards() {
        return new ArrayList<>(claimedTaskRewards);
    }

    public List<String> getCompletedSideQuests() {
        return new ArrayList<>(completedSideQuests);
    }

    public List<String> getClaimedSideQuestRewards() {
        return new ArrayList<>(claimedSideQuestRewards);
    }

    public List<MailMessage> getMailbox() {
        return mailbox;
    }

    public List<String> getTriggeredEvents() {
        return new ArrayList<>(triggeredEvents);
    }

    public long getLastEventTick() {
        return lastEventTick;
    }

    public void setLastEventTick(long lastEventTick) {
        this.lastEventTick = lastEventTick;
    }

    public boolean isTaskCompleted(String taskId) {
        return completedTasks.getOrDefault(taskId, false);
    }

    public boolean completeTask(String taskId) {
        if (!isTaskCompleted(taskId)) {
            completedTasks.put(taskId, true);
            return true;
        }
        return false;
    }

    public boolean isTaskRewardClaimed(String taskId) {
        return claimedTaskRewards.contains(taskId);
    }

    public boolean claimTaskReward(String taskId) {
        if (isTaskCompleted(taskId) && !isTaskRewardClaimed(taskId)) {
            claimedTaskRewards.add(taskId);
            return true;
        }
        return false;
    }

    public boolean isSideQuestCompleted(String sqId) {
        return completedSideQuests.contains(sqId);
    }

    public boolean completeSideQuest(String sqId) {
        return completedSideQuests.add(sqId);
    }

    public boolean isSideQuestRewardClaimed(String sqId) {
        return claimedSideQuestRewards.contains(sqId);
    }

    public boolean claimSideQuestReward(String sqId) {
        if (isSideQuestCompleted(sqId) && !isSideQuestRewardClaimed(sqId)) {
            claimedSideQuestRewards.add(sqId);
            return true;
        }
        return false;
    }

    public int getTotalCompletedProgramTasks() {
        long mainTasks = completedTasks.values().stream().filter(Boolean::booleanValue).count();
        return (int) (mainTasks + completedSideQuests.size());
    }

    public void addMail(MailMessage msg) {
        mailbox.addFirst(msg);
    }

    public int getUnreadMailCount() {
        int count = 0;
        for (MailMessage msg : mailbox) {
            if (!msg.read()) count++;
        }
        return count;
    }

    public boolean markMailRead(String messageId) {
        for (int i = 0; i < mailbox.size(); i++) {
            MailMessage msg = mailbox.get(i);
            if (msg.id().equals(messageId)) {
                if (!msg.read()) {
                    mailbox.set(i, msg.withRead(true));
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public MailMessage claimMailReward(String messageId) {
        for (int i = 0; i < mailbox.size(); i++) {
            MailMessage msg = mailbox.get(i);
            if (msg.id().equals(messageId)) {
                if (!msg.rewardClaimed() && !msg.rewardType().equals("NONE")) {
                    MailMessage updated = msg.withRewardClaimed().withRead(true);
                    mailbox.set(i, updated);
                    return msg;
                }
                return null;
            }
        }
        return null;
    }

    public boolean hasTriggeredEvent(String eventId) {
        return triggeredEvents.contains(eventId);
    }

    public void recordTriggeredEvent(String eventId) {
        triggeredEvents.add(eventId);
    }
}
