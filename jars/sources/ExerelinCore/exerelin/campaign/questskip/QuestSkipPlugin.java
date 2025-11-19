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

    /**
     * For quest plugins, whether an individual quest should be shown. For chain plugins, whether the whole chain should be shown.
     * @return
     */
    boolean shouldShow();

    /**
     * Whether to hide the specified entry; only called for chain plugins.
     * @param entry
     * @return
     */
    boolean shouldShow(QuestSkipEntry entry);

    boolean shouldEnableByDefault(QuestSkipEntry entry);

    void setQuest(QuestSkipEntry quest);
    QuestSkipEntry getQuest();
    void setQuestChain(QuestChainSkipEntry chain);
    QuestChainSkipEntry getQuestChain();

    void applyMemKeys();
}
