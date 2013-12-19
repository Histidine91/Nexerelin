package exerelin.skills.scripts;

import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;

public class FleetDeploymentPerk2 implements CharacterStatsSkillEffect {

    public void apply(MutableCharacterStatsAPI stats, String id, float level)
    {
    }

    public void unapply(MutableCharacterStatsAPI stats, String id)
    {
    }

	public String getEffectDescription(float level) {
		return "+" + (int)(ExerelinSkillData.FACTION_FLEETDEPLOYMENT_PERK_CAPITAL_SHIP_AVAILABILITY_CHANCE_BONUS_PERCENTAGE) + "% increased chance of capital ship construction";
	}
	
	public String getEffectPerLevelDescription() {
		return "" + (int)(ExerelinSkillData.FACTION_FLEETDEPLOYMENT_PERK_CAPITAL_SHIP_AVAILABILITY_CHANCE_BONUS_PERCENTAGE) + "%";
	}

	public ScopeDescription getScopeDescription() {
		return ScopeDescription.ALL_OUTPOSTS;
	}

}
