package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;

import exerelin.campaign.AllianceManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.MiningHelperLegacy;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.fleets.ResponseFleetManager;
import org.histidine.industry.scripts.MiningHelper;

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
		return true;
	}

	@Override
	public void updateEntityFacts(SectorEntityToken entity, MemoryAPI memory) {
		super.updateEntityFacts(entity, memory);
		
		boolean canMine;
		if (ExerelinModPlugin.HAVE_STELLAR_INDUSTRIALIST)
			canMine = MiningHelper.canMine(entity);
		else
			canMine = MiningHelperLegacy.canMine(entity);
		memory.set("$canMine", canMine, 0);
		
		if (entity instanceof AsteroidAPI)
		{
			memory.set("$isAsteroid", true, 0);
		}
	}
	
	@Override
	public void updatePlayerFacts(MemoryAPI memory) {
		String associatedFactionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI associatedFaction = Global.getSector().getFaction(associatedFactionId);
		memory.set("$faction", associatedFaction, 0);
		memory.set("$factionId", associatedFactionId, 0);
		memory.set("$theFaction", associatedFaction.getDisplayNameWithArticle(), 0);

		Alliance associatedAlliance = AllianceManager.getFactionAlliance(associatedFactionId);
		
		if (associatedAlliance != null) {
			AllianceManager.setMemoryKeys(memory, associatedAlliance);
		}
		else
		{
			memory.set("$isInAlliance", false, 0);
		}
	}
	
	@Override
	public void updateMarketFacts(MarketAPI market, MemoryAPI memory) {
		memory.set("$reserveSize", ResponseFleetManager.getReserveSize(market), 0);
		memory.set("$alertLevel", CovertOpsManager.getAlertLevel(market), 0);
	}
	
	@Override
	public void updateFactionFacts(FactionAPI faction, MemoryAPI memory) {
		memory.set("$theFaction", faction.getDisplayNameWithArticle(), 0);
		memory.set("$warWeariness", DiplomacyManager.getWarWeariness(faction.getId()), 0);
		memory.set("$numWars", DiplomacyManager.getFactionsAtWarWithFaction(faction, false, false, false).size(), 0);

		Alliance associatedAlliance = AllianceManager.getFactionAlliance(faction.getId());
		if (associatedAlliance != null) {
			AllianceManager.setMemoryKeys(memory, associatedAlliance);
		}
		else
		{
			memory.set("$isInAlliance", false, 0);
		}
	}
}