package data.characters.skills.scripts;

import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;

public class FleetCrewTrainingEffect1 implements CharacterStatsSkillEffect {

    public void apply(MutableCharacterStatsAPI stats, String id, float level)
    {
    }

    public void unapply(MutableCharacterStatsAPI stats, String id)
    {
    }

	public String getEffectDescription(float level) {
		return "+" + (int)(ExerelinSkillData.FACTION_FLEETCREWTRAINING_EFFECT_EXPERIENCE_BONUS_PERCENTAGE * level) + "% more expereinced crew in faction fleets";
	}
	
	public String getEffectPerLevelDescription() {
		return "" + (int)(ExerelinSkillData.FACTION_FLEETCREWTRAINING_EFFECT_EXPERIENCE_BONUS_PERCENTAGE) + "%";
	}

	public ScopeDescription getScopeDescription() {
		return ScopeDescription.ALL_OUTPOSTS;
	}

}
