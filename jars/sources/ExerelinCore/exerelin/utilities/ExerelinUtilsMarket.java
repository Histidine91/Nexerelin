package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI.EntryType;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD.TempDataInvasion;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.InvasionRound.InvasionRoundResult;
import exerelin.campaign.PlayerFactionStore;
import java.util.Arrays;
import java.util.List;

public class ExerelinUtilsMarket {
	
	// use the memory key instead of this wherever possible
	public static final List<String> NO_INVADE_MARKETS = Arrays.asList(new String[]{"SCY_prismFreeport", "prismFreeport", "prismFreeport_market"});
	
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
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		market.getTariff().modifyMult("nexerelinMult", ExerelinConfig.baseTariffMult);
		market.getTariff().modifyMult("nexerelinFactionMult", conf.tariffMult);
		if (factionId.equals(Factions.PLAYER) || market.hasCondition(Conditions.FREE_PORT))
		{
			market.getTariff().modifyMult("nexerelin_freeMarket", ExerelinConfig.freeMarketTariffMult);
		}
		else
		{
			market.getTariff().unmodify("nexerelin_freeMarket");
		}
	}
	
	public static float getMarketIndustryValue(MarketAPI market) {
		float value = 0;
		for (Industry ind : market.getIndustries()) {
			//if (!ind.isFunctional()) continue;
			value += ind.getBuildCost();
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
		if (factionId.equals(Factions.PLAYER))
			return origOwner == null || origOwner.equals(Factions.PLAYER);
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
	
	/**
	 * Can factions launch invasion fleets at <code>market</code>?
	 * Player may still be able to invade even if this returns false.
	 * @param market
	 * @param minSize Minimum market size to consider for invasions
	 * @return
	 */
	public static boolean shouldTargetForInvasions(MarketAPI market, int minSize)
	{
		if (market.getSize() < minSize) return false;
		if (market.isHidden()) return false;
		FactionAPI marketFaction = market.getFaction();
		String factionId = marketFaction.getId();
		ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
		boolean isIndie = factionId.equals(Factions.INDEPENDENT);
		boolean isDerelict = factionId.equals(Factions.DERELICT);
		
		if (config != null && !config.playableFaction && !isIndie && !isDerelict)
			return false;
		
		boolean allowPirates = ExerelinConfig.allowPirateInvasions;
		boolean isPirate = ExerelinUtilsFaction.isPirateFaction(factionId);
		// player markets count as pirate if player has a commission with a pirate faction
		if (factionId.equals(Factions.PLAYER))
			isPirate = isPirate || ExerelinUtilsFaction.isPirateFaction(PlayerFactionStore.getPlayerFactionId());
		
		if (!allowPirates && (isPirate || isIndie))
		{
			// this is the only circumstance when pirate markets can be invaded while allowPirateInvasions is off
			if (!ExerelinConfig.retakePirateMarkets)
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
			PersonAPI person = market.getFaction().createRandomPerson();
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
}
