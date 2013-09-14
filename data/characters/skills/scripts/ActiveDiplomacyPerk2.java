package data.characters.skills.scripts;

import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;

public class ActiveDiplomacyPerk2 implements CharacterStatsSkillEffect {

    public void apply(MutableCharacterStatsAPI stats, String id, float level)
    {
    }

    public void unapply(MutableCharacterStatsAPI stats, String id)
    {
    }

	public String getEffectDescription(float level) {
		return "" + (int)(ExerelinSkillData.FACTION_ACTIVEDIPLOMACY_PERK_ITEM_REUSE_CHANCE) + "% chance that a diplomacy operative can repeat their mission";
	}
	
	public String getEffectPerLevelDescription() {
		return "" + (int)(ExerelinSkillData.FACTION_ACTIVEDIPLOMACY_PERK_ITEM_REUSE_CHANCE) + "%";
	}

	public ScopeDescription getScopeDescription() {
		return ScopeDescription.ALL_OUTPOSTS;
	}

}
