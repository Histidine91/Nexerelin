package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.econ.impl.GroundDefenses;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import exerelin.campaign.intel.groundbattle.GBDataManager.IndustryDef;
import exerelin.campaign.intel.groundbattle.GroundBattleSide;
import java.util.List;

public class GroundDefensesPlugin extends IndustryForBattlePlugin {
	
	@Override
	public Float getTroopContribution(String type) {
		Float contrib = super.getTroopContribution(type);
		if (contrib == 0) return contrib;
				
		List<SpecialItemData> specialItems = indForBattle.getIndustry().getVisibleInstalledItems();
		for (SpecialItemData item : specialItems) {
			if (item.getId().equals(Items.DRONE_REPLICATOR)) {
				contrib *= ItemEffectsRepo.DRONE_REPLICATOR_BONUS_MULT;
				Global.getLogger(this.getClass()).info("  Applying drone replicator bonus");
			}
		}
		
		String aiCoreId = indForBattle.getIndustry().getAICoreId();
		if (Commodities.ALPHA_CORE.equals(aiCoreId))
			contrib *= GroundDefenses.ALPHA_CORE_BONUS;
		
		return contrib;
	}
	
	/**
	 * e.g. Bonus of 3 with mult of 2 becomes 1.5
	 * @param bonus
	 * @param mult
	 * @return 
	 */
	protected float multiplyBonus(float bonus, float mult) {
		bonus--;
		bonus *= mult;
		bonus++;
		return bonus;
	}
	
	@Override
	public void apply() {
		super.apply();
		
		if (indForBattle.isIndustryTrueDisrupted()) {
			return;
		}
		
		IndustryDef def = getDef();
		GroundBattleSide otherSide = indForBattle.getNonHoldingSide();
		float dropMult = def.enemyDropCostMult, bombardMult = def.enemyBombardmentCostMult, attritionFactor = def.dropAttritionFactor;
		if (otherSide.getData().containsKey("ability_ew_active")) {
			float mult = EWAbilityPlugin.GROUND_DEF_EFFECT_MULT;
			dropMult = multiplyBonus(dropMult, mult);
			bombardMult = multiplyBonus(bombardMult, mult);
			attritionFactor *= mult;
		}
		
		otherSide.getDropCostMod().modifyMult(indForBattle.getIndustry().getId(), dropMult, 
				indForBattle.getName());
		
		otherSide.getBombardmentCostMod().modifyMult(defId, bombardMult, 
				indForBattle.getName());
		
		otherSide.getDropAttrition().modifyFlat(defId, attritionFactor, 
				indForBattle.getName());
	}
}
