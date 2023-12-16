package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsBaseOfficial;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.world.templars.TEM_Antioch;
import exerelin.ExerelinConstants;
import exerelin.campaign.customstart.Nex_SpacerObligation;
import exerelin.campaign.intel.Nex_GalatianAcademyStipend;
import exerelin.campaign.ui.OwnFactionSetupScript;
import exerelin.utilities.*;
import exerelin.utilities.NexFactionConfig.SpecialItemSet;
import exerelin.utilities.NexFactionConfig.StartFleetType;
import exerelin.world.ExerelinCorvusLocations;
import exerelin.world.VanillaSystemsGenerator;
import lombok.extern.log4j.Log4j;
import org.lwjgl.util.vector.Vector2f;

import java.util.Collections;
import java.util.List;
import java.util.Random;

@Log4j
public class StartSetupPostTimePass {
	
	public static void execute()
	{
		Global.getLogger(StartSetupPostTimePass.class).info("Running start setup post time pass");
		SectorAPI sector = Global.getSector();
		if (Global.getSector().isInNewGameAdvance()) return;
		
		boolean corvusMode = SectorManager.getManager().isCorvusMode();
		Global.getSector().getMemoryWithoutUpdate().set(ExerelinConstants.MEMORY_KEY_RANDOM_SECTOR, !corvusMode);
		
		if (corvusMode && !TutorialMissionIntel.isTutorialInProgress())
			VanillaSystemsGenerator.exerelinEndGalatiaPortionOfMission();
		
		String factionId = PlayerFactionStore.getPlayerFactionIdNGC();
		final String selectedFactionId = factionId;	// can't be changed by config
		String factionIdForSpawnLoc = factionId;
		
		FactionAPI myFaction = sector.getFaction(factionId);
		CampaignFleetAPI playerFleet = sector.getPlayerFleet();
		
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(
				ExerelinSetupData.MEM_KEY_START_FLEET_TYPE, ExerelinSetupData.getInstance().startFleetType);
		
		// assign officers
		int numOfficers = ExerelinSetupData.getInstance().numStartingOfficers;
		int maxLevel = Global.getSettings().getLevelupPlugin().getMaxLevel();
		for (int i=0; i<numOfficers; i++)
		{
			int level = 1;	//numOfficers - i;
			PersonAPI officer = OfficerManagerEvent.createOfficer(myFaction, level, false);
			Misc.setMentored(officer, true);
			officer.getStats().setBonusXp(Global.getSettings().getLevelupPlugin().getXPForLevel(maxLevel));
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
		if (!TutorialMissionIntel.isTutorialInProgress() && !SectorManager.getManager().isHardMode() && !Misc.isSpacerStart()
				&& ExerelinSetupData.getInstance().enableStipend)
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
					int debt = report.getPreviousDebt();
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
			MemoryAPI playMem = Global.getSector().getCharacterData().getMemoryWithoutUpdate();

			mem.set(GateEntityPlugin.CAN_SCAN_GATES, true);
			mem.set(GateEntityPlugin.GATES_ACTIVE, true);
			Global.getSector().getCharacterData().addAbility(Abilities.TRANSVERSE_JUMP);
			Global.getSector().getCharacterData().addAbility(Abilities.GRAVITIC_SCAN);
			
			mem.set("$interactedWithGABarEvent", true);
			playMem.set("$metBaird", true);
			
			if (corvusMode) {
				playMem.set("$metDaud", true);
				playMem.set("$gaveDaudYaribayContact", true);
				playMem.set("$metBrotherCotton", true);
				playMem.set("$satWithCottonCount", 1);	// assume met once during gaATG
				mem.set("$gaATG_missionCompleted", true);
				mem.set("$gaATG_missionGiven", true);
				mem.set("$gaFC_missionCompleted", true);
				mem.set("$gaKA_missionCompleted", true);
				mem.set("$gaPZ_missionCompleted", true);
				
				addStoryContact(People.ARROYO);
				//addStoryContact(People.HEGEMONY_GA_OFFICER);
				addStoryContact(People.HORUS_YARIBAY);
				addStoryContact(People.IBRAHIM);
				handleAcademyVars();
			} else {
				mem.set("$gaIntro2found", true);
				mem.set("$lpp_missionCompleted", true);
				mem.set("$lpp_didHookStart", true);
				mem.set("$didSDBarRaid", true);
				mem.set("$sdtu_ramDidProposal", true);
			}
			
			// alpha site location intel?
			FleetLogIntel intel = new FleetLogIntel() {
								
				@Override
				public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
					info.addImage(getIcon(), 0);
					info.addPara(StringHelper.getString("exerelin_misc", "alphaSiteIntelDesc"), 
							3, Misc.getHighlightColor(), getSite().getName());
					this.addDeleteButton(info, width);
				}
				
				protected SectorEntityToken getSite() {
					return Global.getSector().getEntityById("site_alpha");
				}
				
				@Override
				protected String getName() {
					return getSite().getName();
				}
				
				@Override
				public String getIcon() {
					return "graphics/icons/missions/project_ziggurat.png";
				}
				
				@Override
				public SectorEntityToken getMapLocation(SectorMapAPI map) {
					return getSite();
				}
			};
			Global.getSector().getIntelManager().addIntel(intel);
		}

		// fix post-Ziggurat encounter crash and some other issues
		if (!corvusMode) {
			createImportantPeopleInRandomSector();
		}
		
		// Spacer obligation
		String backgroundID = Global.getSector().getMemoryWithoutUpdate().getString("$nex_selected_background");
		if (ExerelinSetupData.getInstance().spacerObligation && (backgroundID == null || !backgroundID.equals("nex_unpaid_debt"))) {
			new Nex_SpacerObligation();
		}

		// timestamp
		CampaignClockAPI clock = Global.getSector().getClock();
		Global.getSector().getMemoryWithoutUpdate().set("$nex_startTimestamp", clock.getTimestamp());
		if (clock.getDay() == 1 && clock.getMonth() == 1) {
			Global.getSector().getMemoryWithoutUpdate().set("$nex_startOn1Jan", true);
		}
	}

	// runcode exerelin.campaign.StartSetupPostTimePass.createImportantPeopleInRandomSector()
	public static void createImportantPeopleInRandomSector() {
		ImportantPeopleAPI ip = Global.getSector().getImportantPeople();

		if (ip.getPerson(People.SIYAVONG) == null) {
			PersonAPI person = Global.getFactory().createPerson();
			person.setId(People.SIYAVONG);
			person.setFaction(Factions.PERSEAN);
			person.setGender(FullName.Gender.MALE);
			person.setRankId(Ranks.AGENT);
			person.setPostId(Ranks.POST_SPECIAL_AGENT);
			person.setImportance(PersonImportance.HIGH);
			person.getName().setFirst(StringHelper.getString("exerelin_misc", "siyavongName1"));
			person.getName().setLast(StringHelper.getString("exerelin_misc", "siyavongName2"));
			person.setPortraitSprite(Global.getSettings().getSpriteName("characters", person.getId()));
			ip.addPerson(person);
		}

		if (ip.getPerson(People.COTTON) == null) {
			PersonAPI person = Global.getFactory().createPerson();
			person.setId(People.COTTON);
			person.setFaction(Factions.LUDDIC_PATH);
			person.setGender(FullName.Gender.MALE);
			person.setRankId(Ranks.BROTHER);
			person.setPostId(Ranks.POST_TERRORIST);
			person.setImportance(PersonImportance.HIGH);
			person.getName().setFirst(StringHelper.getString("exerelin_misc", "cottonName1"));
			person.getName().setLast(StringHelper.getString("exerelin_misc", "cottonName2"));
			person.setPortraitSprite(Global.getSettings().getSpriteName("characters", person.getId()));
			ip.addPerson(person);
		}

		MarketAPI market = Global.getSector().getEconomy().getMarket("kantas_den");
		if (market != null && ip.getPerson(People.KANTA) == null) {
			PersonAPI person = Global.getFactory().createPerson();
			person.setId("kanta");
			person.setFaction(Factions.PIRATES);
			person.setGender(FullName.Gender.FEMALE);
			//person.setPostId(Ranks.POST_ADMINISTRATOR);
			person.setPostId(Ranks.POST_WARLORD);
			person.setRankId(Ranks.FACTION_LEADER);
			person.setImportance(PersonImportance.VERY_HIGH);
			person.getName().setFirst(StringHelper.getString("exerelin_misc", "kantaName1"));
			person.getName().setLast(StringHelper.getString("exerelin_misc", "kantaName2"));
			person.setPortraitSprite(Global.getSettings().getSpriteName("characters", "kanta"));
			person.getStats().setSkillLevel(Skills.WOLFPACK_TACTICS, 1);
//			person.getStats().setSkillLevel(Skills.SPACE_OPERATIONS, 1);
//			person.getStats().setSkillLevel(Skills.PLANETARY_OPERATIONS, 1);
			person.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);

			market.getCommDirectory().addPerson(person, 0);
			market.addPerson(person);
			ip.addPerson(person);
		}

		if (market != null && ip.getPerson(People.CYDONIA) == null) {
			PersonAPI person = Global.getFactory().createPerson();
			person.setId("cydonia");
			person.setFaction(Factions.PIRATES);
			person.setGender(FullName.Gender.MALE);
			person.setPostId(Ranks.POST_DOCTOR);
			person.setRankId(Ranks.CITIZEN);
			person.setImportance(PersonImportance.MEDIUM);
			person.getName().setFirst(StringHelper.getString("exerelin_misc", "cydoniaName1"));
			person.getName().setLast(StringHelper.getString("exerelin_misc", "cydoniaName2"));
			person.setPortraitSprite(Global.getSettings().getSpriteName("characters", "doctor"));
			person.getStats().setSkillLevel(Skills.GUNNERY_IMPLANTS, 1);
			market.getCommDirectory().addPerson(person, 0);
			market.addPerson(person);
			ip.addPerson(person);
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
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().set("$metSebestyen", true);
		
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

		if (mem.get("$nex_spawnlocation_background_overwrite") != null) {
			entity = mem.getEntity("$nex_spawnlocation_background_overwrite");
		}
		else if (mem.contains("$nex_startLocation")) {
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
		
		FactionAPI ef = entity.getFaction();
		if (ef != null && ef.getId().equals(PlayerFactionStore.getPlayerFactionIdNGC()))
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
	
	// runcode exerelin.campaign.StartSetupPostTimePass.generateContactAtStartingLocation(Global.getSector().getEconomy().getMarket("jangala"))
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
			if (ContactIntel.playerHasContact(contact, true)) continue;
			if (contact.getMemoryWithoutUpdate().getBoolean("$nex_no_starting_contact")) continue;
			
			String postId = contact.getPostId();
			if (postId == null) continue;
			if (!Nex_IsBaseOfficial.isOfficial(postId, "any")) continue;
			
			// make contact always military except for trade starts
			boolean military = Nex_IsBaseOfficial.isOfficial(postId, "military");
			StartFleetType type = ExerelinSetupData.getInstance().startFleetType;
			boolean tradeStart = type != null && type.isTrade();
			//Global.getLogger(StartSetupPostTimePass.class).info(String.format("Testing contact of post %s: %s, %s", postId, military, tradeStart));
			if (military == tradeStart) continue;
			
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
