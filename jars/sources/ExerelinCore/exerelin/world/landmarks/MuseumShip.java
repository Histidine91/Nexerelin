package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
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
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.List;

public class MuseumShip extends BaseLandmarkDef {
	
	public static final String id = "museum_ship";
	
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
		return !ExerelinUtilsFaction.isPirateOrTemplarFaction(factionId) && !ExerelinUtilsFaction.isFactionHostileToAll(factionId);
	}
	
	@Override
	public int getCount() {
		int marketCount = Global.getSector().getEconomy().getMarketsCopy().size();
		return (int)Math.ceil(marketCount *= 0.05f);
	}
		
	@Override
	public void createAt(SectorEntityToken entity)
	{
		FactionAPI faction = entity.getFaction();
		
		for (int i=0; i<MAX_TRIES; i++)
		{
			WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>();

			rolePicker.add(ShipRoles.CARRIER_LARGE, 2f);
			rolePicker.add(ShipRoles.COMBAT_CAPITAL, 5f);
			rolePicker.add(ShipRoles.COMBAT_FREIGHTER_LARGE, 2f);
			rolePicker.add(ShipRoles.CARRIER_MEDIUM, 1f);
			rolePicker.add(ShipRoles.COMBAT_LARGE, 3f);
			String role = rolePicker.pick();

			List<ShipRolePick> picks = faction.pickShip(role, 1, random);
			if (picks.isEmpty()) continue;
			
			String variantId = picks.get(0).variantId;
			DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(
					new ShipRecoverySpecial.PerShipData(variantId,	ShipCondition.PRISTINE), false);
			CustomCampaignEntityAPI ship = (CustomCampaignEntityAPI) BaseThemeGenerator.addSalvageEntity(
											 entity.getContainingLocation(),
											 Entities.WRECK, Factions.NEUTRAL, params);
			
			float orbitRadius = entity.getRadius() + 100;
			float orbitPeriod = ExerelinUtilsAstro.getOrbitalPeriod(entity, orbitRadius);
			//ship.setCircularOrbitWithSpin(entity, ExerelinUtilsAstro.getRandomAngle(random), orbitRadius, orbitPeriod, 20, 30);
			ship.setCircularOrbitPointingDown(entity, ExerelinUtilsAstro.getRandomAngle(random), orbitRadius, orbitPeriod);
			
			ship.setFaction(faction.getId());
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
