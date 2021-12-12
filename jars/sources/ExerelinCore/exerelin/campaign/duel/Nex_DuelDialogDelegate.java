package exerelin.campaign.duel;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.eventide.DuelDialogDelegate;
import com.fs.starfarer.api.impl.campaign.eventide.DuelPanel;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import java.util.Map;

public class Nex_DuelDialogDelegate extends DuelDialogDelegate {
	
	public Nex_DuelDialogDelegate(@Deprecated String musicId, DuelPanel duelPanel, InteractionDialogAPI dialog,
							  Map<String, MemoryAPI> memoryMap, boolean tutorialMode) {
		super(musicId, duelPanel, dialog, memoryMap, tutorialMode);
	}
	
	public void reportDismissed(int option) {
		if (memoryMap != null) { // null when called from the test dialog
			if (!tutorialMode) {
				if (duelPanel.getPlayer().health > 0) {
					memoryMap.get(MemKeys.LOCAL).set("$nex_playerWonDuel", true, 0);
				} else {
					memoryMap.get(MemKeys.LOCAL).set("$nex_playerLostDuel", true, 0);
				}
				FireBest.fire(null, dialog, memoryMap, "Nex_FencingDuelFinished");
			} else {
				FireBest.fire(null, dialog, memoryMap, "Nex_FencingTutorialFinished");
			}
		}
	}
}
