package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI.EntryType;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD.TempDataInvasion;
import com.fs.starfarer.api.ui.MarkerData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.InvasionRound.InvasionRoundResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.missions.ConquestMissionIntel;
import exerelin.campaign.intel.missions.ConquestMissionManager;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.*;
import java.util.List;

@Log4j
public class NexUtilsMarket {
	
	// use the memory key instead of this wherever possible
	public static final List<String> NO_INVADE_MARKETS = Arrays.asList(new String[]{"SCY_prismFreeport", "prismFreeport", "prismFreeport_market"});

	public static final Comparator<MarketAPI> marketSizeComparator = new Comparator<MarketAPI>() {

		public int compare(MarketAPI m1, MarketAPI m2) {
			return Integer.compare(m2.getSize(), m1.getSize());
		}};
	
	public static float getPopulation(MarketAPI market)
	{
		return getPopulation(market.getSize());
	}
	
	public static float getPopulation(int size)
	{
		return 2 * (float)Math.pow(10, size);
	}
		
	public static float getHyperspaceDistance(MarketAPI market1, MarketAPI market2)
	{
		SectorEntityToken primary1 = market1.getPrimaryEntity();
		SectorEntityToken primary2 = market2.getPrimaryEntity();
		if (primary1.getContainingLocation() == primary2.getContainingLocation())
			return 0;
		
		return Misc.getDistance(primary1.getLocationInHyperspace(), primary2.getLocationInHyperspace());
	}
		
	public static void setTariffs(MarketAPI market)
	{
		String factionId = market.getFactionId();
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		market.getTariff().modifyMult("nexerelinMult", NexConfig.baseTariffMult);
		market.getTariff().modifyMult("nexerelinFactionMult", conf.tariffMult);
		if (factionId.equals(Factions.PLAYER) || market.hasCondition(Conditions.FREE_PORT))
		{
			market.getTariff().modifyMult("nexerelin_freeMarket", NexConfig.freeMarketTariffMult);
		}
		else
		{
			market.getTariff().unmodify("nexerelin_freeMarket");
		}
	}
	
	// runcode Console.showMessage(exerelin.utilities.ExerelinUtilsMarket.getMarketIndustryValue(market));
	public static float getMarketIndustryValue(MarketAPI market) {
		float value = 0;
		for (Industry ind : market.getIndustries()) {
			//if (!ind.isFunctional()) continue;
			value += ind.getBuildCost();
			
			String aiCoreId = ind.getAICoreId();
			if (aiCoreId != null) {
				try {
					value += Global.getSettings().getCommoditySpec(aiCoreId).getBasePrice();
				} catch (Exception ex) {
					log.warn("Failed to get commodity value of AI core " + aiCoreId, ex);
				}
			}
			if (ind.getSpecialItem() != null) {
				try {
					String id = ind.getSpecialItem().getId();
					value += Global.getSettings().getSpecialItemSpec(id).getBasePrice();
				} catch (NullPointerException npe) {
					String msg = "Nonexistent special item found on " + market.getName() + ", id " + ind.getSpecialItem().getId();
					log.error(msg);
					Global.getSector().getCampaignUI().addMessage(msg, Color.red);
					//throw npe;
				}
			}
		}
		return value;
	}
	
	// see https://en.wikipedia.org/wiki/Net_present_value#Formula
	public static float getIncomeNetPresentValue(MarketAPI market, int months, float discountRate) 
	{
		if (months <= 0) return 0;
		
		float netIncome = market.getNetIncome();
		if (discountRate == 0)
			return netIncome * months;
		
		float x = 1/(1+discountRate);
		float numerator = 1 - (float)Math.pow(x, months);
		float denominator = 1 - x;
		
		return netIncome * (numerator/denominator);
	}
	
	@Deprecated
	public static boolean isMarketBeingInvaded(MarketAPI market)
	{
		return market.getMemoryWithoutUpdate().getBoolean("$beingInvaded")	// NPC fleet
				|| market.getId().equals(Global.getSector().getCharacterData().getMemoryWithoutUpdate().getString("$invasionTarget"));	// player
	}
	
	public static String getOriginalOwner(MarketAPI market) 
	{
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		if (mem.contains(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION))
			return mem.getString(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION);
		return null;
	}
	
	/**
	 * Did this market originally belong to the specified faction?
	 * @param market
	 * @param factionId
	 * @return
	 */
	public static boolean wasOriginalOwner(MarketAPI market, String factionId)
	{
		String origOwner = getOriginalOwner(market);
		if (factionId == null) return origOwner == null;
		return factionId.equals(origOwner);
	}
	
	/**
	 * Is this market still owned by its original owner?
	 * @param market
	 * @return
	 */
	public static boolean isWithOriginalOwner(MarketAPI market)
	{
		return wasOriginalOwner(market, market.getFactionId());
	}

	public static boolean shouldTargetForInvasions(MarketAPI market, int minSize) {
		return shouldTargetForInvasions(market, minSize, InvasionFleetManager.EventType.INVASION);
	}
	
	/**
	 * Can factions launch invasion fleets at <code>market</code>?
	 * Player may still be able to invade even if this returns false.
	 * @param market
	 * @param minSize Minimum market size to consider for invasions
	 * @return
	 */
	public static boolean shouldTargetForInvasions(MarketAPI market, int minSize, InvasionFleetManager.EventType type)
	{
		if (market.getSize() < minSize) return false;
		if (market.isHidden()) return false;
		if (market.getContainingLocation().hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
		FactionAPI marketFaction = market.getFaction();
		String factionId = marketFaction.getId();
		NexFactionConfig config = NexConfig.getFactionConfig(factionId);
		boolean isIndie = factionId.equals(Factions.INDEPENDENT);
		boolean isDerelict = factionId.equals("nex_derelict");
		
		if (config != null && !config.playableFaction && !isIndie && !isDerelict)
			return false;

		if (type == InvasionFleetManager.EventType.INVASION || type == InvasionFleetManager.EventType.RESPAWN
				|| type == InvasionFleetManager.EventType.SAT_BOMB) {
			if (!NexConfig.allowInvadeStoryCritical && Misc.isStoryCritical(market))
				return false;

			if (!NexConfig.allowInvadeStartingMarkets && market.getMemoryWithoutUpdate().getBoolean(ExerelinConstants.MEMKEY_MARKET_EXISTED_AT_START))
				return false;
		}

		

		if (market.getMemoryWithoutUpdate().getBoolean(ExerelinConstants.MEMORY_KEY_NPC_NO_INVADE))
			return false;
		
		boolean allowPirates = NexConfig.allowPirateInvasions;
		boolean isPirate = NexUtilsFaction.isPirateFaction(factionId);
		// player markets count as pirate if player has a commission with a pirate faction
		if (factionId.equals(Factions.PLAYER))
			isPirate = isPirate || NexUtilsFaction.isPirateFaction(PlayerFactionStore.getPlayerFactionId());
		
		if (!allowPirates && (isPirate || isIndie))
		{
			// this is the only circumstance when pirate markets can be invaded while allowPirateInvasions is off
			if (!NexConfig.retakePirateMarkets)
				return false;
			if (isWithOriginalOwner(market))	// was a pirate market all along, can't invade
				return false;
		}
		
		return canBeInvaded(market, false);
	}
	
	/**
	 * Can this market be invaded, by player or by NPCs?
	 * @param market
	 * @param isPlayer Is the would-be invader the player?
	 * @return
	 */
	public static boolean canBeInvaded(MarketAPI market, boolean isPlayer)
	{
		if (market.hasCondition(Conditions.ABANDONED_STATION)) return false;		
		if (market.getPrimaryEntity() instanceof CampaignFleetAPI) return false;
		if (market.isHidden()) return false;
		
		FactionAPI marketFaction = market.getFaction();
		if (isPlayer)
		{
			if (marketFaction == PlayerFactionStore.getPlayerFaction() || marketFaction.isPlayerFaction())
				return false;
		}
		if (marketFaction.isNeutralFaction()) return false;
		if (!market.isInEconomy()) return false;
		
		if (market.getPrimaryEntity().hasTag(ExerelinConstants.TAG_UNINVADABLE))
			return false;
		if (market.getMemoryWithoutUpdate().getBoolean(ExerelinConstants.MEMORY_KEY_UNINVADABLE))
			return false;
		
		return true;
	}
	
	public static boolean canTradeWithMarket(MarketAPI market)
	{
		FactionAPI faction = market.getFaction();
		if (faction.isAtWorst(Factions.PLAYER, RepLevel.SUSPICIOUS))
			return true;
		if (market.hasCondition(Conditions.FREE_PORT))
			return true;
		if (faction.getCustomBoolean(Factions.CUSTOM_ALLOWS_TRANSPONDER_OFF_TRADE))
			return true;
		
		return false;
	}
	
	public static boolean hasHeavyIndustry(MarketAPI market) {
		for (Industry ind : market.getIndustries()) 
		{
			if (!ind.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY))
				continue;
			if (ind.getSpec().getId().equals("IndEvo_ScrapYard")) continue;
			return true;
		}
		return false;
	}
	
	public static boolean hasWorkingSpaceport(MarketAPI market) {
		for (Industry ind : market.getIndustries()) 
		{
			if (!ind.getSpec().hasTag(Industries.TAG_SPACEPORT))
				continue;
			if (ind.isDisrupted()) continue;
			return true;
		}
		return false;
	}
	
	public static PersonAPI getPerson(MarketAPI market, String postId)
	{
		for (CommDirectoryEntryAPI dir : market.getCommDirectory().getEntriesCopy())
		{
			if (dir.getType() != EntryType.PERSON) continue;
			PersonAPI person = (PersonAPI)dir.getEntryData();
			if (person.getPostId().equals(postId))
				return person;
		}
		return null;
	}
	
	public static boolean hasPerson(MarketAPI market, String postId)
	{
		return getPerson(market, postId) != null;
	}
	
	public static boolean removePerson(MarketAPI market, String postId)
	{
		PersonAPI person = getPerson(market, postId);
		if (person == null) return false;
		
		market.getCommDirectory().removePerson(person);
		market.removePerson(person);
		Global.getSector().getImportantPeople().removePerson(person);
		return true;
	}
	
	public static PersonAPI addPerson(ImportantPeopleAPI ip, MarketAPI market, 
			String rankId, String postId, boolean noDuplicate)
	{
		if (noDuplicate && hasPerson(market, postId))
			return null;
		
		PersonAPI person = market.getFaction().createRandomPerson();
		if (rankId != null) person.setRankId(rankId);
		person.setPostId(postId);

		market.getCommDirectory().addPerson(person);
		market.addPerson(person);
		ip.addPerson(person);
		ip.getData(person).getLocation().setMarket(market);
		ip.checkOutPerson(person, "permanent_staff");
		
		if (postId.equals(Ranks.POST_BASE_COMMANDER) || postId.equals(Ranks.POST_STATION_COMMANDER)
				|| postId.equals(Ranks.POST_ADMINISTRATOR)) 
		{
			if (market.getSize() >= 8) {
				person.setImportanceAndVoice(PersonImportance.VERY_HIGH, StarSystemGenerator.random);
			} else if (market.getSize() >= 6) {
				person.setImportanceAndVoice(PersonImportance.HIGH, StarSystemGenerator.random);
			} else {
				person.setImportanceAndVoice(PersonImportance.MEDIUM, StarSystemGenerator.random);
			}
		} else if (postId.equals(Ranks.POST_PORTMASTER)) {
			if (market.getSize() >= 8) {
				person.setImportanceAndVoice(PersonImportance.HIGH, StarSystemGenerator.random);
			} else if (market.getSize() >= 6) {
				person.setImportanceAndVoice(PersonImportance.MEDIUM, StarSystemGenerator.random);
			} else if (market.getSize() >= 4) {
				person.setImportanceAndVoice(PersonImportance.LOW, StarSystemGenerator.random);
			} else {
				person.setImportanceAndVoice(PersonImportance.VERY_LOW, StarSystemGenerator.random);
			}
		} else if (postId.equals(Ranks.POST_SUPPLY_OFFICER)) {
			if (market.getSize() >= 6) {
				person.setImportanceAndVoice(PersonImportance.MEDIUM, StarSystemGenerator.random);
			} else if (market.getSize() >= 4) {
				person.setImportanceAndVoice(PersonImportance.LOW, StarSystemGenerator.random);
			} else {
				person.setImportanceAndVoice(PersonImportance.VERY_LOW, StarSystemGenerator.random);
			}
		}	
		
		return person;
	}
	
	public static void addMarketPeople(MarketAPI market)
	{
		ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
		
		if (market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_DO_NOT_INIT_COMM_LISTINGS)) return;
		
		boolean addedPerson = false;
		if (market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND)) {
			String rankId = Ranks.GROUND_MAJOR;
			if (market.getSize() >= 6) {
				rankId = Ranks.GROUND_GENERAL;
			} else if (market.getSize() >= 4) {
				rankId = Ranks.GROUND_COLONEL;
			}
			
			addPerson(ip, market, rankId, Ranks.POST_BASE_COMMANDER, true);
			addedPerson = true;
		}

		boolean hasStation = false;
		for (Industry curr : market.getIndustries()) {
			if (curr.getSpec().hasTag(Industries.TAG_STATION)) {
				hasStation = true;
				continue;
			}
		}
		if (hasStation) {
			String rankId = Ranks.SPACE_COMMANDER;
			if (market.getSize() >= 6) {
				rankId = Ranks.SPACE_ADMIRAL;
			} else if (market.getSize() >= 4) {
				rankId = Ranks.SPACE_CAPTAIN;
			}
			
			addPerson(ip, market, rankId, Ranks.POST_STATION_COMMANDER, true);
			addedPerson = true;
		}

//			if (market.hasIndustry(Industries.WAYSTATION)) {
//				// kept here as a reminder to check core plugin again when needed
//			}

		if (market.hasSpaceport()) {
			//person.setRankId(Ranks.SPACE_CAPTAIN);

			addPerson(ip, market, null, Ranks.POST_PORTMASTER, true);
			addedPerson = true;
		}

		if (addedPerson) {
			addPerson(ip, market, Ranks.SPACE_COMMANDER, Ranks.POST_SUPPLY_OFFICER, true);
			addedPerson = true;
		}

		if (!addedPerson) {
			addPerson(ip, market, Ranks.CITIZEN, Ranks.POST_ADMINISTRATOR, true);
		}
	}
	
	public static void incrementInvasionFailStreak(MarketAPI market, FactionAPI attacker, boolean canSpawnConquestMission) 
	{
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		int current = 0;
		if (mem.contains(GBConstants.MEMKEY_INVASION_FAIL_STREAK)) {
			current = mem.getInt(GBConstants.MEMKEY_INVASION_FAIL_STREAK);
		}
		current++;
		mem.set(GBConstants.MEMKEY_INVASION_FAIL_STREAK, current);
		
		if (canSpawnConquestMission && !attacker.isPlayerFaction()) {
			if (current <= 1) return;
			float chance = current/(current + 3f);
			double roll = Math.random();
			log.info(String.format("Trying fail streak conquest mission: streak %s, chance %.2f, roll %.2f", current, chance, roll));
			
			if (chance < roll) return;
			
			FactionAPI issuer = attacker;
			
			// too much trouble to check whether the ally is non-player, hostile to target, etc.
			//Alliance alliance = AllianceManager.getFactionAlliance(attacker.getId());
			//if (alliance != null) issuer = Global.getSector().getFaction(alliance.getRandomMember());
			
			ConquestMissionIntel intel = new ConquestMissionIntel(market, issuer, ConquestMissionManager.getDuration(market));
			intel.init();
			Global.getSector().addScript(intel);	// to allow it to expire
		}
	}

	public static float getIndustryDisruptTime(Industry ind) {
		return ind.getSpec().getDisruptDanger().disruptionDays;
	}

	public static int getIndustrySupply(Industry ind, String commodityId) {
		MutableCommodityQuantity quant = ind.getSupply(commodityId);
		if (quant == null) return 0;
		return quant.getQuantity().getModifiedInt();
	}

	/**
	 * Checks for whether the industry has an upgrade and its {@code canUpgrade} method returns true.
	 * @param ind
	 * @param instant
	 * @return True if upgrade was carried out, false if unable to upgrade.
	 */
	public static boolean upgradeIndustryIfCan(Industry ind, boolean instant) {
		if (!canUpgradeIndustry(ind)) return false;

		ind.startUpgrading();
		if (instant) ind.finishBuildingOrUpgrading();
		return true;
	}

	/**
	 * Checks for whether the industry has an upgrade and its {@code canUpgrade} method returns true.
	 * @param ind
	 */
	public static boolean canUpgradeIndustry(Industry ind) {
		if (ind == null) return false;
		if (!ind.canUpgrade()) return false;
		String upg = ind.getSpec().getUpgrade();
		if (upg == null) return false;

		// I could add a check for whether the upgrade actually exists here, but we only want to check for things that don't reflect underlying errors

		return true;
	}

	/**
	 * Upgrades the industry to the specified type (doesn't need to be its 'normal' upgrade option).
	 * Obeys {@code IndustryAPI.canUpgrade()} unless {@code force} is used.
	 * @param ind
	 * @param upgradeId
	 */
	public static boolean upgradeIndustryToTarget(Industry ind, String upgradeId, boolean force, boolean instant) {
		if (ind == null) return false;
		if (upgradeId == null) {
			log.error("upgradeIndustryToTarget called with null upgradeId");
			return false;
		}
		if (!ind.canUpgrade() && !force) return false;

		String prevUpgradeId = ind.getSpec().getUpgrade();
		ind.getSpec().setUpgrade(upgradeId);
		ind.startUpgrading();
		ind.getSpec().setUpgrade(prevUpgradeId);
		if (instant) ind.finishBuildingOrUpgrading();
		return true;
	}

	public static Industry getIndustryWithTag(MarketAPI market, String tag) {
		for (Industry ind : market.getIndustries()) {
			if (ind.getSpec().hasTag(tag)) return ind;
		}
		return null;
	}

	public static void pickEntityDestination(final InteractionDialogAPI dialog,
											 final List<SectorEntityToken> destinations, String confirmText,
											 final CampaignEntityPickerWrapper wrapper) {
		pickEntityDestination(dialog, destinations, confirmText, wrapper, null);
	}
	
	public static void pickEntityDestination(final InteractionDialogAPI dialog, 
			final List<SectorEntityToken> destinations, String confirmText,
			final CampaignEntityPickerWrapper wrapper, final List<IntelInfoPlugin.ArrowData> arrows)
	{
		final Set<StarSystemAPI> toShow = new HashSet<>();
		for (SectorEntityToken token : destinations) {
			if (token.getStarSystem() == null) continue;
			toShow.add(token.getStarSystem());
		}
		dialog.showCampaignEntityPicker(StringHelper.getString("exerelin_misc", "campaignEntityPicker_selectDestination"),
				StringHelper.getString("exerelin_misc", "campaignEntityPicker_destination"),
				confirmText, 
				Global.getSector().getPlayerFaction(), destinations, 
			new BaseCampaignEntityPickerListener() {
				public void pickedEntity(SectorEntityToken entity) {
					wrapper.reportEntityPicked(entity);
				}
				public void cancelledEntityPicking() {
					wrapper.reportEntityPickCancelled();
				}
				public String getSelectedTextOverrideFor(SectorEntityToken entity) {
					return entity.getName() + " - " + entity.getContainingLocation().getNameWithTypeShort();
				}
				public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) {
					wrapper.createInfoText(info, entity);
				}
				public boolean canConfirmSelection(SectorEntityToken entity) {
					return true;
				}
				public float getFuelColorAlphaMult() {
					return 0.5f;
				}
				public float getFuelRangeMult() {
					return 1;
				}

				@Override
				public List<IntelInfoPlugin.ArrowData> getArrows() {
					return arrows;
				}

				@Override
				public List<MarkerData> getMarkers() {
					return null;
				}

				@Override
				public Set<StarSystemAPI> getStarSystemsToShow() {
					return toShow;
				}
			});
	}
	
	public static void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, 
			TempDataInvasion actionData, CargoAPI cargo) 
	{
		for (InvasionListener x : Global.getSector().getListenerManager().getListeners(InvasionListener.class)) {
			x.reportInvadeLoot(dialog, market, actionData, cargo);
		}
	}
	
	public static void reportInvasionRound(InvasionRoundResult result,
			CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr)
	{
		for (InvasionListener x : Global.getSector().getListenerManager().getListeners(InvasionListener.class)) {
			x.reportInvasionRound(result, fleet, defender, atkStr, defStr);
		}
	}
	
	public static void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, 
			MarketAPI market, float numRounds, boolean success)
	{
		for (InvasionListener x : Global.getSector().getListenerManager().getListeners(InvasionListener.class)) {
			x.reportInvasionFinished(fleet, attackerFaction, market, numRounds, success);
		}
	}
	
	public static void reportMarketTransferred(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, 
            boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength)
	{
		for (InvasionListener x : Global.getSector().getListenerManager().getListeners(InvasionListener.class)) {
			x.reportMarketTransfered(market, newOwner, oldOwner, playerInvolved, 
					isCapture, factionsToNotify, repChangeStrength);
		}
	}

	public static void reportNPCGenericRaid(MarketAPI market, MarketCMD.TempData actionData) 
	{
		for (ColonyNPCHostileActListener x : Global.getSector().getListenerManager().getListeners(ColonyNPCHostileActListener.class)) 
		{
			x.reportNPCGenericRaid(market, actionData);
		}
	}
	
	public static void reportNPCIndustryRaid(MarketAPI market, MarketCMD.TempData actionData, Industry industry)
	{
		for (ColonyNPCHostileActListener x : Global.getSector().getListenerManager().getListeners(ColonyNPCHostileActListener.class)) 
		{
			x.reportNPCIndustryRaid(market, actionData, industry);
		}
	}
	
	public static void reportNPCTacticalBombardment(MarketAPI market, MarketCMD.TempData actionData) 
	{
		for (ColonyNPCHostileActListener x : Global.getSector().getListenerManager().getListeners(ColonyNPCHostileActListener.class)) 
		{
			x.reportNPCTacticalBombardment(market, actionData);
		}
	}
	
	public static void reportNPCSaturationBombardment(MarketAPI market, MarketCMD.TempData actionData)
	{
		for (ColonyNPCHostileActListener x : Global.getSector().getListenerManager().getListeners(ColonyNPCHostileActListener.class)) 
		{
			x.reportNPCSaturationBombardment(market, actionData);
		}
	}
	
	public static interface CampaignEntityPickerWrapper {
		public void reportEntityPicked(SectorEntityToken entity);
		public void reportEntityPickCancelled();
		public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity);
	}
}
