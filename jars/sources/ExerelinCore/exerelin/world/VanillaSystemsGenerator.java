package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.JumpPointInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import exerelin.utilities.NexConfig;
import java.util.ArrayList;
import java.util.List;
import org.codehaus.janino.ScriptEvaluator;

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
		
		ScriptEvaluator eval = new ScriptEvaluator();
		eval.setReturnType(void.class);
		eval.setParentClassLoader(Global.getSettings().getScriptClassLoader());
		
		StringBuilder sb = new StringBuilder();
		for (String input : inputList) sb.append(input);
		//Global.getLogger(VanillaSystemsGenerator.class).info("Output: " + sb.toString());
		try
		{
			eval.cook(sb.toString());
			eval.evaluate(null);
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	public static void generate(SectorAPI sector)
	{		
		//ClassLoader cl = Global.getSettings().getScriptClassLoader();
		
		StarSystemAPI system = sector.createStarSystem("Corvus");
		//system.getLocation().set(16000 - 8000, 9000 - 10000);
		system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
		
		//sector.setCurrentLocation(system);
		sector.setRespawnLocation(system);
		sector.getRespawnCoordinates().set(-2500, -3500);
		
		initFactionRelationships(sector);
		
		generateSystemsJanino();
		
		//TutorialMissionEvent.endGalatiaPortionOfMission();
		exerelinEndGalatiaPortionOfMission();
		
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
		
		abyssLabel.setFixedLocation(-65000, -47000);
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
		
		FactionAPI hegemony = Global.getSector().getFaction(Factions.HEGEMONY);
		if (hegemony.getRelToPlayer().getRel() < 0) {
			hegemony.getRelToPlayer().setRel(0);
		}
		
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
		
		market = derinkuyu.getMarket();
		market.setEconGroup(null);
		derinkuyu.setFaction(Factions.INDEPENDENT);
		market.setFactionId(Factions.INDEPENDENT);
			
		FactionAPI ind = Global.getSector().getFaction(Factions.INDEPENDENT);
		market.getSubmarket(Submarkets.SUBMARKET_OPEN).setFaction(ind);
	}
	
	public static void initFactionRelationships(SectorAPI sector) 
	{	
		// forget why this is necessary - workaround for some JANINO issue, I think
		//Class c = HeavyArmor.class;
		
		boolean enhancedRelations = NexConfig.useEnhancedStartRelations;
		
		FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
		FactionAPI tritachyon = sector.getFaction(Factions.TRITACHYON);
		FactionAPI pirates = sector.getFaction(Factions.PIRATES);
		FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);
		FactionAPI kol = sector.getFaction(Factions.KOL);
		FactionAPI church = sector.getFaction(Factions.LUDDIC_CHURCH);
		FactionAPI path = sector.getFaction(Factions.LUDDIC_PATH);
		FactionAPI player = sector.getFaction(Factions.PLAYER);
		FactionAPI diktat = sector.getFaction(Factions.DIKTAT);
		FactionAPI persean = sector.getFaction(Factions.PERSEAN);
		FactionAPI remnant = sector.getFaction(Factions.REMNANTS);
		FactionAPI derelict = sector.getFaction(Factions.DERELICT);
		
		player.setRelationship(hegemony.getId(), 0);
		player.setRelationship(tritachyon.getId(), 0);
		player.setRelationship(persean.getId(), 0);
		//player.setRelationship(pirates.getId(), RepLevel.HOSTILE);
		player.setRelationship(pirates.getId(), -0.65f);
		
		player.setRelationship(independent.getId(), 0);
		player.setRelationship(kol.getId(), 0);
		player.setRelationship(church.getId(), 0);
		//player.setRelationship(path.getId(), RepLevel.HOSTILE);
		player.setRelationship(path.getId(), -0.65f);
		

		// replaced by hostilities set in CoreLifecyclePluginImpl
		//hegemony.setRelationship(tritachyon.getId(), RepLevel.HOSTILE);
		//hegemony.setRelationship(persean.getId(), RepLevel.HOSTILE);
		
		hegemony.setRelationship(pirates.getId(), RepLevel.HOSTILE);
		
		tritachyon.setRelationship(pirates.getId(), RepLevel.HOSTILE);
		//tritachyon.setRelationship(independent.getId(), -1);
		tritachyon.setRelationship(kol.getId(), RepLevel.HOSTILE);
		//tritachyon.setRelationship(church.getId(), RepLevel.HOSTILE);
		tritachyon.setRelationship(path.getId(), RepLevel.HOSTILE);
		tritachyon.setRelationship(persean.getId(), RepLevel.SUSPICIOUS);
		
		pirates.setRelationship(kol.getId(), RepLevel.HOSTILE);
		pirates.setRelationship(church.getId(), RepLevel.HOSTILE);
		pirates.setRelationship(path.getId(), 0);
		pirates.setRelationship(independent.getId(), RepLevel.HOSTILE);
		pirates.setRelationship(diktat.getId(), RepLevel.HOSTILE);
		pirates.setRelationship(persean.getId(), RepLevel.HOSTILE);
		
		church.setRelationship(kol.getId(), RepLevel.COOPERATIVE);
		path.setRelationship(kol.getId(), RepLevel.FAVORABLE);
		
		path.setRelationship(independent.getId(), RepLevel.HOSTILE);
		path.setRelationship(hegemony.getId(), RepLevel.HOSTILE);
		path.setRelationship(diktat.getId(), RepLevel.HOSTILE);
		path.setRelationship(persean.getId(), RepLevel.HOSTILE);
		if (!enhancedRelations)
			path.setRelationship(church.getId(), RepLevel.COOPERATIVE);
		
		persean.setRelationship(tritachyon.getId(), RepLevel.SUSPICIOUS);
		persean.setRelationship(pirates.getId(), RepLevel.HOSTILE);
		persean.setRelationship(path.getId(), RepLevel.HOSTILE);
		if (enhancedRelations)
			persean.setRelationship(diktat.getId(), RepLevel.WELCOMING);
		else
			persean.setRelationship(diktat.getId(), RepLevel.COOPERATIVE);
		
		player.setRelationship(remnant.getId(), RepLevel.HOSTILE);
		independent.setRelationship(remnant.getId(), RepLevel.HOSTILE);
		pirates.setRelationship(remnant.getId(), RepLevel.HOSTILE);
		hegemony.setRelationship(remnant.getId(), RepLevel.HOSTILE);
		kol.setRelationship(remnant.getId(), RepLevel.HOSTILE);
		church.setRelationship(remnant.getId(), RepLevel.HOSTILE);
		path.setRelationship(remnant.getId(), RepLevel.HOSTILE);
		diktat.setRelationship(remnant.getId(), RepLevel.HOSTILE);
		persean.setRelationship(remnant.getId(), RepLevel.HOSTILE);
		
//		independent.setRelationship(hegemony.getId(), 0);
//		independent.setRelationship(tritachyon.getId(), 0);
//		independent.setRelationship(pirates.getId(), 0);
//		independent.setRelationship(independent.getId(), 0);
//		independent.setRelationship(player.getId(), 0);
	}
	
	protected static MarketAPI getMarket(String marketId) {
		return Global.getSector().getEconomy().getMarket(marketId);
	}
		
	public static void enhanceVanillaMarkets() {
		if (!NexConfig.useEnhancedCoreWorlds || Global.getSettings().getModManager().isModEnabled("archeus"))
			return;
		
		MarketAPI ancyra = getMarket("ancyra_market");
		ancyra.addCondition(Conditions.FARMLAND_POOR);
		ancyra.getCondition(Conditions.FARMLAND_POOR).setSurveyed(true);
		ancyra.addIndustry(Industries.FARMING);
		
		MarketAPI agreus = getMarket("agreus");
		agreus.addIndustry(Industries.GROUNDDEFENSES);
		
		MarketAPI asher = getMarket("asher");
		asher.addIndustry(Industries.ORBITALSTATION_MID);
		asher.removeIndustry(Industries.GROUNDDEFENSES, null, false);
		asher.addIndustry(Industries.HEAVYBATTERIES);
		
		//MarketAPI chalcedon = getMarket("chalcedon");
		//chalcedon.addIndustry(Industries.ORBITALSTATION);
		
		MarketAPI culann = getMarket("culann");
		culann.getIndustry(Industries.BATTLESTATION_HIGH).setAICoreId("alpha_core");
		culann.getIndustry(Industries.MILITARYBASE).setAICoreId("alpha_core");
		
		MarketAPI derinkuyu = getMarket("derinkuyu_market");
		derinkuyu.addCondition(Conditions.ORE_SPARSE);
		derinkuyu.getCondition(Conditions.ORE_SPARSE).setSurveyed(true);
		derinkuyu.addCondition(Conditions.RARE_ORE_SPARSE);
		derinkuyu.getCondition(Conditions.RARE_ORE_SPARSE).setSurveyed(true);
		
		MarketAPI eldfell = getMarket("eldfell");
		eldfell.addIndustry(Industries.GROUNDDEFENSES);
		
		MarketAPI eochu_bres = getMarket("eochu_bres");
		eochu_bres.getIndustry(Industries.STARFORTRESS_HIGH).setAICoreId("alpha_core");
		
		MarketAPI eventide = getMarket("eventide");
		eventide.addIndustry(Industries.BATTLESTATION_HIGH);
		
		MarketAPI fikenhild = getMarket("fikenhild");
		//fikenhild.removeIndustry(Industries.PATROLHQ, null, false);
		//fikenhild.addIndustry(Industries.MILITARYBASE);
		
		MarketAPI mazalot = getMarket("mazalot");
		//mazalot.getMemoryWithoutUpdate().set(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, Factions.LUDDIC_CHURCH);
		
		MarketAPI volturn = getMarket("volturn");
		volturn.addIndustry(Industries.PATROLHQ);
		
		MarketAPI tigraCity = getMarket("tigra_city");
		tigraCity.addCondition(Conditions.ORE_MODERATE);
		tigraCity.getCondition(Conditions.ORE_MODERATE).setSurveyed(true);
		tigraCity.addIndustry(Industries.MINING);
	}
}
