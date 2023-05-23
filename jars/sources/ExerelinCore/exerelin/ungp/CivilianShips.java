package exerelin.ungp;

import com.fs.starfarer.api.campaign.BuffManagerAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.merc.MercFleetGenPlugin;
import exerelin.utilities.StringHelper;
import ungp.api.rules.UNGP_BaseRuleEffect;
import ungp.api.rules.tags.UNGP_PlayerFleetTag;
import ungp.scripts.campaign.specialist.UNGP_SpecialistSettings;
import ungp.scripts.utils.UNGP_BaseBuff;

import java.awt.*;
import java.util.List;

public class CivilianShips extends UNGP_BaseRuleEffect implements UNGP_PlayerFleetTag {
	public static final float BASE_CR_REDUCTION = 0.2f;
	
	@Deprecated protected int difficulty;
	protected UNGP_SpecialistSettings.Difficulty difficultyEnum;
	protected float crPenalty;
	protected float maintMult;
	protected float milDP, civDP;
	protected boolean needReapply = false;
	
	/**
	 * The degree to which military ships out-DP civilian ones. Returns 0 when
	 * civilian DP >= military DP, 1 when all ships are military.
	 * @return 0 to 1.
	 */
	protected float getMilExcess() {
		if (milDP == 0) return 0;
		float value = (milDP - civDP)/milDP;
		if (value < 0) return 0;
		return value;
	}
	
	@Override
	public void updateDifficultyCache(UNGP_SpecialistSettings.Difficulty difficulty) {
		difficultyEnum = difficulty;
		//Global.getLogger(this.getClass()).info(String.format("Updating cache"));
		getValueByDifficulty(-1, difficulty);
	}
	
	@Override
	public float getValueByDifficulty(int index, UNGP_SpecialistSettings.Difficulty difficulty) {
		float excess = getMilExcess();
		if (excess > 1) excess = 1;
		crPenalty = BASE_CR_REDUCTION * excess;
		maintMult = 1 + excess;
		if (index == 0) return crPenalty;
		else if (index == 1) return maintMult;
		return 0;
	}

	@Deprecated @Override
	public void updateDifficultyCache(int difficulty) {
		this.difficulty = difficulty;
		//Global.getLogger(this.getClass()).info(String.format("Updating cache"));
		getValueByDifficulty(-1, difficulty);
	}
	
	@Deprecated	@Override
	public float getValueByDifficulty(int index, int difficulty) {
		float denominator = UNGP_SpecialistSettings.MAX_DIFFICULTY/2;
		float excess = getMilExcess();
		crPenalty = BASE_CR_REDUCTION * (difficulty/denominator) * excess;
		maintMult = 1 + excess * (difficulty/denominator);
		if (index == 0) return crPenalty;
		else if (index == 1) return maintMult;
		return 0;
	}

	protected class MilitaryDebuff extends UNGP_BaseBuff {
		public MilitaryDebuff(String id, float dur) {
			super(id, dur);
		}

		@Override
		public void apply(FleetMemberAPI member) {
			//Global.getLogger(this.getClass()).info(String.format("Applying debuff for %s: %s CR penalty, %s maint mult", 
			//		member.getShipName(), crPenalty, maintMult));
			decreaseMaxCR(member.getStats(), id, crPenalty, rule.getName());
			member.getStats().getSuppliesPerMonth().modifyMult(id, maintMult, rule.getName());
		}
	}
	
	
	@Override
	public void applyPlayerFleetStats(CampaignFleetAPI fleet) {
		float oldCivDP = civDP;
		float oldMilDP = milDP;
		
		civDP = 0;
		milDP = 0;
		
		final List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
		for (FleetMemberAPI member : members) {
			if (member.isMothballed()) continue;
			if (member.getVariant().hasHullMod(HullMods.CIVGRADE) 
					|| member.getVariant().hasHullMod(HullMods.MILITARIZED_SUBSYSTEMS) 
					|| member.getHullSpec().getHints().contains(ShipTypeHints.CIVILIAN)) {
				civDP += member.getDeploymentPointsCost();
			}
			else if (member.getVariant().hasHullMod("rugged")) {

			} else {
				milDP += member.getDeploymentPointsCost();
			}
		}
		if (oldCivDP != civDP || oldMilDP != milDP) {
			needReapply = true;
			//Global.getLogger(this.getClass()).info("Reapplying buff");
		}
		
		updateDifficultyCache(difficultyEnum);
		
		//Global.getLogger(this.getClass()).info(String.format("Applying stats: %s civ, %s mil", civDP, milDP));
		//Global.getLogger(this.getClass()).info(String.format("Applying stats: %s crPenalty, %s maintMult", crPenalty, maintMult));
		
		boolean needsSync = false;
		for (FleetMemberAPI member : members) {
			if (member.getBuffManager().getBuff(MercFleetGenPlugin.MERC_BUFF_ID) != null)
				continue;

			String buffId = rule.getBuffID();
			boolean civ = member.getVariant().hasHullMod(HullMods.CIVGRADE) || member.getVariant().hasHullMod(HullMods.MILITARIZED_SUBSYSTEMS)
					|| member.getHullSpec().getHints().contains(ShipTypeHints.CIVILIAN);
			if (civ) {
				member.getBuffManager().removeBuff(buffId);
				continue;
			}
			
			if (needReapply) {
				member.getBuffManager().removeBuff(buffId);
			}
			
			//Global.getLogger(this.getClass()).info("Checking fleet member for buff: " + member.getShipName());
			float buffDur = 0.1f;
			BuffManagerAPI.Buff test = member.getBuffManager().getBuff(buffId);
			if (test instanceof MilitaryDebuff) {
				MilitaryDebuff buff = (MilitaryDebuff) test;
				buff.setDur(buffDur);
				buff.apply(member);
				//Global.getLogger(this.getClass()).info("Updating buff duration for " + member.getShipName());
			} else {
				member.getBuffManager().addBuff(new MilitaryDebuff(buffId, buffDur));
				needsSync = true;
			}
		}
		needReapply = false;
		if (needsSync) {
			fleet.forceSync();
		}
	}

	@Override
	public void unapplyPlayerFleetStats(CampaignFleetAPI fleet) {
	}
	
	@Override
	public String getDescriptionParams(int index) {
		if (index == 1) return getPercentString(BASE_CR_REDUCTION * 100);
		if (index == 0) return 2 + "×";
		return null;
	}

	@Deprecated @Override
	public String getDescriptionParams(int index, int difficulty) {
		return getDescriptionParams(index);
	}
	
	@Override
	public String getDescriptionParams(int index, UNGP_SpecialistSettings.Difficulty difficulty) 
	{
		return getDescriptionParams(index);
	}

	@Override
	public boolean addIntelTips(TooltipMakerAPI imageTooltip) {
		Color hl = Misc.getHighlightColor();
		Color bad = Misc.getNegativeHighlightColor();
		String cat = "exerelin_misc";
		String id = "ungp_civilianShips_tip";
		imageTooltip.addPara(StringHelper.getString(cat, id + "1"), 0, hl, milDP + "");
		imageTooltip.addPara(StringHelper.getString(cat, id + "2"), 0, hl, civDP + "");
		imageTooltip.addPara(StringHelper.getString(cat, id + "3"), 0, (crPenalty > 0 ? bad : hl), Math.round(crPenalty * 100) + "");
		imageTooltip.addPara(StringHelper.getString(cat, id + "4"), 0, (maintMult > 1 ? bad : hl), String.format("%.1f", maintMult));
		return true;
	}
}