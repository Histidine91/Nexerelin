package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GroundBattleAI;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleRoundResolve;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.campaign.intel.groundbattle.dialog.AbilityDialogPlugin;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class FireSupportAbilityPlugin extends AbilityPlugin {
	
	// TODO: use this to keep the same fleet from bombarding to infinity
	// probably not needed now that base cost has been reduced?
	public static final String MEMORY_KEY_FUEL_SPENT = "$nex_gbFireSupport_spend";
	public static float BASE_DAMAGE = 12;	// multiplied by market size with a floor of 3
	public static float CLOSE_SUPPORT_DAMAGE_MULT = 1.25f;
	public static float BASE_COST = 10;
		
	@Override
	public void activate(InteractionDialogAPI dialog, PersonAPI user) {
		super.activate(dialog, user);
		
		float damage = getDamage();
		if (target.isContested()) damage *= CLOSE_SUPPORT_DAMAGE_MULT;
		int cost = getFuelCost(user.getFleet());
		
		int numEnemies = 0;
		for (GroundUnit unit : target.getUnits()) {
			if (unit.isAttacker() != side.isAttacker()) {
				numEnemies++;
			}
		}		
		
		boolean enemyHeld = target.heldByAttacker != side.isAttacker();
		//if (enemyHeld) damage /= target.getPlugin().getStrengthMult();	// applied in the actual damage check
		
		logActivation(user);	// so it displays before the unit destruction messages, if any
		
		GroundBattleRoundResolve resolve = new GroundBattleRoundResolve(getIntel());
		resolve.distributeDamage(target, !side.isAttacker(), Math.round(damage));
		for (GroundUnit unit : new ArrayList<>(target.getUnits())) {
			if (unit.getSize() <= 0)
				unit.destroyUnit(0);
			else
				resolve.checkReorganize(unit);
		}
		
		float disruptTime = 0;
		boolean canDisrupt = !target.getPlugin().getDef().hasTag("resistBombard") 
				&& !target.getPlugin().getDef().hasTag("noBombard");
		if (enemyHeld && canDisrupt) 
		{
			Industry ind = target.getIndustry();
			disruptTime = NexUtilsMarket.getIndustryDisruptTime(ind);
			ind.setDisrupted(disruptTime + ind.getDisruptedDays(), true);
		}
		
		if (dialog != null) {
			Color h = Misc.getHighlightColor();
			dialog.getTextPanel().setFontSmallInsignia();
			dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_bombard_result1"), 
					h, Math.round(damage) + "", numEnemies + "");

			if (disruptTime > 0) {
				dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_bombard_result2"), 
					h, target.getName(), (int)disruptTime + "");
			}
		}
		
		if (user != null && user.isPlayer()) {
			Global.getSector().getPlayerFleet().getCargo().removeFuel(cost);
			getIntel().getPlayerData().fuelUsed += cost;
		}
		
		getIntel().reapply();
		
		if (dialog != null)
			dialog.getTextPanel().setFontInsignia();
	}

	@Override
	public void dialogAddIntro(InteractionDialogAPI dialog) {
		dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_bombard_blurb"));
		TooltipMakerAPI tooltip = dialog.getTextPanel().beginTooltip();
		generateTooltip(tooltip);
		dialog.getTextPanel().addTooltip();
		int cost = getFuelCost(Global.getSector().getPlayerFleet());
		boolean canAfford = dialog.getTextPanel().addCostPanel(null,
					Commodities.FUEL, cost, true);
		
		if (!canAfford) {
			dialog.getOptionPanel().setEnabled(AbilityDialogPlugin.OptionId.ACTIVATE, false);
		}
		
		addCooldownDialogText(dialog);
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
		float needed = getFuelCost(Global.getSector().getPlayerFleet());
		float curr = Global.getSector().getPlayerFleet().getCargo().getFuel();
		Color col = curr >= needed ? h : Misc.getNegativeHighlightColor();
		tooltip.addPara(GroundBattleIntel.getString("ability_bombard_tooltip4"), opad, col, Math.round(needed) + "");
	}
		
	@Override
	public Pair<String, Map<String, Object>> getDisabledReason(PersonAPI user) {
		CampaignFleetAPI fleet = null;
		if (user != null) {
			if (user.isPlayer()) fleet = Global.getSector().getPlayerFleet();
			else fleet = user.getFleet();
		}
		
		if (side.getData().containsKey(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			Map<String, Object> params = new HashMap<>();
			
			String id = "bombardmentPrevented";
			String desc = GroundBattleIntel.getString("ability_bombard_prevented");
			params.put("desc", desc);
			return new Pair<>(id, params);
		}
		// fuel check
		if (fleet != null) {
			int cost = getFuelCost(fleet);
			float have = fleet.getCargo().getMaxFuel();
			if (user.isPlayer()) {
				have = fleet.getCargo().getFuel();
			}
			if (cost > have) {
				Map<String, Object> params = new HashMap<>();
			
				String id = "notEnoughFuel";
				String desc = String.format(GroundBattleIntel.getString("ability_bombard_insufficientFuel"), cost);
				params.put("desc", desc);
				return new Pair<>(id, params);
			}
		}
		float[] strengths = getNearbyFleetStrengths();
		{
			float ours = strengths[0];
			float theirs = strengths[1];
			if (ours < theirs * 2) {
				Map<String, Object> params = new HashMap<>();
			
				String id = "enemyPresence";
				String ourStr = String.format("%.0f", ours);
				String theirStr = String.format("%.0f", theirs);
				
				String desc = String.format(GroundBattleIntel.getString("ability_bombard_enemyPresence"), ourStr, theirStr);
				params.put("desc", desc);
				return new Pair<>(id, params);
			}
		}
		
		Pair<String, Map<String, Object>> reason = super.getDisabledReason(user);
		return reason;
	}
	
	public int getFuelCost(CampaignFleetAPI fleet) {
		int marketSize = getIntel().getMarket().getSize();
		if (marketSize < 3) marketSize = 3;
		float cost = BASE_COST * marketSize;
		if (fleet != null) {
			cost -= Misc.getFleetwideTotalMod(fleet, Stats.FLEET_BOMBARD_COST_REDUCTION, 0f)/2f;
		}
		
		cost = side.getBombardmentCostMod().computeEffective(cost);
		if (cost < 0) cost = 0;
				
		return Math.round(cost);
	}
	
	public int getDamage() {
		int marketSize = getIntel().getMarket().getSize();
		if (marketSize < 3) marketSize = 3;
		return (int)Math.round(BASE_DAMAGE * marketSize);
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
	
	@Override
	public List<IndustryForBattle> getTargetIndustries() {
		List<IndustryForBattle> targets = new ArrayList<>();
		for (IndustryForBattle ifb : getIntel().getIndustries()) {
			if (ifb.heldByAttacker != getSide().isAttacker() && !ifb.isIndustryTrueDisrupted() 
					&& ifb.getPlugin().getDef().hasTag("noBombard"))
				continue;
			if (!ifb.containsEnemyOf(side.isAttacker())) continue;
			targets.add(ifb);
		}
		return targets;
	}
	
	@Override
	public float getAIUsePriority(GroundBattleAI ai) {
		List<GroundBattleAI.IFBStrengthRecord> industries = ai.getIndustriesWithEnemySorted();
		if (industries.isEmpty()) return 0;
		return industries.get(0).reinforcePriorityCache * 5;
	}
	
	@Override
	public boolean aiExecute(GroundBattleAI ai, PersonAPI user) {
		// find a fleet to execute bombardment
		// NPCs can't use player fleet
		List<CampaignFleetAPI> fleets = getIntel().getSupportingFleets(side.isAttacker());
		if (fleets.isEmpty()) return false;
		
		CampaignFleetAPI fleet = null;
		
		for (CampaignFleetAPI candidate : fleets) {
			if (candidate.isPlayerFleet()) continue;
			if (candidate.getAI() != null) {
				if (candidate.getAI().isFleeing() || candidate.getAI().isMaintainingContact())
					continue;
			}
			int cost = getFuelCost(fleet);
			if (candidate.getCargo().getMaxFuel() >= cost) {
				fleet = candidate;
				break;
			}
		}
		
		if (fleet == null) return false;
		
		// pick a bombardment target: the highest priority industry that is a valid target
		List<GroundBattleAI.IFBStrengthRecord> desiredTargets = ai.getIndustriesWithEnemySorted();
		List<IndustryForBattle> validTargets = getTargetIndustries();
		
		List<IndustryForBattle> targetsFiltered = new LinkedList<>();
		for (GroundBattleAI.IFBStrengthRecord candidate : desiredTargets) {
			IndustryForBattle ifb = candidate.industry;
			targetsFiltered.add(ifb);
		}
		targetsFiltered.removeAll(validTargets);
		if (targetsFiltered.isEmpty()) return false;
		
		target = validTargets.get(0);
		return super.aiExecute(ai, fleet.getCommander());
	}
}
