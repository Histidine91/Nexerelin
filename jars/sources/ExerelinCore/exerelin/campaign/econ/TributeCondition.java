package exerelin.campaign.econ;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import java.awt.Color;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

public class TributeCondition extends BaseMarketConditionPlugin implements MarketImmigrationModifier {
	public static final String CONDITION_ID = "nex_tribute_condition";
	public static final float TRIBUTE_INCOME_FACTOR = 0.25f;
	public static final float TRIBUTE_IMMIGRATION_MULT = 0.5f;
	public static final float MAX_SIZE = 5;
	
	protected FactionAPI faction;
		
	@Override
	public void apply(String id) {
		market.addTransientImmigrationModifier(this);
		market.getIncomeMult().modifyMult(id, 1-TRIBUTE_INCOME_FACTOR, getString("cond_incomeDesc"));
	}

	@Override
	public void unapply(String id) {
		market.removeTransientImmigrationModifier(this);
		market.getIncomeMult().unmodify(id);
	}
	
	@Override
	public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
		float mult = TRIBUTE_IMMIGRATION_MULT;
		if (market.getSize() >= MAX_SIZE) mult = 0;
		incoming.getWeight().modifyMult(getModId(), mult, getString("cond_immigrationDesc"));
	}
	
	@Override
	public void advance(float amount) {
		if (!market.isInEconomy()) {
			market.removeSpecificCondition(condition.getIdForPluginModifications());
		}
		
		// TODO: check if system is still controlled by someone else, or grown too large
		// ...do that in the intel code
	}
	
	@Override
	public boolean isTransient() {
		return false;
	}

	@Override
	public void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
		Color h = Misc.getHighlightColor();
		Color n = Misc.getNegativeHighlightColor();
		
		float pad = 3f;
		float small = 5f;
		float opad = 10f;
		
		tooltip.addPara(getString("cond_tooltip1"), opad, n, TRIBUTE_INCOME_FACTOR + "x");
		tooltip.addPara(getString("cond_tooltip2"), pad, n, TRIBUTE_IMMIGRATION_MULT + "x");
		tooltip.addPara(getString("cond_tooltip3"), pad, n, MAX_SIZE + "");
	}

	@Override
	public float getTooltipWidth() {
		return super.getTooltipWidth();
	}

	@Override
	public boolean hasCustomTooltip() {
		return true;
	}
	
	@Override
	public String getIconName() {
		return faction.getCrest();
	}
	
	protected String getString(String key) {
		return StringHelper.getString("nex_tribute", key);
	}
}