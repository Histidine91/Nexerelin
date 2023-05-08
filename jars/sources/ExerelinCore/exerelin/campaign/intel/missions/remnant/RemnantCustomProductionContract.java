package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.CustomProductionContract;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;

public class RemnantCustomProductionContract extends CustomProductionContract {
	
	public static final float COST_MULT = 1.4f;
	
	static {
		//PROD_DAYS = 1;
	}
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		//genRandom = Misc.random;
		//Global.getLogger(this.getClass()).info("Trying Remnant production contract");
		
		PersonAPI person = getPerson();
		if (person == null) return false;
		
		if (!setPersonMissionRef(person, "$cpc_ref")) {
			Global.getLogger(this.getClass()).info("Mission ref already exists");
			return false;
		}
		
		market = getPerson().getMarket();
		if (market == null) {
			Global.getLogger(this.getClass()).info("No market");
			return false;
		}
		if (Misc.getStorage(market) == null) {
			Global.getLogger(this.getClass()).info("No storage on market");
			return false;
		}
		
		faction = person.getFaction();
		
		if (true) { // don't care about ship production, since it's just acquisition from wherever
			PersonImportance imp = getPerson().getImportance();
			float mult = DEALER_MULT.get(imp);
			maxCapacity = getRoundNumber(mult * 
						(DEALER_MIN_CAPACITY + (DEALER_MAX_CAPACITY - DEALER_MIN_CAPACITY) * getQuality()));
		}
				
		costMult = COST_MULT;	//1f - MILITARY_MAX_COST_DECREASE * getRewardMultFraction();
		addMilitaryBlueprints();
		if (ships.isEmpty() && weapons.isEmpty() && fighters.isEmpty()) return false;
		
		setStartingStage(Stage.WAITING);
		setSuccessStage(Stage.DELIVERED);
		setFailureStage(Stage.FAILED);
		setNoAbandon();
		
		connectWithDaysElapsed(Stage.WAITING, Stage.DELIVERED, PROD_DAYS);
		setStageOnMarketDecivilized(Stage.FAILED, market);
		
		return true;
	}
	
	@Override
	protected void updateInteractionDataImpl() {
		super.updateInteractionDataImpl();
		set("$cpc_remnant", true);
	}
	
	@Override
	protected void addMilitaryBlueprints() {
		for (String id : faction.getKnownShips()) {
			ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
			if (spec.hasTag(Tags.NO_SELL)) continue;
			if (spec.hasTag(Tags.RESTRICTED)) continue;
			if (spec.getHints().contains(ShipTypeHints.STATION)) continue;
			if (spec.getHints().contains(ShipTypeHints.UNBOARDABLE) && !spec.getTags().contains(Tags.AUTOMATED_RECOVERABLE)) 
			{
				continue;
			}
			ships.add(id);
		}
		for (String id : faction.getKnownWeapons()) {
			WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(id);
			if (spec.hasTag(Tags.NO_DROP)) continue;
			if (spec.hasTag(Tags.NO_SELL)) continue;
			weapons.add(id);
		}
		for (String id : faction.getKnownFighters()) {
			FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
			if (spec.hasTag(Tags.NO_DROP)) continue;
			//if (spec.hasTag(Tags.NO_SELL)) continue;
			fighters.add(id);
		}
	}
}
