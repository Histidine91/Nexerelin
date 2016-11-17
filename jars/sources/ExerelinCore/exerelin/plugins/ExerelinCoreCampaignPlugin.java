package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

import exerelin.campaign.AllianceManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.MiningHelper;
import exerelin.world.ResponseFleetManager;

@SuppressWarnings("unchecked")
public class ExerelinCoreCampaignPlugin extends CoreCampaignPluginImpl {

	@Override
	public String getId()
	{
		return "ExerelinCoreCampaignPlugin";
	}
	
	@Override
	public boolean isTransient()
	{
		return false;
	}
		
	@Override
	public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
		if (MiningHelper.canMine(interactionTarget)) {
			return new PluginPick<InteractionDialogPlugin>(new RuleBasedInteractionDialogPluginImpl(), PickPriority.MOD_GENERAL);
		}
		
		return super.pickInteractionDialogPlugin(interactionTarget);
	}

	@Override
	public void updateEntityFacts(SectorEntityToken entity, MemoryAPI memory) {
		super.updateEntityFacts(entity, memory);
		boolean canMine = MiningHelper.canMine(entity);
		memory.set("$canMine", canMine, 0);
		
		if (entity instanceof AsteroidAPI)
		{
			memory.set("$isAsteroid", true, 0);
		}
	}
	
	@Override
	public void updatePlayerFacts(MemoryAPI memory) {
		super.updatePlayerFacts(memory);
		String associatedFactionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI associatedFaction = Global.getSector().getFaction(associatedFactionId);
		memory.set("$faction", associatedFaction, 0);
		memory.set("$factionId", associatedFactionId, 0);

		AllianceManager.Alliance associatedAlliance = AllianceManager.getFactionAlliance(associatedFactionId);
		memory.set("$isInAlliance", (associatedAlliance != null), 0);
		if (associatedAlliance != null) {
			memory.set("$allianceId", associatedAlliance.name, 0);
		}
	}
	
	@Override
	public void updateMarketFacts(MarketAPI market, MemoryAPI memory) {
		super.updateMarketFacts(market, memory);
		memory.set("$reserveSize", ResponseFleetManager.getReserveSize(market), 0);
		memory.set("$alertLevel", CovertOpsManager.getAlertLevel(market), 0);
	}
	
	@Override
	public void updateFactionFacts(FactionAPI faction, MemoryAPI memory) {
		super.updateFactionFacts(faction, memory);
		memory.set("$warWeariness", DiplomacyManager.getWarWeariness(faction.getId()), 0);
		memory.set("$numWars", DiplomacyManager.getFactionsAtWarWithFaction(faction, false, false, false).size(), 0);

		AllianceManager.Alliance associatedAlliance = AllianceManager.getFactionAlliance(faction.getId());
		memory.set("$isInAlliance", (associatedAlliance != null), 0);
		if (associatedAlliance != null) {
			memory.set("$allianceId", associatedAlliance.name, 0);
		}
	}
}