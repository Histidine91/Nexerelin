package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import exerelin.campaign.intel.groundbattle.GBDataManager;
import exerelin.campaign.intel.groundbattle.GBDataManager.ConditionDef;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsGUI.CustomPanelGenResult;

public class MarketConditionPlugin extends BaseGroundBattlePlugin {
	
	protected String conditionId;
		
	public void init(GroundBattleIntel intel, String conditionId) {
		init(intel);
		this.conditionId = conditionId;
	}
	
	protected ConditionDef getDef() {
		return GBDataManager.getConditionDef(conditionId);
	}
	
	public boolean isApplicable() {
		return intel.getMarket().hasCondition(conditionId);
	}
	
	@Override
	public void apply() {
		if (!isApplicable()) {
			intel.getMarketConditionPlugins().remove(this);
		}
	}
	
	@Override
	public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker) {		
				
		if (isAttacker != null) {
			return;
		}
		MarketConditionAPI cond = intel.getMarket().getCondition(conditionId);
		
		if (cond == null) return;
		
		TooltipCreator tt = getModifierTooltip();
		
		String icon = cond.getSpec().getIcon();
		CustomPanelGenResult gen = NexUtilsGUI.addPanelWithFixedWidthImage(outer, 
				null, width, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, cond.getName(), 
				width - GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT - 8, 8, 
				icon, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, 3, 
				null, true, tt);
		
		info.addCustom(gen.panel, pad);
	}
	
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
		ConditionDef def = getDef();
		String str = def.desc;
		LabelAPI label = tooltip.addPara(str, 0);
		if (def.highlights != null)
			label.setHighlight(def.highlights.toArray(new String[0]));
	}
	
	public static MarketConditionPlugin loadPlugin(GroundBattleIntel intel, String defId) 
	{
		String className = GBDataManager.getConditionDef(defId).plugin;
		if (className == null) className = "exerelin.campaign.intel.groundbattle.plugins.MarketConditionPlugin";
		
		MarketConditionPlugin plugin = null;
		
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(className);
			plugin = (MarketConditionPlugin)clazz.newInstance();
			plugin.init(intel, defId);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
			Global.getLogger(IndustryForBattlePlugin.class).error("Failed to load market condition plugin " + defId, ex);
		}
		
		return plugin;
	}
}
