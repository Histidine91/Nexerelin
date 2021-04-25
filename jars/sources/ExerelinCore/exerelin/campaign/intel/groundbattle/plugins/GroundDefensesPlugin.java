package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.econ.impl.GroundDefenses;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
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
}
