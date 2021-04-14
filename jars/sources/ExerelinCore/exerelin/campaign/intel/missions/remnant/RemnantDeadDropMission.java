package exerelin.campaign.intel.missions.remnant;


import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.DeadDropMission;
import com.fs.starfarer.api.impl.campaign.missions.DelayedFleetEncounter;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;
import static exerelin.campaign.intel.missions.remnant.RemnantQuestUtils.getString;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.List;

public class RemnantDeadDropMission extends DeadDropMission {

	public static float PROB_COMPLICATIONS = 0.75f;
	public static int ITEM_COUNT = 10;
	
	public static enum Stage {
		DROP_OFF,
		COMPLETED,
		FAILED,
	}
	
	public static List<String> ITEMS = new ArrayList<>();
	
	static {
		for (int i=0; i<ITEM_COUNT; i++) {
			ITEMS.add(getString("deadDropItem" + (i + 1)));
		}
	}
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		//genRandom = Misc.random;
		
		thing = pickOne(ITEMS);
		//Global.getLogger(this.getClass()).info("Picked thing " + thing);
		
		PersonAPI person = getPerson();
		if (person == null) return false;
		
		if (!setPersonMissionRef(person, "$ddro_ref")) {
			return false;
		}
		
		requireSystemInterestingAndNotCore();
		preferSystemInInnerSector();
		preferSystemUnexplored();
		preferSystemInDirectionOfOtherMissions();
		preferSystemTags(ReqMode.ANY, Tags.THEME_REMNANT_DESTROYED, Tags.THEME_DERELICT);
		
		system = pickSystem();
		if (system == null) return false;
		
		target = spawnMissionNode(new LocData(EntityLocationType.HIDDEN_NOT_NEAR_STAR, null, system));
		if (!setEntityMissionRef(target, "$ddro_ref")) return false;
		
		makeImportant(target, "$ddro_target", Stage.DROP_OFF);
		

		setStartingStage(Stage.DROP_OFF);
		setSuccessStage(Stage.COMPLETED);
		setFailureStage(Stage.FAILED);
		
		setStageOnMemoryFlag(Stage.COMPLETED, target, "$ddro_completed");
		setTimeLimit(Stage.FAILED, MISSION_DAYS, null);
		

		setCreditReward(CreditReward.HIGH);
		
		String complFactionId = getComplicationFaction();
		if (complFactionId != null && rollProbability(PROB_COMPLICATIONS)) {
			triggerComplicationBegin(Stage.DROP_OFF, ComplicationSpawn.APPROACHING_OR_ENTERING,
					system, complFactionId,
					String.format(StringHelper.getString("exerelin_misc", "deadDropStr1"), 
							getWithoutArticle(thing)), 
					StringHelper.getString("it"),
					String.format(StringHelper.getString("exerelin_misc", "deadDropStr2"), 
							getWithoutArticle(thing),person.getFaction().getDisplayNameWithArticle()),
					0,
					true, ComplicationRepImpact.LOW, null);
			triggerComplicationEnd(true);
		}
		
		return true;
	}
	
	public String getComplicationFaction() {
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(this.getGenRandom());
		//FactionAPI remnants = Global.getSector().getFaction(Factions.REMNANTS);
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
			
			float weight = 0;
			if (DiplomacyTraits.hasTrait(factionId, TraitIds.DISLIKES_AI)
					|| DiplomacyTraits.hasTrait(factionId, TraitIds.HATES_AI))
				weight += 2;
			
			if (weight > 0) picker.add(factionId);
		}
		picker.add(Factions.INDEPENDENT, 1);
		return picker.pick();
	}
	
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		
		if (isSucceeded() && rollProbability(PROB_PATROL_AFTER)) {
			PersonAPI person = getPerson();
			if (person == null || person.getMarket() == null) return;
			String patrolFaction = getComplicationFaction();
			if (patrolFaction == null ||
					patrolFaction.equals(person.getFaction().getId()) || 
					Misc.isPirateFaction(person.getMarket().getFaction()) ||
					Factions.PLAYER.equals(patrolFaction)) {
				return;
			}
			
			DelayedFleetEncounter e = new DelayedFleetEncounter(genRandom, getMissionId());
			e.setDelayNone();
			e.setLocationInnerSector(true, patrolFaction);
			e.beginCreate();
			e.triggerCreateFleet(FleetSize.LARGE, FleetQuality.DEFAULT, patrolFaction, FleetTypes.PATROL_LARGE, new Vector2f());
			e.setFleetWantsThing(patrolFaction, 
					StringHelper.getString("exerelin_misc", "deadDropCoordsStr1"), 
					StringHelper.getString("they"),
					String.format(StringHelper.getString("exerelin_misc", "deadDropCoordsStr2"), 
							person.getFaction().getDisplayNameWithArticle()),
					0,
					true, ComplicationRepImpact.FULL,
					DelayedFleetEncounter.TRIGGER_REP_LOSS_MINOR, getPerson());
			e.triggerSetAdjustStrengthBasedOnQuality(true, getQuality());
			e.triggerSetPatrol();
			e.triggerSetStandardAggroInterceptFlags();
			e.endCreate();
		}
	}
	
}






