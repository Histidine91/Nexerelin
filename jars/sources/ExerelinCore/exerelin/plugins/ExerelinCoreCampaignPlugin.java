package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.util.Misc;

import exerelin.campaign.AllianceManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.MiningHelperLegacy;
import exerelin.campaign.NexFleetInteractionDialogPluginImpl;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.combat.SSP_BattleCreationPluginImpl;

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
		// if we do have SI, let it handle mining instead
		if (!ExerelinModPlugin.HAVE_STELLAR_INDUSTRIALIST)
		{
			canMine = MiningHelperLegacy.canMine(entity);
			memory.set("$nex_canMine", canMine, 0);
		}
		
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
		if (memory.contains("$nex_recentlyRaided"))
		{
			float expire = memory.getExpire("$nex_recentlyRaided");
			String days = String.format("%.1f", expire);
			memory.set("$nex_raidCooldownStr", days, 0);
		}
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
	
	@Override
    public PluginPick<BattleCreationPlugin> pickBattleCreationPlugin(SectorEntityToken opponent) {
        if (opponent instanceof CampaignFleetAPI) {
            return new PluginPick<BattleCreationPlugin>(new SSP_BattleCreationPluginImpl(), PickPriority.MOD_GENERAL);
        }
        return null;
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        if (interactionTarget instanceof CampaignFleetAPI) {
			
			if (interactionTarget.getMemoryWithoutUpdate().getBoolean("$nex_defstation"))
			{
				CampaignFleetAPI fleet = (CampaignFleetAPI)interactionTarget;
				if (fleet.getBattle() == null)
					return new PluginPick<InteractionDialogPlugin>(new RuleBasedInteractionDialogPluginImpl(), PickPriority.MOD_GENERAL);
			}
			
            return new PluginPick<InteractionDialogPlugin>(new NexFleetInteractionDialogPluginImpl(), PickPriority.MOD_GENERAL);
        }
		if (!ExerelinModPlugin.HAVE_STELLAR_INDUSTRIALIST && interactionTarget instanceof AsteroidAPI) {
			return new PluginPick<InteractionDialogPlugin>(new RuleBasedInteractionDialogPluginImpl(), PickPriority.MOD_GENERAL);
		}
        return null;
    }
}