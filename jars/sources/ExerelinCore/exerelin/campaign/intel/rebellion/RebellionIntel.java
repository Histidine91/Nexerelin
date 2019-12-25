package exerelin.campaign.intel.rebellion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import static exerelin.campaign.fleets.InvasionFleetManager.getFleetName;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.util.List;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class RebellionIntel extends BaseIntelPlugin {
	
	public static final String COMMODITY_STAT_MOD_ID = "nex_rebellion";
	
	public static final float MAX_DAYS = 180;
	public static final float VALUE_WEAPONS = 0.5f;
	public static final float VALUE_SUPPLIES = 0.1f;
	public static final float VALUE_MARINES = 0.25f;
	//public static final float VALUE_PER_CREDIT = 0.01f * 0.01f;
	public static final float REP_MULT = 1.5f;
	public static final float REBEL_TRADE_MULT = 2f;
	public static final float STRENGTH_CHANGE_MULT = 0.25f;
	public static final float SUPPRESSION_FLEET_INTERVAL = 60f;
	public static final int MAX_STABILITY_PENALTY = 4;
	public static final int MAX_FINAL_UNREST = 3;
	
	public static final boolean DEBUG_MODE = false;
	
	protected static Logger log = Global.getLogger(RebellionIntel.class);
	
	protected MarketAPI market;
	protected boolean started = false;
	protected float age = 0;
	protected float suppressionFleetCountdown = SUPPRESSION_FLEET_INTERVAL * MathUtils.getRandomNumberInRange(0.25f, 0.4f);
	protected IntervalUtil disruptInterval = new IntervalUtil(30, 42);
	
	protected String govtFactionId = null;	// for token substition, if market is liberated
	protected String rebelFactionId = null;
	protected float govtStrength = 1;
	protected float rebelStrength = 1;
	protected float govtTradePoints = 0;
	protected float rebelTradePoints = 0;
	
	protected SuppressionFleetData suppressionFleet = null;
	protected MarketAPI suppressionFleetSource = null;
	protected boolean suppressionFleetWarning = false;
	
	protected float intensity = 0;
	protected int rebellionLevel = 0;	// TODO: not actually set yet
	protected float delay = 0;
	protected int stabilityPenalty = 1;
	protected RebellionResult result = null;
	protected ExerelinReputationAdjustmentResult repResultGovt;
	protected ExerelinReputationAdjustmentResult repResultRebel;
	
	protected String conditionToken = null;
	
	protected IntervalUtil interval = new IntervalUtil(1, 1);
	
	public void init() {
		
		govtFactionId = market.getFactionId();
		setInitialStrengths();
		if (DEBUG_MODE)
		{
			suppressionFleetCountdown = 2;
		}
	}
	
	public static float getSizeMod(int size)
	{
		return (float)Math.pow(2, size - 2);
	}	
	
	/**
	 * Gets an exponent-of-two value based on the market size.
	 * @param market
	 * @return
	 */
	public static float getSizeMod(MarketAPI market)
	{
		return getSizeMod(market.getSize());
	}
	
	protected void setInitialStrengths()
	{
		float stability = market.getStabilityValue();
		float sizeMult = getSizeMod(market);
		govtStrength = (6 + stability * 1.25f) * sizeMult;
		rebelStrength = (3 + (10 - stability)) * sizeMult;
	}
	
	protected float getNormalizedStrength(boolean rebel)
	{
		float numerator = rebel ? rebelStrength : govtStrength;
		return numerator/(govtStrength + rebelStrength);
	}
	
	public float getGovtStrength() {
		return govtStrength;
	}
	
	public float getRebelStrength() {
		return rebelStrength;
	}
	
	public void setGovtStrength(float strength) {
		govtStrength = strength;
	}
	
	public void setRebelStrength(float strength) {
		rebelStrength = strength;
	}
	
	public float getDelay()
	{
		return delay;
	}
	
	protected float updateConflictIntensity()
	{
		float currIntensity = (govtStrength + rebelStrength) * 0.5f - (govtStrength - rebelStrength) * 0.5f;
		currIntensity /= getSizeMod(market);
		
		// counteracts intensity bleeding as belligerents' strength wears down
		float age = (int)(this.age/3);
		if (age < 0) age = 0;
		currIntensity += Math.sqrt(age);
		
		if (!started) currIntensity *= 0.5f;
		this.intensity = currIntensity;
		debugMessage("  Conflict intensity: " + currIntensity);
		return currIntensity;
	}
	
	/**
	 * Resolves an engagement round between government and rebels.
	 * @return
	 */
	protected boolean battleRound()
	{
		float stability = market.getStabilityValue();
		debugMessage("  Stability: " + stability);
		debugMessage("  Initial force strengths: " + govtStrength + ", " + rebelStrength);
		
		float strG = (float)Math.sqrt(govtStrength);
		float strR = (float)Math.sqrt(rebelStrength);
		
		strG *= 1f + 0.5f * Math.random() * (0.5f + 0.5f * stability / 5);
		strR *= 0.75f + 0.5f * Math.random();
		if (market.getFactionId().equals("templars")) strG *= 2;
		
		debugMessage("  Government engagement strength: " + strG);
		debugMessage("  Rebel engagement strength: " + strR);
		
		float diff = strG - strR;
		rebelStrength -= (strG - strR/2) * STRENGTH_CHANGE_MULT;
		govtStrength -= (strR - strG/2) * STRENGTH_CHANGE_MULT;
		
		debugMessage("  Updated force strengths: " + govtStrength + ", " + rebelStrength);
		
		return diff > 0;
	}
	
	protected void gatherStrength()
	{
		float stability = market.getStabilityValue();
		debugMessage("  Stability: " + stability);
		debugMessage("  Initial force strengths: " + govtStrength + ", " + rebelStrength);
		
		float mult = market.getCommodityData(Commodities.HAND_WEAPONS).getAvailable() / 5;
		mult += market.getCommodityData(Commodities.MARINES).getAvailable() / 5;
		mult *= market.getCommodityData(Commodities.SUPPLIES).getAvailable() / 5;
		mult += 0.25f;
		
		govtStrength += (2f + stability/10) * mult;
		rebelStrength += (1 + (10 - stability)/10) * mult;
		
		debugMessage("  Updated force strengths: " + govtStrength + ", " + rebelStrength);
	}
	
	protected void updateCommodityDemand()
	{
		String modId = COMMODITY_STAT_MOD_ID;
		if (result != null)
		{
			market.getCommodityData(Commodities.MARINES).getAvailableStat().unmodify(modId);
			market.getCommodityData(Commodities.HAND_WEAPONS).getAvailableStat().unmodify(modId);
			market.getCommodityData(Commodities.SUPPLIES).getAvailableStat().unmodify(modId);
			return;
		}
		
		int size = market.getSize();
		if (size < 3) size = 3;
		
		market.getCommodityData(Commodities.MARINES).getAvailableStat().modifyFlat(modId, -rebellionLevel);
		market.getCommodityData(Commodities.HAND_WEAPONS).getAvailableStat().modifyFlat(modId, -rebellionLevel);
		market.getCommodityData(Commodities.SUPPLIES).getAvailableStat().modifyFlat(modId, -rebellionLevel);
	}
	
	/**
	 * Roll a random chance for newly spawned patrol to join rebel faction.
	 * @return
	 */
	protected boolean shouldTransferPatrol()
	{
		float chance = 0.2f + 0.8f * getNormalizedStrength(true);
		return Math.random() < chance;
	}
	
	public String getRebelFactionId() {
		return rebelFactionId;
	}
	
	public FactionAPI getRebelFaction() {
		return Global.getSector().getFaction(rebelFactionId);
	}
	
	public int getStabilityPenalty() {
		return stabilityPenalty;
	}
	
	protected void updateStabilityPenalty()
	{
		if (!started) return;
		
		stabilityPenalty = (int)(intensity/6 + 0.5f);
		if (stabilityPenalty < 1) stabilityPenalty = 1;
		else if (stabilityPenalty > MAX_STABILITY_PENALTY) stabilityPenalty = MAX_STABILITY_PENALTY;
		market.reapplyCondition(conditionToken);
	}
	
	/**
	 * Applies recent unrest instability once the rebellion ends.
	 * @param result
	 * @param amount
	 */
	protected void applyFinalInstability(RebellionResult result, int amount)
	{
		if (result == RebellionResult.OTHER) return;
		
		if (amount > MAX_FINAL_UNREST) amount = MAX_FINAL_UNREST;
		
		//RecentUnrest.get(market).add(stabilityPenalty, getString("rebellion"));
	}
	
	/**
	 * Applies reputation gain/loss from aiding government or rebels
	 * This is only done once the event ends, to prevent endless buy/sell exploit
	 */
	protected void applyReputationChange()
	{
		if (result == RebellionResult.OTHER) return;
		
		final FactionAPI govt = Global.getSector().getFaction(govtFactionId);
		final FactionAPI rebs = Global.getSector().getFaction(rebelFactionId);
		
		float netRebelTradePoints = rebelTradePoints - govtTradePoints;
		
		if (netRebelTradePoints > 0)
		{
			final float rep = netRebelTradePoints/getSizeMod(market) * 0.01f * REP_MULT;
			debugMessage("  Rebel trade rep: " + rep);
			repResultRebel = NexUtilsReputation.adjustPlayerReputation(rebs, rep, null, null);
			repResultGovt = NexUtilsReputation.adjustPlayerReputation(govt, -rep*1.5f, null, null);
		}
		else if (netRebelTradePoints < 0)
		{
			final float rep = netRebelTradePoints/getSizeMod(market) * 0.01f * REP_MULT;
			debugMessage("  Government trade rep: " + rep);
			repResultRebel = NexUtilsReputation.adjustPlayerReputation(rebs, -rep*1.5f, null, null);
			repResultGovt = NexUtilsReputation.adjustPlayerReputation(govt, rep, null, null);
		}
	}
	
	/**
	 * Callin for when a market is captured by an invasion fleet.
	 * Checks if should end rebellion (continue it if new owner is also hostile)
	 * @param oldOwnerId
	 * @param newOwnerId
	 */
	public void marketCaptured(String newOwnerId, String oldOwnerId)
	{
		if (result != null) return;
		FactionAPI newOwner = Global.getSector().getFaction(newOwnerId);
		if (newOwner.isAtWorst(rebelFactionId, RepLevel.SUSPICIOUS))
		{
			if (started) endEvent(RebellionResult.LIBERATED);
			else endEvent(RebellionResult.OTHER);
		}
		else
		{
			govtFactionId = newOwnerId;
		}
	}
	
	public void endEvent(RebellionResult result)
	{
		if (result != null) return;
		this.result = result;
		updateCommodityDemand();
		market.removeSpecificCondition(conditionToken);
		
		applyFinalInstability(result, stabilityPenalty);
		applyReputationChange();
		
		// transfer market depending on rebellion outcome
		if (result == RebellionResult.REBEL_VICTORY)
			SectorManager.transferMarket(market, Global.getSector().getFaction(rebelFactionId), market.getFaction(), 
					false, true, null, 0);
		else if (result == RebellionResult.MUTUAL_ANNIHILATION)
			SectorManager.transferMarket(market, Global.getSector().getFaction(Factions.PIRATES), market.getFaction(), 
					false, true, null, 0);
		
		if (result != RebellionResult.OTHER)
			RebellionEventCreator.incrementRebellionPointsStatic(market, -100);
		
		// report event
		String reportStage = null;
		switch (result) {
			case REBEL_VICTORY:
				reportStage = "end_rebel_win";
				break;
			case GOVERNMENT_VICTORY:
				reportStage = "end_govt_win";
				break;
			case LIBERATED:
				reportStage = "end_liberated";
				if (AllianceManager.areFactionsAllied(market.getFactionId(), rebelFactionId))
					reportStage = "end_liberated_ally";
				break;
			case PEACE:
				reportStage = "end_peace";
				break;
			case MUTUAL_ANNIHILATION:
				reportStage = "end_mutual_annihilation";
				break;
			case TIME_EXPIRED:
				reportStage = "end_timeout";
				break;
		}
		endAfterDelay();
		if (reportStage != null)
			sendUpdateIfPlayerHasIntel(reportStage, false);
	}
	
	public void suppressionFleetArrived(SuppressionFleetData data)
	{
		if (suppressionFleet == data)
		{
			sendUpdateIfPlayerHasIntel("suppression_fleet_arrived", false);
			suppressionFleet = null;
			suppressionFleetSource = null;
			int marines = data.fleet.getCargo().getMarines();
			//ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.MARINES, marines);
			data.fleet.getCargo().removeMarines(marines);
			govtStrength += marines * VALUE_MARINES;
			rebelStrength *= 0.75f;	// morale loss + bombardment
		}
	}
	
	public void suppressionFleetDefeated(SuppressionFleetData data)
	{
		if (suppressionFleet == data)
		{
			sendUpdateIfPlayerHasIntel("suppression_fleet_defeated", false);
			suppressionFleet = null;
			suppressionFleetSource = null;
			// morale boost
			rebelStrength *= 1.2f;
			govtStrength *= 0.75f;
		}
	}
	
	protected SuppressionFleetData getSuppressionFleet(MarketAPI sourceMarket)
	{
		String factionId = sourceMarket.getFactionId();
		float fp = (int)(getSizeMod(market) * 1.5f);
		if (market.hasIndustry(Industries.HIGHCOMMAND))
			fp *= 1.25f;
		if (market.hasIndustry(Industries.MILITARYBASE))
			fp *= 1.1f;
		if (market.hasIndustry(Industries.MEGAPORT))
			fp *= 1.1f;
		
		String name = getFleetName("nex_suppressionFleet", factionId, fp);
		
		int numMarines = (int)((rebelStrength * 2 - govtStrength));
		
		float distance = ExerelinUtilsMarket.getHyperspaceDistance(sourceMarket, market);
		int tankerFP = (int)(fp * InvasionFleetManager.TANKER_FP_PER_FLEET_FP_PER_10K_DIST * distance/10000);
		//fp -= tankerFP;
				
		FleetParamsV3 fleetParams = new FleetParamsV3(
				sourceMarket,
				"nex_suppressionFleet", // fleet type
				fp*0.85f, // combat
				fp*0.1f, // freighters
				tankerFP,		// tankers
				numMarines/200,		// personnel transports
				0,		// liners
				fp*0.05f,	// utility
				0);	// quality mod
		
		CampaignFleetAPI fleet = ExerelinUtilsFleet.customCreateFleet(sourceMarket.getFaction(), fleetParams);
		if (fleet == null) return null;
		
		fleet.getCargo().addMarines(numMarines);
		fleet.setName(name);
		fleet.setAIMode(true);
		
		SuppressionFleetData data = new SuppressionFleetData(fleet);
		data.startingFleetPoints = fleet.getFleetPoints();
		data.sourceMarket = sourceMarket;
		data.source = sourceMarket.getPrimaryEntity();
		data.targetMarket = market;
		data.target = market.getPrimaryEntity();
		data.marineCount = numMarines;
		data.event = this;
		
		sourceMarket.getContainingLocation().addEntity(fleet);
		SectorEntityToken entity = sourceMarket.getPrimaryEntity();
		fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
		
		// add AI script
		fleet.addScript(new SuppressionFleetAI(fleet, data));
		
		log.info("\tSpawned suppression fleet " + data.fleet.getNameWithFaction() + " of size " + fp);
		return data;
	}
	
	protected MarketAPI pickSuppressionFleetSource()
	{
		// pick source market
		Vector2f targetLoc = market.getLocationInHyperspace();
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
		List<MarketAPI> markets;
		if (AllianceManager.getFactionAlliance(govtFactionId) != null)
			markets = AllianceManager.getFactionAlliance(govtFactionId).getAllianceMarkets();
		else
			markets = ExerelinUtilsFaction.getFactionMarkets(govtFactionId);
		for (MarketAPI maybeSource : markets)
		{
			if (maybeSource == this.market)
				continue;
			
			if (!maybeSource.hasSpaceport()) continue;
			
			float dist = Misc.getDistance(maybeSource.getLocationInHyperspace(), targetLoc);
			if (dist < 5000.0f) {
				dist = 5000.0f;
			}
			float weight = 20000.0f / dist;
			weight *= maybeSource.getSize();
			
			if (!govtFactionId.equals(maybeSource.getFactionId()))
				weight /= 2;
				
			if (market.hasIndustry(Industries.MILITARYBASE))
				weight *= 2;
			if (market.hasIndustry(Industries.HIGHCOMMAND))
				weight *= 3;
			if (market.hasIndustry(Industries.MEGAPORT))
				weight *= 2;
			picker.add(maybeSource, weight);
		}
		MarketAPI source = picker.pick();
		return source;
	}
	
	/**
	 * Spawns a fleet full of marines from another market to help crush the rebellion
	 */
	protected void spawnSuppressionFleet()
	{
		SuppressionFleetData data = getSuppressionFleet(suppressionFleetSource);
		suppressionFleet = data;
		sendUpdateIfPlayerHasIntel("suppression_fleet_launched", false);
		govtStrength *= 1.2f;	// morale boost
		suppressionFleetWarning = false;
	}
	
	// =========================================================================
	// =========================================================================
	
	@Override
	public void advanceImpl(float amount) 
	{
		if (result != null) return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
		
		if (started)
		{
			age += days;
			if (age > MAX_DAYS)
			{
				endEvent(RebellionResult.TIME_EXPIRED);
				return;
			}
			
			if (govtStrength < rebelStrength * 1.25f && suppressionFleet == null)
			{
				suppressionFleetCountdown -= days;
				if (!suppressionFleetWarning && suppressionFleetCountdown < 12)
				{
					suppressionFleetSource = pickSuppressionFleetSource();
					// no markets to launch fleet from, reset countdown
					if (suppressionFleetSource == null)
						suppressionFleetCountdown = SUPPRESSION_FLEET_INTERVAL * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
					else
					{
						sendUpdateIfPlayerHasIntel("suppression_fleet_warning", false);
						suppressionFleetWarning = true;
					}
				}
				if (suppressionFleetCountdown < 0)
				{
					// don't spawn suppression fleet if the source market was lost in the meantime
					if (suppressionFleetSource != null && AllianceManager.areFactionsAllied(
							suppressionFleetSource.getFactionId(), govtFactionId))
						spawnSuppressionFleet();
					else
						suppressionFleetSource = null;
					suppressionFleetCountdown = SUPPRESSION_FLEET_INTERVAL * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
					
				}
			}
		}
		else
		{
			age += days;
			if (age > 0)
			{
				started = true;
				sendUpdateIfPlayerHasIntel("start", false);
			}
		}
		
		if (started) {
			disruptInterval.advance(days);
			if (disruptInterval.intervalElapsed()) {
				// TODO: industry disruption
			}
		}
		
		interval.advance(days);
		if (!interval.intervalElapsed()) return;
		
		debugMessage("Updating rebellion on " + market.getName() + ": day " + (int)age);
		
		// check if factions involved are still at war
		if (market.getFaction().isAtWorst(rebelFactionId, RepLevel.SUSPICIOUS))
		{
			//endEvent(RebellionResult.PEACE);
			//return;
		}
		
		if (started)
			battleRound();
		else
			gatherStrength();
		
		updateConflictIntensity();
		updateCommodityDemand();
		updateStabilityPenalty();
		
		if (govtStrength <= 0)
		{
			if (rebelStrength > intensity)	// rebel win
			{
				endEvent(RebellionResult.REBEL_VICTORY);
			}
			else	// mutual annihilation
			{
				endEvent(RebellionResult.MUTUAL_ANNIHILATION);
			}
		}
		else if (rebelStrength < 0)
		{
			if (govtStrength > intensity)	// government win
			{
				endEvent(RebellionResult.GOVERNMENT_VICTORY);
			}
			else	// mutual annihilation
			{
				endEvent(RebellionResult.MUTUAL_ANNIHILATION);
			}
		}
	}
	
	// Patrol defection
	/*
	@Override
	public void reportFleetSpawned(CampaignFleetAPI fleet) 
	{
		if (age < 0) return;
		if (ended) return;
		if (fleet.getFaction() != market.getFaction())
			return;
		
		MemoryAPI mem = fleet.getMemoryWithoutUpdate();
		if (!mem.contains(MemFlags.MEMORY_KEY_SOURCE_MARKET))
			return;
		String marketId = mem.getString(MemFlags.MEMORY_KEY_SOURCE_MARKET);
		MarketAPI sourceMarket = Global.getSector().getEconomy().getMarket(marketId);
		if (sourceMarket != market) return;
		
		if (!sourceMarket.getFaction().isHostileTo(rebelFactionId))
			return;
		
		String fleetType = ExerelinUtilsFleet.getFleetType(fleet);
		if (fleetType.equals(FleetTypes.PATROL_SMALL) || fleetType.equals(FleetTypes.PATROL_MEDIUM) || fleetType.equals(FleetTypes.PATROL_LARGE))
		{
			if (shouldTransferPatrol())
				fleet.setFaction(rebelFactionId, true);
		}
	}
	*/
	
	public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
		if (suppressionFleet != null && fleet == suppressionFleet.fleet)
		{
			suppressionFleet = null;
			suppressionFleetSource = null;
		}
	}
	
	public float getNetCommoditySold(PlayerMarketTransaction transaction, String commodityId)
	{
		return transaction.getQuantitySold(commodityId) - transaction.getQuantityBought(commodityId);
	}
	
	public void modifyPoints(float points, boolean rebels)
	{
		if (rebels)
		{
			rebelStrength += points;
			rebelTradePoints += points;			
		}
		else
		{
			govtStrength += points;
			govtTradePoints += points;
		}
	}
	
	// Called from SectorManager
	// TODO: handle ship sales as well?
	//@Override
	public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
		if (transaction.getMarket() != market)
			return;
		debugMessage("Reporting player transaction");
		
		float points = 0;
		
		float marinePoints = getNetCommoditySold(transaction, Commodities.MARINES) * VALUE_MARINES;
		float weaponPoints = getNetCommoditySold(transaction, Commodities.HAND_WEAPONS) * VALUE_WEAPONS;
		float supplyPoints = getNetCommoditySold(transaction, Commodities.SUPPLIES) * VALUE_SUPPLIES;
		debugMessage("  Marine points: " + marinePoints);
		debugMessage("  Weapon points: " + weaponPoints);
		debugMessage("  Supply points: " + supplyPoints);
		
		points += marinePoints;
		points += getNetCommoditySold(transaction, Commodities.HAND_WEAPONS) * VALUE_WEAPONS;
		points += getNetCommoditySold(transaction, Commodities.SUPPLIES) * VALUE_SUPPLIES;
		
		boolean forRebels = transaction.getSubmarket().getPlugin().isBlackMarket();
		if (forRebels) points *= 2f;
		
		modifyPoints(points, forRebels);
	}
		
	@Override
	public String getIcon() {
		return "graphics/icons/intel/faction_conflict.png";
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return market.getPrimaryEntity();
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return market.getFaction();
	}
	
	// TODO
	public String getName() {
		String name = StringHelper.getStringAndSubstituteToken("exerelin_events", "rebellion", "$market", market.getName());
		return name;
	}
	
	// TODO
	public String getString() {
		return null;
	}
	
	protected void debugMessage(String message)
	{
		log.info(message);
		//Global.getSector().getCampaignUI().addMessage(message);
	}
	
	//runcode exerelin.campaign.events.RebellionEvent.startDebugEvent()
	public static void startDebugEvent()
	{
		SectorEntityToken target = Global.getSector().getEntityById("jangala");
		if (target != null)
		{
			/*
			InstigateRebellion rebel = new InstigateRebellion(target.getMarket(), 
					Global.getSector().getFaction(Factions.TRITACHYON), target.getFaction(), false, null);
			rebel.setResult(CovertOpsManager.CovertActionResult.SUCCESS_DETECTED);
			rebel.onSuccess();
			*/
		}
	}
	
	public static RebellionIntel getOngoingEvent(MarketAPI market)
	{
		CampaignEventPlugin eventSuper = Global.getSector().getEventManager().getOngoingEvent(
			new CampaignEventTarget(market), "nex_rebellion");
		if (eventSuper != null)
		{
			RebellionIntel event = (RebellionIntel)eventSuper;
			return event;
		}
		return null;
	}
	
	public static boolean isOngoing(MarketAPI market)
	{
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(RebellionIntel.class))
		{
			RebellionIntel reb = (RebellionIntel)intel;
			if (reb.market == market) return true;
		}
		return false;
	}
	
	public static class SuppressionFleetData extends InvasionFleetManager.InvasionFleetData {
		public RebellionIntel event;
		
		public SuppressionFleetData(CampaignFleetAPI fleet) {
			super(fleet);
		}
		
	}
	
	protected enum UpdateParam {
		PREP, START, FLEET_SPAWNED, FLEET_ARRIVED, FLEET_DEFEATED, END
	}
	
	protected enum RebellionResult {
		PEACE, GOVERNMENT_VICTORY, REBEL_VICTORY, MUTUAL_ANNIHILATION, TIME_EXPIRED, LIBERATED, DECIVILIZED, OTHER
	}
}
