package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import data.scripts.campaign.SSP_CampaignPlugin;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.world.ResponseFleetManager;

@SuppressWarnings("unchecked")
public class ExerelinCoreCampaignPluginWithSSP extends SSP_CampaignPlugin {
	
	@Override
	public String getId()
	{
		return "ExerelinCoreCampaignPlugin";
	}
	
	@Override
	public PluginPick<ReputationActionResponsePlugin> pickReputationActionResponsePlugin(Object action, String factionId) {
		if (action instanceof RepActions || action instanceof RepActionEnvelope) {
			return new PluginPick<ReputationActionResponsePlugin>(
				new ExerelinReputationPluginWithSSP(),
				PickPriority.HIGHEST
			);
		}
		return null;
	}
	
	@Override
	public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
		String factionId = interactionTarget.getFaction().getId();
		if (interactionTarget instanceof CampaignFleetAPI && factionId.equals(PlayerFactionStore.getPlayerFactionId())) {
			return new  PluginPick<InteractionDialogPlugin>(new ExerelinFleetInteractionDialogPluginWithSSP(), PickPriority.MOD_SPECIFIC);
		}
		return super.pickInteractionDialogPlugin(interactionTarget);
	}
	
	@Override
	public void updatePlayerFacts(MemoryAPI memory) {
		super.updatePlayerFacts(memory);
		String associatedFactionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI associatedFaction = Global.getSector().getFaction(associatedFactionId);
		//Global.getLogger(this.getClass()).info("Associated faction is " + associatedFactionId);
		memory.set("$faction", associatedFaction, 0);
		memory.set("$factionId", associatedFactionId, 0);
	}
	
	@Override
	public void updateMarketFacts(MarketAPI market, MemoryAPI memory) {
		super.updateMarketFacts(market, memory);
		memory.set("$reserveSize", ResponseFleetManager.getReserveSize(market), 0);
	}
	
	@Override
	public void updateFactionFacts(FactionAPI faction, MemoryAPI memory) {
		super.updateFactionFacts(faction, memory);
		memory.set("$warWeariness", DiplomacyManager.getWarWeariness(faction.getId()), 0);
		memory.set("$numWars", DiplomacyManager.getFactionsAtWarWithFaction(faction, false).size(), 0);
	}
}