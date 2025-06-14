package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.JumpPointInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.*;
import exerelin.ExerelinConstants;
import exerelin.campaign.SectorManager;
import exerelin.campaign.skills.NexSkills;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;

import java.util.ArrayList;
import java.util.List;

public class VanillaSystemsGenerator {
		
	public static void generateSystemsJanino() {
		List<String> inputList = new ArrayList<>();
		String[] sysNames = {"Galatia", "Askonia", "Eos", "Valhalla", "Arcadia", 
			"Magec", "Aztlan", "Samarra", "Penelope", "Yma", "Hybrasil", "Duzahk", 
			"TiaTaxet", "Canaan", "AlGebbar", "Isirah", "KumariKandam", "Naraka", 
			"Thule", "Mayasura", "Zagan", "Westernesse", "Tyle"};
		
		inputList.add("import com.fs.starfarer.api.campaign.SectorAPI;\r\n");
		inputList.add("SectorAPI sector = com.fs.starfarer.api.Global.getSector();\r\n");
		inputList.add("new data.scripts.world.corvus.Corvus().generate(sector);\r\n");
		for (String systemName : sysNames) {
			String entry = "new data.scripts.world.systems." + systemName + "().generate(sector);\r\n";
			inputList.add(entry);
		}
		
		StringBuilder sb = new StringBuilder();
		for (String input : inputList) sb.append(input);
		//Global.getLogger(VanillaSystemsGenerator.class).info("Output: " + sb.toString());
		try
		{
			NexUtils.runCode(sb.toString(), null, null, null);
		}
		catch (Exception ex)
		{
			throw new RuntimeException("Failed to generate vanilla star systems", ex);
		}
	}
	
	public static void generate(SectorAPI sector)
	{		
		//ClassLoader cl = Global.getSettings().getScriptClassLoader();
		
		StarSystemAPI system = sector.createStarSystem(StringHelper.getString("exerelin_misc", "systemCorvus"));
		//system.getLocation().set(16000 - 8000, 9000 - 10000);
		system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
		
		//sector.setCurrentLocation(system);
		sector.setRespawnLocation(system);
		sector.getRespawnCoordinates().set(-2500, -3500);
		
		initFactionRelationships(sector);
		
		generateSystemsJanino();
		
		//TutorialMissionEvent.endGalatiaPortionOfMission();
		//exerelinEndGalatiaPortionOfMission();	// moved handling elsewhere

		LocationAPI hyper = Global.getSector().getHyperspace();
		SectorEntityToken atlanticLabel = hyper.addCustomEntity("atlantic_label_id", null, "atlantic_label", null);
		SectorEntityToken perseanLabel = hyper.addCustomEntity("persean_label_id", null, "persean_label", null);
		SectorEntityToken luddicLabel = hyper.addCustomEntity("luddic_label_id", null, "luddic_label", null);
		SectorEntityToken zinLabel = hyper.addCustomEntity("zin_label_id", null, "zin_label", null);
		SectorEntityToken abyssLabel = hyper.addCustomEntity("opabyss_label_id", null, "opabyss_label", null);
		SectorEntityToken telmunLabel = hyper.addCustomEntity("telmun_label_id", null, "telmun_label", null);
		SectorEntityToken cathedralLabel = hyper.addCustomEntity("cathedral_label_id", null, "cathedral_label", null);
		SectorEntityToken coreLabel = hyper.addCustomEntity("core_label_id", null, "core_label", null);

		atlanticLabel.setFixedLocation(500, -2000);
		perseanLabel.setFixedLocation(-10000, 1000);
		luddicLabel.setFixedLocation(-14000, -9500);
		zinLabel.setFixedLocation(-22000, -17000);
		telmunLabel.setFixedLocation(-16000, 0);
		cathedralLabel.setFixedLocation(-12700, -12000);
		coreLabel.setFixedLocation(0, -6000);

		// try to make abyss label a constant relative distance from sector center
		float abyssLabelX = -65000f/164000 * Global.getSettings().getFloat("sectorWidth");
		float abyssLabelY = -47000f/104000 * Global.getSettings().getFloat("sectorHeight");
		abyssLabel.setFixedLocation(abyssLabelX, abyssLabelY);
	}
	
	public static void exerelinEndGalatiaPortionOfMission()
	{
		StarSystemAPI system = Global.getSector().getStarSystem("galatia");
		PlanetAPI ancyra = (PlanetAPI) system.getEntityById("ancyra");
		PlanetAPI pontus = (PlanetAPI) system.getEntityById("pontus");
		PlanetAPI tetra = (PlanetAPI) system.getEntityById("tetra");
		SectorEntityToken derinkuyu = system.getEntityById("derinkuyu_station");
		SectorEntityToken probe = system.getEntityById("galatia_probe");
		SectorEntityToken inner = system.getEntityById("galatia_jump_point_alpha");
		SectorEntityToken fringe = system.getEntityById("galatia_jump_point_fringe");
		SectorEntityToken relay = system.getEntityById("ancyra_relay");
		
		relay.getMemoryWithoutUpdate().unset(MemFlags.OBJECTIVE_NON_FUNCTIONAL);
		
		// removing this leaves "supplement from local resources" bonuses active
		//ancyra.getMarket().removeSubmarket(Submarkets.LOCAL_RESOURCES);
		
		Global.getSector().getEconomy().addMarket(ancyra.getMarket(), false);
		Global.getSector().getEconomy().addMarket(derinkuyu.getMarket(), false);
		
		inner.getMemoryWithoutUpdate().unset(JumpPointInteractionDialogPluginImpl.UNSTABLE_KEY);
		inner.getMemoryWithoutUpdate().unset(JumpPointInteractionDialogPluginImpl.CAN_STABILIZE);
		
		fringe.getMemoryWithoutUpdate().unset(JumpPointInteractionDialogPluginImpl.UNSTABLE_KEY);
		fringe.getMemoryWithoutUpdate().unset(JumpPointInteractionDialogPluginImpl.CAN_STABILIZE);
		
		system.removeTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER);
		
		MarketAPI market = ancyra.getMarket();
		market.getMemoryWithoutUpdate().unset(MemFlags.MARKET_DO_NOT_INIT_COMM_LISTINGS);
		market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).unmodifyMult("tut");
		market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).unmodifyMult("tut");
		market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).unmodifyMult("tut");
		market.setEconGroup(null);
		
		// [MODIFIED] Nex: give Ancyra its base staff
		NexUtilsMarket.addMarketPeople(market);
		
		market = derinkuyu.getMarket();
		market.setEconGroup(null);
		SectorManager.transferMarket(market, Global.getSector().getFaction(Factions.INDEPENDENT), market.getFaction(), 
				false, false, null, 0, true);
		
	}
	
	public static void initFactionRelationships(SectorAPI sector) 
	{
		boolean enhancedRelations = NexConfig.useEnhancedStartRelations;

		FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
		FactionAPI tritachyon = sector.getFaction(Factions.TRITACHYON);
		FactionAPI church = sector.getFaction(Factions.LUDDIC_CHURCH);
		FactionAPI path = sector.getFaction(Factions.LUDDIC_PATH);
		FactionAPI diktat = sector.getFaction(Factions.DIKTAT);
		FactionAPI persean = sector.getFaction(Factions.PERSEAN);

		String str = "import data.scripts.world.SectorGen;\r\nSectorGen.initFactionRelationships(sector);";
		String[] paramNames = {"sector"};
		try {
			NexUtils.runCode(str, paramNames, null, sector);
		} catch (Exception ex) {
			Global.getLogger(VanillaSystemsGenerator.class).error("Failed to run vanilla faction relationships", ex);
		}


		if (enhancedRelations) {
			path.setRelationship(church.getId(), RepLevel.NEUTRAL);
			persean.setRelationship(diktat.getId(), RepLevel.WELCOMING);
		}
		
		// emulate starting hostilities
		hegemony.setRelationship(Factions.PERSEAN, RepLevel.HOSTILE);
		tritachyon.setRelationship(Factions.LUDDIC_CHURCH, RepLevel.HOSTILE);
	}
	
	protected static MarketAPI getMarket(String marketId) {
		return Global.getSector().getEconomy().getMarket(marketId);
	}
		
	public static void enhanceVanillaMarkets() {
		// this part always runs regardless of setting
		MarketAPI derinkuyu = getMarket("derinkuyu_market");
		derinkuyu.getMemoryWithoutUpdate().set(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, Factions.INDEPENDENT);
		
		if (!NexConfig.useEnhancedCoreWorlds || Global.getSettings().getModManager().isModEnabled("archeus"))
			return;
		
		MarketAPI ancyra = getMarket("ancyra_market");
		ancyra.addCondition(Conditions.FARMLAND_POOR);
		ancyra.getCondition(Conditions.FARMLAND_POOR).setSurveyed(true);
		ancyra.addIndustry(Industries.FARMING);
		
		MarketAPI agreus = getMarket("agreus");
		if (!agreus.hasIndustry(Industries.GROUNDDEFENSES) && !agreus.hasIndustry(Industries.HEAVYBATTERIES))	// Ko Combine mod adds HB
			agreus.addIndustry(Industries.GROUNDDEFENSES);
		
		MarketAPI asher = getMarket("asher");
		asher.addIndustry(Industries.ORBITALSTATION_MID);
		asher.removeIndustry(Industries.GROUNDDEFENSES, null, false);
		asher.addIndustry(Industries.HEAVYBATTERIES);
		
		MarketAPI chalcedon = getMarket("chalcedon");
		//chalcedon.addIndustry(Industries.ORBITALSTATION);
		chalcedon.addIndustry(Industries.WAYSTATION);
		
		MarketAPI culann = getMarket("culann");
		culann.getIndustry(Industries.BATTLESTATION_HIGH).setAICoreId("alpha_core");
		culann.getIndustry(Industries.MILITARYBASE).setAICoreId("alpha_core");
		
		//MarketAPI derinkuyu = getMarket("derinkuyu_market");
		derinkuyu.addCondition(Conditions.ORE_SPARSE);
		derinkuyu.getCondition(Conditions.ORE_SPARSE).setSurveyed(true);
		derinkuyu.addCondition(Conditions.RARE_ORE_SPARSE);
		derinkuyu.getCondition(Conditions.RARE_ORE_SPARSE).setSurveyed(true);
		
		MarketAPI eldfell = getMarket("eldfell");
		eldfell.addIndustry(Industries.GROUNDDEFENSES);
		
		MarketAPI epiphany = getMarket("epiphany");
		epiphany.addIndustry(Industries.WAYSTATION);
		
		MarketAPI eochu_bres = getMarket("eochu_bres");
		eochu_bres.getIndustry(Industries.STARFORTRESS_HIGH).setAICoreId("alpha_core");
		
		MarketAPI eventide = getMarket("eventide");
		eventide.addIndustry(Industries.BATTLESTATION_MID);
		
		MarketAPI fikenhild = getMarket("fikenhild");
		//fikenhild.removeIndustry(Industries.PATROLHQ, null, false);
		//fikenhild.addIndustry(Industries.MILITARYBASE);
		
		MarketAPI mazalot = getMarket("mazalot");
		//mazalot.getMemoryWithoutUpdate().set(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, Factions.LUDDIC_CHURCH);
		
		MarketAPI sindria = getMarket("sindria");
		sindria.getIndustry(Industries.HIGHCOMMAND).setAICoreId(Commodities.ALPHA_CORE);
		
		MarketAPI volturn = getMarket("volturn");
		volturn.addIndustry(Industries.PATROLHQ);
		Industry gd = volturn.getIndustry(Industries.GROUNDDEFENSES);
		if (gd != null) {
			gd.setSpecialItem(new SpecialItemData(Items.DRONE_REPLICATOR, null));
		}
		
		MarketAPI tigraCity = getMarket("tigra_city");
		tigraCity.addCondition(Conditions.ORE_SPARSE);
		tigraCity.getCondition(Conditions.ORE_SPARSE).setSurveyed(true);
		tigraCity.addCondition(Conditions.FARMLAND_POOR);
		tigraCity.getCondition(Conditions.FARMLAND_POOR).setSurveyed(true);
		tigraCity.addIndustry(Industries.MINING);
		tigraCity.addIndustry(Industries.FARMING);
	}
	
	public static void enhanceVanillaAdmins() {
		if (!NexConfig.useEnhancedAdmins) return;
		
		addSkillToPerson(People.ANDRADA, NexSkills.TACTICAL_DRILLS_EX);
		addSkillToPerson(People.SUN, NexSkills.BULK_TRANSPORT_EX);
		addSkillToPerson(People.SUN, Skills.ELECTRONIC_WARFARE);
		addSkillToPerson(People.DAUD, NexSkills.AUXILIARY_SUPPORT_EX);
	}
	
	public static void addSkillToPerson(String personId, String skillId) {
		PersonAPI person = Global.getSector().getImportantPeople().getPerson(personId);
		if (person == null) return;
		person.getStats().setSkillLevel(skillId, 1);
	}
}
