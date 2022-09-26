package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.List;
import java.util.Map;
		
public class SellAICore extends HubMissionWithBarEvent {	
	
	public static enum Stage {
		TALK_TO_PERSON,
		COMPLETED,
		FAILED,
	}
	public static enum Variation {
		BETA,
		BETA_WITH_GAMMA,
		ALPHA
	}
	
	public static float COST_MULT = 2;
	
	protected Variation variation;
	protected String commodityId;
	protected String commodityId2;
	protected int price;
	
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
				
		PersonAPI person = getPerson();
		if (person == null) return false;
		MarketAPI market = person.getMarket();
		if (market == null) return false;
		
		if (!setPersonMissionRef(person, "$nex_sellAICore_ref")) {
			return false;
		}
		
		if (barEvent) {
			setGiverIsPotentialContactOnSuccess();
		}
		
		variation = pickVariation();
		if (variation == null) return false;

		if (variation == Variation.ALPHA) {
			setRepPersonChangesLow();
			setRepFactionChangesVeryLow();
		} else {
			setRepPersonChangesVeryLow();
			setRepFactionChangesTiny();
		}
		
		return true;
	}
	
	protected Variation pickVariation() {
		RepLevel rep = getPerson().getRelToPlayer().getLevel();

		WeightedRandomPicker<Variation> picker = new WeightedRandomPicker<>(genRandom);
		switch (rep) {
			case COOPERATIVE:
				picker.add(Variation.ALPHA, 1);
				picker.add(Variation.BETA_WITH_GAMMA, 2);
				break;
			case FRIENDLY:
				picker.add(Variation.BETA_WITH_GAMMA, 1);
				break;
			case WELCOMING:
				picker.add(Variation.BETA, 1);
				break;
			default:
				return null;
		}
		Variation var = picker.pick();
		switch (var) {
			case ALPHA:
				commodityId = Commodities.ALPHA_CORE;
				break;
			case BETA:
				commodityId = Commodities.BETA_CORE;
				break;
			case BETA_WITH_GAMMA:
				commodityId = Commodities.BETA_CORE;
				commodityId2 = Commodities.GAMMA_CORE;
				break;
		}
		price = Math.round(getSpec(commodityId).getBasePrice());
		if (commodityId2 != null) {
			price += Math.round(getSpec(commodityId2).getBasePrice());
		}
		price *= COST_MULT;
		
		return var;
	}
	
	@Override
	public boolean callEvent(String ruleId, InteractionDialogAPI dialog, 
			List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String action = params.get(0).getString(memoryMap);
		if ("transact".equals(action)) {
			CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
			cargo.addCommodity(commodityId, 1);
			AddRemoveCommodity.addCommodityGainText(commodityId, 1, dialog.getTextPanel());
			if (commodityId2 != null) {
				cargo.addCommodity(commodityId2, 1);
				AddRemoveCommodity.addCommodityGainText(commodityId2, 1, dialog.getTextPanel());
			}
			cargo.getCredits().subtract(price);
			AddRemoveCommodity.addCreditsLossText(price, dialog.getTextPanel());
			return true;
		}
		
		return super.callEvent(ruleId, dialog, params, memoryMap);
	}
	
	@Override
	protected void updateInteractionDataImpl() {
		set("$nex_sellAICore_ref2", this);
		
		set("$nex_sellAICore_commodityId", commodityId);
		set("$nex_sellAICore_commodityName", getSpec(commodityId).getName());
		if (commodityId2 != null) {
			set("$nex_sellAICore_commodityId2", commodityId2);
			set("$nex_sellAICore_commodityName2", getSpec(commodityId2).getName());
		}
		//set("$nex_sellAICore_quantity", quantity);
		//set("$nex_sellAICore_quantity2", quantity2);
		set("$nex_sellAICore_price", price);
		set("$nex_sellAICore_variation", variation);
		set("$nex_sellAICore_manOrWoman", getPerson().getManOrWoman());
	}
	
	@Override
	public void accept(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		// if it's the local variation, there's no intel item and the commodity/credits etc is handled
		// in the rules csv. Need to abort here, though, so that mission ref is unset from person memory

		currentStage = new Object(); // so that the abort() assumes the mission was successful
		abort();
	}
	
	protected CommoditySpecAPI getSpec(String commodityId) {
		return Global.getSettings().getCommoditySpec(commodityId);
	}
}
