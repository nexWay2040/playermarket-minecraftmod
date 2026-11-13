package com.playermarket.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

public class SharedCampaignData {

    public static final Codec<SharedCampaignData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("campaignAccepted", false).forGetter(SharedCampaignData::isCampaignAccepted),
            Codec.INT.optionalFieldOf("currentStage", 1).forGetter(SharedCampaignData::getCurrentStage),
            Codec.STRING.listOf().optionalFieldOf("completedTasks", List.of()).forGetter(SharedCampaignData::getCompletedTasksList),
            Codec.STRING.listOf().optionalFieldOf("completedSideQuests", List.of()).forGetter(SharedCampaignData::getCompletedSideQuestsList),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("objectiveProgress", Map.of()).forGetter(SharedCampaignData::getObjectiveProgress),
            Codec.STRING.listOf().optionalFieldOf("registeredParticipants", List.of()).forGetter(SharedCampaignData::getParticipantsStringList),
            Codec.STRING.optionalFieldOf("endingState", "NONE").forGetter(SharedCampaignData::getEndingState),
            Codec.LONG.optionalFieldOf("endingStartedTick", 0L).forGetter(SharedCampaignData::getEndingStartedTick),
            Codec.STRING.listOf().optionalFieldOf("impairedPlayers", List.of()).forGetter(SharedCampaignData::getImpairedPlayersStringList),
            Codec.STRING.listOf().optionalFieldOf("disconnectedPlayers", List.of()).forGetter(SharedCampaignData::getDisconnectedPlayersStringList),
            Codec.STRING.listOf().optionalFieldOf("triggeredStoryEvents", List.of()).forGetter(SharedCampaignData::getTriggeredStoryEventsList),
            Codec.LONG.optionalFieldOf("lastStoryEventTick", 0L).forGetter(SharedCampaignData::getLastStoryEventTick),
            Codec.BOOL.optionalFieldOf("endingStarted", false).forGetter(SharedCampaignData::isEndingStarted),
            Codec.BOOL.optionalFieldOf("endingApplied", false).forGetter(SharedCampaignData::isEndingApplied),
            Codec.BOOL.optionalFieldOf("cinematicCompleted", false).forGetter(SharedCampaignData::isCinematicCompleted),
            Codec.BOOL.optionalFieldOf("disconnectSent", false).forGetter(SharedCampaignData::isDisconnectSent)
    ).apply(instance, SharedCampaignData::new));

    private boolean campaignAccepted;
    private int currentStage;
    private final Set<String> completedTasks = new LinkedHashSet<>();
    private final Set<String> completedSideQuests = new LinkedHashSet<>();
    private final Map<String, Integer> objectiveProgress = new LinkedHashMap<>();
    private final Set<UUID> registeredParticipants = new LinkedHashSet<>();
    private String endingState = "NONE";
    private long endingStartedTick = 0L;
    private final Set<UUID> impairedPlayers = new LinkedHashSet<>();
    private final Set<UUID> disconnectedPlayers = new LinkedHashSet<>();
    private final Set<String> triggeredStoryEvents = new LinkedHashSet<>();
    private long lastStoryEventTick = 0L;

    // Independent ending lifecycle state flags (eliminates reconnect loops)
    private boolean endingStarted = false;
    private boolean endingApplied = false;
    private boolean cinematicCompleted = false;
    private boolean disconnectSent = false;

    public SharedCampaignData() {
        this.campaignAccepted = false;
        this.currentStage = 1;
    }

    public SharedCampaignData(
            boolean campaignAccepted,
            int currentStage,
            List<String> completedTasks,
            List<String> completedSideQuests,
            Map<String, Integer> objectiveProgress,
            List<String> registeredParticipants,
            String endingState,
            long endingStartedTick,
            List<String> impairedPlayers,
            List<String> disconnectedPlayers,
            List<String> triggeredStoryEvents,
            long lastStoryEventTick,
            boolean endingStarted,
            boolean endingApplied,
            boolean cinematicCompleted,
            boolean disconnectSent
    ) {
        this.campaignAccepted = campaignAccepted;
        this.currentStage = Math.max(1, currentStage);
        if (completedTasks != null) this.completedTasks.addAll(completedTasks);
        if (completedSideQuests != null) this.completedSideQuests.addAll(completedSideQuests);
        if (objectiveProgress != null) this.objectiveProgress.putAll(objectiveProgress);
        if (registeredParticipants != null) {
            for (String s : registeredParticipants) {
                try { this.registeredParticipants.add(UUID.fromString(s)); } catch (Exception ignored) {}
            }
        }
        this.endingState = endingState != null ? endingState : "NONE";
        this.endingStartedTick = endingStartedTick;
        if (impairedPlayers != null) {
            for (String s : impairedPlayers) {
                try { this.impairedPlayers.add(UUID.fromString(s)); } catch (Exception ignored) {}
            }
        }
        if (disconnectedPlayers != null) {
            for (String s : disconnectedPlayers) {
                try { this.disconnectedPlayers.add(UUID.fromString(s)); } catch (Exception ignored) {}
            }
        }
        if (triggeredStoryEvents != null) this.triggeredStoryEvents.addAll(triggeredStoryEvents);
        this.lastStoryEventTick = lastStoryEventTick;
        this.endingStarted = endingStarted;
        this.endingApplied = endingApplied;
        this.cinematicCompleted = cinematicCompleted;
        this.disconnectSent = disconnectSent;
    }

    public boolean isCampaignAccepted() { return campaignAccepted; }
    public void setCampaignAccepted(boolean accepted) { this.campaignAccepted = accepted; }

    public int getCurrentStage() { return currentStage; }
    public void setCurrentStage(int stage) { this.currentStage = stage; }

    public Set<String> getCompletedTasks() { return completedTasks; }
    public List<String> getCompletedTasksList() { return new ArrayList<>(completedTasks); }

    public boolean isTaskCompleted(String taskId) { return completedTasks.contains(taskId); }
    public boolean completeTask(String taskId) { return completedTasks.add(taskId); }

    public Set<String> getCompletedSideQuests() { return completedSideQuests; }
    public List<String> getCompletedSideQuestsList() { return new ArrayList<>(completedSideQuests); }

    public boolean isSideQuestCompleted(String id) { return completedSideQuests.contains(id); }
    public boolean completeSideQuest(String id) { return completedSideQuests.add(id); }

    public Map<String, Integer> getObjectiveProgress() { return objectiveProgress; }
    public void recordBiome(String biome) {
        if (biome != null && !biome.isEmpty()) {
            addContribution("biome_" + biome, 1);
            int count = (int) objectiveProgress.keySet().stream().filter(k -> k.startsWith("biome_")).count();
            setObjective("visited_biomes_count", count);
        }
    }

    public int getObjective(String key) { return objectiveProgress.getOrDefault(key, 0); }
    public void setObjective(String key, int val) { objectiveProgress.put(key, val); }
    public int addContribution(String key, int delta) {
        int next = objectiveProgress.getOrDefault(key, 0) + delta;
        objectiveProgress.put(key, next);
        return next;
    }

    public Set<UUID> getRegisteredParticipants() { return registeredParticipants; }
    public List<String> getParticipantsStringList() {
        List<String> list = new ArrayList<>();
        for (UUID u : registeredParticipants) list.add(u.toString());
        return list;
    }
    public void registerParticipant(UUID uuid) { registeredParticipants.add(uuid); }
    public boolean isParticipant(UUID uuid) { return registeredParticipants.contains(uuid); }

    public String getEndingState() { return endingState != null ? endingState : "NONE"; }
    public void setEndingState(String endingState) { this.endingState = endingState; }

    public long getEndingStartedTick() { return endingStartedTick; }
    public void setEndingStartedTick(long endingStartedTick) { this.endingStartedTick = endingStartedTick; }

    public Set<UUID> getImpairedPlayers() { return impairedPlayers; }
    public List<String> getImpairedPlayersStringList() {
        List<String> list = new ArrayList<>();
        for (UUID u : impairedPlayers) list.add(u.toString());
        return list;
    }
    public boolean isPlayerImpaired(UUID uuid) { return impairedPlayers.contains(uuid); }
    public void addImpairedPlayer(UUID uuid) { impairedPlayers.add(uuid); }

    public Set<UUID> getDisconnectedPlayers() { return disconnectedPlayers; }
    public List<String> getDisconnectedPlayersStringList() {
        List<String> list = new ArrayList<>();
        for (UUID u : disconnectedPlayers) list.add(u.toString());
        return list;
    }
    public boolean hasBeenDisconnected(UUID uuid) { return disconnectedPlayers.contains(uuid); }
    public void recordDisconnected(UUID uuid) { disconnectedPlayers.add(uuid); }

    public Set<String> getTriggeredStoryEvents() { return triggeredStoryEvents; }
    public List<String> getTriggeredStoryEventsList() { return new ArrayList<>(triggeredStoryEvents); }
    public boolean hasTriggeredStoryEvent(String eventId) { return triggeredStoryEvents.contains(eventId); }
    public void recordStoryEvent(String eventId) { triggeredStoryEvents.add(eventId); }

    public long getLastStoryEventTick() { return lastStoryEventTick; }
    public void setLastStoryEventTick(long tick) { this.lastStoryEventTick = tick; }

    public boolean isEndingStarted() { return endingStarted; }
    public void setEndingStarted(boolean endingStarted) { this.endingStarted = endingStarted; }

    public boolean isEndingApplied() { return endingApplied; }
    public void setEndingApplied(boolean endingApplied) { this.endingApplied = endingApplied; }

    public boolean isCinematicCompleted() { return cinematicCompleted; }
    public void setCinematicCompleted(boolean cinematicCompleted) { this.cinematicCompleted = cinematicCompleted; }

    public boolean isDisconnectSent() { return disconnectSent; }
    public void setDisconnectSent(boolean disconnectSent) { this.disconnectSent = disconnectSent; }

    public int getTotalCompletedProgramTasks() {
        return completedTasks.size() + completedSideQuests.size();
    }

    public void resetAll() {
        this.campaignAccepted = false;
        this.currentStage = 1;
        this.completedTasks.clear();
        this.completedSideQuests.clear();
        this.objectiveProgress.clear();
        this.registeredParticipants.clear();
        this.endingState = "NONE";
        this.endingStartedTick = 0L;
        this.impairedPlayers.clear();
        this.disconnectedPlayers.clear();
        this.triggeredStoryEvents.clear();
        this.lastStoryEventTick = 0L;
        this.endingStarted = false;
        this.endingApplied = false;
        this.cinematicCompleted = false;
        this.disconnectSent = false;
    }
}
