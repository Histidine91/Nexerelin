package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import org.jetbrains.annotations.NotNull;

public abstract class BaseGroundBattlePlugin implements GroundBattlePlugin {
	
	protected GroundBattleIntel intel;
	
	@Override
	public void init(GroundBattleIntel intel) 
	{
		this.intel = intel;
	}

	@Override
	public void onBattleStart() {}

	@Override
	public void onPlayerJoinBattle() {}

	@Override
	public void apply() {}
	
	@Override
	public void unapply() {}

	@Override
	public void advance(float days) {}
	
	@Override
	public void beforeTurnResolve(int turn) {}
	
	@Override
	public void beforeCombatResolve(int turn, int numThisTurn) {}
	
	@Override
	public void afterTurnResolve(int turn) {
		if (isDone()) {
			unapply();
			intel.removePlugin(this);
		}
	}

	@Override
	public void reportUnitCreated(GroundUnit unit) {}
	
	@Override
	public void reportUnitMoved(GroundUnit unit, IndustryForBattle lastLoc) {}
	
	@Override
	public MutableStat modifyDamageDealt(GroundUnit unit, MutableStat dmg) {
		return dmg;
	}

	@Override
	public MutableStat modifyAttackStat(GroundUnit unit, MutableStat dmg) {
		return dmg;
	}

	@Override
	public StatBonus modifyAttackStatBonus(GroundUnit unit, StatBonus attack) {
		return attack;
	}

	@Override
	@Deprecated
	public float modifyDamageReceived(GroundUnit unit, float dmg) {
		return dmg;
	}
	
	@Override
	public MutableStat modifyDamageReceived(GroundUnit unit, MutableStat dmg) {
		return dmg;
	}
	
	@Override
	public float modifyMoraleDamageReceived(GroundUnit unit, float dmg) {
		return dmg;
	}
	
	@Override
	public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker) {
		
	}
	
	public TooltipCreator getModifierTooltip() {
		return new TooltipCreator() {
				@Override
				public boolean isTooltipExpandable(Object tooltipParam) {
					return false;
				}

				@Override
				public float getTooltipWidth(Object tooltipParam) {
					return 360;
				}

				@Override
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
					processTooltip(tooltip, expanded, tooltipParam);
				}
		};
	}
	
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
		
	}
	
	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public float getSortOrder() {
		return 0;
	}

	@Override
	public int compareTo(@NotNull GroundBattlePlugin o) {
		return Float.compare(this.getSortOrder(), o.getSortOrder());
	}
}
