package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;

public interface GroundBattlePlugin {
	
	public static final float MODIFIER_ENTRY_HEIGHT = 32;
	
	public void init();
	
	public void apply();
	
	public void unapply();
	
	public void beforeTurnResolve(int turn);
	
	public void afterTurnResolve(int turn);
	
	public void reportUnitMoved(GroundUnit unit, IndustryForBattle previousLoc);
	
	/**
	 * Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param dmg The incoming damage value.
	 * @return The new damage value.
	 */
	public float modifyDamageDealt(GroundUnit unit, float dmg);
	
	/**
	 * Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param dmg The incoming damage value.
	 * @return The new damage value.
	 */
	public float modifyDamageReceived(GroundUnit unit, float dmg);
	
	/**
	 * Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param dmg The incoming damage value.
	 * @return The new damage value.
	 */
	public float modifyMoraleDamageReceived(GroundUnit unit, float dmg);
	
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
