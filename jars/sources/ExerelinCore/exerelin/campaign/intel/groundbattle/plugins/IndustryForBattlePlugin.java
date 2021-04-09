package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.GroundDefenses;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.campaign.intel.groundbattle.GBDataManager;
import exerelin.campaign.intel.groundbattle.GBDataManager.IndustryDef;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;

public class IndustryForBattlePlugin {
	
	IndustryForBattle indForBattle;
	protected String defId;
	
	public void init(String defId, IndustryForBattle ind) {
		this.defId = defId;
		this.indForBattle = ind;
	}
	
	protected IndustryDef getDef() {
		return GBDataManager.getDef(defId);
	}
	
	public Float getTroopContribution(String type) {
		if (indForBattle.getIndustry().isDisrupted()) return 0f;
		
		Float num = getDef().troopCounts.get(type);
		if (num == null) return 0f;
		
		Industry ind = indForBattle.getIndustry();
		if (ind.getSpec().hasTag(Industries.TAG_GROUNDDEFENSES)) 
		{
			if (Commodities.ALPHA_CORE.equals(ind.getAICoreId()))
				num *= 1 + GroundDefenses.ALPHA_CORE_BONUS;
			if (ind.isImproved())
				num *= 1 + GroundDefenses.IMPROVE_DEFENSE_BONUS;
		}
		
		return num;
	}
	
	// TODO: load plugins by classpath string
	public static IndustryForBattlePlugin loadPlugin(String defId, IndustryForBattle ind) 
	{
		IndustryForBattlePlugin plugin = new IndustryForBattlePlugin();
		plugin.init(defId, ind);
		return plugin;
	}
}
