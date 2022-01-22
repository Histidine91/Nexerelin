package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.NexUtilsAstro;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.List;

public class MuseumShip extends BaseLandmarkDef {
		
	protected static final int MAX_TRIES = 5;
	
	@Override
	public boolean isApplicableToEntity(SectorEntityToken entity)
	{
		MarketAPI market = entity.getMarket();
		if (market == null || market.isPlanetConditionMarketOnly())
			return false;
		if (market.getSize() < 4)
			return false;
		
		String factionId = entity.getFaction().getId();
		return !NexUtilsFaction.isPirateOrTemplarFaction(factionId) && !NexUtilsFaction.isFactionHostileToAll(factionId);
	}
	
	@Override
	public int getCount() {
		int marketCount = Global.getSector().getEconomy().getMarketsCopy().size();
		return (int)Math.ceil(marketCount *= 0.05f);
	}
		
	@Override
	public void createAt(SectorEntityToken entity)
	{
		FactionAPI faction = Global.getSector().getFaction(getNonDerelictFaction(entity.getMarket()));
		
		for (int i=0; i<MAX_TRIES; i++)
		{
			WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>(random);
			
			// pick a ship
			// try to make it a capital or maybe a cruiser, if at all possible
			rolePicker.add(ShipRoles.CARRIER_LARGE, 2f);
			rolePicker.add(ShipRoles.COMBAT_CAPITAL, 5f);
			//rolePicker.add(ShipRoles.COMBAT_FREIGHTER_LARGE, 2f);
			rolePicker.add(ShipRoles.CARRIER_MEDIUM, 1f);
			rolePicker.add(ShipRoles.COMBAT_LARGE, 3f);
			String role = rolePicker.pick();

			List<ShipRolePick> picks = faction.pickShip(role, ShipPickParams.priority(), null, random);
			if (picks.isEmpty()) continue;
			
			// create ship
			String variantId = picks.get(0).variantId;
			DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(
					new ShipRecoverySpecial.PerShipData(variantId,	ShipCondition.PRISTINE), false);
			CustomCampaignEntityAPI ship = (CustomCampaignEntityAPI) BaseThemeGenerator.addSalvageEntity(
											 entity.getContainingLocation(),
											 Entities.WRECK, Factions.NEUTRAL, params);
			
			// orbit
			float orbitRadius = entity.getRadius() + 100;
			float orbitPeriod = NexUtilsAstro.getOrbitalPeriod(entity, orbitRadius);
			//ship.setCircularOrbitWithSpin(entity, ExerelinUtilsAstro.getRandomAngle(random), orbitRadius, orbitPeriod, 20, 30);
			ship.setCircularOrbitPointingDown(entity, NexUtilsAstro.getRandomAngle(random), orbitRadius, orbitPeriod);
			
			// tags etc.
			// note: entity faction can differ from the one we used to pick the ship type,
			// e.g. with Derelict Empire
			ship.setFaction(entity.getFaction().getId());
			ship.setInteractionImage("illustrations", "terran_orbit");
			
			String name = faction.pickRandomShipName();	//Global.getSettings().getVariant(variantId).getHullSpec().getHullName();
			
			ship.setName(StringHelper.getStringAndSubstituteToken("exerelin_landmarks", "museumShip", "$name", name));
			ship.setCustomDescriptionId("nex_museum_ship");
			ship.addTag("nex_museum_ship");
			ship.setDiscoverable(true);
			
			log.info("Spawning museum ship around " + entity.getName() + ", " + entity.getContainingLocation().getName());
			
			break;
		}
	}
}
