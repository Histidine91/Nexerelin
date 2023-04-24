package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.skills.NexSkills;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RemnantQuestUtils {
		
	public static final String PERSON_DISSONANT = "nex_dissonant";
	public static final String PERSON_TOWERING = "nex_towering";
	public static final List<String> TAG_AS_REMNANT_MISSION = new ArrayList<>(Arrays.asList(new String[]{
		//"proCom", // meh
		"sShip", 
		//"dhi", "dsp", // don't seem to work, maybe do custom versions later?
		//"tabo",		// requires military, maybe do custom version
		"seco", "ssat",
		//"sitm"	// next time maybe?
	}));
	
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
		person.getStats().setSkillLevel(Skills.TACTICAL_DRILLS, 2);
		person.getStats().setSkillLevel(Skills.ELECTRONIC_WARFARE, 2);
		person.getStats().setSkillLevel(Skills.CARRIER_GROUP, 2);
		person.getStats().setSkillLevel(Skills.FLUX_REGULATION, 2);

		return person;
	}

	public static void enhanceTowering() {
		PersonAPI person = Global.getSector().getImportantPeople().getPerson(PERSON_TOWERING);
		person.getStats().setLevel(10);
		person.getStats().setSkillLevel(Skills.ORDNANCE_EXPERTISE, 2);
		person.getStats().setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2);
		person.getStats().setSkillLevel(Skills.COORDINATED_MANEUVERS, 2);
		person.getStats().setSkillLevel(NexSkills.FORCE_CONCENTRATION_EX, 2);
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
	
	public static String getString(String id) {
		return StringHelper.getString("nex_remnantQuest", id);
	}
}
