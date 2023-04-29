package exerelin.campaign.skills;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.utilities.StringHelper;

public class NexSkills {
	
	public static final String TACTICAL_DRILLS_EX = "nex_tactical_drills_ex";
	public static final String AUXILIARY_SUPPORT_EX = "nex_auxiliary_support_ex";
	public static final String BULK_TRANSPORT_EX = "nex_bulk_transport_ex";
	public static final String MAKESHIFT_EQUIPMENT_EX = "nex_makeshift_equipment_ex";
	public static final String FORCE_CONCENTRATION_EX = "nex_force_concentration_ex";
	
	public static String getString(String id) {
		return StringHelper.getString("nex_skills", id);
	}
	
	public static class AgentBonus implements CharacterStatsSkillEffect {
		public static final int BONUS_AGENTS = 1;
		
		@Override
		public void apply(MutableCharacterStatsAPI stats, String id, float level) {
			stats.getDynamic().getStat("nex_max_agents").modifyFlat(id, BONUS_AGENTS);
		}

		@Override
		public void unapply(MutableCharacterStatsAPI stats, String id) {
			stats.getDynamic().getStat("nex_max_agents").unmodify(id);
		}
		
		@Override
		public String getEffectDescription(float level) {
			return "+" + (int) BONUS_AGENTS + " " + StringHelper.getString("nex_agents", "skillBonusAgents");
		}
		
		@Override
		public String getEffectPerLevelDescription() {
			return null;
		}

		@Override
		public ScopeDescription getScopeDescription() {
			return ScopeDescription.NONE;
		}
	}
	
	public static class TacticalDrillsEx1 implements MarketSkillEffect {
		public static final float DEFEND_BONUS = 50;
		
		public void apply(MarketAPI market, String id, float level) {
			String desc = getString("tacticalDrillsEx");
			market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(id, 1f + DEFEND_BONUS * 0.01f, desc);
		}

		public void unapply(MarketAPI market, String id) {
			market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodify(id);
		}
		
		public String getEffectDescription(float level) {
			return String.format(getString("tacticalDrillsExDesc1"), (int)Math.round(DEFEND_BONUS) + "%");
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}

		public ScopeDescription getScopeDescription() {
			return ScopeDescription.GOVERNED_OUTPOST;
		}
	}
	
	public static class TacticalDrillsEx2 implements MarketSkillEffect {
		public static final float MORALE_DMG_MULT = 0.9f;
		
		public void apply(MarketAPI market, String id, float level) {
			String desc = getString("tacticalDrillsEx");
			market.getStats().getDynamic().getMod(GBConstants.STAT_MARKET_MORALE_DAMAGE).modifyMult(id, MORALE_DMG_MULT, desc);
		}

		public void unapply(MarketAPI market, String id) {
			market.getStats().getDynamic().getMod(GBConstants.STAT_MARKET_MORALE_DAMAGE).unmodify();
		}
		
		public String getEffectDescription(float level) {
			return String.format(getString("tacticalDrillsExDesc2"), StringHelper.toPercent(1 - MORALE_DMG_MULT));
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}

		public ScopeDescription getScopeDescription() {
			return ScopeDescription.GOVERNED_OUTPOST;
		}
	}
	
	public static class AuxiliarySupportEx1 implements MarketSkillEffect {
		public static final float FLEET_SIZE = 20f;
		
		public void apply(MarketAPI market, String id, float level) {
			String desc = getString("auxiliarySupportEx");
			market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat(id, FLEET_SIZE / 100f, desc);
		}

		public void unapply(MarketAPI market, String id) {
			market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyFlat(id);
		}
		
		public String getEffectDescription(float level) {
			return String.format(getString("auxiliarySupportExDesc1"), (int)Math.round(FLEET_SIZE) + "%");
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}

		public ScopeDescription getScopeDescription() {
			return ScopeDescription.GOVERNED_OUTPOST;
		}
	}
	
	public static class AuxiliarySupportEx2 implements MarketSkillEffect {
		public static final float DEFEND_BONUS = 25f;
		
		public void apply(MarketAPI market, String id, float level) {
			String desc = getString("auxiliarySupportEx");
			market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(id, 1f + DEFEND_BONUS * 0.01f, desc);
		}

		public void unapply(MarketAPI market, String id) {
			market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodify(id);
		}
		
		public String getEffectDescription(float level) {
			return String.format(getString("auxiliarySupportExDesc3"), (int)Math.round(DEFEND_BONUS) + "%");
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}

		public LevelBasedEffect.ScopeDescription getScopeDescription() {
			return LevelBasedEffect.ScopeDescription.GOVERNED_OUTPOST;
		}
	}
	
	public static class BulkTransportEx1 implements MarketSkillEffect {
		public static final float ACCESS = 0.3f;
		
		public void apply(MarketAPI market, String id, float level) {
			String desc = getString("bulkTransportEx");
			market.getAccessibilityMod().modifyFlat(id, ACCESS, desc);
		}

		public void unapply(MarketAPI market, String id) {
			market.getAccessibilityMod().unmodifyFlat(id);
		}
		
		public String getEffectDescription(float level) {
			return String.format(getString("bulkTransportExDesc1"), StringHelper.toPercent(ACCESS));
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}

		public ScopeDescription getScopeDescription() {
			return ScopeDescription.GOVERNED_OUTPOST;
		}
	}
	
	public static class MakeshipEquipmentEx1 implements CharacterStatsSkillEffect {
		public static final int DEMAND_REDUCTION = 1;
		
		public void apply(MutableCharacterStatsAPI stats, String id, float level) {
			String desc = getString("makeshipEquipmentEx");
			stats.getDynamic().getMod(Stats.DEMAND_REDUCTION_MOD).modifyFlat(id, DEMAND_REDUCTION, desc);
		}

		public void unapply(MutableCharacterStatsAPI stats, String id) {
			stats.getDynamic().getMod(Stats.DEMAND_REDUCTION_MOD).unmodifyFlat(id);
		}
		
		public String getEffectDescription(float level) {
			return String.format(getString("makeshipEquipmentExDesc1"), DEMAND_REDUCTION);
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}

		public LevelBasedEffect.ScopeDescription getScopeDescription() {
			return LevelBasedEffect.ScopeDescription.GOVERNED_OUTPOST;
		}
	}
	
	public static class MakeshipEquipmentEx2 implements MarketSkillEffect {
		public static final float UPKEEP_MULT = 0.9f;
		
		public void apply(MarketAPI market, String id, float level) {
			String desc = getString("makeshipEquipmentEx");
			market.getUpkeepMult().modifyMult(id, UPKEEP_MULT, desc);
		}

		public void unapply(MarketAPI market, String id) {
			market.getUpkeepMult().unmodifyPercent(id);
		}
		
		public String getEffectDescription(float level) {
			float percent = Math.round((1 - UPKEEP_MULT) * 100f);
			return String.format(getString("makeshipEquipmentExDesc2"), Math.round(percent));
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}

		public LevelBasedEffect.ScopeDescription getScopeDescription() {
			return LevelBasedEffect.ScopeDescription.GOVERNED_OUTPOST;
		}
	}

	public static class ForceConcentrationEx1 implements ShipSkillEffect {
		public static final int ZERO_FLUX_SPEED_BONUS = 50;

		public String getEffectDescription(float level) {
			return String.format(getString("forceConcentrationExDesc1"), ZERO_FLUX_SPEED_BONUS);
		}

		public String getEffectPerLevelDescription() {
			return null;
		}

		public ScopeDescription getScopeDescription() {
			return ScopeDescription.ALL_SHIPS;
		}

		@Override
		public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id, float level) {
			stats.getZeroFluxSpeedBoost().modifyFlat(id, ZERO_FLUX_SPEED_BONUS);
		}
		
		@Override
		public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id) {
			stats.getZeroFluxSpeedBoost().unmodify(id);
		}
	}
}
