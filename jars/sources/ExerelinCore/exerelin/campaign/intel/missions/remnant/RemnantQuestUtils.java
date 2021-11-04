package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RemnantQuestUtils {
		
	public static final String PERSON_DISSONANT = "nex_dissonant";
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
		Global.getSector().getImportantPeople().addPerson(person);
		market.addPerson(person);
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
