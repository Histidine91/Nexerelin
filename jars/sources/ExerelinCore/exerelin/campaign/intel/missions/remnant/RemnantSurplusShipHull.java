package exerelin.campaign.intel.missions.remnant;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.SurplusShipHull;
import com.fs.starfarer.api.util.Misc;

public class RemnantSurplusShipHull extends SurplusShipHull {

	public static float BASE_PRICE_MULT_REM = 1f;
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
				
		PersonAPI person = getPerson();
		if (person == null) return false;
		MarketAPI market = person.getMarket();
		if (market == null) return false;
		
		if (!Misc.getAllowedRecoveryTags().contains(Tags.AUTOMATED_RECOVERABLE))
			return false;
		
		if (!setPersonMissionRef(person, "$sShip_ref")) {
			return false;
		}
		
		if (barEvent) {
			setGiverIsPotentialContactOnSuccess();
		}
		
		ShipPickParams params = new ShipPickParams(ShipPickMode.PRIORITY_THEN_ALL);
		String role = pickRole(getQuality(), person.getFaction(), person.getImportance(), genRandom);
		
		ShipVariantAPI variant = null;
		for (int i = 0; i < 10; i++) {
			List<ShipRolePick> picks = getPerson().getFaction().pickShip(role, params, null, genRandom);
			if (picks.isEmpty()) return false;
			String variantId = picks.get(0).variantId;
			variant = Global.getSettings().getVariant(variantId);
			variant = Global.getSettings().getVariant(variant.getHullSpec().getHullId() + "_Hull").clone();
			if (variant.getHullSpec().hasTag(Tags.NO_SELL)) {
				variant = null;
				continue;
			}
			ShipHullSpecAPI spec = variant.getHullSpec();
			if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE) && !spec.getTags().contains(Tags.AUTOMATED_RECOVERABLE)) 
			{
				variant = null;
				continue;
			}
			break;
		}
		if (variant == null) return false;
			
		member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
		assignShipName(member, Factions.REMNANTS);
		
		float quality = 1;
		float averageDmods = DefaultFleetInflater.getAverageDmodsForQuality(quality);
		int addDmods = DefaultFleetInflater.getNumDModsToAdd(variant, averageDmods, genRandom);
		if (addDmods > 0) {
			DModManager.setDHull(variant);
			DModManager.addDMods(member, true, addDmods, genRandom);
		}
		member.getCrewComposition().setCrew(100000);
		member.getRepairTracker().setCR(0.7f);
		
		if (BASE_PRICE_MULT_REM == 1f) {
			price = (int) Math.round(variant.getHullSpec().getBaseValue());
		} else {
			price = getRoundNumber(variant.getHullSpec().getBaseValue() * BASE_PRICE_MULT_REM);
		}
		
		setRepFactionChangesTiny();
		setRepPersonChangesVeryLow();
		
		return true;
	}
}

