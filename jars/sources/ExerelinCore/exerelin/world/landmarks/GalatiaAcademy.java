package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.People;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GalatiaAcademy extends BaseLandmarkDef {
	
	@Override
	public List<SectorEntityToken> getEligibleLocations() {
		List<SectorEntityToken> results = new ArrayList<>();
		List<SectorEntityToken> resultsBackup = new ArrayList<>();
		Set<StarSystemAPI> populatedSystems = new HashSet<>();
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			StarSystemAPI sys = market.getStarSystem();
			if (sys == null) continue;
			populatedSystems.add(sys);
		}
		
		for (StarSystemAPI system : populatedSystems)
		{
			List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
			
			for (PlanetAPI planet : system.getPlanets())
			{
				if (!planet.isGasGiant()) continue;
				boolean near = isNearAnotherMarket(planet, markets);
				
				if (near) resultsBackup.add(planet);
				else results.add(planet);
			}
		}
		if (results.isEmpty()) return resultsBackup;
		return results;
	}
	
	@Override
	protected boolean weighByMarketSize() {
		return false;
	}
		
	@Override
	public void createAt(SectorEntityToken entity)
	{
		SectorEntityToken galatiaAcademy = entity.getContainingLocation().addCustomEntity("station_galatia_academy", 
				null, "station_galatia", Factions.INDEPENDENT);
		galatiaAcademy.setCircularOrbitPointingDown(entity, 30, 434, 55);
		galatiaAcademy.setInteractionImage("illustrations", "galatia_academy");
		galatiaAcademy.setCustomDescriptionId("station_galatia_academy");
		configureAcademy(galatiaAcademy);		
		
		log.info("Spawning Galatia Academy around " + entity.getName() + ", " + entity.getContainingLocation().getName());
	}
	
	protected void configureAcademy(SectorEntityToken galatiaAcademy) {
		MarketAPI market = Global.getFactory().createMarket("ga_market", galatiaAcademy.getName(), 3);
		market.setSize(3);
		market.setHidden(true);
		market.setFactionId(Factions.INDEPENDENT);
		market.setSurveyLevel(SurveyLevel.FULL);
		market.setFactionId(galatiaAcademy.getFaction().getId());
		market.getMemoryWithoutUpdate().set(MemFlags.MARKET_HAS_CUSTOM_INTERACTION_OPTIONS, true);
		
		market.setPrimaryEntity(galatiaAcademy);
		galatiaAcademy.setMarket(market);
		
		People.createAcademyPersonnel(market);
		market.getCommDirectory().getEntryForPerson(People.SEBESTYEN).setHidden(false);

		// cockblock the Extract Researcher mission so Academy quest chain can't progress
		Global.getSector().getMemoryWithoutUpdate().set("$gaTJ_ref", false);

		Global.getSector().getMemoryWithoutUpdate().set("$nex_randomSector_galatiaAcademy", galatiaAcademy);
	}
}
