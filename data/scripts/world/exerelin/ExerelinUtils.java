package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Random;
import java.util.List;
import java.util.Iterator;

public class ExerelinUtils
{
	public static int getRandomInRange(int min, int max)
	{
		return min + (int)(Math.random() * ((max - min) + 1)); // hate java
	}

	// rounds up or down with closer integer having a proportionally higher chance
	public static int getRandomNearestInteger(float number)
	{
		if (number >= 0) {
			return (int)(number + Math.random());
		} else {
			return (int)(number - Math.random());
		}
	}

	public static void shuffleStringArray (String[] array)
	{
		Random rng = new Random();   // i.e., java.util.Random.
		int n = array.length;        // The number of items left to shuffle (loop invariant).
		while (n > 1)
		{
			int k = rng.nextInt(n);  // 0 <= k < n.
			n--;                     // n is now the last pertinent index;
			String temp = array[n];     // swap array[n] with array[k] (does nothing if k == n).
			array[n] = array[k];
			array[k] = temp;
		}
	}

    public static Boolean doesStringArrayContainValue(String value, String[] valuesToCheck, Boolean partialMatch)
    {
        for(int i = 0; i < valuesToCheck.length; i++)
        {
            if(partialMatch && value.contains(valuesToCheck[i]))
                return true;
            else if(value.equalsIgnoreCase(valuesToCheck[i]))
                return true;
        }

        return false;
    }

	public static SectorEntityToken getRandomOffMapPoint(LocationAPI location)
	{
		int edge = ExerelinUtils.getRandomInRange(0, 3);
		int x = 0;
		int y = 0;
		int maxSize = ExerelinData.getInstance().getSectorManager().getMaxSystemSize();
		int negativeMaxSize = -1 * maxSize;

		if(edge == 0)
			x = maxSize;
		else if(edge == 1)
			x = negativeMaxSize;
		else if(edge == 2)
			y = maxSize;
		else if(edge == 3)
			y = negativeMaxSize;

		if(x == 0)
			x = ExerelinUtils.getRandomInRange(negativeMaxSize, maxSize);

		if(y == 0)
			y = ExerelinUtils.getRandomInRange(negativeMaxSize, maxSize);

		SectorEntityToken spawnPoint = location.createToken(x, y);
		return spawnPoint;
	}

	public static Boolean canStationSpawnFleet(SectorEntityToken station, CampaignFleetAPI fleet, float numberToSpawn, float marinesPercent, boolean noCivilianShips, CargoAPI.CrewXPLevel crewXPLevel)
	{
		if (noCivilianShips) {
			List members = fleet.getFleetData().getMembersListCopy();

			for(int i = 0; i < members.size(); i++)	{
				FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);

				if(fmAPI.isCivilian()) {
					fleet.getFleetData().removeFleetMember(fmAPI);
				}
			}
		}

		CargoAPI stationCargo = station.getCargo();
		if(getBestFleetForStation(stationCargo, fleet, numberToSpawn))
		{
			// Recalc again
			float fleetCost = getFleetCost(fleet);

			float reqCrew     = fleetCost;
			float reqSupplies = fleetCost * 4;
			float reqFuel     = fleetCost;
			int reqMarines    = (int)(fleetCost / 2);

			// Check again just in case other changes
			if((stationCargo.getFuel() / numberToSpawn) < reqFuel || (stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) / numberToSpawn) < reqCrew || (stationCargo.getSupplies() / numberToSpawn) < reqSupplies || (stationCargo.getMarines() / numberToSpawn) < reqMarines)
				return false;
			else
			{
				decreaseCargo(stationCargo, "crewRegular", (int)reqCrew);
				decreaseCargo(stationCargo, "fuel", (int)reqFuel);
				decreaseCargo(stationCargo, "supplies", (int)reqSupplies);
				decreaseCargo(stationCargo, "marines", reqMarines);

				// Reset fleet cargo and put correct cargo in for fleet size otherwise accidents will occur
				CargoAPI fleetCargo = fleet.getCargo();
				fleetCargo.clear();
                ExerelinUtils.resetFleetCargoToDefaults(fleet, 1.0f - marinesPercent, marinesPercent, crewXPLevel);

				return true;
			}
		}
		else
		{
			return false;
		}
	}

	// Recursive method
	// Will remove fleet members until either 0 is left or fleet can be spawned from station
	private static Boolean getBestFleetForStation(CargoAPI stationCargo, CampaignFleetAPI fleet, float numberToSpawn)
	{
		List members = fleet.getFleetData().getMembersListCopy();

		if(members.size() == 0)
			return false;

		float fleetCost = getFleetCost(fleet);

		float reqCrew     = fleetCost;
		float reqSupplies = fleetCost * 4;
		float reqFuel     = fleetCost;
		int reqMarines    = (int)(fleetCost / 2);

		if((stationCargo.getFuel() / numberToSpawn) < reqFuel || (stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) / numberToSpawn) < reqCrew || (stationCargo.getSupplies() / numberToSpawn) < reqSupplies || (stationCargo.getMarines() / numberToSpawn) < reqMarines)
		{
			if(members.size() == 1)
				return false;

			// Can't spawn, so remove random members and try again
			// Remove capital ships first
			if(isCapitalInFleet(fleet))
			{
				int toRemove = -1;
				for(int i = 0; i < members.size(); i++)
				{
					if(((FleetMemberAPI)members.get(i)).isCapital())
					{
						toRemove = i;
						break;
					}
				}

				if(toRemove != -1)
				{
					fleet.getFleetData().removeFleetMember((FleetMemberAPI)members.get(toRemove));
				}
			}
			else
			{
				int removeMembers = getRandomInRange(1, members.size());
				for(int j = 0; j < removeMembers; j = j + 1)
					fleet.getFleetData().removeFleetMember((FleetMemberAPI)members.get(getRandomInRange(0, members.size() - 1)));
			}

			return getBestFleetForStation(stationCargo, fleet, numberToSpawn);
		}
		else
		{
			// Can spawn so make sure fleet size isn't small if it has capitals
			if(fleet.getFleetData().getFleetPointsUsed() < 40 && isCapitalInFleet(fleet))
			{
				for(int l = 0; l < members.size(); l++)
				{
					if(((FleetMemberAPI)members.get(l)).isCapital())
					{
						members.remove(l);
						break;
					}
				}

				if(members.size() == 0)
					return false;
			}
			return true;
		}
	}

	private static int getFleetCost(CampaignFleetAPI fleet)
	{
		final float FLEET_COST_MULT = 0.7f;

		float fleetCost = 0f;
		float mult;
		List members = fleet.getFleetData().getMembersListCopy();

		for (int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI ship = (FleetMemberAPI)members.get(i);

            if (ship.isCivilian()) { // superfreighters are not battleships and shouldn't cost that much
            	mult = 2f;
            } else if (ship.isFighterWing()) {
            	mult = 2f;
            } else if (ship.isFrigate()) {
            	mult = 2f;
            } else if (ship.isDestroyer()) {
            	mult = 3f;
            } else if (ship.isCruiser()) {
            	mult = 4f;
            } else if (ship.isCapital()) {
            	mult = 7f;
            } else {
            	mult = 2f;
            }

            fleetCost += (ship.getFleetPointCost() * mult);
		}

		return Math.round(fleetCost * FLEET_COST_MULT);
	}

	public static String getStationOwnerFactionId(SectorEntityToken stationToken)
	{
		String stationName = stationToken.getFullName().toLowerCase();

		if(stationName.contains("omnifactory"))
			return "neutral";

		if(stationName.contains("storage"))
			return "neutral";

		if(stationName.contains("abandoned"))
			return "abandoned";

		String[] factions = ExerelinData.getInstance().getAvailableFactions(Global.getSector());
		for(int i = 0; i < factions.length; i = i + 1)
		{
			if(stationName.contains(factions[i].toLowerCase()))
				return factions[i];
		}

		System.out.println("Couldn't derive faction for: " + stationToken.getFullName());
		return "neutral"; // Do nothing
	}

	public static SectorEntityToken getClosestEnemyStation(String targetingFaction, StarSystemAPI starSystemAPI, SectorAPI sector, SectorEntityToken anchor)
	{
		List stations = starSystemAPI.getOrbitalStations();
		Float bestDistance = 10000000000000f;
		SectorEntityToken bestStation = anchor;

		for(int i = 0; i < stations.size(); i = i + 1)
		{
			SectorEntityToken theStation = (SectorEntityToken)stations.get(i);

			if(theStation.getFullName().contains("Omnifactory"))
				continue;

			if(theStation.getFullName().contains("Storage"))
				continue;

			FactionAPI stationOwner = sector.getFaction(getStationOwnerFactionId(theStation));

			if(stationOwner == null)
				continue; //Crash protect...

			Float attackDistance = MathUtils.getDistanceSquared(anchor, theStation);

			if(stationOwner.getRelationship(targetingFaction) < 0 && bestDistance > attackDistance)
			{
				bestStation = theStation;
				bestDistance = attackDistance;
			}
		}

		if(MathUtils.getDistance(anchor, bestStation) == 0)
		{
			//System.out.println("Couldn't get station target for: " + targetingFaction);
			return null; // no available targets
		}
		else
			return bestStation;
	}

	public static SectorEntityToken getRandomStationForFaction(String factionId, StarSystemAPI starSystemAPI, SectorAPI sector)
	{
		List stations = starSystemAPI.getOrbitalStations();
		SectorEntityToken theStation;
		SectorEntityToken factionStation = null;

		for(int i = 0; i < stations.size(); i = i + 1)
		{
			theStation = (SectorEntityToken)stations.get(i);

			if(theStation.getFullName().contains("Omnifactory"))
				continue; // Skip current station

			if(theStation.getFullName().contains("Storage"))
				continue;

			if(!getStationOwnerFactionId(theStation).equalsIgnoreCase(factionId))
				continue; // Skip current station

			if(factionStation == null)
				factionStation = theStation;
			else
			{
				if(ExerelinUtils.getRandomInRange(0, 1) == 0)
					factionStation = theStation;
			}
		}

		return factionStation;
	}

	public static void decreaseCargo(CargoAPI cargo, String type, int quantity)
	{
		if(type.equalsIgnoreCase("crewRegular"))
		{
			cargo.removeCrew(CargoAPI.CrewXPLevel.REGULAR,  quantity);
			if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) < 0)
				cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR)*-1);
		}
		else if(type.equalsIgnoreCase("fuel"))
		{
			cargo.removeFuel(quantity) ;
			if(cargo.getFuel() < 0)
				cargo.addFuel(cargo.getFuel() * -1) ;
		}
		else if(type.equalsIgnoreCase("supplies"))
		{
			cargo.removeSupplies(quantity) ;
			if(cargo.getSupplies() < 0)
				cargo.addSupplies(cargo.getSupplies() * -1) ;
		}
		else if(type.equalsIgnoreCase("marines"))
		{
			cargo.removeMarines(quantity) ;
			if(cargo.getMarines() < 0)
				cargo.addMarines(cargo.getMarines() * -1) ;
		}
		else
		{
			System.out.println("EXERELIN ERROR: Invalid cargo type to remove: " + type);
		}
	}

	public static void renameFleet(CampaignFleetAPI fleet, String type)
	{
		String fleetFaction = fleet.getFaction().getId();

		String fleetTypeName = "";
		float fleetSize = fleet.getFleetData().getFleetPointsUsed();
		if(type.equalsIgnoreCase("attack"))
		{
			if(fleetSize < 40)
				fleetTypeName = "Advance Force";
			else if (fleetSize < 90)
				fleetTypeName = "Strike Force";
			else if (fleetSize > 90)
				fleetTypeName = "Crusaders";
		}
		else if(type.equalsIgnoreCase("defense"))
		{
			if(fleetSize < 40)
				fleetTypeName = "Watch Fleet";
			else if (fleetSize < 90)
				fleetTypeName = "Guard Fleet";
			else if (fleetSize > 90)
				fleetTypeName = "Sentinels";
		}
		else if(type.equalsIgnoreCase("patrol"))
		{
			if(fleetSize < 40)
				fleetTypeName = "Recon Patrol";
			else if (fleetSize < 90)
				fleetTypeName = "Ranger Patrol";
			else if (fleetSize > 90)
				fleetTypeName = "Wayfarers";
		}
		else
		{
			System.out.println("EXERELIN ERROR: Invalid fleet type to rename: " + type);
		}
		fleet.setName(fleetTypeName);
	}

	public static void addWeaponsToCargo(CargoAPI cargo, int count, String factionId, SectorAPI sector)
	{
		String[] factionWeapons = ExerelinUtils.getFactionWeapons(factionId);
		int maxQuantityInStack = 4;

		List weapons = cargo.getWeapons();
		if(weapons.size() > 30)
		{
			removeRandomWeaponStacksFromCargo(cargo, weapons.size() - 25);
		}

		if(factionWeapons.length > 0)
		{
			for(int i = 0; i < count; i = i + 1)
			{
				String weaponId = factionWeapons[ExerelinUtils.getRandomInRange(0, factionWeapons.length - 1)];
				cargo.addWeapons(weaponId, ExerelinUtils.getRandomInRange(1, maxQuantityInStack));
			}
		}
		else
		{
			addRandomWeapons(cargo, count, sector);
		}
	}

	private static void addRandomWeapons(CargoAPI cargo, int count, SectorAPI sector)
	{
		List weaponIds = sector.getAllWeaponIds();
		for (int i = 0; i < count; i++) {
			String weaponId = (String) weaponIds.get((int) (weaponIds.size() * Math.random()));
			int quantity = 3;
			cargo.addWeapons(weaponId, quantity);
		}
	}

	private static String[] getFactionWeapons(String factionId)
	{
		if(factionId.equalsIgnoreCase("hegemony"))
		{
			return new String[] {
					"annihilatorpod",
					"harpoon",
					"heavymg",
					"arbalest",
					"heavyac",
					"dualflak",
					"lightdualac",
					"lightag",
					"lightneedler",
					"heavymauler",
					"hellbore",
					"cyclone",
					"pilum",
					"hveldriver",
					"reaper",
					"shredder",
					"flak",
					"chaingun",
					"heavyneedler",
					"gauss",
					"hephag",
					"mark9",
					"mjolnir",
					"multineedler",

			};
		}
		else if (factionId.equalsIgnoreCase("pirates"))
		{
			return new String[] {
					"annihilatorpod",
					"harpoon",
					"heavymg",
					"arbalest",
					"heavyac",
					"dualflak",
					"lightdualac",
					"lightag",
					"lightneedler",
					"heavymauler",
					"hellbore",
					"cyclone",
					"pilum",
					"hveldriver",
					"reaper",
					"pulselaser",
					"shredder",
					"flak",
					"chaingun",
					"heavyneedler",
					"gauss",
					"hephag",
					"mark9",
					"mjolnir",
					"multineedler",
			};
		}
		else if (factionId.equalsIgnoreCase("tritachyon"))
		{
			return new String[] {
					"atropos",
					"typhoon",
					"ioncannon",
					"sabot",
					"guardian",
					"plasma",
					"sabotpod",
					"pilum",
					"autopulse",
					"plasma",
					"hil",
					"hurricane",
					"taclaser",
					"lrpdlaser",
					"pdlaser",
					"pdburst",
					"irpulse",
					"pulselaser",
					"heavyburst",
					"gravitonbeam",
					"tachyonlance",
					"heavyblaster",
					"phasebeam",
					"amblaster",
			};
		}
		else if (factionId.equalsIgnoreCase("independent"))
		{
			return new String[] {
					"annihilatorpod",
					"harpoon",
					"heavymg",
					"arbalest",
					"heavyac",
					"dualflak",
					"heavyblaster",
					"lightdualac",
					"lightag",
					"lightneedler",
					"heavymauler",
					"gravitonbeam",
					"hellbore",
					"cyclone",
					"pilum",
					"taclaser",
					"pulselaser",
					"heavyburst",
					"hveldriver",
					"reaper",
					"atropos",
					"typhoon",
					"ioncannon",
					"heavyneedler",
					"sabot",
					"dualflak",
					"guardian",
					"plasma",
					"chaingun",
					"sabotpod",
					"pilum",
					"autopulse",
					"plasma",
					"hil",
					"hurricane",
					"mjolnir",
					"taclaser",
					"lrpdlaser",
					"pdlaser",
					"pdburst",
					"irpulse",
					"lrpdlaser",
					"pdlaser",
					"pdburst",
					"lrpdlaser",
					"gravitonbeam",
					"phasebeam",
					"amblaster",
					"heavyblaster",
					"hveldriver",
					"shredder",
					"flak",
					"chaingun",
					"gauss",
					"hephag",
					"mark9",
					"multineedler",
			};
		}
		else if (factionId.equalsIgnoreCase("antediluvian"))
		{
			return new String[] {
					"pandora",
					"advancedsonicturret",
					"sonicturret",
					"prometheus",
					"deucalion",
					"advancedturret",
					"dualturret",
					"extendedturret",
					"superiorityturret",
					"tripleturret",
					"pressureturret",
					"reflectorbeam",
					"modifiedreflectorbeam",
					"batteryturret",
					"nozzle",
					"pressureturret",
					"dualturret",
					"advancedturret",
					"tripleturret",
			};
		}
		else if (factionId.equalsIgnoreCase("blackrock"))
		{
			return new String[] {
					"brdy_ac",
					"brdy_ag",
					"achilles_mrm",
					"brvulcan",
					"brburst",
					"brvulcan",
					"brdy_fury",
					"brdy_quill",
					"brdy_solenoid",
					"brdy_plasma",
					"achillespod",
					"brdy_squallgun",
					"brdy_volley",
					"brdy_solenoidlarge",
					"brdy_dualac",
					"brdy_squallbattery",
					"brdy_2xfury",
					"br_pde",
					"br_fpde",
			};
		}
		else if (factionId.equalsIgnoreCase("gedune"))
		{
			return new String[] {
					"gedune_plasma",
					"gedune_repeater",
					"gedune_flarepd",
					"annihilator",
					"lightag",
					"taclaser",
					"irpulse",
					"gedune_maelstrom",
					"gedune_scythe",
					"phasebeam",
					"hurricane",
					"gedune_flarepd",
                    "gedune_phase_repeater",
                    "gedune_reithe",
                    "gedune_kdart"
			};
		}
		else if (factionId.equalsIgnoreCase("junkpirate"))
		{
			return new String[] {
					"amblaster",
					"pdburst",
					"pdlaser",
					"junk_pirates_cutlass",
					"junk_pirates_lexcimer",
					"heavyburst",
					"chaingun",
					"gravitonbeam",
					"junk_pirates_scatterpd",
					"pulselaser",
					"ioncannon",
					"taclaser",
					"lightmg",
					"lightac",
					"lightag",
					"phasecl",
					"pilum",
					"sabot",
					"harpoon",
					"heatseeker",
					"annihilator",
					"swarmer",
					"atropos",
					"reaper",
					"sabotpod",
					"harpoonpod",
					"railgun",
					"miningblaster",
					"hil",
					"junk_pirates_grapeshot",
			};
		}
		else if (factionId.equalsIgnoreCase("lotusconglomerate"))
		{
			return new String[] {
					"annihilator",
					"annihilatorpod",
					"arbalest",
					"atropos",
					"atropos_single",
					"bomb",
					"clusterbomb",
					"chaingun",
					"cyclone",
					"dualflak",
					"flak",
					"fragbomb",
					"gauss",
					"harpoon",
					"harpoon_single",
					"harpoonpod",
					"hdstinger",
					"heatseeker",
					"heavyac",
					"heavymauler",
					"heavymg",
					"heavyneedler",
					"hellbore",
					"hephag",
					"hurricane",
					"hveldriver",
					"lightac",
					"lightag",
					"lightdualac",
					"lightmg",
					"lightmortar",
					"lightneedler",
					"mjolnir",
					"multineedler",
					"phasecl",
					"pilum",
					"railgun",
					"reaper",
					"revolver",
					"sabot",
					"sabot_single",
					"sabotpod",
					"salamanderpod",
					"shredder",
					"stinger_single",
					"stinger",
					"stingerlauncher",
					"swarmer",
					"typhoon",
					"vulcan",
			};
		}
		else if (factionId.equalsIgnoreCase("council_loyalists"))
		{
			return new String[] {
					"mrd_council_spear",
					"mrd_gladiator_beam",
					"mrd_heavy_assault_gun",
					"mrd_heavy_autoblaster",
					"mrd_heavy_beam",
					"mrd_light_autoblaster",
					"mrd_light_blaster",
					"mrd_pd_laser_mk_ii",
					"mrd_pdminigun",
					"mrd_quad_autoblaster",
					"mrd_turbo_blaster",
					"mrd_heavy_assault_gun",
					"amblaster",
					"annihilator",
					"annihilatorpod",
					"atropos",
					"atropos_single",
					"chaingun",
					"cyclone",
					"dualflak",
					"flak",
					"gauss",
					"harpoon",
					"harpoon_single",
					"harpoonpod",
					"heavyblaster",
					"heatseeker",
					"heavyburst",
					"heavymauler",
					"heavymg",
					"heavyneedler",
					"hellbore",
					"hephag",
					"hurricane",
					"ioncannon",
					"lightag",
					"lightmg",
					"lightneedler",
					"mjolnir",
					"mark9",
					"pdburst",
					"pdlaser",
					"pilum",
					"reaper",
					"sabot",
					"sabot_single",
					"sabotpod",
					"salamanderpod",
					"swarmer",
					"mrd_council_spear",
					"mrd_gladiator_beam",
					"mrd_heavy_assault_gun",
					"mrd_heavy_autoblaster",
					"mrd_heavy_beam",
					"mrd_light_autoblaster",
					"mrd_light_blaster",
					"mrd_pd_laser_mk_ii",
					"mrd_pdminigun",
					"mrd_quad_autoblaster",
					"mrd_turbo_blaster",
					"mrd_heavy_assault_gun",
			};
		}
		else if (factionId.equalsIgnoreCase("neutrino"))
		{
			return new String[] {
					"autopulse",
					"atropos",
					"atropos_single",
					"harpoon",
					"harpoon_single",
					"harpoonpod",
					"hurricane",
					"mjolnir",
					"neutrino_photontorpedo",
					"neutrino_lightphoton",
					"neutrino_antiproton",
					"neutrino_tractorbeam",
					"neutrino_pulsebeam",
					"neutrino_javelin",
					"neutrino_particlecannonarray",
					"neutrino_darkmatterbeamcannon",
					"neutrino_advancedtorpedo",
					"neutrino_advancedtorpedosingle",
					"neutrino_XLadvancedtorpedo",
					"neutrino_pulsar",
					"neutrino_dualpulsar",
					"neutrino_heavypulsar",
					"neutrino_dualpulsebeam",
					"neutrino_disruptor",
					"neutrino_derp_launcher",
					"neutrino_neutronpulse",
					"neutrino_neutronpulsebattery",
					"neutrino_neutronpulseheavy",
					"neutrino_neutronlance",
					"neutrino_unstable_photon",
					"neutrino_photongun",
					"neutrino_fusionlance",
					"neutrino_phasedarray",
					"neutrino_pulsebeam",
					"neutrino_graviton_inverter",
					"neutrino_heavyphotonrepeater",
					"phasecl",
					"pilum",
					"pdburst",
					"sabot",
					"sabot_single",
					"sabotpod",
					"salamanderpod",
					"swarmer",
					"taclaser",
			};
		}
		else if (factionId.equalsIgnoreCase("shadowyards_hi"))
		{
			return new String[] {
					"amblaster",
					"autopulse",
					"pdburst",
					"ms_cepc",
					"ms_pdcepc",
					"ms_mcepc",
					"ms_scattercepc",
					"ms_blackcap_3x",
					"ms_blackcap_6x",
					"ms_blackcap_pod",
					"heavyburst",
					"hil",
					"irpulse",
					"gravitonbeam",
					"ms_shrike_single",
					"ms_shrike_rack",
					"ms_shrike_pod",
					"pulselaser",
					"lightag",
					"phasecl",
					"phasebeam",
					"harpoon",
					"heatseeker",
					"annihilator",
					"atropos",
					"reaper",
					"ms_cepc",
					"ms_pdcepc",
					"ms_mcepc",
					"ms_scattercepc",
					"ms_blackcap_3x",
					"ms_blackcap_6x",
					"ms_blackcap_pod",
					"ms_shrike_single",
					"ms_shrike_rack",
					"ms_shrike_pod",
                    "ms_tusk_single",
                    "ms_tusk_rack"
			};
		}
		else if (factionId.equalsIgnoreCase("valkyrian"))
		{
			return new String[] {
					"ether_driver",
					"irpdburst",
					"irpdcannon",
					"quadheavymg",
					"lightflak",
					"lrflak",
					"wraith",
					"ultraheavy_lancer",
					"heavyneedler",
					"lightneedler",
					"heavylancer",
					"lightlancer",
					"hephag",
					"gauss",
					"ibcm",
					"lightdualmg",
					"scorpionpod",
					"scorpion",
					"sabotpod",
					"sabot",
					"pilum",
					"antiproton_laser",
					"lightflak",
					"lrflak",
					"irpdburst",
					"irpdcannon",
                    "heavyarbalest",
                    "phantasm",
                    "spectre"
			};
		}
		else if (factionId.equalsIgnoreCase("syndicateasp"))
		{
			return new String[] {
					"atropos",
					"typhoon",
					"ioncannon",
					"sabot",
					"guardian",
					"plasma",
					"sabotpod",
					"pilum",
					"hurricane",
					"taclaser",
					"lrpdlaser",
					"pdlaser",
					"pdburst",
					"irpulse",
					"pulselaser",
					"heavyburst",
					"gravitonbeam",
					"pdlaser",
					"pdburst",
					"irpulse",
					"pulselaser",
					"heavyburst",
					"heavyblaster",
					"phasebeam",
					"amblaster",
			};
		}
		else if (factionId.equalsIgnoreCase("nomad"))
		{
			return new String[] {
					"nom_ultralight_electron_maser",
					"nom_light_electron_maser",
					"nom_heavy_electron_maser",
					"nom_maser_pulse_beam",
					"nom_twin_electron_maser",
					"nom_doom_cannon",
					"nom_light_electron_maser",
					"nom_light_electron_maser",
					"nom_heavy_electron_maser",
					"pilum",
					"harpoon",
					"hurricane",
			};
		}
		else if (factionId.equalsIgnoreCase("interstellarFederation"))
		{
			return new String[] {
					"annihilatorpod",
					"HV50",
					"HV75",
					"HV100",
					"massdriver",
					"harpypod",
					"arbalest",
					"chaingun",
					"dualflak",
					"gauss",
					"heatseeker",
					"heavyac",
					"heavymauler",
					"heavyneedler",
					"hveldriver",
					"lightac",
					"lightag",
					"lightdualac",
					"lightdualmg",
					"lightneedler",
					"locktide",
					"pdburst",
					"pdlaser",
					"railgun",
					"reaper",
					"riptide",
					"shredder",
					"vulcan",
					"lancer",
					"thunderchief",
					"lancer_dual",
					"lancer_single",
					"piranha",
					"omega",
					"cain",
					"citadelpd",
					"hadron",
			};
		}
		else if (factionId.equalsIgnoreCase("relics"))
		{
			return new String[] {
					"annihilatorpod",
					"harpoon",
					"harpoon_single",
					"lightneedler",
					"dualflak",
					"heavyneedler",
					"multineedler",
					"hveldriver",
					"heavymauler",
					"vulcan",
					"pilum",
					"railgun",
					"relics_exo_h",
					"relics_blazo_h",
					"relics_zaap_h",
					"relics_exo_h",
					"relics_blazo_h",
					"relics_zaap_h",
					"relics_exo_h",
					"relics_blazo_h",
					"relics_zaap_h",
					"relics_exo_h",
					"relics_blazo_h",
					"relics_zaap_h",
					"ioncannon",
					"guardian",
					"plasma",
					"taclaser",
					"lrpdlaser",
					"pdlaser",
					"pdburst",
					"irpulse",
					"pulselaser",
					"heavyburst",
					"gravitonbeam",
					"pdlaser",
					"pdburst",
					"irpulse",
					"heavyblaster",
					"phasebeam",
					"amblaster",
			};
		}
		else if (factionId.equalsIgnoreCase("nihil"))
		{
			return new String[] {
					"nihil_phasebeam",
					"vd",
					"amt",
					"pdvr",
					"ldflak",
					"nullcore",
					"gamma",
					"dooml",
					"pddmg",
			};
		}
		else if (factionId.equalsIgnoreCase("thulelegacy"))
		{
			return new String[] {
                    "thule_barbarossa",
                    "thule_bulwark_srm_launcher",
                    "thule_bulwark_srm_pod",
                    "thule_heavy_hvpc",
                    "thule_heavyslugger",
                    "thule_light_hunker",
                    "thule_light_hvpc",
                    "thule_meteor_launcher",
			};
		}
		else if (factionId.equalsIgnoreCase("bushi"))
		{
			return new String[] {
                    "bushi_pd_laser",
                    "bushi_small_laser",
                    "bushi_medium_laser",
                    "bushi_large_laser",
                    "bushi_energy_beam",
                    "bushi_energy_torpedo_launcher_med",
                    "bushi_energy_torpedo_launcher",
                    "bushi_dark_matter",
                    "bushi_srm_pod",
                    "bushi_mrm_pod",
                    "bushi_mrm_pod_xl",
                    "bushi_lrm_pod",
                    "bushi_lrm_pod_xl",
                    "bushi_torpedo_launcher",
			};
		}
		else if (factionId.equalsIgnoreCase("hiigaran_descendants"))
		{
			return new String[] {
					"hii_pulsar_beam",
					"hii_sustained_ion_beam",
					"hii_sustained_ion_beam_xl",
					"hii_accelerated_pulsar_beam_sm",
					"hii_accelerated_pulsar_beam",
					"hii_singularity_cannon",
					"hii_flak_cannon",
					"hii_kinetic_driver",
					"hii_chaingun_medium",
					"hii_chaingun_large",
					"hii_chaingun_medium_he",
					"hii_chaingun_large_he",
					"hii_kinetic_burst_cannon",
					"hii_kinetic_burst_cannon_large",
					"hii_kinetic_burst_cannon_he",
					"hii_kinetic_burst_cannon_large_he",
					"hii_cor_missile_pod",
					"hii_cor_missile_pod_xl",
					"hii_tviir_launcher",
					"hii_torpedo_launcher",
					"hii_abas_missile_pod",
					"hii_abas_missile_pod_xl",
			};
		}
        else if (factionId.equalsIgnoreCase("ceredia"))
        {
            return new String[] {
                "heavymg",
                "dualflak",
                "heavyblaster",
                "lightdualac",
                "lightag",
                "lightneedler",
                "heavymauler",
                "gravitonbeam",
                "pdburst",
                "irpulse",
                "taclaser",
                "pulselaser",
                "heavyburst",
                "hil",
                "hveldriver",
                "reaper",
                "reaper",
                "heavyneedler",
                "hephag",
                "pilum",
                "typhoon",
                "autopulse",
                "heavyac",
            };
        }
        else if (factionId.equalsIgnoreCase("directorate"))
        {
            return new String[] {
                "microlance",
                "cascadedisruptor",
                "heavyblaster",
                "bolas",
                "lancetbeamer",
                "shardsrm",
                "sparrow",
                "gravitonbeam",
                "impulseproj",
                "irpulse",
                "pdlaser",
                "fluxbolt",
                "heavyburst",
                "hpl",
                "firestorm",
                "reaper",
                "ioncannon",
                "guardian",
                "plasma",
                "taclaser",
                "lrpdlaser",
                "pdlaser",
                "pdburst",
                "irpulse",
                "pulselaser",
                "heavyburst",
                "gravitonbeam",
                "pdlaser",
                "pdburst",
                "irpulse",
                "heavyblaster",
                "phasebeam",
                "amblaster",
            };
        }
        else if (factionId.equalsIgnoreCase("isora"))
        {
            return new String[] {
                "annihilatorpod",
                "harpoon",
                "heavymg",
                "arbalest",
                "heavyac",
                "dualflak",
                "heavyblaster",
                "lightdualac",
                "lightag",
                "lightneedler",
                "heavymauler",
                "gravitonbeam",
                "hellbore",
                "cyclone",
                "pilum",
                "taclaser",
                "pulselaser",
                "heavyburst",
                "hveldriver",
                "reaper",
                "atropos",
                "typhoon",
                "ioncannon",
                "heavyneedler",
                "sabot",
                "dualflak",
                "guardian",
                "plasma",
                "chaingun",
                "sabotpod",
                "pilum",
                "autopulse",
                "plasma",
                "hil",
                "hurricane",
                "mjolnir",
                "taclaser",
                "lrpdlaser",
                "pdlaser",
                "pdburst",
                "irpulse",
                "lrpdlaser",
                "pdlaser",
                "pdburst",
                "lrpdlaser",
                "gravitonbeam",
                "phasebeam",
                "amblaster",
                "heavyblaster",
                "hveldriver",
                "shredder",
                "flak",
                "chaingun",
                "gauss",
                "hephag",
                "mark9",
                "multineedler",
            };
        }
        else if (factionId.equalsIgnoreCase("independantMiners"))
        {
            return new String[] {
                "med_mining_drill",
                "mirv_bomb_launcher",
                "planet_cracker",
                "heavy_glassing_laser",
                "archimedes_laser",
                "infer_bomb",
            };
        }
        else if (factionId.equalsIgnoreCase("shadoworder"))
        {
            return new String[] {
            "tadd_lightshadowblaster",
            "tadd_shadowblaster",
            "tadd_shadowcannon",
            "tadd_smallvortexlauncher",
            "tadd_burstlance",
            "tadd_smallenergycore",
            "tadd_shadowmegacannon",
            "tadd_energycore",
            "tadd_sapper",
            "tadd_shadowscythe",
            "tadd_shadowcannon",

            };
        }
        else if (factionId.equalsIgnoreCase("scrappers"))
        {
            return new String[] {
                "hadd_dartergun",
                "hadd_infernorockets",
                "hadd_microwavebeam",
                "hadd_spines",
                "hadd_pointsnap",
                "hadd_spreadchaingun",
                "hadd_volleyrocket",
                "hadd_wrecker",
                "hadd_pounderassaultgun",
                "hadd_pointmissiles",
                "hadd_hellfirerockets",
                "hadd_flakbombardier",
                "hadd_shredder",
                "hadd_titancannon",
                "hadd_deathbringer",
                "hadd_dancingblade",
                "hadd_worldshammer",
                "hadd_pyrostorm",
            };
        }
        else if (factionId.equalsIgnoreCase("zorg_hive"))
        {
            return new String[] {
                    "handlerray",
                    "disruptorray",
                    "concussionray",
                    "transmitterray",
                    "adaptorray",
                    "interdictorray",
            };
        }
        else if (factionId.equalsIgnoreCase("qualljom_society"))
        {
            return new String[] {
                    "qua_dual_mg_small",
                    "qua_vulcan_small",
                    "qua_ac_small",
                    "qua_ag_he_small",
                    "qua_dual_ac_medium",
                    "qua_ag_he_medium",
                    "qua_quad_ac_large",
                    "qua_heavy_cannon",
                    "qua_dual_flak_medium",
                    "qua_dual_flak_large",
                    "qua_pulse_laser_small",
                    "qua_pulse_laser_medium",
                    "qua_pulse_laser_large",
                    "qua_beam_projector_small",
                    "qua_beam_projector_medium",
                    "qua_pd_burst_laser_small",
                    "qua_pd_laser_small",
                    "qua_pd_laser_medium",
                    "qua_pac_large",
                    "qua_ashet_missile_small",
                    "qua_rodim_missile_small",
                    "qua_issot_missile_small",
                    "qua_ashet_missile_medium",
                    "qua_rodim_missile_medium",
                    "qua_issot_missile_medium",
                    "qua_ashet_missile_large",
                    "qua_rodim_missile_large",
                    "qua_issot_missile_large",
                    "qua_tasut_torp_medium",
            };
        }
		else
		{
			System.out.println("EXERELIN ERROR: Faction specific weapons for " + factionId + " not defined");
			return new String[]{};
		}
	}

	public static void addRandomFactionShipsToCargo(CargoAPI cargo, int count, String factionId, SectorAPI sector)
	{
		int r = getRandomInRange(0, 30);

		CampaignFleetAPI fleet;

		if(r == 0)
			fleet = sector.createFleet(factionId, "exerelinOutSystemSupplyConvoy");
		else if(r == 1)
			fleet = sector.createFleet(factionId, "exerelinAsteroidMiningFleet");
		else if(r == 2)
			fleet = sector.createFleet(factionId, "exerelinGasMiningFleet");
        else if(r == 3 || r == 4)
            fleet = sector.createFleet(factionId, "exerelinInSystemStationAttackFleet");
        else if(r == 5)
            fleet = sector.createFleet(factionId, "exerelinInSystemSupplyConvoy");
		else
			fleet = sector.createFleet(factionId, "exerelinGenericFleet");


		List ships = cargo.getMothballedShips().getMembersListCopy();
		if(ships.size() > 25)
			removeRandomShipsFromCargo(cargo,  ships.size() - 22);

		for(int i = 0; i < count; i = i + 1)
		{
			int memberToGet = ExerelinUtils.getRandomInRange(0, fleet.getFleetData().getMembersListCopy().size() - 1);
			FleetMemberAPI fmAPI = (FleetMemberAPI)fleet.getFleetData().getMembersListCopy().get(memberToGet);
			if(fmAPI.isCapital())
			{
				// Get another one to reduce chance of capitals
				memberToGet = ExerelinUtils.getRandomInRange(0, fleet.getFleetData().getMembersListCopy().size() - 1);
				fmAPI = (FleetMemberAPI)fleet.getFleetData().getMembersListCopy().get(memberToGet);
			}
			String shipId = fmAPI.getHullId();
			FleetMemberType memberType = fmAPI.getType();

			if(memberType == FleetMemberType.FIGHTER_WING)
			{
				// Fix wrong Antedilvian names
				if(shipId.startsWith("fighter_"))
				{
					shipId = shipId.substring(8, shipId.length());
					if(shipId.equalsIgnoreCase("persephone"))
						shipId = shipId + "_large";
				}

				// Fix wrong Valkyrian names
				if(shipId.equalsIgnoreCase("helia") || shipId.equalsIgnoreCase("excalibur"))
					shipId = shipId + "_corv";
				if(shipId.equalsIgnoreCase("ancord"))
					shipId = shipId + "_hcorv";

				// Fix wrong Nihil names
				if(shipId.equalsIgnoreCase("nihil_anti"))
					shipId = "anti";

                shipId = shipId + "_wing";

                // Fix wrong independantMiner names (these don't have _wing on the end)
                if(shipId.equalsIgnoreCase("rat_wing"))
                    shipId = "rat";
                if(shipId.equalsIgnoreCase("weasel_wing"))
                    shipId = "weasel";
                if(shipId.equalsIgnoreCase("mouse_wing"))
                    shipId = "mouse";
                if(shipId.equalsIgnoreCase("cony_wing"))
                    shipId = "cony";
			}
			else if (memberType == FleetMemberType.SHIP)
				shipId = shipId + "_Hull";
			else
				return;
			cargo.addMothballedShip(memberType, shipId, null);
			//cargo.getMothballedShips().addFleetMember(fmAPI);
		}
	}

	public static void addRandomEscortShipsToFleet (CampaignFleetAPI campaignFleet, int minCount, int maxCount, String factionId, SectorAPI sector)
	{
		List members = campaignFleet.getFleetData().getMembersListCopy();
		float minSpeed = 1000f;

		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
			float speed = fmAPI.getStats().getMaxSpeed().getModifiedValue();

			if (minSpeed > speed) {
				minSpeed = speed;
			}
		}

		minSpeed -= (minSpeed / 8f + 5f);

		CampaignFleetAPI dummyFleet;
		float[] weights;
		float totalWeight = 0.f;

		do
		{
			dummyFleet = sector.createFleet(factionId, "exerelinGenericFleet");

			members = dummyFleet.getFleetData().getMembersListCopy();
			weights = new float[ members.size() ];
			int m = 0;

			for(Iterator it = members.iterator(); it.hasNext(); )
			{
				FleetMemberAPI fmAPI = (FleetMemberAPI)it.next();

				if (fmAPI.isCapital() || fmAPI.getStats().getMaxSpeed().getModifiedValue() < minSpeed) {
					it.remove();
					continue;
				} else if (fmAPI.isFighterWing()) {
					weights[m] = 1.2f;
				} else if (fmAPI.isFrigate()) {
					weights[m] = 1.2f;
				} else if (fmAPI.isDestroyer()) {
					weights[m] = 0.7f;
				} else if (fmAPI.isCruiser()) {
					weights[m] = 0.2f;
				}

				totalWeight += weights[m];
				m++;
			}
		} while (totalWeight == 0.f); // repeat until dummyFleet contains at least one valid escort ship

		FleetDataAPI fleetData = campaignFleet.getFleetData();
		int count = ExerelinUtils.getRandomInRange(minCount, maxCount);

		for(int i = 0; i < count; )
		{
			float randomWeight = (float)Math.random() * totalWeight;

			int m = 0;
			while (randomWeight >= weights[m])
			{
				randomWeight -= weights[m];
				m++;
			}

			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(m);
			FleetMemberType memberType = fmAPI.getType();
			String shipId = fmAPI.getSpecId();

			if (shipId == null) continue;

			fleetData.addFleetMember( Global.getFactory().createFleetMember(memberType, shipId) );

			i++;
		}
	}

	public static void removeRandomShipsFromCargo(CargoAPI cargoAPI, int numToRemove)
	{
		List ships = cargoAPI.getMothballedShips().getMembersListCopy();
		for(int j = 0; j < Math.min(numToRemove, ships.size()); j++)
		{
			cargoAPI.getMothballedShips().removeFleetMember((FleetMemberAPI)ships.get(ExerelinUtils.getRandomInRange(0, ships.size() - 1)));
		}
	}

	public static void removeRandomWeaponStacksFromCargo(CargoAPI cargoAPI, int numToRemove)
	{
		List weapons = cargoAPI.getWeapons();
		for(int j = 0; j < Math.min(weapons.size(), numToRemove); j++)
		{
			cargoAPI.removeItems(CargoAPI.CargoItemType.WEAPONS, null ,5);
		}
	}

	public static boolean isValidMiningFleet(CampaignFleetAPI fleet)
	{
		List members = fleet.getFleetData().getMembersListCopy();
		Boolean hasMiningShip = false;
		Boolean hasShip = false;
		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
			if(ExerelinUtils.doesStringArrayContainValue(fmAPI.getSpecId(), ExerelinData.getInstance().getValidMiningShips(), true))
				hasMiningShip = true;
			else if(!fmAPI.isFighterWing())
				hasShip = true;
		}

		return (hasMiningShip && hasShip);
	}

	public static int getMiningPower(CampaignFleetAPI fleet)
	{
		int power = 0;

		List members = fleet.getFleetData().getMembersListCopy();
		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
			if(ExerelinUtils.doesStringArrayContainValue(fmAPI.getSpecId(), ExerelinData.getInstance().getValidMiningShips(), true))
				power = power + 1;
		}

		return power;
	}

    // Check if a fleet is a valid boarding fleet
	public static boolean isValidBoardingFleet(CampaignFleetAPI fleet, Boolean checkForTroopTransport)
	{
		List members = fleet.getFleetData().getMembersListCopy();

        if(members.size() < 3)
            return false; // Must be 1 flagship, 1 transport, 1 other ship

		Boolean hasValidFlagship = false;

        Boolean hasValidTroopTransport;
        if(checkForTroopTransport)
            hasValidTroopTransport = false;
        else
            hasValidTroopTransport = true;

		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);

            if(ExerelinUtils.doesStringArrayContainValue(fmAPI.getSpecId(), ExerelinData.getInstance().getValidBoardingFlagships(), true))
				hasValidFlagship = true;

            if(ExerelinUtils.doesStringArrayContainValue(fmAPI.getSpecId(), ExerelinData.getInstance().getValidTroopTransportShips(), true))
                hasValidTroopTransport = true;

            if(hasValidFlagship && hasValidTroopTransport)
                break;
		}

		return (hasValidFlagship && hasValidTroopTransport);
	}

	private static boolean isCapitalInFleet(CampaignFleetAPI fleet)
	{
		List members = fleet.getFleetData().getMembersListCopy();
		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
			if(fmAPI.isCapital())
			{
				return true;
			}
		}

		return false;
	}

	public static void handlePlayerFleetMining(CampaignFleetAPI playerFleet)
	{
		StarSystemAPI system = (StarSystemAPI)playerFleet.getContainingLocation();

		Vector2f playerLocation = playerFleet.getLocation();
		float xMax = playerLocation.getX() + 5;
		float xMin = playerLocation.getX() - 5;
		float yMax = playerLocation.getY() + 5;
		float yMin = playerLocation.getY() - 5;

		List asteroids = system.getAsteroids();
		List planets = system.getPlanets();

		for(int i = 0; i < asteroids.size(); i++)
		{
			SectorEntityToken asteroid = (SectorEntityToken)asteroids.get(i);
			Vector2f asteroidLocation = asteroid.getLocation();

			if(asteroidLocation.getX() > xMin
					&& asteroidLocation.getX() < xMax
					&& asteroidLocation.getY() > yMin
					&& asteroidLocation.getY() < yMax)
			{
				// Mine supplies
				int miningPower = getMiningPower(playerFleet);
				if(miningPower > 0)
				{
					System.out.println(MathUtils.getDistanceSquared(playerFleet.getLocation(), asteroidLocation));
					playerFleet.getCargo().addSupplies(50*miningPower) ;
					Global.getSector().addMessage("Mined " + 50*miningPower + " supplies", Color.green);
					System.out.println("Mined " + 50*miningPower + " supplies");
				}
				return;
			}
		}

		for(int i = 0; i < planets.size(); i++)
		{
			SectorEntityToken planet = (SectorEntityToken)planets.get(i);
			Vector2f planetLocation = planet.getLocation();

			if(planetLocation.getX() > xMin
					&& planetLocation.getX() < xMax
					&& planetLocation.getY() > yMin
					&& planetLocation.getY() < yMax)
			{
				if(planet.getFullName().contains("Gaseous")
						&& !(planet.getFullName().contains(" I") || planet.getFullName().contains(" II") || planet.getFullName().contains(" III")))
				{
					// Mine gas
					int miningPower = getMiningPower(playerFleet);
					if(miningPower > 0)
					{
						System.out.println(MathUtils.getDistanceSquared(playerFleet.getLocation(), planetLocation));
						playerFleet.getCargo().addFuel(50*miningPower);
						Global.getSector().addMessage("Mined " + 50*miningPower + " fuel", Color.green);
						System.out.println("Mined " + 50*miningPower + " fuel");
					}
					return;
				}
			}
		}
	}

	public static void populateStartingStorageFacility(SectorEntityToken storageFacility)
	{
		CargoAPI cargo = storageFacility.getCargo();
		cargo.addItems(CargoAPI.CargoItemType.RESOURCES, "agent", 2);
		cargo.addItems(CargoAPI.CargoItemType.RESOURCES, "prisoner", 2);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "mining_drone_wing", null);
		cargo.addMothballedShip(FleetMemberType.FIGHTER_WING, "mining_drone_wing", null);
	}

    public static void addEliteShipToFleet(CampaignFleetAPI fleet)
    {
        try
        {
            CampaignFleetAPI eliteFleet = SectorManager.getCurrentSectorManager().getSectorAPI().createFleet(fleet.getFaction().getId(), "exerelinEliteFleet");
            if(eliteFleet.getFleetData().getMembersListCopy().size() > 0)
            {
                int memberToAdd = getRandomInRange(0, eliteFleet.getFleetData().getMembersListCopy().size() - 1);
                System.out.println("Adding eliteFleet ship:" + ((FleetMemberAPI)eliteFleet.getFleetData().getMembersListCopy().get(memberToAdd)).getSpecId() + " to " + fleet.getName());
                fleet.getFleetData().addFleetMember((FleetMemberAPI)eliteFleet.getFleetData().getMembersListCopy().get(memberToAdd));
            }
        }
        catch(Exception e)
        {
            System.out.println("EXERELIN ERROR (eliteFleetAdd): " + e.getMessage());
            // Elite fleet not setup for this faction
        }
    }

    public static void handlePlayerBoarding(CampaignFleetAPI playerFleet)
    {
        StarSystemAPI starSystemAPI = (StarSystemAPI)playerFleet.getContainingLocation();
        SystemManager systemManager = SystemManager.getSystemManagerForAPI(starSystemAPI);

        // Check player fleet composition
        if(!ExerelinUtils.isValidBoardingFleet(playerFleet, true))
            return;

        // Get factionId of station at XY coords (if exists)
        SectorEntityToken possibleBoardTarget = systemManager.getStationTokenForXY(playerFleet.getLocation().getX(), playerFleet.getLocation().getY(), 60);
        if(possibleBoardTarget == null)
            return;

        // Get owner faction id
        String possibleBoardTargetFactionId = ExerelinUtils.getStationOwnerFactionId(possibleBoardTarget);
        if(possibleBoardTargetFactionId.equalsIgnoreCase(""))
            return;

        // Check if at war or station is abandonded
        if(Global.getSector().getFaction(ExerelinData.getInstance().getPlayerFaction()).getRelationship(possibleBoardTargetFactionId) >= 0)
            return;

        // Attempt to takeover station
        if(ExerelinUtils.boardStationAttempt(playerFleet, possibleBoardTarget, true))
        {
            systemManager.setStationOwner(possibleBoardTarget, ExerelinData.getInstance().getPlayerFaction(), true, true);

        }
    }

    public static void removeShipsFromFleet(CampaignFleetAPI fleet, String[] shipTypes, Boolean limitToOneRemove)
    {
        List members = fleet.getFleetData().getMembersListCopy();

        for(int i = 0; i < shipTypes.length; i++)
        {
            for(int j = 0; j < members.size(); j++)
            {
                if(((FleetMemberAPI)members.get(j)).getSpecId().contains(shipTypes[i]))
                {
                    fleet.getFleetData().removeFleetMember((FleetMemberAPI)members.get((j)));
                    if(limitToOneRemove)
                        return;
                }
            }
        }
    }

    // Play out a station boarding attempt
    // Returns true if board successful, flase if not
    // Subsequent code must handle any station ownership changes etc.
    public static boolean boardStationAttempt(CampaignFleetAPI fleet, SectorEntityToken station, Boolean playerFleet)
    {
        int marinesDefending = station.getCargo().getMarines();
        int marinesAttacking = fleet.getCargo().getMarines();

        // Let station record know it is being boarded
        StarSystemAPI starSystemAPI = (StarSystemAPI)fleet.getContainingLocation();
        SystemManager systemManager = SystemManager.getSystemManagerForAPI(starSystemAPI);
        systemManager.getSystemStationManager().getStationRecordForToken(station).setIsBeingBoarded(true);

        while(marinesDefending > 0 && marinesAttacking > 0)
        {
            int attackRoll = ExerelinUtils.getRandomInRange(1, 6);
            int defendRoll = ExerelinUtils.getRandomInRange(1, 6);

            if(attackRoll > defendRoll)
                marinesDefending = marinesDefending - (attackRoll - defendRoll);
            else
                marinesAttacking = marinesAttacking - (defendRoll - attackRoll);

            if(ExerelinUtils.getRandomInRange(0, 20) == 0)
                break;
        }

        // Report if player fleet
        if(playerFleet)
        {
            if(fleet.getCargo().getMarines() > marinesAttacking)
            {
                Global.getSector().addMessage("Your fleet lost " + (fleet.getCargo().getMarines() - marinesAttacking) + " marines assualting the station", Color.green);
                System.out.println("Your fleet lost " + (fleet.getCargo().getMarines() - marinesAttacking) + " marines assualting the station");
            }
            if(marinesDefending <= 0)
            {
                Global.getSector().addMessage("Your fleet successfully boarded " + station.getName(), Color.green);
                System.out.println("Your fleet successfully boarded " + station.getName());
            }
        }

        if(station.getCargo().getMarines() > marinesDefending)
            ExerelinUtils.decreaseCargo(station.getCargo(), "marines", station.getCargo().getMarines() - marinesDefending);

        if(fleet.getCargo().getMarines() > marinesAttacking)
            ExerelinUtils.decreaseCargo(fleet.getCargo(), "marines", fleet.getCargo().getMarines() - marinesAttacking);

        if(marinesDefending <= 0)
        {
            // Attackers won
            ExerelinUtils.removeShipsFromFleet(fleet, ExerelinData.getInstance().getValidBoardingFlagships(), true);
            ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.1f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(fleet.getFaction().getId()));
            return true;
        }
        else
        {
            // Defenders won
            if(fleet.getCargo().getMarines() <= 0)
            {
                // Defenders total win
                ExerelinUtils.removeShipsFromFleet(fleet, ExerelinData.getInstance().getValidBoardingFlagships(), true);
                ExerelinUtils.removeShipsFromFleet(fleet, ExerelinData.getInstance().getValidTroopTransportShips(), false);
                ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.1f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(fleet.getFaction().getId()));
                if(playerFleet)
                {
                    Global.getSector().addMessage("Your fleet has failed to capture station and has suffered extensive losses", Color.green);
                    System.out.println("Your fleet has failed to capture station and has suffered extensive losses");
                }
            }
            return false;
        }
    }

    // Defaults a fleets cargo to acceptable ranges
    // Useful after changing a fleet composition
    public static void resetFleetCargoToDefaults(CampaignFleetAPI fleet, float extraCrewPercent, float marinesPercent, CargoAPI.CrewXPLevel crewXPLevel)
    {
        CargoAPI fleetCargo = fleet.getCargo();
        List members = fleet.getFleetData().getMembersListCopy();
        fleetCargo.clear();
        for(int i = 0; i < members.size(); i = i + 1)
        {
            FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
            fleetCargo.addCrew(crewXPLevel, (int) fmAPI.getMinCrew() + (int) ((fmAPI.getMaxCrew() - fmAPI.getMinCrew()) * extraCrewPercent));
            fleetCargo.addMarines((int) ((fmAPI.getMaxCrew() - fmAPI.getMinCrew()) * marinesPercent));
            fleetCargo.addFuel(fmAPI.getFuelCapacity() / 2f);
            fleetCargo.addSupplies(fmAPI.getCargoCapacity() / 2f);
        }
    }

    // Returns a factions crew xp level
    public static CargoAPI.CrewXPLevel getCrewXPLevelForFaction(String factionId)
    {
        //if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
        //    return CargoAPI.CrewXPLevel.GREEN;
        //else
            return  CargoAPI.CrewXPLevel.REGULAR;
    }

    public static void mergeFleets(CampaignFleetAPI mainFleet, CampaignFleetAPI fleetToMerge)
    {
        List members = fleetToMerge.getFleetData().getMembersListCopy();
        for(int i = 0; i < members.size(); i = i + 1)
            mainFleet.getFleetData().addFleetMember((FleetMemberAPI)members.get((i)));
    }
}
