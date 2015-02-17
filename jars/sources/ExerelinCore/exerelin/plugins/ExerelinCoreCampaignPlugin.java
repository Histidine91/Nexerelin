package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.ExerelinReputationPlugin;

@SuppressWarnings("unchecked")
public class ExerelinCoreCampaignPlugin extends CoreCampaignPluginImpl {

	@Override
	public PluginPick<ReputationActionResponsePlugin> pickReputationActionResponsePlugin(Object action, String factionId) {
		if (action instanceof RepActions || action instanceof RepActionEnvelope) {
			return new PluginPick<ReputationActionResponsePlugin>(
				new ExerelinReputationPlugin(),
				PickPriority.MOD_GENERAL
			);
	}
	return null;
	}
	
	@Override
	public void updatePlayerFacts(MemoryAPI memory) {
		super.updatePlayerFacts(memory);
		String associatedFactionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI associatedFaction = Global.getSector().getFaction(associatedFactionId);
		memory.set("$faction", associatedFaction);
	}
}