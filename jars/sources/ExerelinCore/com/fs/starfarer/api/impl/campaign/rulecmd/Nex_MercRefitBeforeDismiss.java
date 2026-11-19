package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.merc.MercContractIntel;

import java.util.List;
import java.util.Map;

public class Nex_MercRefitBeforeDismiss extends BaseCommandPlugin implements CoreInteractionListener {

	private Map<String, MemoryAPI> memoryMap;
	private InteractionDialogAPI dialog;

	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		this.dialog = dialog;
		this.memoryMap = memoryMap;

		CoreUITabId tabId = CoreUITabId.REFIT;
		dialog.getOptionPanel().clearOptions();

		CampaignUIAPI.CoreUITradeMode mode = CampaignUIAPI.CoreUITradeMode.OPEN;
		if (params.size() > 0) {
			mode = CampaignUIAPI.CoreUITradeMode.valueOf(params.get(0).getString(memoryMap));
		}

		//memoryMap.get(MemKeys.LOCAL).set("$lastTradeMode", mode.name(), 0);
		dialog.getVisualPanel().showCore(tabId, dialog.getInteractionTarget(), mode, this);

		Misc.stopPlayerFleet();

		return true;
	}

	public void coreUIDismissed() {
		// update player memory - supplies/fuel the player has, etc
		Global.getSector().getCharacterData().getMemory();

		MercContractIntel curr = MercContractIntel.getOngoing();
		if (curr == null) return;
		Nex_MercHire.setMercMemoryKeys(curr, dialog);

		FireBest.fire(null, dialog, memoryMap, "Nex_MercLeavingOptions");
	}
}
