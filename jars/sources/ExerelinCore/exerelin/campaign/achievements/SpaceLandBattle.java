package exerelin.campaign.achievements;

import com.fs.starfarer.api.Global;
import exerelin.campaign.intel.groundbattle.GroundBattleCampaignListener;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import org.magiclib.achievements.MagicAchievement;

public class SpaceLandBattle extends MagicAchievement implements GroundBattleCampaignListener {

    @Override
    public void onSaveGameLoaded(boolean isComplete) {
        if (isComplete) return;
        Global.getSector().getListenerManager().addListener(this, true);

    }

    @Override
    public void reportBattleStarted(GroundBattleIntel battle) {}

    @Override
    public void reportPlayerJoinedBattle(GroundBattleIntel battle) {}

    @Override
    public void reportBattleBeforeTurn(GroundBattleIntel battle, int turn) {}

    @Override
    public void reportBattleAfterTurn(GroundBattleIntel battle, int turn) {}

    @Override
    public void reportBattleEnded(GroundBattleIntel battle) {
        if (battle.isPlayerAttacker() == Boolean.TRUE && battle.getOutcome() == GroundBattleIntel.BattleOutcome.ATTACKER_VICTORY) {
            completeAchievement();
        }
    }
}
