package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.CustomProductionContract;
import static com.fs.starfarer.api.impl.campaign.missions.CustomProductionContract.DEALER_MAX_CAPACITY;
import static com.fs.starfarer.api.impl.campaign.missions.CustomProductionContract.DEALER_MIN_CAPACITY;
import static com.fs.starfarer.api.impl.campaign.missions.CustomProductionContract.DEALER_MULT;
import static com.fs.starfarer.api.impl.campaign.missions.CustomProductionContract.PROD_DAYS;
import static com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission.getRoundNumber;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.Map;

public class RemnantCustomProductionContract extends CustomProductionContract {
	
	public static final float COST_MULT = 1.2f;
	
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
		setSuccessStage(Stage.COMPLETED);
		//setSuccessStage(Stage.DELIVERED);	// doing it this way prevents double reporting, but loses the "delivered to" bullet point
		setFailureStage(Stage.FAILED);
		setNoAbandon();
		
		connectWithDaysElapsed(Stage.WAITING, Stage.DELIVERED, PROD_DAYS);
		setStageOnMarketDecivilized(Stage.FAILED, market);
		
		return true;
	}
	
	// see https://fractalsoftworks.com/forum/index.php?topic=21461.msg324415#msg324415
	// note that it causes double notification on completion
	public void setCurrentStage(Object next, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		super.setCurrentStage(next, dialog, memoryMap);
		if (currentStage == Stage.DELIVERED) {
			endSuccess(dialog, memoryMap);
		}
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
			if (spec.getHints().contains(ShipTypeHints.STATION)) continue;
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
