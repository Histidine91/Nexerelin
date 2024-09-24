package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GroundBattleAI;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.dialog.AbilityDialogPlugin;
import exerelin.utilities.StringHelper;

import java.util.HashMap;
import java.util.Map;

public class InspireAbilityPlugin extends AbilityPlugin {

	public static float MORALE_BOOST = 0.4f;
	public static int MIN_LEADERSHIP_SKILLS = 2;
	public static int SIC_MIN_LEVEL = 5;	// when having Second in Command mod
	
	@Override
	public void activate(InteractionDialogAPI dialog, PersonAPI user) {
		super.activate(dialog, user);
		for (GroundUnit unit : side.getUnits()) {
			unit.modifyMorale(MORALE_BOOST, 0, 1.1f);
			unit.reorganize(-1);
		}
		
		side.getData().put("usedInspire", true);
		logActivation(user);
	}
	
	@Override
	public void addDialogOptions(InteractionDialogAPI dialog) {
		super.addDialogOptions(dialog);
		SetStoryOption.set(dialog, 1, AbilityDialogPlugin.OptionId.ACTIVATE, "nex_gb_inspire", 
				Sounds.STORY_POINT_SPEND_LEADERSHIP, null);
	}
	
	@Override
	public Pair<String, Map<String, Object>> getDisabledReason(PersonAPI user) {
		Map<String, Object> params = new HashMap<>();

		if (side.getData().containsKey(GBConstants.TAG_PREVENT_INSPIRE)) {
			String id = "inspirePrevented";
			String desc = GroundBattleIntel.getString("ability_inspire_prevented");
			params.put("desc", desc);
			return new Pair<>(id, params);
		}
		if (Boolean.TRUE.equals(side.getData().get("usedInspire"))) {
			String id = "alreadyUsed";
			String desc = GroundBattleIntel.getString("ability_alreadyUsed");
			params.put("desc", desc);
			return new Pair<>(id, params);
		}
		int leadership = getLeadershipSkill(user);
		if (leadership < MIN_LEADERSHIP_SKILLS) {
			String id = "prerequisitesNotMet";
			String desc = GroundBattleIntel.getString("ability_inspire_prereq");
			desc = String.format(desc, MIN_LEADERSHIP_SKILLS);
			params.put("desc", desc);
			return new Pair<>(id, params);
		}
		else if (user == Global.getSector().getPlayerPerson() && user.getStats().getLevel() < 5) {
			String id = "prerequisitesNotMet";
			String desc = GroundBattleIntel.getString("ability_inspire_prereq_sic");
			desc = String.format(desc, SIC_MIN_LEVEL);
			params.put("desc", desc);
			return new Pair<>(id, params);
		}
		
		Pair<String, Map<String, Object>> reason = super.getDisabledReason(user);
		return reason;
	}
	
	@Override
	public boolean showIfDisabled(Pair<String, Map<String, Object>> disableReason) {
		if ("prerequisitesNotMet".equals(disableReason.one)) {
			//return false;
		}
		return true;
	}

	@Override
	public void dialogAddIntro(InteractionDialogAPI dialog) {
		dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_inspire_blurb"));
		dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_inspire_tooltip"),
				Misc.getStoryOptionColor(), StringHelper.toPercent(MORALE_BOOST));
		
		addCooldownDialogText(dialog);
	}

	@Override
	public void generateTooltip(TooltipMakerAPI tooltip) {
		tooltip.addPara(GroundBattleIntel.getString("ability_inspire_tooltip"), 0,
				Misc.getStoryOptionColor(), StringHelper.toPercent(MORALE_BOOST));
	}
	
	public int getLeadershipSkill(PersonAPI person) {
		if (person == null) return 0;
		if (Global.getSettings().getModManager().isModEnabled("second_in_command")) {
			return 999;
		}
		int level = 0;
		for (SkillLevelAPI skill : person.getStats().getSkillsCopy()) {
			if (!skill.getSkill().getGoverningAptitudeId().equals(Skills.APT_LEADERSHIP))
				continue;
			if (skill.getSkill().isAptitudeEffect()) continue;
			level++;
		}
		return level;
	}
	
	@Override
	public float getAIUsePriority(GroundBattleAI ai) {
		float score = 0, divisor = 0;
		for (GroundUnit unit : side.getUnits()) {
			divisor++;
			if (unit.getMorale() < 0.2f)
				score += 2;
			else if (unit.getMorale() < 0.5f)
				score += 1;
			if (unit.isReorganizing())
				score++;
		}
		if (divisor == 0) return 0;
		return score/divisor * 6f;
	}
}
