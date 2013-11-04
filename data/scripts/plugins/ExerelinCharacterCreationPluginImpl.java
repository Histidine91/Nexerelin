package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI.CrewXPLevel;
import com.fs.starfarer.api.characters.CharacterCreationPlugin;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import data.scripts.world.exerelin.ExerelinData;

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
	private ResponseImpl ONE_VANILLA_FACTION = new ResponseImpl("1 familiar face [Vanilla Only]");
	private ResponseImpl ALL_VANILLA_FACTION = new ResponseImpl("A few old friends [Vanilla Only]");

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
	private ResponseImpl START_SHIP_TOREUPPLENTY1 = new ResponseImpl("Start with a Tore Up Plenty frigate");
	private ResponseImpl START_SHIP_TOREUPPLENTY2 = new ResponseImpl("Start with a Tore Up Plenty other class");

	// -- FACTION RESPONSES AUTO GENERATED --

	private ResponseImpl NEXT = new ResponseImpl("Next...");
	private ResponseImpl PREV = new ResponseImpl("Prev...");

	private ResponseImpl FREE_GOODS = new ResponseImpl("Be given free goods");
	private ResponseImpl PAY_FOR_GOODS = new ResponseImpl("Pay for goods");


	private int stage = 0;
	private String [] prompts = new String [] {
        "Number of systems?",
		"Max system size?",
		"Max planets per system?",
		"Max asteroid belts per system?",
		"Max stations per system?",
		"Is the OmniFactory available?",
		"When you arrive at Sector Exerelin, how many other factions are there with you initially?",
		"Shall factions return to Sector Exerelin?",
		"How much time passes between factions returning to Sector Exerelin?",
		"What kind of ship do you start with?",
		"For the conquest of Sector Exerelin you have joined...",
		"... or you joined ..",
		//"At your faction aligned stations you expect to...",
	};

	private boolean factionStartShip = true;
	private boolean toreUpPlentyFrigate = false;
	private boolean toreUpPlentyOther = false;

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
            ExerelinData.resetInstance();
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
			result.add(ONE_VANILLA_FACTION);
			result.add(ALL_VANILLA_FACTION);
		}
		else if (stage == 7)
		{
			result.add(RESPAWN_YES);
			result.add(RESPAWN_YES_COND);
			result.add(RESPAWN_YES_COND_MAX);
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
			result.add(START_SHIP_TOREUPPLENTY1);
			result.add(START_SHIP_TOREUPPLENTY2);
		}
		else if (stage == 10)
		{
			String[] possibleFactions = ExerelinData.getInstance().getPossibleFactions();
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
			String[] possibleFactions = ExerelinData.getInstance().getPossibleFactions();
			for(int i = possibleFactions.length/2; i < possibleFactions.length; i = i + 1)
			{
				result.add(new ResponseImpl(possibleFactions[i]));
			}
			result.add(PREV);
		}
		else if (stage == 12)
		{
			stage++; // SKIP THIS STAGE
			//result.add(FREE_GOODS);
			//result.add(PAY_FOR_GOODS);

		}

        return result;
    }


	public void submit(Response response, CharacterCreationData data)
	{
		stage++;
        if (response == ONE_SYSTEM)
            ExerelinData.getInstance().numSystems = 1;
        else if (response == TWO_SYSTEMS)
            ExerelinData.getInstance().numSystems = 2;
        else if (response == FOUR_SYSTEMS)
            ExerelinData.getInstance().numSystems = 4;
        else if (response == SIX_SYSTEMS)
            ExerelinData.getInstance().numSystems = 6;
        else if (response == EIGHT_SYSTEMS)
            ExerelinData.getInstance().numSystems = 8;
        else if (response == TWELVE_SYSTEMS)
            ExerelinData.getInstance().numSystems = 12;
        else if (response == SIXTEEN_SYSTEMS)
            ExerelinData.getInstance().numSystems = 16;
        else if (response == TWENTY_SYSTEMS)
            ExerelinData.getInstance().numSystems = 20;
        else if (response == TWENTYFOUR_SYSTEMS)
            ExerelinData.getInstance().numSystems = 24;
		else if (response == SMALL_SYSTEM)
			ExerelinData.getInstance().maxSystemSize = 16000;
		else if (response == MEDIUM_SYSTEM)
			ExerelinData.getInstance().maxSystemSize = 32000;
		else if (response == LARGE_SYSTEM)
			ExerelinData.getInstance().maxSystemSize = 40000;
		else if (response == THREE_PLANETS)
			ExerelinData.getInstance().maxPlanets = 3;
		else if (response == SIX_PLANETS)
			ExerelinData.getInstance().maxPlanets = 6;
		else if (response == NINE_PLANETS)
			ExerelinData.getInstance().maxPlanets = 9;
		else if (response == TWELVE_PLANETS)
			ExerelinData.getInstance().maxPlanets = 12;
		else if (response == FIFTEEN_PLANETS)
			ExerelinData.getInstance().maxPlanets = 15;
		else if (response == EIGHTEEN_PLANETS)
			ExerelinData.getInstance().maxPlanets = 18;
        else if (response == TWENTYONE_PLANETS)
            ExerelinData.getInstance().maxPlanets = 21;
		else if (response == ZERO_ASTEROID_BELTS)
			ExerelinData.getInstance().maxAsteroidBelts = 0;
		else if (response == TWO_ASTEROID_BELTS)
			ExerelinData.getInstance().maxAsteroidBelts = 2;
		else if (response == FOUR_ASTEROID_BELTS)
			ExerelinData.getInstance().maxAsteroidBelts = 4;
		else if (response == SIX_ASTEROID_BELTS)
			ExerelinData.getInstance().maxAsteroidBelts = 6;
		else if (response == EIGHT_ASTEROID_BELTS)
			ExerelinData.getInstance().maxAsteroidBelts = 8;
		else if (response == TEN_ASTEROID_BELTS)
			ExerelinData.getInstance().maxAsteroidBelts = 10;
		else if (response == FIVE_STATIONS)
			ExerelinData.getInstance().maxStations = 5;
		else if (response == TEN_STATIONS)
			ExerelinData.getInstance().maxStations = 10;
		else if (response == FIFTEEN_STATIONS)
			ExerelinData.getInstance().maxStations = 15;
		else if (response == TWENTY_STATIONS)
			ExerelinData.getInstance().maxStations = 20;
		else if (response == TWENTYFIVE_STATIONS)
			ExerelinData.getInstance().maxStations = 25;
		else if (response == THIRTY_STATIONS)
			ExerelinData.getInstance().maxStations = 30;
		else if (response == THIRTYFIVE_STATIONS)
			ExerelinData.getInstance().maxStations = 35;
		else if (response == OMNI_FAC_PRESENT)
			ExerelinData.getInstance().omniFacPresent = true;
		else if (response == OMNI_FAC_NOT_PRESENT)
			ExerelinData.getInstance().omniFacPresent = false;
		else if (response == ONE_FACTION)
		{
			ExerelinData.getInstance().numStartFactions = 1;
			ExerelinData.getInstance().onlyVanillaFactions = false;
		}
		else if (response == THREE_FACTION)
		{
			ExerelinData.getInstance().numStartFactions = 3;
			ExerelinData.getInstance().onlyVanillaFactions = false;
		}
		else if (response == SIX_FACTION)
		{
			ExerelinData.getInstance().numStartFactions = 6;
			ExerelinData.getInstance().onlyVanillaFactions = false;
		}
		else if (response == NINE_FACTION)
		{
			ExerelinData.getInstance().numStartFactions = 9;
			ExerelinData.getInstance().onlyVanillaFactions = false;
		}
		else if (response == ALL_FACTION)
		{
			ExerelinData.getInstance().numStartFactions = 99; // Just use how many available factions there are
			ExerelinData.getInstance().onlyVanillaFactions = false;
		}
		else if (response == ONE_VANILLA_FACTION)
		{
			ExerelinData.getInstance().numStartFactions = 1;
			ExerelinData.getInstance().onlyVanillaFactions = true;
		}
		else if (response == ALL_VANILLA_FACTION)
		{
			ExerelinData.getInstance().numStartFactions = 3;
			ExerelinData.getInstance().onlyVanillaFactions = true;
		}
		else if (response == RESPAWN_YES)
		{
			ExerelinData.getInstance().respawnFactions = true;
			ExerelinData.getInstance().onlyRespawnStartingFactions = false;
			ExerelinData.getInstance().maxFactionsInExerelinAtOnce = 999;
		}
		else if (response == RESPAWN_YES_COND)
		{
			ExerelinData.getInstance().respawnFactions = true;
			ExerelinData.getInstance().onlyRespawnStartingFactions = true;
			ExerelinData.getInstance().maxFactionsInExerelinAtOnce = 999;
		}
		else if (response == RESPAWN_YES_COND_MAX)
		{
			ExerelinData.getInstance().respawnFactions = true;
			ExerelinData.getInstance().onlyRespawnStartingFactions = false;
			ExerelinData.getInstance().maxFactionsInExerelinAtOnce = ExerelinData.getInstance().numStartFactions + 1;
		}
		else if (response == RESPAWN_NO)
		{
			ExerelinData.getInstance().respawnFactions = false;
			ExerelinData.getInstance().onlyRespawnStartingFactions = false;
			ExerelinData.getInstance().maxFactionsInExerelinAtOnce = 999;
			stage++;

			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == RESPAWN_ZERO)
		{
			ExerelinData.getInstance().respawnDelay = 0;

			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == RESPAWN_TWO)
		{
			ExerelinData.getInstance().respawnDelay = 60;

			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == RESPAWN_FOUR)
		{
			ExerelinData.getInstance().respawnDelay = 120;

			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == RESPAWN_EIGHT)
		{
			ExerelinData.getInstance().respawnDelay = 240;

			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == RESPAWN_SIXTEEN)
		{
			ExerelinData.getInstance().respawnDelay = 480;
			
			if(!isToreUpPlentyInstalled())
				stage++;
		}
		else if (response == START_SHIP_FACTION)
		{
			factionStartShip = true;
		}
		else if (response == START_SHIP_TOREUPPLENTY1)
		{
			factionStartShip = false;
			toreUpPlentyFrigate = true;
		}
		else if (response == START_SHIP_TOREUPPLENTY2)
		{
			factionStartShip = false;
			toreUpPlentyOther = true;
		}
		else if (response == FREE_GOODS)
		{
			data.getStartingCargo().getCredits().set(0f);
			ExerelinData.getInstance().playerOwnedStationFreeTransfer = true;
			ExerelinData.getInstance().confirmedFreeTransfer = true;
		}
		else if (response == PAY_FOR_GOODS)
		{
			data.getStartingCargo().getCredits().add(4000f);
			ExerelinData.getInstance().playerOwnedStationFreeTransfer = false;
			ExerelinData.getInstance().confirmedFreeTransfer = true;
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
			String[] possibleFactions = ExerelinData.getInstance().getPossibleFactions();
			for(int i = 0; i < possibleFactions.length; i = i + 1)
			{
				if(response.getText().equalsIgnoreCase(possibleFactions[i]))
				{
					ExerelinData.getInstance().setPlayerFaction(possibleFactions[i]);
					setStartingShipFromFactionSelection(possibleFactions[i], data);
					break;
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
			if(factionId.equalsIgnoreCase("hegemony"))
			{
				data.addStartingShipChoice("hound_Assault");
				data.addStartingShipChoice("lasher_CS");
			}
			else if (factionId.equalsIgnoreCase("pirates"))
			{
				data.addStartingShipChoice("hound_Assault");
                data.addStartingShipChoice("lasher_Standard");
			}
			else if (factionId.equalsIgnoreCase("tritachyon"))
			{
				data.addStartingShipChoice("wolf_CS");
				data.addStartingShipChoice("afflictor_Strike");
				data.addStartingShipChoice("omen_PD");
			}
			else if (factionId.equalsIgnoreCase("sindrian_diktat"))
			{
				data.addStartingShipChoice("brawler_Assault");
				data.addStartingShipChoice("vigilance_Standard");
			}
			else if (factionId.equalsIgnoreCase("shadowyards_hi"))
			{
				data.addStartingShipChoice("ms_enlil_Standard");
				data.addStartingShipChoice("ms_seski_Standard");
				data.addStartingShipChoice("ms_shamash_Standard");
				data.addStartingShipChoice("ms_inanna_Standard");
			}
			else if (factionId.equalsIgnoreCase("syndicateasp"))
				data.addStartingShipChoice("syndicate_asp_diamondback_Standard");
			else if (factionId.equalsIgnoreCase("junkpirate"))
			{
				data.addStartingShipChoice("junk_pirates_sickle_Standard");
				data.addStartingShipChoice("junk_pirates_stoatA_Standard");
			}
			else if (factionId.equalsIgnoreCase("nomads"))
			{
				data.addStartingShipChoice("nom_wurm_assault");
				data.addStartingShipChoice("nom_yellowjacket_sniper");
			}
			else if (factionId.equalsIgnoreCase("council_loyalists"))
			{
				data.addStartingShipChoice("mrd_slasher_Balanced");
				data.addStartingShipChoice("mrd_ambassador_standard");
				data.addStartingShipChoice("mrd_spearhead_assault");
				data.addStartingShipChoice("mrd_dragonfly_assault");
				//data.addStartingShipChoice("mrd_sparrow_Fast");
				data.addStartingShipChoice("mrd_defender_assault");
			}
			else if (factionId.equalsIgnoreCase("blackrock"))
			{
				data.addStartingShipChoice("scarab_attack");
				data.addStartingShipChoice("brdy_locust_patrol");
				data.addStartingShipChoice("brdy_mantis_strike");
			}
			else if (factionId.equalsIgnoreCase("antediluvian"))
			{
				data.addStartingShipChoice("donovan_Antediluvian");
				data.addStartingShipChoice("bulwark_Antediluvian");
				data.addStartingShipChoice("cape_Antediluvian");
				data.addStartingShipChoice("sentinel_Antediluvian");
			}
			else if (factionId.equalsIgnoreCase("valkyrian"))
			{
				data.addStartingShipChoice("yuusha_M");
				data.addStartingShipChoice("tesladora_M");
				data.addStartingShipChoice("longinus_M");
				data.addStartingShipChoice("jenova_ECM");
				data.addStartingShipChoice("inquisitor_M");
			}
			else if (factionId.equalsIgnoreCase("lotusconglomerate"))
			{
				data.addStartingShipChoice("jackal_Hunter");
				data.addStartingShipChoice("blizzard_Hunter");
				data.addStartingShipChoice("stingray_Hunter");
			}
			else if (factionId.equalsIgnoreCase("gedune"))
			{
				data.addStartingShipChoice("gedune_kyirus_variant");
				data.addStartingShipChoice("gedune_kitsune_variant");
				data.addStartingShipChoice("gedune_nanda_variant1");
			}
			else if (factionId.equalsIgnoreCase("neutrino"))
			{
				data.addStartingShipChoice("neutrino_relativity_standard");
				data.addStartingShipChoice("neutrino_singularity_balanced");

			}
			else if (factionId.equalsIgnoreCase("interstellarFederation"))
			{
				data.addStartingShipChoice("albatross_Attack");
				data.addStartingShipChoice("dakota_Standard");
				data.addStartingShipChoice("scythe_Frigate");
				data.addStartingShipChoice("echo_Standard");
				data.addStartingShipChoice("rickshaw_Standard");
			}
			else if (factionId.equalsIgnoreCase("relics"))
			{
				data.addStartingShipChoice("relics_egler_Standard");
				data.addStartingShipChoice("relics_pusher_Standard");
				data.addStartingShipChoice("relics_solver_Standard");
			}
			else if (factionId.equalsIgnoreCase("nihil"))
			{
				data.addStartingShipChoice("nihil_votex_predator");
				data.addStartingShipChoice("nihil_null_cultist");
			}
			else if (factionId.equalsIgnoreCase("thulelegacy"))
			{
				data.addStartingShipChoice("thule_vikingmki_OD");
				data.addStartingShipChoice("thule_vikingmkii_OD");
			}
			else if (factionId.equalsIgnoreCase("bushi"))
			{
				data.addStartingShipChoice("bushi_kaiken_Standard");
				data.addStartingShipChoice("bushi_yari_Standard");
			}
			else if (factionId.equalsIgnoreCase("hiigaran_descendants"))
			{
				data.addStartingShipChoice("hii_fiirkan_Standard");
				data.addStartingShipChoice("hii_jet_Standard");
				data.addStartingShipChoice("hii_kaan_Standard");
			}
            else if (factionId.equalsIgnoreCase("shadoworder"))
            {
                data.addStartingShipChoice("tadd_wight_Standard");
                data.addStartingShipChoice("tadd_venom_Standard");
                data.addStartingShipChoice("tadd_mirage_Standard");
                data.addStartingShipChoice("tadd_wraith_Standard");
            }
            else if (factionId.equalsIgnoreCase("scrappers"))
            {
                data.addStartingShipChoice("hadd_refuge_Balanced");
                data.addStartingShipChoice("hadd_vice_Balanced");
                data.addStartingShipChoice("hadd_bite_Balanced");
                data.addStartingShipChoice("hadd_manta_Balanced");
                data.addStartingShipChoice("hadd_array_Balanced");
                data.addStartingShipChoice("hadd_imp_Standard");
            }
            else if (factionId.equalsIgnoreCase("independantMiners"))
            {
                data.addStartingShipChoice("mole_miner");
            }
            else if (factionId.equalsIgnoreCase("isora"))
            {
                data.addStartingShipChoice("vigilance_FS");
                data.addStartingShipChoice("brawler_Assault");
                data.addStartingShipChoice("anvil_Standard");
            }
            else if (factionId.equalsIgnoreCase("directorate"))
            {
                data.addStartingShipChoice("proton_Standard");
                data.addStartingShipChoice("amanita_Assault");
            }
            else if (factionId.equalsIgnoreCase("ceredia"))
            {
                data.addStartingShipChoice("javelin_Artil");
                data.addStartingShipChoice("marten_PD");
            }
            else if (factionId.equalsIgnoreCase("zorg_hive"))
            {
                data.addStartingShipChoice("zorg_probe_Configurated");
            }
            else if (factionId.equalsIgnoreCase("qualljom_society"))
            {
                data.addStartingShipChoice("qua_minal_standard");
                data.addStartingShipChoice("qua_taom_standard");
            }
            else if (factionId.equalsIgnoreCase("regime"))
            {
                data.addStartingShipChoice("khs_camel_Combat");
                data.addStartingShipChoice("khs_hyena_Picket");
            }
            else if (factionId.equalsIgnoreCase("insurgency"))
            {
                data.addStartingShipChoice("khs_hyena_acehigh");
                data.addStartingShipChoice("khs_buzzard_pd");
            }
            else if (factionId.equalsIgnoreCase("citadeldefenders"))
            {
                data.addStartingShipChoice("FoxFrigate_elitevariant");
                data.addStartingShipChoice("FoxCorvette_elitevariant");
            }
			else
			{
				System.out.println("EXERELIN ERROR: Faction starting ship for " + factionId + " not defined");
				data.addStartingShipChoice("shuttle_Attack");
			}
		}
		else
		{
			if(toreUpPlentyFrigate)
			{
				data.addStartingShipChoice("foxhound_Basic");
				data.addStartingShipChoice("sentinel_Basic");
				data.addStartingShipChoice("striker_Basic");
				data.addStartingShipChoice("timberwolf_Basic");
				data.addStartingShipChoice("wrestler_Basic");
				data.addStartingShipChoice("moth_Basic");
				data.addStartingShipChoice("ryker_Basic");
			}
			else if(toreUpPlentyOther)
			{
				data.addStartingShipChoice("talus_Basic");
				data.addStartingShipChoice("annihilator_Basic");
				data.addStartingShipChoice("lance_Basic");
				data.addStartingShipChoice("mace_Basic");
				data.addStartingShipChoice("damocles_Basic");
				data.addStartingShipChoice("centaur_Basic");
                data.addStartingShipChoice("stampede_Basic");
                data.addStartingShipChoice("hedgehog_Basic");
                data.addStartingShipChoice("cormorant_Basic");
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
		data.getStartingCargo().addSupplies(20);
		data.getStartingCargo().addCrew(CrewXPLevel.REGULAR, 25);
		data.getStartingCargo().addMarines(3);
        ExerelinData.getInstance().setPlayerStartingShipVariant(variantId);
	}

	public boolean isToreUpPlentyInstalled()
	{
		System.out.println("EXERELIN: Getting if tore up plenty mod installed");

		try
		{
			Global.getSettings().getScriptClassLoader().loadClass("data.missions.mace.MissionDefinition");
			System.out.println("tore up plenty installed");
			System.out.println("- - - - - - - - - -");
			return true;
		}
		catch (ClassNotFoundException ex)
		{
			System.out.println("tore up plenty not installed");
			System.out.println("- - - - - - - - - -");
			return false;
		}
	}
}







