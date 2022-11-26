package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import static com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel.log;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Nex_FactionCommissionIntel extends FactionCommissionIntel implements ColonyPlayerHostileActListener {
	
	public static final RepLevel PIRATE_JOIN_REQUIRED_REP = RepLevel.SUSPICIOUS;
	public static final RepLevel PIRATE_STAY_REQUIRED_REP = RepLevel.INHOSPITABLE;
	
	public static final String MEM_KEY_ATROCITIES = "$nex_player_atrocities_by_faction";
	
	public Nex_FactionCommissionIntel(FactionAPI faction) {
		super(faction);
	}
	
	@Override
	public void makeRepChanges(InteractionDialogAPI dialog) {
		// do nothing, we take care of it elsewhere
	}
	
	public static Map<String, Integer> getAtrocitiesByFaction() {
		MemoryAPI mem = Global.getSector().getCharacterData().getMemoryWithoutUpdate();
		if (!mem.contains(MEM_KEY_ATROCITIES)) {
			mem.set(MEM_KEY_ATROCITIES, new HashMap<String, Integer>());
		}
		return (Map<String, Integer>)mem.get(MEM_KEY_ATROCITIES);
	}
	
	public void undoAllRepChanges(InteractionDialogAPI dialog) {
		Map<String, Float> storedRelations = (Map<String, Float>)Global.getSector().getPersistentData().get(PlayerFactionStore.PLAYER_RELATIONS_KEY);
		Map<String, Integer> atrocities = getAtrocitiesByFaction();
		float restorePenalty = Global.getSettings().getFloat("factionCommissionRestoredRelationshipPenalty");
		
		List<FactionAPI> factions = Global.getSector().getAllFactions();
		for (FactionAPI faction : factions) {
			if (faction == this.faction) continue;
			
			String factionId = faction.getId();
			if (atrocities.containsKey(factionId)) continue;
			
			Float prevRep = storedRelations.get(factionId);
			if (prevRep == null) continue;
			
			float currRep = faction.getRelToPlayer().getRel();
			Float newRep = null;
			
			if (currRep < prevRep - restorePenalty) {
				newRep = Math.max(prevRep - restorePenalty, currRep);
			}
			
			if (newRep != null) {
				undoRepChangeCustom(faction, newRep - currRep, dialog);
			}
		}
	}
	
	public void undoRepChangeCustom(FactionAPI faction, float delta, InteractionDialogAPI dialog) {
		CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
		impact.delta = delta;
		if (impact.delta > 0) {
			Global.getSector().adjustPlayerReputation(
					new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, 
							impact, null, dialog != null ? dialog.getTextPanel() : null, false, true), 
							faction.getId());
		}
	}
	
	@Override
	public String getName() {
		return Misc.ucFirst(super.getName());
	}

	// Do not remove the script when mission ends, that keeps it from expiring
	@Override
	public void endMission(InteractionDialogAPI dialog) {
		log.info(String.format("Ending commission with [%s]", faction.getDisplayName()));
		Global.getSector().getListenerManager().removeListener(this);
		//Global.getSector().removeScript(this);
		
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().unset(MemFlags.FCM_FACTION);
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().unset(MemFlags.FCM_EVENT);
		
		undoAllRepChanges(dialog);
		
		endAfterDelay();
	}

	// override normal rep level check
	@Override
	public void advanceMission(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		
		RepLevel level = faction.getRelToPlayer().getLevel();
		if (!level.isAtWorst(getMinRep())) {
			setMissionResult(new MissionResult(-1, null));
			setMissionState(MissionState.COMPLETED);
			endMission();
			sendUpdateIfPlayerHasIntel(missionResult, false);
		} else {
			makeRepChanges(null);
		}
	}
	
	public RepLevel getMinRep() {
		if (NexConfig.getFactionConfig(this.faction.getId()).pirateFaction) {
			return PIRATE_STAY_REQUIRED_REP;
		}
		return RepLevel.NEUTRAL;
	}
	
	@Override
	public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {
	}

	@Override
	public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, Industry industry) {
	}

	@Override
	public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
	}

	@Override
	public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
		if (actionData == null) return;
		List<FactionAPI> hostile = actionData.willBecomeHostile;
		Map<String, Integer> atrocities = getAtrocitiesByFaction();
		for (FactionAPI rage : hostile) {
			NexUtils.modifyMapEntry(atrocities, rage.getId(), 1);
		}
	}
}
