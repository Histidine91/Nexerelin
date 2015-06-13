package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinUtils;
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
		if (!ExerelinUtils.isSSPInstalled()) return super.pickInteractionDialogPlugin(interactionTarget);
		return null;
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