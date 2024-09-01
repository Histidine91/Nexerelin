package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

public abstract class BaseQuestSkipPlugin implements QuestSkipPlugin {

    protected QuestSkipEntry quest;
    protected QuestChainSkipEntry chain;

    @Override
    public void init() {}

    @Override
    public void onEnabled() {}

    @Override
    public void onDisabled() {}

    @Override
    public void onNewGame() {}

    @Override
    public void onNewGameAfterProcGen() {}

    @Override
    public void onNewGameAfterEconomyLoad() {}

    @Override
    public void onNewGameAfterTimePass() {}

    @Override
    public boolean shouldShow() {
        return true;
    }

    @Override
    public void setQuest(QuestSkipEntry quest) {
        this.quest = quest;
    }

    @Override
    public QuestSkipEntry getQuest() {
        return quest;
    }

    @Override
    public void setQuestChain(QuestChainSkipEntry chain) {
        this.chain = chain;
    }

    @Override
    public QuestChainSkipEntry getQuestChain() {
        return chain;
    }

    protected void makeNonStoryCritical(String marketId, String reason) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null) return;
        Misc.makeNonStoryCritical(market, reason);
    }
}
