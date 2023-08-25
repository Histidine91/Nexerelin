package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.*;
import exerelin.utilities.NexUtilsMath;
import exerelin.utilities.StringHelper;

import java.awt.*;

import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;

public class IndEvoMinefieldPlugin extends MarketConditionPlugin {
	
	public static float DROP_COST_MULT = 1.25f;
	public static float DROP_ATTRITION = 10;
	
	@Override
	public void init(GroundBattleIntel intel, String conditionId) {
		super.init(intel, conditionId);
		isAttacker = false;
	}

	@Override
	public void apply() {
		super.apply();

		GBDataManager.ConditionDef def = this.getDef();
		GroundBattleSide attacker = intel.getSide(true);
		float dropMult = DROP_COST_MULT;
		float attritionFactor = DROP_ATTRITION;
		if (attacker.getData().containsKey("ability_ew_active")) {
			float mult = EWAbilityPlugin.GROUND_DEF_EFFECT_MULT;
			dropMult = NexUtilsMath.multiplyBonus(dropMult, mult);
			attritionFactor *= mult;
		}

		float shortageMult = getShortageMult();
		if (shortageMult < 1) {
			attritionFactor *= shortageMult;
			dropMult = NexUtilsMath.multiplyBonus(dropMult, shortageMult);
		}

		attacker.getDropCostMod().modifyMult(conditionId, dropMult, getCondition().getName());
		attacker.getDropAttrition().modifyFlat(conditionId, attritionFactor, getCondition().getName());
	}

	@Override
	public StatBonus modifyAttackStatBonus(GroundUnit unit, StatBonus attack) {
		// unapply minefield condition's effect on defender strength mult
		MarketConditionAPI cond = this.getCondition();
		//log.info("Attempting to unapply defender strength mult for " + cond.getIdForPluginModifications());
		attack.unmodify(cond.getIdForPluginModifications());

		return attack;
	}

	protected float getShortageMult() {
		CommodityOnMarketAPI data = intel.getMarket().getCommodityData(Commodities.SUPPLIES);
		float avail = data.getAvailable();
		float wanted = data.getMaxDemand();

		return Math.min(avail/wanted, 1);
	}

	@Override
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
		super.processTooltip(tooltip, expanded, tooltipParam);

		final Color h = Misc.getHighlightColor();
		tooltip.addSpacer(3);

		tooltip.addPara("- " + getString("modifierEnemyDropCost"), 0, h, String.format("%.1f×", DROP_COST_MULT));
		tooltip.addPara("- " + getString("modifierDropAttrition"), 0, h, StringHelper.toPercent(DROP_ATTRITION/100));
		if (getDef().tags.contains(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			tooltip.addPara("- " + getString("modifierPreventBombardment"), 0);
		}

		float shortageMult = this.getShortageMult();
		if (shortageMult < 1) {
			tooltip.addPara("- " + getString("modifierGroundDefShortageMult"), 0,
					Misc.getNegativeHighlightColor(), String.format("%.2f×", shortageMult));
		}
	}
}
