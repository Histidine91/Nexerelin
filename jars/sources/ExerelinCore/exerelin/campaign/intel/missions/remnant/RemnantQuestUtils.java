package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.skills.NexSkills;
import exerelin.utilities.StringHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RemnantQuestUtils {
		
	public static final String PERSON_DISSONANT = "nex_dissonant";
	public static final String PERSON_TOWERING = "nex_towering";
	public static final String PERSON_ARGENT = "nex_luddicKnight";
	public static final String PERSON_LOST_SCIENTIST = "nex_lostScientist";
	public static final List<String> TAG_AS_REMNANT_MISSION = new ArrayList<>(Arrays.asList(
			"seco", "ssat"
			//"sitm"	// blueprint location; next time maybe?
	));
	
	public static void createDissonant(MarketAPI market) {
		PersonAPI person = Global.getFactory().createPerson();
		person.setId(PERSON_DISSONANT);
		person.setImportance(PersonImportance.HIGH);
		person.setVoice(Voices.SCIENTIST);	// best I can come up with
		person.setFaction(Factions.REMNANTS);
		person.setGender(FullName.Gender.FEMALE);
		person.setRankId(Ranks.SPECIAL_AGENT);
		person.setPostId(Ranks.POST_SPECIAL_AGENT);
		person.getName().setFirst(StringHelper.getString("nex_remnantQuest", "dissonantAlias"));
		person.getName().setLast("");
		person.setPortraitSprite(Global.getSettings().getSpriteName("characters", "nex_dissonant"));
		person.addTag("remnant");
		person.setAICoreId(Commodities.ALPHA_CORE);
		Global.getSector().getImportantPeople().addPerson(person);
		market.addPerson(person);
	}

	public static PersonAPI getDissonant() {
		return Global.getSector().getImportantPeople().getPerson(PERSON_DISSONANT);
	}

	public static PersonAPI getOrCreateLostScientist() {
		PersonAPI person = Global.getSector().getImportantPeople().getPerson(PERSON_LOST_SCIENTIST);
		if (person != null) return person;

		person = Global.getFactory().createPerson();
		person.setId(PERSON_LOST_SCIENTIST);
		person.setImportance(PersonImportance.MEDIUM);
		person.setVoice(Voices.SCIENTIST);
		person.setFaction(Factions.INDEPENDENT);
		person.setGender(FullName.Gender.MALE);
		person.setRankId(Ranks.CITIZEN);
		person.setPostId(Ranks.POST_ACADEMICIAN);
		person.getName().setFirst(getString("scientistName1"));
		person.getName().setLast(getString("scientistName2"));
		person.setPortraitSprite("graphics/portraits/portrait31.png");
		Global.getSector().getImportantPeople().addPerson(person);

		return person;
	}

	public static PersonAPI getOrCreateTowering() {
		PersonAPI person = Global.getSector().getImportantPeople().getPerson(PERSON_TOWERING);
		if (person != null) return person;

		person = Global.getFactory().createPerson();
		person.setId(PERSON_TOWERING);
		person.setImportance(PersonImportance.VERY_HIGH);
		person.setVoice(Voices.SOLDIER);
		person.setFaction(Factions.REMNANTS);
		person.setGender(FullName.Gender.MALE);
		person.setRankId(Ranks.SPACE_ADMIRAL);
		person.setPostId(Ranks.POST_WARLORD);
		person.setPersonality(Personalities.RECKLESS);
		person.getName().setFirst(getString("toweringName1"));
		person.getName().setLast(getString("toweringName2"));
		person.setPortraitSprite(Global.getSettings().getSpriteName("characters", "nex_towering"));
		person.addTag("remnant");
		person.setAICoreId(Commodities.ALPHA_CORE);
		person.getMemoryWithoutUpdate().set("$chatterChar", "none");	// could be acecombat_torres but meh
		Global.getSector().getImportantPeople().addPerson(person);

		person.getStats().setLevel(8);
		person.getStats().setSkillLevel(Skills.HELMSMANSHIP, 2);
		person.getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 2);
		person.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 2);
		person.getStats().setSkillLevel(Skills.FIELD_MODULATION, 2);
		person.getStats().setSkillLevel(Skills.GUNNERY_IMPLANTS, 2);
		person.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE, 2);
		person.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);
		person.getStats().setSkillLevel(Skills.POINT_DEFENSE, 2);
		person.getStats().setSkillLevel(Skills.TACTICAL_DRILLS, 1);
		person.getStats().setSkillLevel(Skills.ELECTRONIC_WARFARE, 1);
		person.getStats().setSkillLevel(Skills.CARRIER_GROUP, 1);
		person.getStats().setSkillLevel(Skills.FLUX_REGULATION, 1);

		return person;
	}

	public static void enhanceTowering() {
		PersonAPI person = Global.getSector().getImportantPeople().getPerson(PERSON_TOWERING);
		person.getStats().setLevel(10);
		person.getStats().setSkillLevel(Skills.ORDNANCE_EXPERTISE, 2);
		person.getStats().setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2);
		person.getStats().setSkillLevel(Skills.COORDINATED_MANEUVERS, 1);
		person.getStats().setSkillLevel(NexSkills.FORCE_CONCENTRATION_EX, 1);
	}

	public static PersonAPI getOrCreateArgent() {
		PersonAPI person = Global.getSector().getImportantPeople().getPerson(PERSON_ARGENT);
		if (person != null) return person;

		person = Global.getSector().getFaction(Factions.LUDDIC_CHURCH).createRandomPerson(FullName.Gender.ANY);
		person.setId(PERSON_ARGENT);
		person.setImportance(PersonImportance.HIGH);
		person.setVoice(Voices.FAITHFUL);
		person.setFaction(Factions.LUDDIC_CHURCH);
		person.setRankId(Ranks.KNIGHT_CAPTAIN);
		person.setPostId(Ranks.POST_FLEET_COMMANDER);
		person.setPersonality(Personalities.STEADY);

		person.getStats().setLevel(7);
		person.getStats().setSkillLevel(Skills.HELMSMANSHIP, 2);
		person.getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 2);
		person.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
		person.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE, 2);
		person.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 1);
		person.getStats().setSkillLevel(Skills.MISSILE_SPECIALIZATION, 2);
		person.getStats().setSkillLevel(Skills.ORDNANCE_EXPERTISE, 1);
		person.getStats().setSkillLevel(Skills.TACTICAL_DRILLS, 1);
		person.getStats().setSkillLevel(Skills.CREW_TRAINING, 1);
		person.getStats().setSkillLevel(Skills.COORDINATED_MANEUVERS, 1);

		// personal stuff; uncomment if/when we get a permanent character
		person.setGender(FullName.Gender.FEMALE);
		person.getName().setFirst(getString("knightName1"));
		person.getName().setLast(getString("knightName2"));
		person.setPortraitSprite(Global.getSettings().getSpriteName("characters", "nex_argent"));

		Global.getSector().getImportantPeople().addPerson(person);
		return person;
	}
	
	public static void setupRemnantContactMissions() {
		for (String id : TAG_AS_REMNANT_MISSION) {
			Global.getSettings().getMissionSpec(id).getTagsAny().add("remnant");
		}
	}
	
	public static String getComplicationFaction(Random random, boolean allowIndependent) {
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
		//FactionAPI remnants = Global.getSector().getFaction(Factions.REMNANTS);
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
			if (factionId.equals(Factions.PLAYER)) continue;
			float weight = 0;
			if (DiplomacyTraits.hasTrait(factionId, DiplomacyTraits.TraitIds.DISLIKES_AI)
					|| DiplomacyTraits.hasTrait(factionId, DiplomacyTraits.TraitIds.HATES_AI))
				weight += 2;
			
			if (weight > 0) picker.add(factionId);
		}
		picker.add(Factions.INDEPENDENT, 1);
		return picker.pick();
	}

	public static void giveReturnToNearestRemnantBaseAssignments(CampaignFleetAPI fleet, boolean withClear) {
		CampaignFleetAPI nearestStation = null;
		float nearestDistSq = 999999999999f;
		Vector2f fleetPos = fleet.getLocationInHyperspace();

		for (StarSystemAPI system : Global.getSector().getStarSystems())
		{
			if (!system.hasTag(Tags.THEME_REMNANT)) continue;

			for (CampaignFleetAPI maybeStation : system.getFleets())
			{
				if (!Factions.REMNANTS.equals(maybeStation.getFaction().getId()))
					continue;
				if (fleet.getMemoryWithoutUpdate().getBoolean("$ArtilleryStation")) continue;
				if (maybeStation.isStationMode())
				{
					float distSq = MathUtils.getDistanceSquared(system.getHyperspaceAnchor().getLocation(), fleetPos);
					if (distSq < nearestDistSq) {
						nearestDistSq = distSq;
						nearestStation = maybeStation;
					} else {
						// skip checking the other fleets in system
						// even if there was somehow another nexus in the system it's not going to be any closer anyway
						continue;
					}
				}
			}
		}

		if (nearestStation == null) {
			Misc.giveStandardReturnToSourceAssignments(fleet, withClear);
			return;
		}

		if (withClear) {
			fleet.clearAssignments();
		}

		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, nearestStation, 1000f,
				StringHelper.getFleetAssignmentString("returningTo", StringHelper.getString("unknownLocation")));
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, nearestStation, 1f + 1f * (float) Math.random());
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, nearestStation, 1000f);

	}
	
	public static String getString(String id) {
		return StringHelper.getString("nex_remnantQuest", id);
	}
}
