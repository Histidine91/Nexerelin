package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinUtils;
import exerelin.campaign.MiningHelper;
import exerelin.utilities.StringHelper;
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
		String factionId = interactionTarget.getFaction().getId();
		if (interactionTarget instanceof CampaignFleetAPI) 
		{
			if (factionId.equals(PlayerFactionStore.getPlayerFactionId()) || factionId.equals("player_npc"))
				return new PluginPick<InteractionDialogPlugin>(new ExerelinFleetInteractionDialogPlugin(), PickPriority.MOD_SPECIFIC);
		}
		if (MiningHelper.canMine(interactionTarget)) {
			return new PluginPick<InteractionDialogPlugin>(new RuleBasedInteractionDialogPluginImpl(), PickPriority.MOD_GENERAL);
		}
		
		if (!ExerelinUtils.isSSPInstalled()) return super.pickInteractionDialogPlugin(interactionTarget);
		return null;
	}
	

	@Override
	public PluginPick<BattleAutoresolverPlugin> pickBattleAutoresolverPlugin(SectorEntityToken one, SectorEntityToken two) {
		if (!ExerelinUtils.isSSPInstalled()) return super.pickBattleAutoresolverPlugin(one, two);
		return null;
	}
	
	@Override
	public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(Object param, SectorEntityToken interactionTarget) {
		if (!ExerelinUtils.isSSPInstalled()) return super.pickInteractionDialogPlugin(param, interactionTarget);
		return null;
	}

	@Override
	public PluginPick<ReputationActionResponsePlugin> pickReputationActionResponsePlugin(Object action, String factionId) {
		if (!ExerelinUtils.isSSPInstalled()) return super.pickReputationActionResponsePlugin(action, factionId);
		return null;
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
		memory.set("$numWars", DiplomacyManager.getFactionsAtWarWithFaction(faction, false, false).size(), 0);
	}
}