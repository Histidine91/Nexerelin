package exerelin.skills.scripts;

import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;

public class PassiveDiplomacyEffect1 implements CharacterStatsSkillEffect {

    public void apply(MutableCharacterStatsAPI stats, String id, float level)
    {
    }

    public void unapply(MutableCharacterStatsAPI stats, String id)
    {
    }

	public String getEffectDescription(float level) {
		return "+" + (int)(ExerelinSkillData.FACTION_PASSIVEDIPLOMACY_EFFECT_RELATIONSHIP_BONUS_PERCENTAGE * level) + "% bonus to faction relationships";
	}
	
	public String getEffectPerLevelDescription() {
		return "" + (int)(ExerelinSkillData.FACTION_PASSIVEDIPLOMACY_EFFECT_RELATIONSHIP_BONUS_PERCENTAGE) + "%";
	}

	public ScopeDescription getScopeDescription() {
		return ScopeDescription.ALL_OUTPOSTS;
	}

}
