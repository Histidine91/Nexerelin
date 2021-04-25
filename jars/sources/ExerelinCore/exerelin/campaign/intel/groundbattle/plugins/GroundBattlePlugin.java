package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public interface GroundBattlePlugin {
	
	public static final float MODIFIER_ENTRY_HEIGHT = 32;
	
	public void init();
	
	public void apply();
	
	public void unapply();
	
	public void beforeTurnResolve(int turn);
	
	public void afterTurnResolve(int turn);
	
	/**
	 * Generates an entry for the intel's modifier display.Should have an icon, name and tooltip.
	 * @param info The element to add the tooltip too.
	 * @param outer
	 * @param width
	 * @param pad Padding to apply when adding the new element.
	 * @param isAttacker Indicates the section the entry is being added to. 
	 * True if attacker, false if defender, null if common.
	 */
	public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker);
	
	public boolean isDone();
}
