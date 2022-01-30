package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GBDataManager;
import exerelin.campaign.intel.groundbattle.GBDataManager.AbilityDef;
import exerelin.campaign.intel.groundbattle.GroundBattleAI;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleSide;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.dialog.AbilityDialogPlugin;
import exerelin.utilities.NexUtilsGUI;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EWAbilityPlugin extends AbilityPlugin {
	
	public static final String MEMORY_KEY_ECM_CACHE = "$nex_ecmRating_cache";
	public static float BASE_ECM_REQ = 1.5f;	// at size 3
	public static float GROUND_DEF_EFFECT_MULT = 0.7f;
	public static int BASE_COST = 30;	// at size 3
	
	@Override
	public void activate(InteractionDialogAPI dialog, PersonAPI user) {
		super.activate(dialog, user);
		
		int powerLevel = getPowerLevel();
		if (powerLevel <= 0) return;
		int cost = getSupplyCost();
		
		if (user != null && user.isPlayer()) {
			Global.getSector().getPlayerFleet().getCargo().removeSupplies(cost);
			getIntel().getPlayerData().suppliesUsed += cost;
		}
		
		for (GroundUnit unit : getIntel().getSide(!side.isAttacker()).getUnits()) {
			unit.reorganize(powerLevel);
		}
		EWPersistentEffectPlugin pers = new EWPersistentEffectPlugin();
		pers.init(getIntel(), id, side.isAttacker(), powerLevel);
		getIntel().addOtherPlugin(pers);
		getIntel().reapply();
		
		logActivation(user);
	}
	
	@Override
	public Pair<String, Map<String, Object>> getDisabledReason(PersonAPI user) {
		CampaignFleetAPI fleet = null;
		if (user != null) {
			if (user.isPlayer()) fleet = Global.getSector().getPlayerFleet();
			else fleet = user.getFleet();
		}
		
		if (side.getData().containsKey(GBConstants.TAG_PREVENT_EW)) {
			Map<String, Object> params = new HashMap<>();
			
			String id = "ewPrevented";
			String desc = GroundBattleIntel.getString("ability_ew_prevented");
			params.put("desc", desc);
			return new Pair<>(id, params);
		}
		
		int powerLevel = getPowerLevel();
		if (powerLevel <= 0) {
			Map<String, Object> params = new HashMap<>();
			
			String id = "prerequisitesNotMet";
			String desc = GroundBattleIntel.getString("ability_ew_insufficientECM");
			params.put("desc", desc);
			return new Pair<>(id, params);
		}
		
		// supplies check
		if (fleet != null) {
			int cost = getSupplyCost();
			float have = fleet.getCargo().getMaxCapacity() * 0.5f;
			if (user.isPlayer()) {
				have = fleet.getCargo().getSupplies();
			}
			
			if (cost > have) {
				Map<String, Object> params = new HashMap<>();
			
				String id = "notEnoughSupplies";
				String desc = String.format(GroundBattleIntel.getString("ability_ew_insufficientSupplies"), cost);
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
	
	@Override
	public void dialogAddIntro(InteractionDialogAPI dialog) {
		dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_ew_blurb"));
		TooltipMakerAPI tooltip = dialog.getTextPanel().beginTooltip();
		generateTooltip(tooltip);
		dialog.getTextPanel().addTooltip();
		int cost = getSupplyCost();
		boolean canAfford = dialog.getTextPanel().addCostPanel(null,
					Commodities.SUPPLIES, cost, true);
		
		if (!canAfford) {
			dialog.getOptionPanel().setEnabled(AbilityDialogPlugin.OptionId.ACTIVATE, false);
		}
		
		addCooldownDialogText(dialog);
	}

	@Override
	public void generateTooltip(TooltipMakerAPI tooltip) {
		float opad = 10;
		Color h = Misc.getHighlightColor();
		tooltip.addPara(GroundBattleIntel.getString("ability_ew_tooltip1"), 0,
				h, "" + 1, String.format("%.2f×", GROUND_DEF_EFFECT_MULT));
		float needed = getNeededECMLevel();
		tooltip.addPara(GroundBattleIntel.getString("ability_ew_tooltip2"), opad,
				h, Math.round(needed) + "%", Math.round(needed * 2) + "%");
		float curr = getECMLevel();
		Color col = h;
		if (curr >= needed * 2) {
			col = Misc.getPositiveHighlightColor();
		} else if (curr < needed) {
			col = Misc.getNegativeHighlightColor();
		}
		tooltip.addPara(GroundBattleIntel.getString("ability_ew_tooltip3"), opad,
				col, Math.round(curr) + "%");
	}
	
	/**
	 * Gets the proportion of units on the specified side that have been deployed, by strength
	 * @param side
	 * @return [0-1]
	 */
	public float getDeployedProportion(GroundBattleSide side) {
		float deployed = 0;
		float total = 0;
		for (GroundUnit unit : side.getUnits()) {
			float str = unit.getBaseStrength();
			total += str;
			if (unit.getLocation() != null) deployed += str;
		}
		
		if (total == 0) return 0;
		return deployed/total;
	}
	
	public int getSupplyCost() {
		int marketSize = getIntel().getMarket().getSize();
		return (int)Math.round(BASE_COST * Math.pow(2, marketSize - 3));
	}
	
	public float getNeededECMLevel() {
		int marketSize = getIntel().getMarket().getSize();
		return (float)(BASE_ECM_REQ * (marketSize - 1));
	}
	
	public int getPowerLevel() {
		float level = getECMLevel(), needed = getNeededECMLevel();
		if (level >= 2 * needed) {
			return 2;
		}
		else if (level >= needed) {
			return 1;
		}
		return 0;
	}
	
	public float getECMLevel(CampaignFleetAPI fleet) {
		int fleetLevel = 0;
		if (fleet.getMemoryWithoutUpdate().contains(MEMORY_KEY_ECM_CACHE)) {
			fleetLevel = (int)fleet.getMemoryWithoutUpdate().getFloat(MEMORY_KEY_ECM_CACHE);
			//if (!attacker) Global.getLogger(this.getClass()).info("  ECM level (cached): " + fleetLevel);
			return fleetLevel;
		}

		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			fleetLevel += member.getStats().getDynamic().getValue(Stats.ELECTRONIC_WARFARE_FLAT, 0);
		}
		fleet.getMemoryWithoutUpdate().set(MEMORY_KEY_ECM_CACHE, fleetLevel, 0);
		return fleetLevel;
	}
	
	public float getECMLevel() {
		boolean attacker = side.isAttacker();
		
		if (!attacker) {
			PersonAPI leader = side.getCommander();
			if (leader != null && 
					(leader.getStats().getSkillLevel(Skills.ELECTRONIC_WARFARE) >= 1 
					|| leader.getStats().getSkillLevel(Skills.HYPERCOGNITION) >= 1)) 
			{
				return getNeededECMLevel() * 2f;
			}
		}
		
		// can get ECM from all fleets in range
		int level = 0;
		List<CampaignFleetAPI> fleets = getIntel().getSupportingFleets(side.isAttacker());
		for (CampaignFleetAPI fleet : fleets) {
			level += getECMLevel(fleet);
		}
		return level;
	}
	
	@Override
	public float getAIUsePriority(GroundBattleAI ai) {
		GroundBattleIntel intel = getIntel();
		if (intel.getTurnNum() <= 2) {
			if (getDeployedProportion(intel.getSide(!this.side.isAttacker())) < 0.4f)
				return 0;
		}
		return 10;
	}
	
	@Override
	public boolean aiExecute(GroundBattleAI ai, PersonAPI user) {
		// usually this is because it was used by defender leader
		// TODO: fix NPCs using ability and crediting it to player when player is attacker leader
		// this not-player check should suffice I think?
		if (user != null && !user.isPlayer()) {
			return super.aiExecute(ai, user);
		}
		
		// find a fleet to execute EW
		// NPCs can't use player fleet
		List<CampaignFleetAPI> fleets = getIntel().getSupportingFleets(side.isAttacker());
		if (fleets.isEmpty()) return false;
				
		CampaignFleetAPI fleet = null;
		float best = 0;
		
		for (CampaignFleetAPI candidate : fleets) {
			if (candidate.isPlayerFleet()) continue;
			float ecmLevel = getECMLevel(candidate);
			if (ecmLevel > best) {
				fleet = candidate;
				best = ecmLevel;
			}
		}
		if (fleet == null) return false;
		
		user = fleet.getCommander();
		return super.aiExecute(ai, user);
	}
	
	public static class EWPersistentEffectPlugin extends BaseGroundBattlePlugin {
		
		protected String abilityId;
		protected boolean isAttacker;
		protected int timeRemaining;
		
		public void init(GroundBattleIntel intel, String abilityId, boolean isAttacker, int timeRemaining) 
		{
			super.init(intel);
			this.abilityId = abilityId;
			this.isAttacker = isAttacker;
			this.timeRemaining = timeRemaining;
		}
		
		@Override
		public void apply() {
			GroundBattleSide side = intel.getSide(isAttacker);
			GroundBattleIntel.applyTagWithReason(side.getData(), "ability_ew_active", abilityId);
		}
		
		@Override
		public void unapply() {
			GroundBattleSide side = intel.getSide(isAttacker);
			GroundBattleIntel.unapplyTagWithReason(side.getData(), "ability_ew_active", abilityId);
		}
		
		@Override
		public void afterTurnResolve(int turn) {
			timeRemaining--;
			super.afterTurnResolve(turn);
		}
		
		@Override
		public boolean isDone() {
			return timeRemaining <= 0;
		}
		
		@Override
		public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
				float width, float pad, Boolean isAttacker) {

			if (isAttacker == null || !isAttacker.equals(this.isAttacker)) return;
			
			AbilityDef ability = GBDataManager.getAbilityDef(abilityId);
			String icon = ability.icon;

			NexUtilsGUI.CustomPanelGenResult gen = NexUtilsGUI.addPanelWithFixedWidthImage(outer, 
					null, width, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, ability.name, 
					width - GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT - 8, 8, 
					icon, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, 3, 
					ability.color, true, getModifierTooltip());

			info.addCustom(gen.panel, pad);
		}
		
		@Override
		public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
			Color h = Misc.getHighlightColor();
			tooltip.addPara(GroundBattleIntel.getString("ability_ew_tooltip1"), 0,
					h, "" + 1, String.format("%.1f×", GROUND_DEF_EFFECT_MULT));
			tooltip.addPara(GroundBattleIntel.getString("ability_ew_tooltipTimeRemaining"), 3,
					h, timeRemaining + "");
		}
	}
}
