package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleRoundResolve;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.campaign.intel.groundbattle.dialog.AbilityDialogPlugin;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FireSupportAbilityPlugin extends AbilityPlugin {
	
	public static float BASE_DAMAGE = 32;	// at size 3
	public static float CLOSE_SUPPORT_DAMAGE_MULT = 1.25f;
	public static float BASE_COST = 20;
		
	@Override
	public void activate(InteractionDialogAPI dialog, PersonAPI user) {
		super.activate(dialog, user);
		float damage = getDamage();
		
		
		if (target.isContested()) damage *= CLOSE_SUPPORT_DAMAGE_MULT;
		
		int numEnemies = 0;
		for (GroundUnit unit : target.getUnits()) {
			if (unit.isAttacker() != side.isAttacker()) {
				numEnemies++;
			}
		}
		
		logActivation(user);	// so it displays before the unit destruction messages, if any
		
		GroundBattleRoundResolve resolve = new GroundBattleRoundResolve(getIntel());
		resolve.distributeDamage(target, !side.isAttacker(), Math.round(damage));
		for (GroundUnit unit : new ArrayList<>(target.getUnits())) {
			if (unit.getSize() <= 0)
				unit.destroyUnit(0);
			else
				resolve.checkReorganize(unit);
		}
		
		boolean disrupt = target.heldByAttacker != side.isAttacker();
		float disruptTime = 0;
		if (disrupt) {
			Industry ind = target.getIndustry();
			disruptTime = getDisruptionTime(ind);
			ind.setDisrupted(disruptTime + ind.getDisruptedDays(), true);
		}
		
		Color h = Misc.getHighlightColor();
		dialog.getTextPanel().setFontSmallInsignia();
		dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_bombard_result1"), 
				h, Math.round(damage) + "", numEnemies + "");
		
		if (disrupt) {
			dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_bombard_result2"), 
				h, target.getName(), (int)disruptTime + "");
		}
		
		getIntel().reapply();
		
		dialog.getTextPanel().setFontInsignia();
	}

	@Override
	public void dialogAddIntro(InteractionDialogAPI dialog) {
		dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_bombard_blurb"));
		TooltipMakerAPI tooltip = dialog.getTextPanel().beginTooltip();
		generateTooltip(tooltip);
		dialog.getTextPanel().addTooltip();
		int cost = getFuelCost();
		boolean canAfford = dialog.getTextPanel().addCostPanel(null,
					Commodities.FUEL, cost, true);
		
		if (!canAfford) {
			dialog.getOptionPanel().setEnabled(AbilityDialogPlugin.OptionId.ACTIVATE, false);
		}
		
		addCooldownDialogText(dialog);
	}
	
	@Override
	public void dialogAddConfirmation(InteractionDialogAPI dialog) {
		
	}

	@Override
	public void generateTooltip(TooltipMakerAPI tooltip) {
		float opad = 10;
		Color h = Misc.getHighlightColor();
		int dam = getDamage();
		tooltip.addPara(GroundBattleIntel.getString("ability_bombard_tooltip1"), 0,
				h, "" + dam, StringHelper.toPercent(CLOSE_SUPPORT_DAMAGE_MULT - 1));
		tooltip.addPara(GroundBattleIntel.getString("ability_bombard_tooltip2"), opad);
		tooltip.addPara(GroundBattleIntel.getString("ability_bombard_tooltip3"), opad);
		float needed = getFuelCost(), curr = Global.getSector().getPlayerFleet().getCargo().getFuel();
		Color col = curr >= needed ? h : Misc.getNegativeHighlightColor();
		tooltip.addPara(GroundBattleIntel.getString("ability_bombard_tooltip4"), opad, col, Math.round(needed) + "");
	}
	
	@Override
	public Pair<String, Map<String, Object>> getDisabledReason(PersonAPI user) {
		if (side.getData().containsKey(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			Map<String, Object> params = new HashMap<>();
			
			String id = "bombardmentPrevented";
			String desc = GroundBattleIntel.getString("ability_bombard_prevented");
			params.put("desc", desc);
			return new Pair<>(id, params);
		}
		// fuel check
		{
			int cost = getFuelCost();
			float have = user.getFleet().getCargo().getMaxFuel();
			if (user == Global.getSector().getPlayerPerson()) {
				have = user.getFleet().getCargo().getFuel();
			}
			if (cost > have) {
				Map<String, Object> params = new HashMap<>();
			
				String id = "notEnoughFuel";
				String desc = String.format(GroundBattleIntel.getString("ability_bombard_insufficientFuel"), cost);
				params.put("desc", desc);
				return new Pair<>(id, params);
			}
		}
				
		Pair<String, Map<String, Object>> reason = super.getDisabledReason(user);
		return reason;
	}
	
	public int getFuelCost() {
		int marketSize = getIntel().getMarket().getSize();
		float cost = BASE_COST * (float)Math.pow(2, marketSize - 3);
		//Global.getLogger(this.getClass()).info("wololo " + cost);
		cost = side.getBombardmentCostMod().computeEffective(cost);
		//Global.getLogger(this.getClass()).info("wololo " + cost);
		return Math.round(cost);
	}
	
	public int getDamage() {
		int marketSize = getIntel().getMarket().getSize();
		return (int)Math.round(BASE_DAMAGE * Math.pow(2, marketSize - 3));
	}
	
	@Override
	public boolean targetsIndustry() {
		return true;
	}
	
	@Override
	public boolean shouldCloseDialogOnActivate() {
		return false;
	}
	
	@Override
	public boolean hasActivateConfirmation() {
		//return (target != null && getIntel().getIndustryForBattleByIndustry(target).heldByAttacker != side.isAttacker());
		return false;
	}
	
	public float getDisruptionTime(Industry ind) {
		return ind.getSpec().getDisruptDanger().disruptionDays;
	}
	
	@Override
	public List<IndustryForBattle> getTargetIndustries() {
		List<IndustryForBattle> targets = new ArrayList<>();
		for (IndustryForBattle ifb : getIntel().getIndustries()) {
			if (ifb.heldByAttacker != getSide().isAttacker() && !ifb.isIndustryTrueDisrupted() 
					&& ifb.getPlugin().getDef().tags.contains("noBombard"))
				continue;
			if (!ifb.containsEnemyOf(side.isAttacker())) continue;
			targets.add(ifb);
		}
		return targets;
	}
}
