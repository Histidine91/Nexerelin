package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.world.ExerelinCorvusLocations;
import exerelin.ExerelinConstants;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.world.ExerelinProcGen;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class PlayerStartHandler {
	
	public static void execute()
	{
		SectorAPI sector = Global.getSector();
		if (Global.getSector().isInNewGameAdvance()) return;
		
		SectorEntityToken entity = null;
		String factionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI myFaction = sector.getFaction(factionId);
		CampaignFleetAPI playerFleet = sector.getPlayerFleet();
		if (SectorManager.getCorvusMode())
		{
			// moves player fleet to a suitable location; e.g. Avesta for Association
			String homeEntity = null;
			ExerelinCorvusLocations.SpawnPointEntry spawnPoint = ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
			if (spawnPoint != null)
			{
				homeEntity = spawnPoint.entityId;
				if (homeEntity != null)
					entity = sector.getEntityById(homeEntity);
			}
			if (entity != null)
			{
				Vector2f loc = entity.getLocation();
				playerFleet.setLocation(loc.x, loc.y);
				MarketAPI homeMarket = entity.getMarket();
				if (homeMarket != null)
				{
					// unlock storage
					SubmarketAPI storage = homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE);
					if (storage != null)
					{
						StoragePlugin plugin = (StoragePlugin)homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
						if (plugin != null)
							plugin.setPlayerPaidToUnlock(true);
					}
				}
			}
			// check that all factions support Corvus mode; warn player if not
			int numIncompatibles = 0;
			for (FactionAPI faction : sector.getAllFactions())
			{
				if (!ExerelinUtilsFaction.isCorvusCompatible(faction.getId(), true))
				{
					Global.getLogger(PlayerStartHandler.class).warn("Faction " + faction.getDisplayName() + " does not support Corvus mode!");
					numIncompatibles++;
				}
			}
			if (numIncompatibles > 0)
			{
				Color color = Misc.getHighlightColor();
				Color color2 = Color.RED;
				CampaignUIAPI ui = sector.getCampaignUI();
				ui.addMessage("You are using " + numIncompatibles + " mod faction(s) that do not support Corvus mode!", color, numIncompatibles+"", color2);
				ui.addMessage("See starsector.log for details", color);
			}
			
		}
		else if (!SectorManager.getFreeStart())
		{
			entity = SectorManager.getHomeworld();
			Vector2f loc = entity.getLocation();
			playerFleet.setLocation(loc.x, loc.y);
		}
		
		if (!factionId.equals(ExerelinConstants.PLAYER_NPC_ID))
		{
			if (entity != null && !entity.getFaction().isNeutralFaction())
				ExerelinUtilsFaction.grantCommission(entity);
			else if (ExerelinUtilsFaction.isExiInCorvus(factionId))
				ExerelinUtilsFaction.grantCommission(Global.getSector().getStarSystem("Tasserus").getEntityById("exigency_anomaly"));
		}
		
		int numOfficers = ExerelinSetupData.getInstance().numStartingOfficers;
		
		for (int i=0; i<numOfficers; i++)
		{
			int level = (numOfficers - i)*2 - 1;
			PersonAPI officer = OfficerManagerEvent.createOfficer(myFaction, level, true);
			playerFleet.getFleetData().addOfficer(officer);
			
			// assign officers to ships
			for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy())
			{
				PersonAPI cap = member.getCaptain();
				if ((cap == null || cap.isDefault()) && !member.isFighterWing())
				{
					member.setCaptain(officer);
					break;
				}
				else if (cap == officer)
					break;
			}
		}
	}
	
	public static void addOmnifactory(SectorAPI sector, int index)
	{
		if (!ExerelinSetupData.getInstance().omnifactoryPresent) return;

		SectorEntityToken toOrbit = null;
		//log.info("Randomized omnifac location: " + ExerelinSetupData.getInstance().randomOmnifactoryLocation);
		boolean random = ExerelinSetupData.getInstance().randomOmnifactoryLocation;
		if (index > 0) random = true;
		
		if (random)
		{
			List<StarSystemAPI> systems = new ArrayList(sector.getStarSystems());
			Collections.shuffle(systems);
			for (StarSystemAPI system : systems)
			{
				CollectionUtils.CollectionFilter planetFilter = new OmnifacFilter(system); 
				List planets = CollectionUtils.filter(system.getPlanets(), planetFilter);
				if (!planets.isEmpty())
				{
					Collections.shuffle(planets);
					toOrbit = (SectorEntityToken)planets.get(0);
				}
			}
		}
		
		if (toOrbit == null)
		{
			// Corvus mode: try to place Omnifactory in starting system
			if (ExerelinSetupData.getInstance().corvusMode) {
				do {
					ExerelinCorvusLocations.SpawnPointEntry spawnPoint = ExerelinCorvusLocations.getFactionSpawnPoint(PlayerFactionStore.getPlayerFactionIdNGC());
					if (spawnPoint == null) break;
					// orbit homeworld proper; too much risk of double stations or other such silliness?
					String entityId = spawnPoint.entityId;

					if (entityId != null) {
						SectorEntityToken entity = Global.getSector().getEntityById(entityId);
						if (entity != null && entity instanceof PlanetAPI)
						{
							toOrbit = entity;
							break;
						}
					}
					
					// place at random location in same system
					StarSystemAPI system = Global.getSector().getStarSystem(spawnPoint.systemName);
					if (system == null) break;
					
					CollectionUtils.CollectionFilter planetFilter = new OmnifacFilter(system); 
					List planets = CollectionUtils.filter(system.getPlanets(), planetFilter);
					if (!planets.isEmpty())
					{
						Collections.shuffle(planets);
						toOrbit = (SectorEntityToken)planets.get(0);
					}
				} while (false);
			}
		}
		
		if (toOrbit == null)
		{
			if (ExerelinSetupData.getInstance().corvusMode) toOrbit = sector.getEntityById("corvus_IV");
			else toOrbit = SectorManager.getHomeworld();
		}
		
		LocationAPI system = toOrbit.getContainingLocation();
		Global.getLogger(PlayerStartHandler.class).info("Placing Omnifactory around " + toOrbit.getName() + ", in the " + system.getName());
		String image = (String) ExerelinUtils.getRandomListElement(ExerelinProcGen.stationImages);
		String entityName = "omnifactory" + index;
		SectorEntityToken omnifac = system.addCustomEntity(entityName, "Omnifactory", image, "neutral");
		float radius = toOrbit.getRadius();
		float orbitDistance = radius + 150;
		if (toOrbit instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)toOrbit;
			if (planet.isStar()) 
			{
				orbitDistance = radius + MathUtils.getRandomNumberInRange(3000, 12000);
			}
		}
		omnifac.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, ExerelinUtilsAstro.getOrbitalPeriod(toOrbit, orbitDistance));
		omnifac.setInteractionImage("illustrations", "abandoned_station");
		omnifac.setCustomDescriptionId("omnifactory");

		MarketAPI market = Global.getFactory().createMarket(entityName /*+_market"*/, "Omnifactory", 0);
		SharedData.getData().getMarketsWithoutPatrolSpawn().add(entityName);
		SharedData.getData().getMarketsWithoutTradeFleetSpawn().add(entityName);
		market.setPrimaryEntity(omnifac);
		market.setFactionId(Factions.NEUTRAL);
		market.addCondition(Conditions.ABANDONED_STATION);
		omnifac.setMarket(market);
		sector.getEconomy().addMarket(market);
		
		omnifac.setFaction(Factions.NEUTRAL);
		omnifac.addTag("omnifactory");
		
		//OmniFac.initOmnifactory(omnifac);
	}
	
	public static class OmnifacFilter implements CollectionUtils.CollectionFilter<SectorEntityToken>
	{
		final Set<SectorEntityToken> blocked;
		private OmnifacFilter(StarSystemAPI system)
		{
			String alignedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
			blocked = new HashSet<>();
			for (SectorEntityToken planet : system.getPlanets() )
			{
				String factionId = planet.getFaction().getId();

				if (!factionId.equals("neutral") && !factionId.equals(alignedFactionId))
					blocked.add(planet);
				//else
					//log.info("Authorizing planet " + planet.getName() + " (faction " + factionId + ")");
			}
		}

		@Override
		public boolean accept(SectorEntityToken token)
		{
			return !blocked.contains(token);
		}
	}
}
