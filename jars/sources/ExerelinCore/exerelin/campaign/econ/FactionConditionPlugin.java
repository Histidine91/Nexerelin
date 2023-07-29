package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.econ.faction.FactionConditionSubplugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.util.Map;

@Log4j
public class FactionConditionPlugin extends BaseMarketConditionPlugin {
	
	public static final String CONDITION_ID = "nex_faction_condition";
	
	protected String factionId;	
	@Getter public FactionConditionSubplugin subplugin;
	protected transient String lastModId;
	
	public MarketAPI getMarket() {
		return market;
	}
	
	@Override
	public void init(MarketAPI market, MarketConditionAPI condition) {
		super.init(market, condition);
		factionId = market.getFactionId();
		regenerateSubplugin();
	}
	
	@Override
	public void apply(String id) {
		lastModId = id;
		if (subplugin != null) subplugin.apply(id);
	}
	
	@Override
	public void unapply(String id) {
		lastModId = id;
		if (subplugin != null) subplugin.unapply(id);
	}
	
	/*
	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
		if (subplugin != null) subplugin.createTooltip(tooltip, expanded);
		super.createTooltip(tooltip, expanded);
	}
	*/
	
	@Override
	protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
		if (subplugin != null) subplugin.createTooltip(tooltip, expanded);
		//super.createTooltipAfterDescription(tooltip, expanded);
	}
	
	@Override
	public boolean isTooltipExpandable() {
		if (subplugin != null) return subplugin.isTooltipExpandable();
		return false;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> tokens = super.getTokenReplacements();
		Map<String, String> tokens2 = null;
		if (subplugin != null) {
			tokens2 = subplugin.getTokenReplacements();
			if (tokens2 != null) tokens.putAll(tokens2);
		}
		return tokens;
	}
	
	@Override
	public String getIconName() {
		if (subplugin != null) return subplugin.getIconName();
		return Global.getSector().getFaction(factionId).getCrest();
	}
	
	@Override
	public boolean showIcon() {
		return subplugin != null;
	}
	
	@Override
	public String getName() {
		if (subplugin != null) return subplugin.getName();
		return Global.getSector().getFaction(factionId).getDisplayName();
	}
	
	public void checkForRegen() {
		String mfid = market.getFactionId();
		boolean regen = !mfid.equals(factionId);
		
		if (!regen) {
			String plugin = NexConfig.getFactionConfig(mfid).factionConditionSubplugin;
			String currClass = subplugin != null ? subplugin.getClass().getName() : "";
			regen = !currClass.equals(plugin);
		}
		
		if (regen) {
			unapply(lastModId);
			factionId = mfid;
			regenerateSubplugin();
			apply(lastModId);
		}
	}
	
	public void regenerateSubplugin() {
		subplugin = loadPlugin(factionId);
	}
	
	public FactionConditionSubplugin loadPlugin(String factionId) 
	{
		String className = NexConfig.getFactionConfig(factionId).factionConditionSubplugin;
		if (className == null) return null;
		FactionConditionSubplugin plugin = (FactionConditionSubplugin)NexUtils.instantiateClassByName(className);
		plugin.init(factionId, this);
		
		return plugin;
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_factionConditions", id, ucFirst);
	}
	
	@Override
	public boolean isTransient() {
		return false;
	}
}
