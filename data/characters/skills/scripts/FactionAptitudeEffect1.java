package data.characters.skills.scripts;

import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;

public class FactionAptitudeEffect1 implements CharacterStatsSkillEffect {

    public void apply(MutableCharacterStatsAPI stats, String id, float level)
    {
    }

    public void unapply(MutableCharacterStatsAPI stats, String id)
    {
    }

	public String getEffectDescription(float level) {
		return "+" + (int)(ExerelinSkillData.FACTION_APTITUDE_RESOURCE_DELIVERY_INCREASE_PERCENTAGE * level) + "% resources (including ships/weapons) delivered by supply convoys";
	}
	
	public String getEffectPerLevelDescription() {
		return "" + (int)(ExerelinSkillData.FACTION_APTITUDE_RESOURCE_DELIVERY_INCREASE_PERCENTAGE) + "%";
	}

	public ScopeDescription getScopeDescription() {
		return ScopeDescription.ALL_OUTPOSTS;
	}

}
