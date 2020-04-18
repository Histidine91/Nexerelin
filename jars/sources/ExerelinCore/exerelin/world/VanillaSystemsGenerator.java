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
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import data.scripts.world.corvus.Corvus;
import data.scripts.world.systems.AlGebbar;
import data.scripts.world.systems.Arcadia;
import data.scripts.world.systems.Askonia;
import data.scripts.world.systems.Aztlan;
import data.scripts.world.systems.Canaan;
import data.scripts.world.systems.Duzahk;
import data.scripts.world.systems.Eos;
import data.scripts.world.systems.Galatia;
import data.scripts.world.systems.Hybrasil;
import data.scripts.world.systems.Isirah;
import data.scripts.world.systems.KumariKandam;
import data.scripts.world.systems.Magec;
import data.scripts.world.systems.Mayasura;
import data.scripts.world.systems.Naraka;
import data.scripts.world.systems.Penelope;
import data.scripts.world.systems.Samarra;
import data.scripts.world.systems.Thule;
import data.scripts.world.systems.TiaTaxet;
import data.scripts.world.systems.Tyle;
import data.scripts.world.systems.Valhalla;
import data.scripts.world.systems.Westernesse;
import data.scripts.world.systems.Yma;
import data.scripts.world.systems.Zagan;
import exerelin.ExerelinConstants;
import exerelin.utilities.ExerelinConfig;

public class VanillaSystemsGenerator {
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
		
		new Galatia().generate(sector);
		new Askonia().generate(sector);
		new Eos().generate(sector);
		new Valhalla().generate(sector);
		new Arcadia().generate(sector);
		new Magec().generate(sector);
		new Corvus().generate(sector);
		new Aztlan().generate(sector);
		new Samarra().generate(sector);
		new Penelope().generate(sector);
		new Yma().generate(sector);
		new Hybrasil().generate(sector);
		new Duzahk().generate(sector);
		new TiaTaxet().generate(sector);
		new Canaan().generate(sector);
		new AlGebbar().generate(sector);
		new Isirah().generate(sector);
		new KumariKandam().generate(sector);
		new Naraka().generate(sector);
		new Thule().generate(sector);
		new Mayasura().generate(sector);
		new Zagan().generate(sector);
		new Westernesse().generate(sector);
		new Tyle().generate(sector);
		
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

		ancyra.getMarket().removeSubmarket(Submarkets.LOCAL_RESOURCES);

		Global.getSector().getEconomy().addMarket(ancyra.getMarket(), false);
		Global.getSector().getEconomy().addMarket(derinkuyu.getMarket(), false);
		
		inner.getMemoryWithoutUpdate().unset(JumpPointInteractionDialogPluginImpl.UNSTABLE_KEY);
		inner.getMemoryWithoutUpdate().unset(JumpPointInteractionDialogPluginImpl.CAN_STABILIZE);

		fringe.getMemoryWithoutUpdate().unset(JumpPointInteractionDialogPluginImpl.UNSTABLE_KEY);
		fringe.getMemoryWithoutUpdate().unset(JumpPointInteractionDialogPluginImpl.CAN_STABILIZE);

		system.removeTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER);

		MarketAPI market = ancyra.getMarket();
		market.getMemoryWithoutUpdate().unset(MemFlags.MARKET_DO_NOT_INIT_COMM_LISTINGS);
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
		
		boolean enhancedRelations = ExerelinConfig.useEnhancedStartRelations;
		
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
		if (enhancedRelations)
			player.setRelationship(path.getId(), -0.65f);
		else
			player.setRelationship(path.getId(), RepLevel.HOSTILE);
		

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
	
	/*
		--agreus: ground def
		--ancyra_market: farmland, farming
		--tigra_city: ore, mining
		--eventide: HT battlestation
		--asher: mid orbital station, heavy batteries (replace ground def)
		[nah] chalcedon: LT orbital station
		--eldfell: ground def
		[nah] athulf: ground def
		[dunno] fikenhild: mid battlestation (replace mid orbital station) OR military base
			maybe put the base on athulf instead
				no, too much upkeep (see planet lore about it being a personal posession of the king that generates revenue)
		-- volturn OR cruor: patrol HQ
	*/
	
	public static void enhanceVanillaMarkets() {
		if (!ExerelinConfig.useEnhancedCoreWorlds || Global.getSettings().getModManager().isModEnabled("archeus"))
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
