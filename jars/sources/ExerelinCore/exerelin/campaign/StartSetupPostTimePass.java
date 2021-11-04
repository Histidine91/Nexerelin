package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.People;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsBaseOfficial;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.world.templars.TEM_Antioch;
import exerelin.campaign.intel.Nex_GalatianAcademyStipend;
import exerelin.campaign.ui.OwnFactionSetupScript;
import exerelin.world.ExerelinCorvusLocations;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexFactionConfig.SpecialItemSet;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import exerelin.world.VanillaSystemsGenerator;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.lwjgl.util.vector.Vector2f;

public class StartSetupPostTimePass {
	
	public static void execute()
	{
		Global.getLogger(StartSetupPostTimePass.class).info("Running start setup post time pass");
		SectorAPI sector = Global.getSector();
		if (Global.getSector().isInNewGameAdvance()) return;
		
		boolean corvusMode = SectorManager.getManager().isCorvusMode();
		
		if (corvusMode && !TutorialMissionIntel.isTutorialInProgress())
			VanillaSystemsGenerator.exerelinEndGalatiaPortionOfMission();
		
		String factionId = PlayerFactionStore.getPlayerFactionIdNGC();
		final String selectedFactionId = factionId;	// can't be changed by config
		String factionIdForSpawnLoc = factionId;
		
		FactionAPI myFaction = sector.getFaction(factionId);
		CampaignFleetAPI playerFleet = sector.getPlayerFleet();
		
		// assign officers
		int numOfficers = ExerelinSetupData.getInstance().numStartingOfficers;
		for (int i=0; i<numOfficers; i++)
		{
			int level = numOfficers - i;
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
		
		// rename ships
		for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy())
		{
			member.setShipName(myFaction.pickRandomShipName());
		}
		
		boolean freeStart = SectorManager.getManager().isFreeStart();
		
		// spawn as a different faction if config says we should
		// for Blade Breakers etc.
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		String spawnAsFactionId = null;
		if (sector.getMemoryWithoutUpdate().contains("$nex_spawnAsFaction"))
		{
			spawnAsFactionId = sector.getMemoryWithoutUpdate().getString("$nex_spawnAsFaction");
		}
		else if (conf.spawnAsFactionId != null && !conf.spawnAsFactionId.isEmpty())
		{
			spawnAsFactionId = conf.spawnAsFactionId;
		}
		
		if (spawnAsFactionId != null) {
			factionId = spawnAsFactionId;
			if (freeStart) {
				// Blade Breaker start: use BB start relations
				// Kassadar start: use ?? (should use Kassadar start relations?)
				//Global.getLogger(StartSetupPostTimePass.class).info("Spawn as faction type 1");
				NexUtilsReputation.syncFactionRelationshipsToPlayer();
			}
			else {	
				// Lion's Guard start: use Diktat start relations
				//Global.getLogger(StartSetupPostTimePass.class).info("Spawn as faction type 2");
				NexUtilsReputation.syncPlayerRelationshipsToFaction(factionId);
				factionIdForSpawnLoc = factionId;
			}
		}
		
		handleStartLocation(sector, playerFleet, factionIdForSpawnLoc);
		
		// Galatian stipend
		if (!TutorialMissionIntel.isTutorialInProgress() && !SectorManager.getManager().isHardMode() && !Misc.isSpacerStart()) 
		{
			new Nex_GalatianAcademyStipend();
		}
		
		// Starting blueprints
		if (selectedFactionId.equals(Factions.PLAYER)) {
			if (!freeStart) {
				// clear player debt from colony being unprofitable during new game time pass (if any)
				MonthlyReport report = SharedData.getData().getPreviousReport();
				// TODO: maybe do the thing SCC said and give player money instead?
				if (report != null) {
					report.setDebt(0);
					report.setPreviousDebt(0);
				}
				Global.getSector().addScript(new OwnFactionSetupScript());
			}
		}
		else {
			for (SpecialItemSet set : conf.startSpecialItems) {
				set.pickItemsAndAddToCargo(Global.getSector().getPlayerFleet().getCargo(), 
						StarSystemGenerator.random);
			}
		}
		
		// gate handling and other stuff
		if (!corvusMode || ExerelinSetupData.getInstance().skipStory) {
			Global.getSector().getPlayerFleet().getCargo().addSpecial(new SpecialItemData(Items.JANUS, null), 1);
			MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
			mem.set(GateEntityPlugin.CAN_SCAN_GATES, true);
			mem.set(GateEntityPlugin.GATES_ACTIVE, true);
			Global.getSector().getCharacterData().addAbility(Abilities.TRANSVERSE_JUMP);
			Global.getSector().getCharacterData().addAbility(Abilities.GRAVITIC_SCAN);
			
			mem.set("$interactedWithGABarEvent", true);
			
			if (corvusMode) {
				mem.set("$gaATG_missionCompleted", true);
				mem.set("$gaFC_missionCompleted", true);
				mem.set("$gaKA_missionCompleted", true);
				mem.set("$gaPZ_missionCompleted", true);
				
				addStoryContact(People.ARROYO);
				//addStoryContact(People.HEGEMONY_GA_OFFICER);
				addStoryContact(People.HORUS_YARIBAY);
				addStoryContact(People.IBRAHIM);
				handleAcademyVars();
			}
		}
	}
	
	public static void handleAcademyVars() {
		SectorEntityToken academy = Global.getSector().getEntityById("station_galatia_academy");
		academy.getMemoryWithoutUpdate().set("$metProvost", true);
		
		MarketAPI market = academy.getMarket();
		
		PersonAPI seb = Global.getSector().getImportantPeople().getPerson(People.SEBESTYEN);
		PersonAPI cour = Global.getSector().getImportantPeople().getPerson(People.COUREUSE);
		PersonAPI baird = Global.getSector().getImportantPeople().getPerson(People.BAIRD);
		PersonAPI garg = Global.getSector().getImportantPeople().getPerson(People.GARGOYLE);
		
		seb.getRelToPlayer().setLevel(RepLevel.COOPERATIVE);
		seb.getMemoryWithoutUpdate().set("$gotGAATGpay", true);
		seb.getMemoryWithoutUpdate().set("$askedProvostUpset", true);
		seb.getMemoryWithoutUpdate().set("$metAlready", true);		
		
		market.getCommDirectory().getEntryForPerson(baird).setHidden(false);
		market.getCommDirectory().getEntryForPerson(seb).setHidden(false);
		
		cour.getMarket().getCommDirectory().removePerson(cour);
		cour.getMarket().removePerson(cour);
		market.addPerson(cour);
		market.getCommDirectory().addPerson(cour);
		cour.getMemoryWithoutUpdate().set("$askedAboutBaird", true);
		cour.getMemoryWithoutUpdate().set("$askedAboutGargoyle", true);
		
		garg.getMarket().getCommDirectory().removePerson(garg);
		garg.getMarket().removePerson(garg);
		market.addPerson(garg);
		market.getCommDirectory().addPerson(garg);
		garg.getMemoryWithoutUpdate().set("$askedProvostThrown", true);
	}
	
	public static void addStoryContact(String id) {
		PersonAPI person = Global.getSector().getImportantPeople().getPerson(id);
		ContactIntel.addPotentialContact(1, person, person.getMarket(), null);
		person.getMarket().getCommDirectory().getEntryForPerson(person).setHidden(false);
	}
	
	public static SectorEntityToken pickRandomStartLocation(String factionId, boolean ownFactionOnly) {
		SectorEntityToken entity = null;
		WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>();
		WeightedRandomPicker<SectorEntityToken> pickerBackup = new WeightedRandomPicker<>();
		picker.setRandom(new Random(NexUtils.getStartingSeed()));
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			if (market.getFaction().isAtBest(Factions.PLAYER, RepLevel.INHOSPITABLE))
				continue;
			if (market.getContainingLocation() == null)
				continue;
			if (market.getContainingLocation().hasTag("do_not_respawn_player_in"))
				continue;
			if (market.isHidden()) continue;
			float weight = market.getSize();
			
			if (market.getFactionId().equals(factionId))
				picker.add(market.getPrimaryEntity(), weight);
			if (!ownFactionOnly)
				pickerBackup.add(market.getPrimaryEntity(), weight);
		}
		entity = picker.pick();
		if (entity == null) return pickerBackup.pick();
		
		return entity;
	}
	
	public static void handleStartLocation(SectorAPI sector, CampaignFleetAPI playerFleet, String factionId) 
	{
		if (TutorialMissionIntel.isTutorialInProgress()) {
			playerFleet.getContainingLocation().removeEntity(playerFleet);
			StarSystemAPI system = Global.getSector().getStarSystem(StringHelper.getString("exerelin_misc", "systemGalatia"));
			system.addEntity(playerFleet);
			Global.getSector().setCurrentLocation(system);
			Vector2f loc = new Vector2f(1000, -15000);
			playerFleet.setLocation(loc.x, loc.y);
			return;
		}
		
		SectorEntityToken entity = null;
		
		MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
		if (mem.contains("$nex_startLocation")) {
			if (mem.get("$nex_startLocation") != null) {
				entity = Global.getSector().getEntityById(mem.getString("$nex_startLocation"));
			}
		}
		else if (ExerelinSetupData.getInstance().randomStartLocation) {
			entity = pickRandomStartLocation(factionId, false);
		}
		else if (SectorManager.getManager().isCorvusMode())
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
		}
		else {
			if (ExerelinSetupData.getInstance().randomAntiochEnabled && factionId.equals("templars"))
				entity = TEM_Antioch.getAscalon();
			else if (!SectorManager.getManager().isFreeStart())
				entity = SectorManager.getHomeworld();
		}
		
		// couldn't find an more suitable start location, pick any market that's not unfriendly to us
		if (entity == null) {
			entity = pickRandomStartLocation(factionId, false);
		}
		
		if (entity != null)
		{
			sendPlayerFleetToLocation(playerFleet, entity);
		}
		else {	// hyperspace, turn off transponder in case we're hostile to anyone
			playerFleet.getAbilities().get(Abilities.TRANSPONDER).deactivate();
		}
		
		// insurance popup
		//SectorManager.getManager().getInsurance().sendUpdateIfPlayerHasIntel(null, false);
	}
	
	/**
	 * Sends the player fleet to the intended starting star system, orbiting the home market.
	 * Also grants the starting commission and unlocks storage.
	 * @param playerFleet
	 * @param entity Home planet/station
	 */
	public static void sendPlayerFleetToLocation(CampaignFleetAPI playerFleet, SectorEntityToken entity) 
	{
		playerFleet.getContainingLocation().removeEntity(playerFleet);
		entity.getContainingLocation().addEntity(playerFleet);
		Global.getSector().setCurrentLocation(entity.getContainingLocation());
		Vector2f loc = entity.getLocation();
		playerFleet.setLocation(loc.x, loc.y);
		
		if (!SectorManager.getManager().isFreeStart())
		{
			// unlock storage
			MarketAPI homeMarket = entity.getMarket();
			if (homeMarket != null)
			{
				SubmarketAPI storage = homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE);
				if (storage != null)
				{
					StoragePlugin plugin = (StoragePlugin)homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
					if (plugin != null)
						plugin.setPlayerPaidToUnlock(true);
				}
				if (homeMarket.isPlayerOwned())
					homeMarket.setAdmin(Global.getSector().getPlayerPerson());
				generateContactAtStartingLocation(homeMarket);
			}
		}
	}
	
	public static void generateContactAtStartingLocation(MarketAPI market) {
		if (market.getMemoryWithoutUpdate().getBoolean(ContactIntel.NO_CONTACTS_ON_MARKET)) return;
		if (market.getFaction().getCustomBoolean(Factions.CUSTOM_NO_CONTACTS)) return;
		if (market.getFaction().isPlayerFaction()) return;
		if (NexConfig.getFactionConfig(market.getFactionId()).noStartingContact) return;
		
		List<CommDirectoryEntryAPI> directory = market.getCommDirectory().getEntriesCopy();
		Collections.shuffle(directory, StarSystemGenerator.random);
		
		for (CommDirectoryEntryAPI dir : directory)
		{
			if (dir.getType() != CommDirectoryEntryAPI.EntryType.PERSON) continue;
			PersonAPI contact = (PersonAPI)dir.getEntryData();
			if (ContactIntel.playerHasContact(contact)) continue;
			
			String postId = contact.getPostId();
			if (postId == null) continue;
			if (!Nex_IsBaseOfficial.isOfficial(postId, "any")) continue;
			
			contact.setVoice(contact.getFaction().pickVoice(contact.getImportance(), StarSystemGenerator.random));
			addTagsToStartingContact(contact, postId);
			ContactIntel intel = new ContactIntel(contact, market);
			Global.getSector().getIntelManager().addIntel(intel, false, null);
			intel.develop(null);
			break;
		}
	}
	
	public static void addTagsToStartingContact(PersonAPI contact, String postId) 
	{
		boolean pirate = Misc.isPirateFaction(contact.getFaction()) || NexUtilsFaction.isPirateFaction(contact.getFaction().getId());
		
		boolean mil = postId.equals(Ranks.POST_BASE_COMMANDER) || postId.equals(Ranks.POST_STATION_COMMANDER);
		boolean trade = postId.equals(Ranks.POST_PORTMASTER) || postId.equals(Ranks.POST_SUPPLY_MANAGER) || postId.equals(Ranks.POST_SUPPLY_OFFICER);
		
		if (postId.equals(Ranks.POST_ADMINISTRATOR)) {
			mil = true;
			trade = true;
		}
		
		if (!mil && !trade) {
			if (Math.random() < 0.5f) mil = true;
			else trade = true;
		}
		
		if (mil) {
			contact.addTag(pirate ? Tags.CONTACT_UNDERWORLD : Tags.CONTACT_MILITARY);
		}
		if (trade) {
			contact.addTag(Tags.CONTACT_TRADE);
		}
		
	}
}
