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
	
	void init(GroundBattleIntel intel);

	void onBattleStart();
	void onPlayerJoinBattle();
	
	/**
	 * Called at least once per turn.
	 */
	void apply();
	
	/**
	 * Called at least once per turn.
	 */
	void unapply();
	
	void advance(float days);
	
	void beforeTurnResolve(int turn);
	
	/**
	 * Called twice per turn, before each combat resolution.
	 * @param turn
	 * @param numThisTurn Is this the first or second combat event this turn?
	 */
	void beforeCombatResolve(int turn, int numThisTurn);
	
	void afterTurnResolve(int turn);

	void reportUnitCreated(GroundUnit unit);	
	void reportUnitMoved(GroundUnit unit, IndustryForBattle previousLoc);
	
	/**
	 * Deprecated, use {@code modifyAttackStat} instead.
	 */
	@Deprecated
	MutableStat modifyDamageDealt(GroundUnit unit, MutableStat dmg);

	/**
	 * Modifies the unit's "inherent" attack strength. Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param attack The unit's attack stat.
	 * @return The now-modified attack stat, returned if method chaining is desired.
	 */
	MutableStat modifyAttackStat(GroundUnit unit, MutableStat attack);

	/**
	 * Modifies the unit's attack bonuses from external factors. Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param attack The unit's attack bonus.
	 * 	 * @return The now-modified attack bonus, returned if method chaining is desired.
	 */
	StatBonus modifyAttackStatBonus(GroundUnit unit, StatBonus attack);
	
	/**
	 * Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param dmg The incoming damage value.
	 * @return The new damage value.
	 */
	@Deprecated
	float modifyDamageReceived(GroundUnit unit, float dmg);
	
	/**
	 * Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param dmg The incoming damage value.
	 * @return The new damage value.
	 */
	MutableStat modifyDamageReceived(GroundUnit unit, MutableStat dmg);
	
	/**
	 * Note: Consider modifying the {@code StatBonus} for the relevant {@code GroundBattleSide}, instead.
	 * @param unit
	 * @param dmg The incoming damage value.
	 * @return The new damage value.
	 */
	float modifyMoraleDamageReceived(GroundUnit unit, float dmg);
	
	/**
	 * Generates an entry for the intel's modifier display. Should have an icon, name and tooltip.
	 * @param info The element to add the tooltip too.
	 * @param outer
	 * @param width
	 * @param pad Padding to apply when adding the new element.
	 * @param isAttacker Indicates the section the entry is being added to. 
	 * True if attacker, false if defender, null if common.
	 */
	void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker);

	/**
	 * If this returns false, plugin should remove itself after turn resolution.
	 * @return
	 */
	boolean isDone();

	float getSortOrder();
}
