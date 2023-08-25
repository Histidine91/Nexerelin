package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
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
	 * Deprecated, use {@code modifyAttackStat} instead.
	 */
	@Deprecated
	public MutableStat modifyDamageDealt(GroundUnit unit, MutableStat dmg);

	/**
	 * Modifies the unit's "inherent" attack strength. Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param attack The unit's attack stat.
	 * @return The now-modified attack stat, returned if method chaining is desired.
	 */
	public MutableStat modifyAttackStat(GroundUnit unit, MutableStat attack);

	/**
	 * Modifies the unit's attack bonuses from external factors. Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param attack The unit's attack bonus.
	 * 	 * @return The now-modified attack bonus, returned if method chaining is desired.
	 */
	public StatBonus modifyAttackStatBonus(GroundUnit unit, StatBonus attack);
	
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
