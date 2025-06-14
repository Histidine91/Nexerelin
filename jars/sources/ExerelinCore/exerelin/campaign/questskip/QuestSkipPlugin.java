package exerelin.campaign.questskip;

/**
 * Plugins can be used by both quest chains and individual quests. They're stored as static singletons, so don't store any sector-specific variables in them.
 */
public interface QuestSkipPlugin {

    void init();
    void onEnabled();
    void onDisabled();

    void onNewGame();
    void onNewGameAfterProcGen();
    void onNewGameAfterEconomyLoad();
    void onNewGameAfterTimePass();

    boolean shouldShow();
    /**
     * Use this version to control hiding of individual quests from a single chain plugin.
     * @param entry
     * @return
     */
    boolean shouldShow(QuestSkipEntry entry);

    void setQuest(QuestSkipEntry quest);
    QuestSkipEntry getQuest();
    void setQuestChain(QuestChainSkipEntry chain);
    QuestChainSkipEntry getQuestChain();

    void applyMemKeys();
}
