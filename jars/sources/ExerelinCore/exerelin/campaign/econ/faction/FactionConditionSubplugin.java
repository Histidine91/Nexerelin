package exerelin.campaign.econ.faction;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.econ.FactionConditionPlugin;
import java.awt.Color;
import java.util.List;
import java.util.Map;

public interface FactionConditionSubplugin {
	
	public void init(String factionId, FactionConditionPlugin plugin);
	
	public void apply(String id);
	public void unapply(String id);		
	public void advance(float amount);

	public Map<String, String> getTokenReplacements();
	public String[] getHighlights();
	public Color[] getHighlightColors();
	public void addTokensToList(List<String> list, String ... keys);
	
	//public void createTooltip(TooltipMakerAPI tooltip, boolean expanded);	
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded);
	public boolean isTooltipExpandable();
	public float getTooltipWidth();
	
	public boolean runWhilePaused();
	public String getIconName();
	public String getName();
}
