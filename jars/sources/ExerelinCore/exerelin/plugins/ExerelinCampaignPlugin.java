package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.AbilityAIPlugin;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import exerelin.campaign.*;
import exerelin.campaign.abilities.ai.AlwaysOnTransponderAI;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.battle.NexBattleAutoresolverPlugin;
import exerelin.campaign.battle.NexFleetInteractionDialogPluginImpl;
import exerelin.campaign.colony.AICoreAdminPluginOmega;
import exerelin.campaign.intel.specialforces.SpecialForcesIntel;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFleet;
import lombok.extern.log4j.Log4j;

@SuppressWarnings("unchecked")
@Log4j
public class ExerelinCampaignPlugin extends BaseCampaignPlugin {
	
	public static final String MEM_KEY_BATTLE_PLUGIN = "$nex_battleCreationPlugin";
	
	@Override
	public String getId()
	{
		return "ExerelinCampaignPlugin";
	}
	
	@Override
	public boolean isTransient()
	{
		return true;
	}

	@Override
	public void updateEntityFacts(SectorEntityToken entity, MemoryAPI memory) {
		boolean canMine = MiningHelperLegacy.canMine(entity);
		memory.set("$nex_canMine", canMine, 0);
		
		if (entity instanceof AsteroidAPI)
		{
			memory.set("$isAsteroid", true, 0);
		}
		
		if (entity instanceof CampaignFleetAPI) {
			CampaignFleetAPI fleet = (CampaignFleetAPI)entity;
			String type = NexUtilsFleet.getFleetType(fleet);
			if (SpecialForcesIntel.FLEET_TYPE.equals(type))
			{
				//memory.set("$useVengeanceGreeting", true, 0);
				memory.set("$escalation", 
						RevengeanceManager.getManager().getVengeanceEscalation(fleet.getFaction().getId()), 
						0);
			}
			else if ("vengeanceFleet".equals(type)) {
				memory.set("$useVengeanceGreeting", true, 0);
			}
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
		//memory.set("$reserveSize", ResponseFleetManager.getReserveSize(market), 0);
		memory.set("$alertLevel", CovertOpsManager.getAlertLevel(market), 0);
		if (memory.contains("$nex_recentlyRaided"))
		{
			float expire = memory.getExpire("$nex_recentlyRaided");
			String days = String.format("%.1f", expire);
			memory.set("$nex_raidCooldownStr", days, 0);
		}
		
		// override vanilla behaviour; used for dialog text on docking 
		// (player-owned markets won't have the transponder prompt)
		memory.set("$isPlayerOwned", market.isPlayerOwned() || market.getFaction().isPlayerFaction(), 0);
		memory.set("$nex_isTruePlayerOwned", market.isPlayerOwned(), 0);
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
		/*
		if (opponent instanceof CampaignFleetAPI && NexConfig.useCustomBattleCreationPlugin) {
			return new PluginPick<BattleCreationPlugin>(new SSP_BattleCreationPluginImpl(), PickPriority.MOD_GENERAL);
		}
		*/
		if (opponent.getMemoryWithoutUpdate().contains(MEM_KEY_BATTLE_PLUGIN)) {
			// safety in case the memory has the old way with the whole plugin saved to memory
			Object curr = opponent.getMemoryWithoutUpdate().get(MEM_KEY_BATTLE_PLUGIN);
			if (curr instanceof BattleCreationPlugin) {
				curr = curr.getClass().getName();
				opponent.getMemoryWithoutUpdate().set(MEM_KEY_BATTLE_PLUGIN, curr);
			}
			
			String className = (String)curr;
			BattleCreationPlugin bcp = (BattleCreationPlugin) NexUtils.instantiateClassByName(className);
			return new PluginPick<>(bcp, PickPriority.MOD_SPECIFIC);
		}
		
		return null;
	}
	
	@Override
	public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
		if (interactionTarget instanceof CampaignFleetAPI) {
			return new PluginPick<InteractionDialogPlugin>(new NexFleetInteractionDialogPluginImpl(), PickPriority.MOD_GENERAL);
		}
		if (interactionTarget instanceof AsteroidAPI) {
			return new PluginPick<InteractionDialogPlugin>(new RuleBasedInteractionDialogPluginImpl(), PickPriority.MOD_GENERAL);
		}
		return null;
	}
	
	@Override
	public PluginPick<AbilityAIPlugin> pickAbilityAI(AbilityPlugin ability, ModularFleetAIAPI ai) {
		if (ability == null) return null;
		String id = ability.getId();
		if (id == null) return null;
		CampaignFleetAPI fleet = ai.getFleet();
		if (fleet == null) return null;
		
		if (id.equals(Abilities.TRANSPONDER) && fleet.getMemoryWithoutUpdate().getBoolean(AlwaysOnTransponderAI.MEMORY_KEY_ALWAYS_ON)) {
			Global.getLogger(this.getClass()).info("Adding custom transponder AI to fleet " + fleet.getName());
			AlwaysOnTransponderAI aai = new AlwaysOnTransponderAI();
			aai.init(ability);
			return new PluginPick<AbilityAIPlugin>(aai, PickPriority.MOD_SET);
		}
		return null;
	}

	@Override
	public PluginPick<ReputationActionResponsePlugin> pickReputationActionResponsePlugin(Object action, String factionId) {
		if (action instanceof RepActions || action instanceof RepActionEnvelope) {

			if (!NexReputationPlugin.COVERED_ACTIONS.contains(action))
				return null;

			return new PluginPick<ReputationActionResponsePlugin>(
					new NexReputationPlugin(),
					PickPriority.MOD_GENERAL
			);
		}
		return null;
	}
	
	public PluginPick<AICoreAdminPlugin> pickAICoreAdminPlugin(String commodityId) {
		if (Commodities.OMEGA_CORE.equals(commodityId)) {
			return new PluginPick<AICoreAdminPlugin>(new AICoreAdminPluginOmega(), PickPriority.MOD_GENERAL);
		}
		return null;
	}

	@Override
	public PluginPick<BattleAutoresolverPlugin> pickBattleAutoresolverPlugin(BattleAPI battle) {
		if (battle.isPlayerInvolved()) return null;
		for (CampaignFleetAPI fleet : battle.getBothSides()) {
			if (fleet.getMemoryWithoutUpdate().contains(NexBattleAutoresolverPlugin.MEM_KEY_STRENGTH_MULT)) {
				return new PluginPick<BattleAutoresolverPlugin>(new NexBattleAutoresolverPlugin(battle), PickPriority.MOD_GENERAL);
			}
		}
		return null;
	}
}