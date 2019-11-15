package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.StatBonus;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

public class ReinforcedDefenses extends BaseMarketConditionPlugin {
	public static final String CONDITION_ID = "nex_reinforced_defenses";
	public static final float BASE_TIME = 60;
	public static final float DEFENSE_MULT = 1.25f;
	
	protected float timeRemaining = BASE_TIME;
		
	@Override
	public void apply(String id) {
		StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		defender.modifyMult(id, DEFENSE_MULT, StringHelper.getString("exerelin_markets", "reinforcedDefensesModDesc"));
	}

	@Override
	public void unapply(String id) {
		market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodify(id);
	}
	
	@Override
	public void advance(float amount) {
		if (!market.isInEconomy()) {
			market.removeSpecificCondition(condition.getIdForPluginModifications());
			return;
		}
		float days = Global.getSector().getClock().convertToDays(amount);
		timeRemaining -= days;
		
		if (timeRemaining <= 0)
			market.removeSpecificCondition(condition.getIdForPluginModifications());
	}
	
	@Override
	protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) 
	{
		String str = StringHelper.getString("exerelin_markets", "reinforcedDefensesTooltip");
		tooltip.addPara(str, 10, Misc.getHighlightColor(), DEFENSE_MULT + "", Math.round(timeRemaining) + "");
	}
	
	public void extend(float days) {
		timeRemaining += days;
	}
}