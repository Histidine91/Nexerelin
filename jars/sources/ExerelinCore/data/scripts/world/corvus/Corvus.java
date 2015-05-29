package data.scripts.world.corvus;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoAPI.CrewXPLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;

@SuppressWarnings("unchecked")
public class Corvus { // implements SectorGeneratorPlugin {

	public void generate(SectorAPI sector) {
		final StarSystemAPI system = sector.getStarSystem("Corvus");

//		SectorEntityToken hegemonyStation = system.addOrbitalStation("corvus_hegemony_station", 
//									system.getEntityById("jangala"),
//									//"stations", "station_jangala", 75f,
//									45, 300, 50, "Jangala Station", "hegemony");
//		hegemonyStation.setCustomDescriptionId("station_jangala");
		SectorEntityToken hegemonyStation = system.addCustomEntity("corvus_hegemony_station",
						"Jangala Station", "station_jangala_type", "hegemony");
		//stationAsCustomEntity.setCircularOrbit(system.getEntityById("jangala"), 45, 300, 50);
		hegemonyStation.setCircularOrbitPointingDown(system.getEntityById("jangala"), 45 + 180, 300, 50);		
		hegemonyStation.setCustomDescriptionId("station_jangala");
		//initOrbitalStationCargo(sector, hegemonyStation);
		
//		SectorEntityToken tritachyonStation = system.addOrbitalStation("corvus_tritachyon_station", system.getEntityById("corvus_V"), 45, 300, 50, "Tri-Tachyon Corporate HQ", "tritachyon");
//		SectorEntityToken pirateStation = system.addOrbitalStation("corvus_pirate_station", system.getEntityById("corvus_IIIa"), 45, 300, 50, "Hidden Pirate Base", "pirates");
//		pirateStation.setCustomDescriptionId("pirate_base_generic");
//		initTriTachyonHQCargo(sector, tritachyonStation);

//		tritachyonStation.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "cargo_loading", 640, 400));
		SectorEntityToken pirateStation = system.addCustomEntity("corvus_pirate_station",
				"Hidden Pirate Base", "station_pirate_type", "pirates");
		pirateStation.setCircularOrbitPointingDown(system.getEntityById("corvus_IIIa"), 45, 300, 50);		
		pirateStation.setCustomDescriptionId("pirate_base_barad");
		pirateStation.setInteractionImage("illustrations", "pirate_station");
		//initPirateBaseCargo(sector, pirateStation);
		
//		SectorEntityToken tritachyonStation = system.addOrbitalStation("corvus_tritachyon_station", system.getEntityById("corvus_V"), 45, 300, 50, "Tri-Tachyon Corporate HQ", "tritachyon");
//		initOrbitalStationCargo(sector, hegemonyStation);
//		initTriTachyonHQCargo(sector, tritachyonStation);
//		initPirateBaseCargo(sector, pirateStation);
//		tritachyonStation.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "cargo_loading", 640, 400));
		
		
//		SectorEntityToken testStation = system.addOrbitalStation("corvus_test_station",
//																 system.getEntityById("jangala"),
//																 "stations", // key in graphics section of settings.json
//																 "test_station", // key in "stations"
//																 75, // radius, sprite will be sized to 2 * radius by 2 * radius
//																 -45, 400, 80, "Testing Facility", "hegemony");
		
		SectorEntityToken relay = system.addCustomEntity("corvus_relay", // unique id
														 "Jangala Relay", // name - if null, defaultName from custom_entities.json will be used
														 "comm_relay", // type of object, defined in custom_entities.json
														 "hegemony"); // faction
		relay.setCircularOrbit(system.getEntityById("jangala"), 90, 650, 80);
		
//		SectorEntityToken stationAsCustomEntity = system.addCustomEntity("corvus_rig_station", "Side Station", "test_station", -1, "independent");
//		//stationAsCustomEntity.setCircularOrbit(system.getEntityById("jangala"), 0, 700, 40);
//		stationAsCustomEntity.setCircularOrbitPointingDown(system.getEntityById("jangala"), 0, 700, 40);
//		stationAsCustomEntity.getCargo().addWeapons("amblaster", 5);
		
		
		LocationAPI hyper = Global.getSector().getHyperspace();
		
		SectorEntityToken c2 = system.getEntityByName("Jangala");
		JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("jangala_gate", "Jangala Gate");
		OrbitAPI orbit = Global.getFactory().createCircularOrbit(c2, 0, 500, 30);
		jumpPoint.setOrbit(orbit);
		jumpPoint.setRelatedPlanet(c2);
		
		jumpPoint.setStandardWormholeToHyperspaceVisual();
		system.addEntity(jumpPoint);
		
		system.autogenerateHyperspaceJumpPoints(true, true);
		
		
		SectorEntityToken neutralStation = system.addOrbitalStation("corvus_abandoned_station", system.getEntityById("asharu"), 45, 300, 50, "Abandoned Terraforming Platform", "neutral");
		neutralStation.getMemory().set("$abandonedStation", true);
		MarketAPI market = Global.getFactory().createMarket("corvus_abandoned_station_market", neutralStation.getName(), 0);
		market.setPrimaryEntity(neutralStation);
		market.setFactionId(neutralStation.getFaction().getId());
//		market.addCondition("orbital_station");
//		market.addCondition("regional_capital");
//		market.addCondition("military_base");
//		market.addCondition("population_5");
		market.addCondition(Conditions.ABANDONED_STATION);
//		market.addSubmarket(Strings.SUBMARKET_OPEN);
//		market.addSubmarket(Strings.SUBMARKET_BLACK);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		((StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin()).setPlayerPaidToUnlock(true);
		neutralStation.setMarket(market);
		neutralStation.setCustomDescriptionId("asharu_platform");
		neutralStation.setInteractionImage("illustrations", "abandoned_station");
		
		neutralStation.getMarket().getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addMothballedShip(FleetMemberType.SHIP, "hermes_d_Hull", null);
		
//		market.getLocation().set(system.getLocation());
//		Global.getSector().getEconomy().addMarket(market);
		
		/*
		PirateSpawnPoint pirateSpawn = new PirateSpawnPoint(sector, system, 1f, 50, system.getEntityById("corvus_IIIa"));
		system.addScript(pirateSpawn);
		for (int i = 0; i < 10; i++) {
			CampaignFleetAPI fleet = pirateSpawn.spawnFleet();
			float xOff = (float) Math.random() * 8000f - 4000f;
			float yOff = (float) Math.random() * 16000f - 8000f;
			fleet.setLocation(fleet.getLocation().x + xOff, fleet.getLocation().y + yOff);
		}
		for (int i = 0; i < 5; i++) {
			CampaignFleetAPI fleet = pirateSpawn.spawn("raiders1");
			float xOff = (float) Math.random() * 8000f - 4000f;
			float yOff = (float) Math.random() * 8000f - 4000f;
			fleet.setLocation(fleet.getLocation().x + xOff, fleet.getLocation().y + yOff);
		}
		for (int i = 0; i < 5; i++) {
			CampaignFleetAPI fleet = pirateSpawn.spawn("raiders2");
			float xOff = (float) Math.random() * 8000f - 4000f;
			float yOff = (float) Math.random() * 16000f - 8000f;
			fleet.setLocation(fleet.getLocation().x + xOff, fleet.getLocation().y + yOff);
		}
		*/
		
		
		/*
		HegemonyPatrolSpawnPoint patrolSpawn = new HegemonyPatrolSpawnPoint(sector, system, 10, 5, hegemonyStation);
		system.addScript(patrolSpawn);
		for (int i = 0; i < 5; i++)
			patrolSpawn.spawnFleet();

		SectorEntityToken token = system.createToken(0, 15000);
		HegemonySDFSpawnPoint sdfSpawn = new HegemonySDFSpawnPoint(sector, system, 30, 1, token, hegemonyStation);
		system.addScript(sdfSpawn);
		spawnSDF(sector, system, hegemonyStation);

		TriTachyonSpawnPoint triSpawn = new TriTachyonSpawnPoint(sector, system, 5, 10, system.getEntityByName("Corvus V"), hegemonyStation);
		system.addScript(triSpawn);
		for (int i = 0; i < 2; i++)
			triSpawn.spawnFleet();

		IndependentSpawnPoint independentSpawn = new IndependentSpawnPoint(sector, system, 10, 15, hegemonyStation);
		system.addScript(independentSpawn);
		for (int i = 0; i < 2; i++)
			independentSpawn.spawnFleet();

		
		system.addScript(new HegemonyConvoySpawnPoint(sector, hyper, 13, 1, hyper.createToken(-4000, 4000), hegemonyStation));
		system.addScript(new PiratePlunderFleetSpawnPoint(sector, hyper, 17, 1, hyper.createToken(-4000, 0), pirateStation));
//		system.addScript(new TriTachyonSupplyFleetSpawnPoint(sector, hyper, 23, 1, hyper.createToken(-6000, 2000), tritachyonStation));

		system.addScript(new TriTachyonSupplyFleetSpawnPoint(sector, hyper, 23, 1, hyper.createToken(-6000, 2000), tritachyonStation));
		 */
	}

	private void spawnSDF(SectorAPI sector, StarSystemAPI system, SectorEntityToken location) {
		CampaignFleetAPI fleet = sector.createFleet("hegemony", "systemDefense");
		system.spawnFleet(location, -500, 200, fleet);

		fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, location, 1000000);
		fleet.setPreferredResupplyLocation(location);
		//		
		// fleet = sector.createFleet("tritachyon", "securityDetachment");
		// system.spawnFleet(location, -200, 200, fleet);
		// fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, location,
		// 1000000);

	}

	private void initOrbitalStationCargo(SectorAPI sector, SectorEntityToken station) {
		CargoAPI cargo = station.getCargo();

		List weaponIds = sector.getAllWeaponIds();
		// for (int i = 0; i < 10; i++) {
		// String weaponId = (String) weaponIds.get((int) (weaponIds.size() *
		// Math.random()));
		// int quantity = (int)(Math.random() * 7 + 3);
		// cargo.addWeapons(weaponId, quantity);
		// }

		// focused on weapons that are hard to get from looting
		// and present an upgrade path for the initial ships
		// cargo.addWeapons("heavymg", 5);
		
		//strike
		cargo.addWeapons("bomb", 25);
		cargo.addWeapons("reaper", 12);
		
		//Support
		cargo.addWeapons("lightac", 25);
		cargo.addWeapons("lightmg", 40);
		cargo.addWeapons("annihilator", 10);
		cargo.addWeapons("taclaser", 10);

		cargo.addWeapons("harpoon_single", 12); //medium

		//assault
		cargo.addWeapons("lightmortar", 40);
		cargo.addWeapons("miningblaster", 1); //medium
		
		//PD
		cargo.addWeapons("swarmer", 5);
		cargo.addWeapons("mininglaser", 25);
		cargo.addWeapons("pdlaser", 25);
		
		cargo.addWeapons("flak", 5); //medium
		cargo.addWeapons("shredder", 5); //medium
		cargo.addWeapons("annihilatorpod", 1); //medium
		cargo.addWeapons("pilum", 2); //medium
		cargo.addWeapons("mark9", 2); //large
		
		
//		cargo.addCrew(CrewXPLevel.ELITE, 25);
		// cargo.addCrew(CrewXPLevel.VETERAN, 200);
		cargo.addCrew(CrewXPLevel.REGULAR, 30);
		cargo.addCrew(CrewXPLevel.GREEN, 500);
		cargo.addMarines(100);
		cargo.addSupplies(630);
		cargo.addFuel(500);
		
		
		cargo.addItems(CargoItemType.RESOURCES, "food", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "organics", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "volatiles", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "ore", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "rare_ore", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "metals", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "rare_metals", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "heavy_machinery", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "domestic_goods", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "organs", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "drugs", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "hand_weapons", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "luxury_goods", 1000);
		cargo.addItems(CargoItemType.RESOURCES, "lobster", 1000);

	
		cargo.addMothballedShip(FleetMemberType.SHIP, "vigilance_Hull", null);
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "hound_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "lasher_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "brawler_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "dram_Hull", null);
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "enforcer_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "condor_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "hammerhead_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "sunder_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "valkyrie_Hull", null);
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "falcon_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "eagle_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "dominator_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "venture_Hull", null);
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "atlas_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "onslaught_Hull", null);
		
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "mule_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "mule_Hull", null);
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "hermes_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "hermes_Hull", null);
		
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "talon_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "talon_wing", null);
		
		

	}

	private void initTriTachyonHQCargo(SectorAPI sector, SectorEntityToken station) {
		CargoAPI cargo = station.getCargo();

		List weaponIds = sector.getAllWeaponIds();
		cargo.addCrew(CrewXPLevel.ELITE, 10);
		cargo.addCrew(CrewXPLevel.VETERAN, 10);
		cargo.addCrew(CrewXPLevel.REGULAR, 50);
		// cargo.addCrew(CrewXPLevel.GREEN, 1500);
		cargo.addMarines(75);
		cargo.addSupplies(145);
		cargo.addFuel(100);
		
		//strike
		cargo.addWeapons("amblaster", 5);
		cargo.addWeapons("atropos_single", 5);
		
		//support
		cargo.addWeapons("taclaser", 15);
		cargo.addWeapons("railgun", 5);
		cargo.addWeapons("harpoon", 5);
		
		cargo.addWeapons("pulselaser", 6);
		cargo.addWeapons("gravitonbeam", 10);
		cargo.addWeapons("heavyburst", 6);
		cargo.addWeapons("heavyblaster", 3);
		cargo.addWeapons("phasebeam", 3);
		cargo.addWeapons("harpoonpod", 5);
		cargo.addWeapons("sabotpod", 5);
		
		//PD
		cargo.addWeapons("vulcan", 6);
		cargo.addWeapons("lrpdlaser", 6);
		cargo.addWeapons("pdburst", 6);
		cargo.addWeapons("swarmer", 6);
		
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "wasp_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "wasp_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "wasp_wing", null);
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "hyperion_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "hyperion_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "medusa_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "medusa_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "apogee_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "tempest_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "tempest_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "tempest_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "buffalo_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "buffalo_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "omen_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "omen_Hull", null);
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "doom_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "doom_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "afflictor_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "afflictor_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "shade_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "shade_Hull", null);
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "odyssey_Hull", null);
	}

	private void initPirateBaseCargo(SectorAPI sector, SectorEntityToken station) {
		CargoAPI cargo = station.getCargo();

		List weaponIds = sector.getAllWeaponIds();
		// cargo.addCrew(CrewXPLevel.ELITE, 25);
		cargo.addCrew(CrewXPLevel.VETERAN, 20);
		cargo.addCrew(CrewXPLevel.REGULAR, 50);
		cargo.addCrew(CrewXPLevel.GREEN, 100);
		cargo.addMarines(50);
		cargo.addSupplies(100);
		cargo.addFuel(10);
		
		//strike
		cargo.addWeapons("bomb", 15);
		cargo.addWeapons("typhoon", 5);
		
		//PD
		cargo.addWeapons("clusterbomb", 10);
		cargo.addWeapons("flak", 10);
		cargo.addWeapons("irpulse", 10);
		cargo.addWeapons("swarmer", 10);

		//support
		cargo.addWeapons("fragbomb", 10);
		cargo.addWeapons("heatseeker", 5);
		cargo.addWeapons("harpoon", 5);
		
		cargo.addWeapons("sabot", 5);
		cargo.addWeapons("annihilator", 5);
		cargo.addWeapons("lightdualmg", 10);
		cargo.addWeapons("lightdualac", 10);
		cargo.addWeapons("lightneedler", 10);
		cargo.addWeapons("heavymg", 10);
		cargo.addWeapons("heavymauler", 5);
		cargo.addWeapons("salamanderpod", 5);
		
		cargo.addWeapons("hveldriver", 5);

		//assault
		cargo.addWeapons("lightag", 10);
		cargo.addWeapons("chaingun", 5);
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "wolf_Hull", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "broadsword_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "broadsword_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "piranha_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "piranha_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "talon_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "talon_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "thunder_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "thunder_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "gladius_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "warthog_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "warthog_wing", null);
		
		cargo.addMothballedShip(FleetMemberType.SHIP, "buffalo2_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "buffalo2_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "condor_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "tarsus_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "tarsus_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "gemini_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "cerberus_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "cerberus_Hull", null);

		cargo.addMothballedShip(FleetMemberType.SHIP, "venture_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "dominator_Hull", null);
		cargo.addMothballedShip(FleetMemberType.SHIP, "conquest_Hull", null);
	}
}
