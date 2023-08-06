package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;

public interface GroundBattlePlugin extends Comparable<GroundBattlePlugin> {
	
	public static final float MODIFIER_ENTRY_HEIGHT = 32;
	
	public void init(GroundBattleIntel intel);

	public void onBattleStart();
	
	/**
	 * Called at least once per turn.
	 */
	public void apply();
	
	/**
	 * Called at least once per turn.
	 */
	public void unapply();
	
	public void advance(float days);
	
	public void beforeTurnResolve(int turn);
	
	/**
	 * Called twice per turn, before each combat resolution.
	 * @param turn
	 * @param numThisTurn Is this the first or second combat event this turn?
	 */
	public void beforeCombatResolve(int turn, int numThisTurn);
	
	public void afterTurnResolve(int turn);
	
	public void reportUnitMoved(GroundUnit unit, IndustryForBattle previousLoc);
	
	/**
	 * Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param dmg The incoming damage value.
	 * @return The new damage value.
	 */
	public MutableStat modifyDamageDealt(GroundUnit unit, MutableStat dmg);
	
	/**
	 * Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param dmg The incoming damage value.
	 * @return The new damage value.
	 */
	@Deprecated
	public float modifyDamageReceived(GroundUnit unit, float dmg);
	
	/**
	 * Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param dmg The incoming damage value.
	 * @return The new damage value.
	 */
	public MutableStat modifyDamageReceived(GroundUnit unit, MutableStat dmg);
	
	/**
	 * Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param dmg The incoming damage value.
	 * @return The new damage value.
	 */
	public float modifyMoraleDamageReceived(GroundUnit unit, float dmg);
	
	/**
	 * Generates an entry for the intel's modifier display. Should have an icon, name and tooltip.
	 * @param info The element to add the tooltip too.
	 * @param outer
	 * @param width
	 * @param pad Padding to apply when adding the new element.
	 * @param isAttacker Indicates the section the entry is being added to. 
	 * True if attacker, false if defender, null if common.
	 */
	public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker);

	/**
	 * If this returns false, plugin should remove itself after turn resolution.
	 * @return
	 */
	public boolean isDone();

	public float getSortOrder();
}
