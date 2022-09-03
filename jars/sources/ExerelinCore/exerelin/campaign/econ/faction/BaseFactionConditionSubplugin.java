package exerelin.campaign.econ.faction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.econ.FactionConditionPlugin;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import lombok.Getter;

public abstract class BaseFactionConditionSubplugin implements FactionConditionSubplugin {
	
	@Getter protected String factionId;
	protected FactionConditionPlugin plugin;
	
	@Override
	public void init(String factionId, FactionConditionPlugin plugin) {
		this.factionId = factionId;
		this.plugin = plugin;
	}
	
	@Override
	public void apply(String id) {
		
	}
	
	@Override
	public void unapply(String id) {
		
	}
	
	@Override
	public void advance(float amount) {
		
	}
	
	// basic tokens and such will be inherited by the main plugin from BaseMarketConditionPlugin	
	@Override
	public Map<String, String> getTokenReplacements() {
		return null;
	}
	
	@Override
	public String[] getHighlights() {
		return null;
	}
	
	@Override
	public Color[] getHighlightColors() {
		return null;
	}
	
	@Override
	public void addTokensToList(List<String> list, String ... keys) {
		
	}
	
	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
		
	}	
	
	@Override
	public boolean isTooltipExpandable() {
		return false;
	}
	
	@Override
	public float getTooltipWidth() {
		return 500;
	}
	
	@Override
	public boolean runWhilePaused() {
		return false;
	}
	
	@Override
	public String getIconName() {
		return Global.getSector().getFaction(factionId).getCrest();
	}
	
	public String getName() {
		return Global.getSector().getFaction(factionId).getDisplayName();
	}
	
}
