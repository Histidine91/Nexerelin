package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidBeltTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ColonyManager;
import exerelin.utilities.NexUtilsAstro;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import org.magiclib.terrain.MagicAsteroidBeltTerrainPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KantasDen extends BaseLandmarkDef {
	
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
			for (CampaignTerrainAPI terr : system.getTerrainCopy()) {
				String type = terr.getType();
				if (type.equals(Terrain.ASTEROID_BELT) || type.equals(Terrain.ASTEROID_FIELD))
					resultsBackup.add(terr);
				else if (type.equals(Terrain.RING))
					results.add(terr);
			}
		}
		if (results.isEmpty()) return resultsBackup;
		return results;
	}

	protected void setupStationOrbit(SectorEntityToken station, SectorEntityToken terrainPre) {
		CampaignTerrainAPI terrain = (CampaignTerrainAPI) terrainPre;
		SectorEntityToken primary = terrain.getOrbitFocus();
		if (primary == null) return;

		float orbitRadius = 100;
		float orbitDays = 30;
		float angle = random.nextFloat() * 360;

		switch (terrain.getType()) {
			case Terrain.ASTEROID_BELT:
				CampaignTerrainPlugin plugin = terrain.getPlugin();

				if (plugin == null) {
					log.error(String.format("Warning: Asteroid belt %s in %s has no plugin",
							terrain.getName(), terrain.getContainingLocation()));
					break;
				}

				if (plugin instanceof AsteroidBeltTerrainPlugin) {
					AsteroidBeltTerrainPlugin abt = (AsteroidBeltTerrainPlugin) plugin;
					log.info(String.format("Creating station in asteroid belt %s in %s, has params: %s",
							terrain.getName(), terrain.getContainingLocation(), abt.params != null));
					orbitRadius = (int) abt.params.middleRadius;
					orbitDays = (abt.params.minOrbitDays + abt.params.maxOrbitDays) / 2;
				} else if (plugin instanceof MagicAsteroidBeltTerrainPlugin) {
					MagicAsteroidBeltTerrainPlugin abt = (MagicAsteroidBeltTerrainPlugin) plugin;
					if (abt.params == null) {
						log.error(String.format("Warning: Asteroid belt %s in %s has no params, this is caused by non-fixed versions of MagicLib",
								terrain.getName(), terrain.getContainingLocation()));
						break;
					}
					log.info(String.format("Creating station in Magic asteroid belt %s in %s, has params: %s",
							terrain.getName(), terrain.getContainingLocation(), abt.params != null));
					orbitRadius = (int) abt.params.middleRadius;
					orbitDays = (abt.params.minOrbitDays + abt.params.maxOrbitDays) / 2;
				}
				break;
			case Terrain.RING:
				BaseRingTerrain brt = (BaseRingTerrain) terrain.getPlugin();
				orbitRadius = (int) brt.params.middleRadius;
				orbitDays = NexUtilsAstro.getOrbitalPeriod(primary, orbitRadius);
				break;
			case Terrain.ASTEROID_FIELD:
				orbitRadius = (int) Misc.getDistance(primary, terrain);
				orbitDays = terrain.getCircularOrbitPeriod();
				angle = Misc.getAngleInDegrees(primary.getLocation(), terrain.getLocation());
				break;
		}

		station.setCircularOrbitPointingDown(primary, angle, orbitRadius, orbitDays);
	}
	
	@Override
	protected boolean weighByMarketSize() {
		return false;
	}
		
	@Override
	public void createAt(SectorEntityToken entity)
	{
		SectorEntityToken pirateStation = entity.getContainingLocation().addCustomEntity("kantas_den",
				StringHelper.getString("exerelin_misc", "marketKantasDen"), "station_side06", "pirates");
		setupStationOrbit(pirateStation, entity);
		pirateStation.setCustomDescriptionId("station_kantas_den");
		pirateStation.setInteractionImage("illustrations", "pirate_station");
		configureKantasDen(pirateStation);
		
		log.info("Spawning Kanta's Den around " + entity.getName() + ", " + entity.getContainingLocation().getName());
	}
	
	protected void configureKantasDen(SectorEntityToken station) {
		MarketAPI market = Global.getFactory().createMarket("kantas_den", station.getName(), 3);
		market.setSize(4);
		market.setFactionId(Factions.PIRATES);
		market.setSurveyLevel(SurveyLevel.FULL);
		market.setFactionId(station.getFaction().getId());
		
		market.setPrimaryEntity(station);
		station.setMarket(market);

		market.addCondition(Conditions.POPULATION_4);
		market.addIndustry(Industries.POPULATION);
		market.addIndustry(Industries.SPACEPORT);
		market.addIndustry(Industries.HEAVYBATTERIES);
		market.addIndustry(Industries.BATTLESTATION);
		market.addIndustry(Industries.COMMERCE);
		market.addIndustry(Industries.LIGHTINDUSTRY);
		//market.addCondition(Conditions.STEALTH_MINEFIELDS);	// for extra stability; not needed since we're taking away the free port
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		market.addSubmarket(Submarkets.SUBMARKET_BLACK);

		market.getMemoryWithoutUpdate().set(ColonyManager.MEMORY_KEY_WANT_FREE_PORT, false);

		Global.getSector().getEconomy().addMarket(market, true);

		NexUtilsMarket.addMarketPeople(market);
		FactionAPI pirate = Global.getSector().getFaction(Factions.PIRATES);
		ColonyManager.reassignAdminIfNeeded(market, pirate, pirate);
		if (market.getAdmin() != null) market.getAdmin().getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);

		Global.getSector().getMemoryWithoutUpdate().set("$nex_randomSector_kantasDen", market);
	}
}
