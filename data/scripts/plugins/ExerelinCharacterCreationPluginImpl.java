package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI.CrewXPLevel;
import com.fs.starfarer.api.characters.CharacterCreationPlugin;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.world.exerelin.ExerelinSetupData;
import exerelin.ExerelinUtils;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFleet;

import java.util.ArrayList;
import java.util.List;

public class ExerelinCharacterCreationPluginImpl implements CharacterCreationPlugin
{
	public static class ResponseImpl implements Response {
		private String text;
		public ResponseImpl(String text)
		{
			this.text = text;
		}
		public String getText()
		{
			return text;
		}
	}

    private ResponseImpl ONE_SYSTEM = new ResponseImpl("1 System");
    private ResponseImpl TWO_SYSTEMS = new ResponseImpl("2 Systems");
    private ResponseImpl FOUR_SYSTEMS = new ResponseImpl("4 Systems");
    private ResponseImpl SIX_SYSTEMS = new ResponseImpl("6 Systems");
    private ResponseImpl EIGHT_SYSTEMS = new ResponseImpl("8 Systems");
    private ResponseImpl TWELVE_SYSTEMS = new ResponseImpl("12 Systems");
    private ResponseImpl SIXTEEN_SYSTEMS = new ResponseImpl("16 Systems");
    private ResponseImpl TWENTY_SYSTEMS = new ResponseImpl("20 Systems");
    private ResponseImpl TWENTYFOUR_SYSTEMS = new ResponseImpl("24 Systems");

	private ResponseImpl SMALL_SYSTEM = new ResponseImpl("Small");
	private ResponseImpl MEDIUM_SYSTEM = new ResponseImpl("Medium");
	private ResponseImpl LARGE_SYSTEM = new ResponseImpl("Large");

	private ResponseImpl THREE_PLANETS = new ResponseImpl("3 Planets");
	private ResponseImpl SIX_PLANETS = new ResponseImpl("6 Planets");
	private ResponseImpl NINE_PLANETS = new ResponseImpl("9 Planets");
	private ResponseImpl TWELVE_PLANETS = new ResponseImpl("12 Planets");
	private ResponseImpl FIFTEEN_PLANETS = new ResponseImpl("15 Planets");
	private ResponseImpl EIGHTEEN_PLANETS = new ResponseImpl("18 Planets");
	private ResponseImpl TWENTYONE_PLANETS = new ResponseImpl("21 Planets");

	private ResponseImpl ZERO_ASTEROID_BELTS = new ResponseImpl("0 Asteroid Belts");
	private ResponseImpl TWO_ASTEROID_BELTS = new ResponseImpl("2 Asteroid Belts");
	private ResponseImpl FOUR_ASTEROID_BELTS = new ResponseImpl("4 Asteroid Belts");
	private ResponseImpl SIX_ASTEROID_BELTS = new ResponseImpl("6 Asteroid Belts");
	private ResponseImpl EIGHT_ASTEROID_BELTS = new ResponseImpl("8 Asteroid Belts");
	private ResponseImpl TEN_ASTEROID_BELTS = new ResponseImpl("10 Asteroid Belts");

	private ResponseImpl FIVE_STATIONS = new ResponseImpl("5 Stations");
	private ResponseImpl TEN_STATIONS = new ResponseImpl("10 Stations");
	private ResponseImpl FIFTEEN_STATIONS = new ResponseImpl("15 Stations");
	private ResponseImpl TWENTY_STATIONS = new ResponseImpl("20 Stations");
	private ResponseImpl TWENTYFIVE_STATIONS = new ResponseImpl("25 Stations");
	private ResponseImpl THIRTY_STATIONS = new ResponseImpl("30 Stations");
	private ResponseImpl THIRTYFIVE_STATIONS = new ResponseImpl("35 Stations");

	private ResponseImpl OMNI_FAC_PRESENT = new ResponseImpl("The OmniFactory is available");
	private ResponseImpl OMNI_FAC_NOT_PRESENT = new ResponseImpl("The OmniFactory is not available");

	private ResponseImpl ONE_FACTION = new ResponseImpl("1 other Faction");
	private ResponseImpl THREE_FACTION = new ResponseImpl("3 other Factions");
	private ResponseImpl SIX_FACTION = new ResponseImpl("6 other Factions");
	private ResponseImpl NINE_FACTION = new ResponseImpl("9 other Factions");
	private ResponseImpl ALL_FACTION = new ResponseImpl("All factions you know of!");

	private ResponseImpl RESPAWN_YES = new ResponseImpl("Yes, factions will respawn");
	private ResponseImpl RESPAWN_YES_COND = new ResponseImpl("Yes, but only factions initially in Exerelin");
	private ResponseImpl RESPAWN_YES_COND_MAX = new ResponseImpl("Yes, but only to a maximum of the starting factions");
	private ResponseImpl RESPAWN_NO = new ResponseImpl("No, factions will not respawn");

	private ResponseImpl RESPAWN_ZERO = new ResponseImpl("As soon as possible!");
	private ResponseImpl RESPAWN_TWO = new ResponseImpl("Two months");
	private ResponseImpl RESPAWN_FOUR = new ResponseImpl("Four months");
	private ResponseImpl RESPAWN_EIGHT = new ResponseImpl("Eight months");
	private ResponseImpl RESPAWN_SIXTEEN = new ResponseImpl("Sixteen months");

	private ResponseImpl START_SHIP_FACTION = new ResponseImpl("Start with a faction specfic frigate");
	private ResponseImpl START_SHIP_TOREUPPLENTY = new ResponseImpl("Start with a Tore Up Plenty ship");

	// -- FACTION RESPONSES AUTO GENERATED --
    private ResponseImpl START_AS_PLAYER_FACTION = new ResponseImpl("Start Unaligned");

	private ResponseImpl NEXT = new ResponseImpl("Next...");
	private ResponseImpl PREV = new ResponseImpl("Prev...");

	private ResponseImpl POPULATED_SINGLE = new ResponseImpl("Single Station");
	private ResponseImpl POPULATED_PARTIALLY = new ResponseImpl("Partially");
    private ResponseImpl POPULATED_FULLY = new ResponseImpl("Fully");


	private int stage = 0;
	private String [] prompts = new String [] {
        "Number of systems?",
		"Max system size?",
		"Max planets per system?",
		"Max asteroid belts per system?",
		"Max stations per system?",
		"Is the OmniFactory available?",
		"How many other factions are in Sector Exerelin initially?",
		"Shall factions return to Sector Exerelin?",
		"How much time passes between factions returning to Sector Exerelin?",
		"What kind of ship do you start with?",
		"For the conquest of Sector Exerelin you have joined...",
		"... or you joined ..",
		"How populated is Sector Exerelin?",
	};

	private boolean factionStartShip = true;
	private boolean toreUpPlentyShip = false;

	public String getPrompt()
	{
		if (stage < prompts.length)
		{
			return prompts[stage];
		}
		return null;
	}

	@SuppressWarnings("unchecked")

	public List getResponses()
	{
		List result = new ArrayList();
        if(stage == 0)
        {
            ExerelinSetupData.resetInstance();
            ExerelinConfig.loadSettings();
            result.add(ONE_SYSTEM);
            result.add(TWO_SYSTEMS);
            result.add(FOUR_SYSTEMS);
            result.add(SIX_SYSTEMS);
            result.add(EIGHT_SYSTEMS);
            result.add(TWELVE_SYSTEMS);
            result.add(SIXTEEN_SYSTEMS);
            result.add(TWENTY_SYSTEMS);
            result.add(TWENTYFOUR_SYSTEMS);
        }
		else if(stage == 1)
		{
			result.add(SMALL_SYSTEM);
			result.add(MEDIUM_SYSTEM);
			result.add(LARGE_SYSTEM);
		}
		else if (stage == 2)
		{
			result.add(THREE_PLANETS);
			result.add(SIX_PLANETS);
			result.add(NINE_PLANETS);
			result.add(TWELVE_PLANETS);
			result.add(FIFTEEN_PLANETS);
			result.add(EIGHTEEN_PLANETS);
			result.add(TWENTYONE_PLANETS);
		}
		else if (stage == 3)
		{
			result.add(ZERO_ASTEROID_BELTS);
			result.add(TWO_ASTEROID_BELTS);
			result.add(FOUR_ASTEROID_BELTS);
			result.add(SIX_ASTEROID_BELTS);
			result.add(EIGHT_ASTEROID_BELTS);
			result.add(TEN_ASTEROID_BELTS);
		}
		else if (stage == 4)
		{
			result.add(FIVE_STATIONS);
			result.add(TEN_STATIONS);
			result.add(FIFTEEN_STATIONS);
			result.add(TWENTY_STATIONS);
			result.add(TWENTYFIVE_STATIONS);
			result.add(THIRTY_STATIONS);
			result.add(THIRTYFIVE_STATIONS);
		}
		else if (stage == 5)
		{
			result.add(OMNI_FAC_PRESENT);
			result.add(OMNI_FAC_NOT_PRESENT);
		}
		else if (stage == 6)
		{
			result.add(ONE_FACTION);
			result.add(THREE_FACTION);
			result.add(SIX_FACTION);
			result.add(NINE_FACTION);
			result.add(ALL_FACTION);
		}
		else if (stage == 7)
		{
			//result.add(RESPAWN_YES);
			//result.add(RESPAWN_YES_COND);
			//result.add(RESPAWN_YES_COND_MAX);
			result.add(RESPAWN_NO);
		}
		else if (stage == 8)
		{
			//result.add(RESPAWN_ZERO);
			result.add(RESPAWN_TWO);
			result.add(RESPAWN_FOUR);
			result.add(RESPAWN_EIGHT);
			result.add(RESPAWN_SIXTEEN);
		}
		else if (stage == 9)
		{
			result.add(START_SHIP_FACTION);
			result.add(START_SHIP_TOREUPPLENTY);
		}
		else if (stage == 10)
		{
            // Add option for starting unaligned
            result.add(START_AS_PLAYER_FACTION);

            String[] possibleFactions = ExerelinSetupData.getInstance().getPossibleFactions();
            if(possibleFactions.length > 6)
            {
                for(int i = 0; i < possibleFactions.length/2; i = i + 1)
                {
                    result.add(new ResponseImpl(possibleFactions[i]));
                }
                result.add(NEXT);
            }
            else
            {
                for(int i = 0; i < possibleFactions.length; i = i + 1)
                {
                    result.add(new ResponseImpl(possibleFactions[i]));
                }
            }

		}
		else if (stage == 11)
		{
			String[] possibleFactions = ExerelinSetupData.getInstance().getPossibleFactions();
			for(int i = possibleFactions.length/2; i < possibleFactions.length; i = i + 1)
			{
				result.add(new ResponseImpl(possibleFactions[i]));
			}
			result.add(PREV);
		}
		else if (stage == 12)
		{
			result.add(POPULATED_SINGLE);
			result.add(POPULATED_PARTIALLY);
            result.add(POPULATED_FULLY);
		}

        return result;
    }


	public void submit(Response response, CharacterCreationData data)
	{
		stage++;
        if (response == ONE_SYSTEM)
            ExerelinSetupData.getInstance().numSystems = 1;
        else if (response == TWO_SYSTEMS)
            ExerelinSetupData.getInstance().numSystems = 2;
        else if (response == FOUR_SYSTEMS)
            ExerelinSetupData.getInstance().numSystems = 4;
        else if (response == SIX_SYSTEMS)
            ExerelinSetupData.getInstance().numSystems = 6;
        else if (response == EIGHT_SYSTEMS)
            ExerelinSetupData.getInstance().numSystems = 8;
        else if (response == TWELVE_SYSTEMS)
            ExerelinSetupData.getInstance().numSystems = 12;
        else if (response == SIXTEEN_SYSTEMS)
            ExerelinSetupData.getInstance().numSystems = 16;
        else if (response == TWENTY_SYSTEMS)
            ExerelinSetupData.getInstance().numSystems = 20;
        else if (response == TWENTYFOUR_SYSTEMS)
            ExerelinSetupData.getInstance().numSystems = 24;
		else if (response == SMALL_SYSTEM)
			ExerelinSetupData.getInstance().maxSystemSize = 16000;
		else if (response == MEDIUM_SYSTEM)
			ExerelinSetupData.getInstance().maxSystemSize = 32000;
		else if (response == LARGE_SYSTEM)
			ExerelinSetupData.getInstance().maxSystemSize = 40000;
		else if (response == THREE_PLANETS)
			ExerelinSetupData.getInstance().maxPlanets = 3;
		else if (response == SIX_PLANETS)
			ExerelinSetupData.getInstance().maxPlanets = 6;
		else if (response == NINE_PLANETS)
			ExerelinSetupData.getInstance().maxPlanets = 9;
		else if (response == TWELVE_PLANETS)
			ExerelinSetupData.getInstance().maxPlanets = 12;
		else if (response == FIFTEEN_PLANETS)
			ExerelinSetupData.getInstance().maxPlanets = 15;
		else if (response == EIGHTEEN_PLANETS)
			ExerelinSetupData.getInstance().maxPlanets = 18;
        else if (response == TWENTYONE_PLANETS)
            ExerelinSetupData.getInstance().maxPlanets = 21;
		else if (response == ZERO_ASTEROID_BELTS)
			ExerelinSetupData.getInstance().maxAsteroidBelts = 0;
		else if (response == TWO_ASTEROID_BELTS)
			ExerelinSetupData.getInstance().maxAsteroidBelts = 2;
		else if (response == FOUR_ASTEROID_BELTS)
			ExerelinSetupData.getInstance().maxAsteroidBelts = 4;
		else if (response == SIX_ASTEROID_BELTS)
			ExerelinSetupData.getInstance().maxAsteroidBelts = 6;
		else if (response == EIGHT_ASTEROID_BELTS)
			ExerelinSetupData.getInstance().maxAsteroidBelts = 8;
		else if (response == TEN_ASTEROID_BELTS)
			ExerelinSetupData.getInstance().maxAsteroidBelts = 10;
		else if (response == FIVE_STATIONS)
			ExerelinSetupData.getInstance().maxStations = 5;
		else if (response == TEN_STATIONS)
			ExerelinSetupData.getInstance().maxStations = 10;
		else if (response == FIFTEEN_STATIONS)
			ExerelinSetupData.getInstance().maxStations = 15;
		else if (response == TWENTY_STATIONS)
			ExerelinSetupData.getInstance().maxStations = 20;
		else if (response == TWENTYFIVE_STATIONS)
			ExerelinSetupData.getInstance().maxStations = 25;
		else if (response == THIRTY_STATIONS)
			ExerelinSetupData.getInstance().maxStations = 30;
		else if (response == THIRTYFIVE_STATIONS)
			ExerelinSetupData.getInstance().maxStations = 35;
		else if (response == OMNI_FAC_PRESENT)
			ExerelinSetupData.getInstance().omniFacPresent = true;
		else if (response == OMNI_FAC_NOT_PRESENT)
			ExerelinSetupData.getInstance().omniFacPresent = false;
		else if (response == ONE_FACTION)
		{
			ExerelinSetupData.getInstance().numStartFactions = 1;
		}
		else if (response == THREE_FACTION)
		{
			ExerelinSetupData.getInstance().numStartFactions = 3;
		}
		else if (response == SIX_FACTION)
		{
			ExerelinSetupData.getInstance().numStartFactions = 6;
		}
		else if (response == NINE_FACTION)
		{
			ExerelinSetupData.getInstance().numStartFactions = 9;
		}
		else if (response == ALL_FACTION)
		{
			ExerelinSetupData.getInstance().numStartFactions = 99; // Just use how many available factions there are
		}
		else if (response == RESPAWN_YES)
		{
			ExerelinSetupData.getInstance().respawnFactions = true;
			ExerelinSetupData.getInstance().onlyRespawnStartingFactions = false;
			ExerelinSetupData.getInstance().maxFactionsInExerelinAtOnce = 999;
		}
		else if (response == RESPAWN_YES_COND)
		{
			ExerelinSetupData.getInstance().respawnFactions = true;
			ExerelinSetupData.getInstance().onlyRespawnStartingFactions = true;
			ExerelinSetupData.getInstance().maxFactionsInExerelinAtOnce = 999;
		}
		else if (response == RESPAWN_YES_COND_MAX)
		{
			ExerelinSetupData.getInstance().respawnFactions = true;
			ExerelinSetupData.getInstance().onlyRespawnStartingFactions = false;
			ExerelinSetupData.getInstance().maxFactionsInExerelinAtOnce = ExerelinSetupData.getInstance().numStartFactions + 1;
		}
		else if (response == RESPAWN_NO)
		{
			ExerelinSetupData.getInstance().respawnFactions = false;
			ExerelinSetupData.getInstance().onlyRespawnStartingFactions = false;
			ExerelinSetupData.getInstance().maxFactionsInExerelinAtOnce = 999;
			stage++;

			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == RESPAWN_ZERO)
		{
			ExerelinSetupData.getInstance().respawnDelay = 0;

			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == RESPAWN_TWO)
		{
			ExerelinSetupData.getInstance().respawnDelay = 60;

			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == RESPAWN_FOUR)
		{
			ExerelinSetupData.getInstance().respawnDelay = 120;

			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == RESPAWN_EIGHT)
		{
			ExerelinSetupData.getInstance().respawnDelay = 240;

			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == RESPAWN_SIXTEEN)
		{
			ExerelinSetupData.getInstance().respawnDelay = 480;
			
			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == START_SHIP_FACTION)
		{
			factionStartShip = true;
		}
		else if (response == START_SHIP_TOREUPPLENTY)
		{
			factionStartShip = false;
			toreUpPlentyShip = true;
		}
		else if (response == POPULATED_SINGLE)
		{
            ExerelinSetupData.getInstance().isSectorPopulated = false;
            ExerelinSetupData.getInstance().isSectorPartiallyPopulated = false;
		}
		else if (response == POPULATED_PARTIALLY)
		{
            ExerelinSetupData.getInstance().isSectorPopulated = true;
            ExerelinSetupData.getInstance().isSectorPartiallyPopulated = true;
		}
        else if (response == POPULATED_FULLY)
        {
            ExerelinSetupData.getInstance().isSectorPopulated = true;
            ExerelinSetupData.getInstance().isSectorPartiallyPopulated = false;
        }
		else if (response == PREV)
		{
			stage = stage - 2;
		}
		else if (response == NEXT)
		{
			// Don't do anything
		}
		else
		{
			MutableCharacterStatsAPI stats = data.getPerson().getStats();

			stats.addAptitudePoints(3);
			stats.addSkillPoints(6);

            if(response == START_AS_PLAYER_FACTION)
            {
                ExerelinSetupData.getInstance().setPlayerFaction("player");
                setStartingShipFromFactionSelection("player", data);
            }
            else
            {
                String[] possibleFactions = ExerelinSetupData.getInstance().getPossibleFactions();
                for (int i = 0; i < possibleFactions.length; i = i + 1) {
                    if (response.getText().equalsIgnoreCase(possibleFactions[i])) {
                        ExerelinSetupData.getInstance().setPlayerFaction(possibleFactions[i]);
                        setStartingShipFromFactionSelection(possibleFactions[i], data);
                        break;
                    }
                }
            }

			if(stage == 11)
				stage = stage + 1; // Skip next faction selection
		}
	}

	private void setStartingShipFromFactionSelection(String factionId, CharacterCreationData data)
	{
		if(factionStartShip)
		{
            if(!factionId.equalsIgnoreCase("player"))
            {
                // Get starting variants for selected faction
                String[] startingVariants = ExerelinConfig.getExerelinFactionConfig(factionId).startingVariants;

                for(int i = 0; i < startingVariants.length; i++) {
                    try
                    {
                        data.addStartingShipChoice(startingVariants[i]);
                    }
                    catch(Exception e)
                    {
                        System.out.println("Error loading ship " + startingVariants[i]);
                    }
                }
            }
            else
            {
                // Get variants for unaligned start
                int factionShips = 3;
                if(isToreUpPlentyInstalled())
                {
                    // Use tore up plenty if it is installed
                    try {
                        data.addStartingShipChoice(ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize("scavengers", ShipAPI.HullSize.FRIGATE));
                        data.addStartingShipChoice(ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize("scavengers", ShipAPI.HullSize.FRIGATE));
                        data.addStartingShipChoice(ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize("scavengers", ShipAPI.HullSize.FRIGATE));
                    }
                    catch(Exception e)
                    {
                        System.out.println("Error loading ship from Tore Up Plenty");
                    }
                }
                else
                {
                    factionShips = factionShips + 3;
                }

                for(int i = 0; i < factionShips; i++)
                {
                    // Get some random frigates from possible factions
                    String[] possibleFactions = ExerelinSetupData.getInstance().getPossibleFactions();
                    int rand = ExerelinUtils.getRandomInRange(0, possibleFactions.length - 1);
                    try {
                        data.addStartingShipChoice(ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(possibleFactions[rand], ShipAPI.HullSize.FRIGATE));
                    }
                    catch(Exception e)
                    {
                        System.out.println("Error loading ship for " + possibleFactions[rand]);
                    }
                }
            }
		}
		else
		{
			if(toreUpPlentyShip)
			{
                try
                {
                    data.addStartingShipChoice(ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize("scavengers", ShipAPI.HullSize.FRIGATE));
                    data.addStartingShipChoice(ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize("scavengers", ShipAPI.HullSize.FRIGATE));
                    data.addStartingShipChoice(ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize("scavengers", ShipAPI.HullSize.FRIGATE));
                    data.addStartingShipChoice(ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize("scavengers", ShipAPI.HullSize.DESTROYER));
                    data.addStartingShipChoice(ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize("scavengers", ShipAPI.HullSize.DESTROYER));
                    data.addStartingShipChoice(ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize("scavengers", ShipAPI.HullSize.CRUISER));
                }
                catch(Exception e)
                {
                    System.out.println("Error loading ship from Tore Up Plenty");
                }
			}
			else
			{
				System.out.println("EXERELIN ERROR: Starting ship initialistion failure");
                data.addStartingShipChoice("shuttle_Attack");
            }
		}
        //data.addStartingShipChoice("exerelinshuttle_Attack");
	}

	public void startingShipPicked(String variantId, CharacterCreationData data)
	{
		data.getStartingCargo().addFuel(10);
		data.getStartingCargo().addSupplies(40);
		data.getStartingCargo().addCrew(CrewXPLevel.REGULAR, 25);
		data.getStartingCargo().addMarines(3);
        ExerelinSetupData.getInstance().setPlayerStartingShipVariant(variantId);
	}

	public boolean isToreUpPlentyInstalled()
	{
		try
		{
			Global.getSettings().getScriptClassLoader().loadClass("data.scripts.TUPModPlugin");
			System.out.println("EXERELIN: tore up plenty installed");
			return true;
		}
		catch (ClassNotFoundException ex)
		{
			System.out.println("EXERELIN: tore up plenty not installed");
			return false;
		}
	}
}







