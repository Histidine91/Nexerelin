package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GBDataManager;
import exerelin.campaign.intel.groundbattle.GBDataManager.ConditionDef;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsGUI.CustomPanelGenResult;
import exerelin.utilities.StringHelper;

public class MarketConditionPlugin extends BaseGroundBattlePlugin {
	
	protected String conditionId;
	protected Boolean isAttacker;
		
	public void init(GroundBattleIntel intel, String conditionId) {
		init(intel);
		this.conditionId = conditionId;
	}
	
	protected ConditionDef getDef() {
		return GBDataManager.getConditionDef(conditionId);
	}

	public MarketConditionAPI getCondition() {
		return intel.getMarket().getCondition(conditionId);
	}
	
	public boolean isApplicable() {
		return intel.getMarket().hasCondition(conditionId);
	}
	
	@Override
	public void apply() {
		if (!isApplicable()) {
			intel.getMarketConditionPlugins().remove(this);
			return;
		}
		
		ConditionDef def = getDef();
		if (def.tags.contains(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			GroundBattleIntel.applyTagWithReason(intel.getSide(true).getData(), GBConstants.TAG_PREVENT_BOMBARDMENT, 
					conditionId);
			GroundBattleIntel.applyTagWithReason(intel.getSide(false).getData(), GBConstants.TAG_PREVENT_BOMBARDMENT, 
					conditionId);
		}
		
		if (def.tags.contains(GBConstants.TAG_PREVENT_EW)) {
			GroundBattleIntel.applyTagWithReason(intel.getSide(true).getData(), GBConstants.TAG_PREVENT_EW, 
					conditionId);
			GroundBattleIntel.applyTagWithReason(intel.getSide(false).getData(), GBConstants.TAG_PREVENT_EW, 
					conditionId);
		}
		
		if (def.tags.contains(GBConstants.TAG_PREVENT_INSPIRE)) {
			GroundBattleIntel.applyTagWithReason(intel.getSide(true).getData(), GBConstants.TAG_PREVENT_INSPIRE, 
					conditionId);
			GroundBattleIntel.applyTagWithReason(intel.getSide(false).getData(), GBConstants.TAG_PREVENT_INSPIRE, 
					conditionId);
		}
	}
	
	@Override
	public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker) {		
				
		if (isAttacker != this.isAttacker) {
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
				getDef().color, true, tt);
		
		info.addCustom(gen.panel, pad);
	}
	
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
		ConditionDef def = getDef();
		String str = def.desc;
		str = StringHelper.substituteMarketTokens(str, intel.getMarket());
		LabelAPI label = tooltip.addPara(str, 0);
		if (def.highlights != null)
			label.setHighlight(def.highlights.toArray(new String[0]));
	}
	
	public static MarketConditionPlugin loadPlugin(GroundBattleIntel intel, String defId) 
	{
		String className = GBDataManager.getConditionDef(defId).plugin;
		if (className == null) className = "exerelin.campaign.intel.groundbattle.plugins.MarketConditionPlugin";
		
		MarketConditionPlugin plugin = (MarketConditionPlugin) NexUtils.instantiateClassByName(className);
		plugin.init(intel, defId);
		
		return plugin;
	}
}
