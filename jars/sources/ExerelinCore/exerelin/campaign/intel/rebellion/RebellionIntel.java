package exerelin.campaign.intel.rebellion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.*;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

import static exerelin.campaign.SectorManager.MEMORY_KEY_RECENTLY_CAPTURED_BY_PLAYER;
import static exerelin.campaign.fleets.InvasionFleetManager.getFleetName;

public class RebellionIntel extends BaseIntelPlugin implements InvasionListener, 
		FleetEventListener {

	public static final boolean DEBUG_MODE = false;

	public static final String COMMODITY_STAT_MOD_ID = "nex_rebellion";
	public static final Set<String> ALLOWED_SUBMARKETS = new HashSet<>(Arrays.asList(new String[] {
		//"AL_militaryMarket", Submarkets.GENERIC_MILITARY, Submarkets.SUBMARKET_OPEN,
		Submarkets.SUBMARKET_BLACK
	}));

	public static final String BUTTON_AUTO_SUPPRESS_FLEET = "autoSuppress";

	public static final float MAX_DAYS = 365*2;
	public static final float VALUE_WEAPONS = 0.5f;
	public static final float VALUE_SUPPLIES = 0.1f;
	public static final float VALUE_MARINES = 0.25f;
	//public static final float VALUE_PER_CREDIT = 0.01f * 0.01f;
	public static final float REP_MULT = 0.1f;
	public static final float MAX_REP = 0.2f;
	public static final float STRENGTH_CHANGE_MULT = 0.1f;	// damage done per round
	public static final float SUPPRESSION_FLEET_INTERVAL = DEBUG_MODE ? 6f : 120f;
	public static final float SMUGGLER_INTERVAL = DEBUG_MODE ? 8f : 160f;
	public static final float INDEPENDENT_SMUGGLER_CHANCE = 0.5f;
	public static final float SUPPRESSION_COST_MULT = 0.45f;
	public static final float REBEL_LIBERATION_STRONG_STR_MULT = 1.3f;
	public static final float REBEL_LIBERATION_STR_MULT = 1.15f;
	public static final int MAX_STABILITY_PENALTY = 5;
	public static final int MAX_FINAL_UNREST = 4;
	
	public static final int DETAIL_LEVEL_TO_KNOW_FACTION = 1;
	public static final int DETAIL_LEVEL_FOR_STRENGTH_COLORS = 2;
	public static final int DETAIL_LEVEL_FOR_STRENGTH_VALUES = 4;
	
	public static final boolean USE_REBEL_REP = false;
	public static final boolean ALLOW_NEGATIVE_REBEL_TRADE = false;
	
	protected static Logger log = Global.getLogger(RebellionIntel.class);
	
	protected MarketAPI market;
	protected boolean started = false;
	protected float elapsed = 0;
	protected IntervalUtil disruptInterval = new IntervalUtil(30, 42);
	@Getter @Setter protected boolean playerInitiated;

	@Getter protected FactionAPI govtFaction;
	@Getter protected FactionAPI rebelFaction;
	@Getter @Setter protected FactionAPI liberatorFaction;
	protected float govtStrength = 1;
	protected float rebelStrength = 1;
	protected float govtTradePoints = 0;
	protected float rebelTradePoints = 0;
	protected float rebelTradePointsForStrength = 0;	// unlike the other values, this is modified even for player markets and can go negative
	protected float govtTradePointsWithCurrentGovt = 0;	// reset if government changes
	protected float rebelTradePointsWithCurrentGovt = 0;	// reset if government changes

	// suppression fleet and smuggler
	protected float suppressionFleetCountdown = SUPPRESSION_FLEET_INTERVAL * MathUtils.getRandomNumberInRange(0.25f, 0.4f);
	protected float smugglerCountdown = SMUGGLER_INTERVAL * MathUtils.getRandomNumberInRange(0.25f, 0.4f);

	protected SupportFleetData suppressionData = new SupportFleetData(true);
	protected SupportFleetData smugglerData = new SupportFleetData(false);

	// industry disruption
	protected Industry lastIndustryDisrupted;
	protected float disruptTime;
	protected Long disruptionTimestamp;	// note: not updated automatically when lastUpdateTimestamp is updated by sendUpdate()
	
	protected float intensity = 0;
	protected float commodityFactor = 0;
	protected int stabilityPenalty = 1;
	protected UpdateParam lastUpdate;
	protected Long lastUpdateTimestamp;
	@Getter @Setter	StatBonus intelModifier = new StatBonus();

	protected RebellionResult result = null;
	protected ExerelinReputationAdjustmentResult repResultGovt;
	protected ExerelinReputationAdjustmentResult repResultRebel;
	
	protected String conditionToken;
	protected PersonAPI rebelRep;
	
	protected IntervalUtil interval = new IntervalUtil(1, 1);
	
	public RebellionIntel(MarketAPI market, FactionAPI rebelFaction, float delay) {
		this.market = market;
		this.rebelFaction = rebelFaction;

		elapsed = -delay;
	}

	protected Object readResolve() {
		if (suppressionData == null) {
			suppressionData = new SupportFleetData(true);
		}
		if (smugglerData == null) {
			smugglerData = new SupportFleetData(false);
		}
		if (intelModifier == null) intelModifier = new StatBonus();
		return this;
	}
	
	public void init(boolean instant) {
		govtFaction = market.getFaction();
		setInitialStrengths(instant);
		if (DEBUG_MODE)
		{
			suppressionFleetCountdown = 2;
		}
		if (instant) {
			elapsed = 0;
			started = true;
		}
		
		if (USE_REBEL_REP) {
			ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
			rebelRep = rebelFaction.createRandomPerson();
			rebelRep.setPostId("nex_rebel_representative");
			rebelRep.setRankId(Ranks.AGENT);
			rebelRep.getMemoryWithoutUpdate().set("$nex_rebel_representative", true);
			market.getCommDirectory().addPerson(rebelRep);
			market.addPerson(rebelRep);
			ip.getData(rebelRep).getLocation().setMarket(market);
			ip.checkOutPerson(rebelRep, "nex_rebel_representative");
		}
		Global.getSector().getListenerManager().addListener(this);
		int nexIntelQueued = NexConfig.nexIntelQueued;
		switch (nexIntelQueued) {

			case 0:

				Global.getSector().getIntelManager().addIntel(this, true);
				break;

			case 1:

				if (govtFaction == Misc.getCommissionFaction()
					|| rebelFaction == Misc.getCommissionFaction()
					|| govtFaction == PlayerFactionStore.getPlayerFaction()
					|| rebelFaction == PlayerFactionStore.getPlayerFaction()){
					Global.getSector().getIntelManager().addIntel(this, true);
				}

				else Global.getSector().getIntelManager().queueIntel(this);
				break;

			case 2:

				if (rebelFaction == PlayerFactionStore.getPlayerFaction()) {
					Global.getSector().getIntelManager().addIntel(this, true);
				}

				else
				Global.getSector().getIntelManager().queueIntel(this);
				break;

			default:

				Global.getSector().getIntelManager().addIntel(this, true);
				Global.getSector().getCampaignUI().addMessage("Switch statement within init(), in RebellionIntel, " +
						"defaulted. This is not supposed to happen. If your nexIntelQueued setting within ExerelinConfig " +
						"is below 0 or above 2, that is the likely cause. Otherwise, please contact the mod author!");
		}
		conditionToken = market.addCondition("nex_rebellion_condition");
		
		if (instant) {
			updateConflictIntensity();
			updateStabilityPenalty();
			updateCommodityDemand();
		}
		
		Global.getSector().addScript(this);
		if (!instant)
			sendUpdate(UpdateParam.PREP);
		else {
			//sendUpdate(UpdateParam.START);	// leave it silent //TODO: niko-dear god how am i going to implement this
		}
		
	}
	
	public static float getSizeMod(int size)
	{
		return (float)Math.pow(2, size - 1);
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
	
	public static boolean isRebelOriginalFaction(FactionAPI origOwner, FactionAPI rebelFaction) {
		if (origOwner == null) return false;
		
		boolean isPlayer = rebelFaction.isPlayerFaction() || rebelFaction == Misc.getCommissionFaction();
		
		if (origOwner == rebelFaction) return true;
		
		if (isPlayer) {
			return (origOwner.isPlayerFaction() || origOwner == Misc.getCommissionFaction());
		}		
		
		return false;
	}
	
	public static boolean isNotUnderOriginalOwner(MarketAPI market) {
		String origOwnerId = NexUtilsMarket.getOriginalOwner(market);
		return origOwnerId != null && !origOwnerId.equals(market.getFactionId());
	}
	
	protected void setInitialStrengths(boolean instant)
	{
		float stability = market.getStabilityValue();
		float sizeMult = getSizeMod(market);
		govtStrength = (6 + stability * 1.25f) * sizeMult;
		rebelStrength = (3 + (10 - stability) * 1.25f) * 1.2f * sizeMult * MathUtils.getRandomNumberInRange(0.8f, 1.2f);
		if (isNotUnderOriginalOwner(market)) {
			FactionAPI origOwner = Global.getSector().getFaction(NexUtilsMarket.getOriginalOwner(market));
			if (isRebelOriginalFaction(origOwner, rebelFaction))
				rebelStrength *= REBEL_LIBERATION_STRONG_STR_MULT;
			else
				rebelStrength *= REBEL_LIBERATION_STR_MULT;
		}			
		
		if (instant) {
			govtStrength *= 1.5f;
			rebelStrength *= 1.5f;
		}
	}
	
	/**
	 * Sets different values. 
	 * Not based on stability (to avoid perverse incentives), and lowers government strength 
	 * if government faction is player, so player has to spend resources to put down the rebellion.
	 * @param isPlayer
	 */
	public void setInitialStrengthsAfterInvasion(boolean isPlayer)
	{
		float sizeMult = getSizeMod(market);
		if (isPlayer)
			govtStrength = 10 * sizeMult;
		else
			govtStrength = 15 * sizeMult;
		rebelStrength = 12 * sizeMult * MathUtils.getRandomNumberInRange(0.9f, 1.2f);
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
	
	public boolean isStarted() {
		return started;
	}
	
	public int getDetailLevel() {
		//if (isEnding() || isEnded()) return true;
		if (result == RebellionResult.REBEL_VICTORY) return 100;
		if (Global.getSettings().isDevMode()) return 100;
		
		int level = 0;
		
		if (rebelFaction.isPlayerFaction() || rebelFaction == Misc.getCommissionFaction())
			level = DETAIL_LEVEL_TO_KNOW_FACTION;
		
		for (AgentIntel intel : CovertOpsManager.getManager().getAgents()) {
			if (intel.getMarket() == market)
				level += intel.getLevel();
		}
		level = Math.round(intelModifier.computeEffective(level));
		
		return level;
	}
	
	protected float updateConflictIntensity()
	{
		float currIntensity = (govtStrength + rebelStrength) * 0.5f - (govtStrength - rebelStrength) * 0.5f;
		currIntensity /= getSizeMod(market);
		currIntensity *= 2;
		
		// counteracts intensity bleeding as belligerents' strength wears down
		float age = (int)(this.elapsed/3);
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
		
		// safety
		if (govtStrength < 0) govtStrength = 0;
		if (rebelStrength < 0) rebelStrength = 0;
		
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
		rebelStrength += (1 + (10 - stability)/10) * 1.2f * mult;
		
		debugMessage("  Updated force strengths: " + govtStrength + ", " + rebelStrength);
	}
	
	protected boolean checkVictory() 
	{
		if (govtStrength <= 0)
		{
			if (rebelStrength > intensity)	// rebel win
			{
				endEvent(RebellionResult.REBEL_VICTORY);
				return true;
			}
			else	// mutual annihilation
			{
				endEvent(RebellionResult.MUTUAL_ANNIHILATION);
				return true;
			}
		}
		else if (rebelStrength <= 0)
		{
			if (govtStrength > intensity)	// government win
			{
				endEvent(RebellionResult.GOVERNMENT_VICTORY);
				return true;
			}
			else	// mutual annihilation
			{
				endEvent(RebellionResult.MUTUAL_ANNIHILATION);
				return true;
			}
		}
		return false;
	}
	
	protected void fastResolve() {
		int tries = 0;
		while (tries < 250) {
			battleRound();
			boolean ended = checkVictory();
			if (ended) return;
			tries++;
		}
	}
	
	protected void updateCommodityFactor() {
		float level = (govtStrength + rebelStrength) * 0.25f;
		level /= getSizeMod(market);
		
		commodityFactor = Math.round(level * 100)/100;
		if (commodityFactor > 5)
			commodityFactor = 5;
	}
	
	protected void updateCommodityDemand()
	{
		String modId = COMMODITY_STAT_MOD_ID;
		if (result != null)
		{
			market.getCommodityData(Commodities.MARINES).getPlayerSupplyPriceMod().unmodify(modId);
			market.getCommodityData(Commodities.HAND_WEAPONS).getPlayerSupplyPriceMod().unmodify(modId);
			market.getCommodityData(Commodities.SUPPLIES).getPlayerSupplyPriceMod().unmodify(modId);
			market.getCommodityData(Commodities.MARINES).getPlayerDemandPriceMod().unmodify(modId);
			market.getCommodityData(Commodities.HAND_WEAPONS).getPlayerDemandPriceMod().unmodify(modId);
			market.getCommodityData(Commodities.SUPPLIES).getPlayerDemandPriceMod().unmodify(modId);
			return;
		}
		
		updateCommodityFactor();
		
		float priceMult = 1 + (commodityFactor/8);
		String desc = getString("commodityPriceDesc");
		
		market.getCommodityData(Commodities.MARINES).getPlayerSupplyPriceMod().modifyMult(modId, priceMult, desc);
		market.getCommodityData(Commodities.HAND_WEAPONS).getPlayerSupplyPriceMod().modifyMult(modId, priceMult, desc);
		market.getCommodityData(Commodities.SUPPLIES).getPlayerSupplyPriceMod().modifyMult(modId, priceMult, desc);
		market.getCommodityData(Commodities.MARINES).getPlayerDemandPriceMod().modifyMult(modId, priceMult, desc);
		market.getCommodityData(Commodities.HAND_WEAPONS).getPlayerDemandPriceMod().modifyMult(modId, priceMult, desc);
		market.getCommodityData(Commodities.SUPPLIES).getPlayerDemandPriceMod().modifyMult(modId, priceMult, desc);
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
	 */
	protected void applyFinalInstability()
	{
		if (result == RebellionResult.OTHER) return;
		
		int amount = stabilityPenalty;
		if (amount > MAX_FINAL_UNREST) amount = MAX_FINAL_UNREST;
		
		String desc = getString("unrestDesc");
		RecentUnrest.get(market).add(amount, desc);
	}
	
	protected Industry pickIndustryToDisrupt() {
		WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>();
		for (Industry ind : market.getIndustries()) {
			if (!ind.canBeDisrupted()) continue;
			if (ind.getDisruptedDays() > 15) continue;
			if (ind.getSpec().hasTag(Industries.TAG_UNRAIDABLE))
				continue;
			if (ind.getSpec().hasTag(Industries.TAG_SPACEPORT))
				continue;
			picker.add(ind);
		}
		return picker.pick();
	}
	
	protected void attemptIndustryDisruption() {
		Industry ind = pickIndustryToDisrupt();
		if (ind == null) return;
		
		float random = MathUtils.getRandomNumberInRange(0.5f, 1.75f);
		float atkStr = rebelStrength * (0.25f + random);
		random = MathUtils.getRandomNumberInRange(0.25f, 1.5f);
		float defStr = govtStrength * (0.25f + random);
		
		disruptionTimestamp = Global.getSector().getClock().getTimestamp();
		lastIndustryDisrupted = ind;
		
		float disruptTime = 30 * (atkStr/defStr);
		if (disruptTime > 120) disruptTime = 120;
		log.info("Rolled disruption time: " + disruptTime);
		if (disruptTime < 30) {
			this.disruptTime = 0;
			
			log.info("  Transmitting disruption failure");
			if (govtFaction.isPlayerFaction() || govtFaction == Misc.getCommissionFaction() || isImportant())
				sendUpdate(UpdateParam.INDUSTRY_DISRUPT_FAIL);
			return;
		}
		this.disruptTime = Math.round(disruptTime);
		log.info("  Transmitting disruption success: " + this.disruptTime);
		ind.setDisrupted(disruptTime + ind.getDisruptedDays());
		sendUpdate(UpdateParam.INDUSTRY_DISRUPTED);
	}
	
	/**
	 * Applies reputation gain/loss from aiding government or rebels
	 * This is only done once the event ends, to prevent endless buy/sell exploit
	 */
	protected void applyReputationChange()
	{
		if (result == RebellionResult.OTHER) return;
		
		float rebelTradePointsFloored = Math.max(rebelTradePoints, 0);
		float rebelTradePointsCurrGovtFloored = Math.max(rebelTradePointsWithCurrentGovt, 0);
		float govtTradePointsFloored = Math.max(govtTradePoints, 0);
		float govtTradePointsCurrGovtFloored = Math.max(govtTradePointsWithCurrentGovt, 0);
		
		float netRebelTradePoints = rebelTradePointsFloored - govtTradePointsFloored;
		float netRebelTradePointsCurrGovt = rebelTradePointsCurrGovtFloored - govtTradePointsCurrGovtFloored;

		// rebels know about all trade, govt only knows the trade since they took power

		float rebelRep = (float)Math.sqrt(Math.abs(netRebelTradePoints)) * market.getSize() * 0.01f * REP_MULT;
		if (rebelRep > MAX_REP) rebelRep = MAX_REP;
		if (netRebelTradePoints < 0) rebelRep = -rebelRep;
		if (rebelRep < 0) rebelRep *= 1.5f;

		float govtRep = (float)Math.sqrt(Math.abs(netRebelTradePointsCurrGovt)) * market.getSize() * 0.01f * REP_MULT;
		if (govtRep > MAX_REP) govtRep = MAX_REP;
		if (netRebelTradePointsCurrGovt > 0) govtRep = -govtRep;	// note the inequality runs in the opposite direction here
		if (govtRep < 0) govtRep *= 1.5f;

		debugMessage("  Rebel trade rep: " + rebelRep);
		repResultRebel = NexUtilsReputation.adjustPlayerReputation(rebelFaction, rebelRep, null, null);

		debugMessage("  Government trade rep: " + govtRep);
		repResultGovt = NexUtilsReputation.adjustPlayerReputation(govtFaction, govtRep, null, null);
	}
	
	@Override
	public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, 
			boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength)
	{
		// Liberation checks go here
		if (this.market != market) return;
		if (result != null) return;
		
		boolean selfLiberate = newOwner == rebelFaction;
		if (isCapture) {
			// new owner welcoming or better to rebels, count as liberation and (if new owner is not player or ruled faction) give rebels the planet
			if (newOwner.isAtWorst(rebelFaction, RepLevel.WELCOMING))
			{
				if (started) {
					liberatorFaction = newOwner;
					endEvent(RebellionResult.LIBERATED);
					
					if (!selfLiberate && !Nex_IsFactionRuler.isRuler(newOwner)) {
						SectorManager.transferMarket(market, rebelFaction, market.getFaction(), 
								false, false, null, 0);
						DiplomacyManager.adjustRelations(newOwner, rebelFaction, market.getSize() * 0.02f, null, null, null);
					}
				}
				else endEvent(RebellionResult.OTHER);
			}
		}
		else {
			// faction hands over planet to rebels, this is also a liberation
			// FIXME has wrong text e.g. "Player forces have removed the player government on X"
			// ...I forget if this is still broken
			if (newOwner == rebelFaction)
			{
				if (started) {
					liberatorFaction = oldOwner;
					endEvent(RebellionResult.LIBERATED);
					DiplomacyManager.adjustRelations(oldOwner, rebelFaction, market.getSize() * 0.02f, null, null, null);
				}
				else endEvent(RebellionResult.OTHER);
			}
		}
		
		if (result == null)
		{
			govtFaction = newOwner;
		}

		if (newOwner != oldOwner) {
			govtTradePointsWithCurrentGovt = 0;
			rebelTradePointsWithCurrentGovt = 0;
		}
	}
	
	@Override
	public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, 
			Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {
	}

	@Override
	public void reportInvasionRound(InvasionRound.InvasionRoundResult result, 
			CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) {
	}

	@Override
	public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, 
			MarketAPI market, float numRounds, boolean success) {
	}
	
	@Override
	public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, 
			FleetDespawnReason reason, Object param) {
		if (suppressionData != null && suppressionData.fleet == fleet
				&& reason != FleetDespawnReason.REACHED_DESTINATION
				&& reason != FleetDespawnReason.PLAYER_FAR_AWAY) {
			fleetDefeated(suppressionData);
		}
		if (smugglerData != null && smugglerData.fleet == fleet
				&& reason != FleetDespawnReason.REACHED_DESTINATION
				&& reason != FleetDespawnReason.PLAYER_FAR_AWAY) {
			fleetDefeated(smugglerData);
		}
	}

	@Override
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, 
			BattleAPI battle) {
	}
	
	public void sendUpdate(UpdateParam param) 
	{
		lastUpdate = (UpdateParam)listInfoParam;
		lastUpdateTimestamp = Global.getSector().getClock().getTimestamp();
		sendUpdateIfPlayerHasIntel(param, false);
	}
	
	public void endEvent(RebellionResult result)
	{
		if (this.result != null) return;
		this.result = result;
		updateCommodityDemand();
		market.removeSpecificCondition(conditionToken);
		
		applyFinalInstability();
		applyReputationChange();
		
		// transfer market depending on rebellion outcome
		if (result == RebellionResult.REBEL_VICTORY) {
			SectorManager.transferMarket(market, rebelFaction, market.getFaction(), 
					false, true, null, 0);
			
			if (playerInitiated) {
				market.getMemoryWithoutUpdate().set(MEMORY_KEY_RECENTLY_CAPTURED_BY_PLAYER, true, 60);
			}
		}
		
		else if (result == RebellionResult.MUTUAL_ANNIHILATION)
			SectorManager.transferMarket(market, Global.getSector().getFaction(Factions.PIRATES), market.getFaction(), 
					false, true, null, 0);
		
		if (result != RebellionResult.OTHER)
			RebellionCreator.getInstance().incrementRebellionPoints(market, -100);
		
		// report event
		endAfterDelay();
		sendUpdate(UpdateParam.END);
	}
	
	@Override
	protected void notifyEnding() {
		Global.getSector().getListenerManager().removeListener(this);
		if (rebelRep != null) {
			Global.getSector().getImportantPeople().removePerson(rebelRep);
			market.removePerson(rebelRep);
			market.getCommDirectory().removePerson(rebelRep);
			Global.getSector().getImportantPeople().returnPerson(rebelRep, "nex_rebel_representative");
			Global.getSector().getImportantPeople().removePerson(rebelRep);
		}
	}
	
	public void fleetArrived(SupportFleetData data)
	{
		if (isEnding() || isEnded()) return;
		if (data.success != null) return;

		data.success = true;
		data.timestampNow();

		log.info((data.forGovernment ? "Suppression fleet" : "Smugglers") + " arrived");
		log.info(String.format("Strengths before: %.1f, %.1f", govtStrength, rebelStrength));

		if (data.forGovernment)
		{
			sendUpdate(UpdateParam.FLEET_ARRIVED);
			float str = data.fleet.getMemoryWithoutUpdate().getFloat("$nex_rebellion_suppr_payload");
			govtStrength *= 1.1f;
			govtStrength += str * VALUE_MARINES;
			rebelStrength *= 0.75f;	// morale loss + bombardment
		} else {
			sendUpdate(UpdateParam.SMUGGLER_ARRIVED);
			float str = data.fleet.getMemoryWithoutUpdate().getFloat("$nex_rebellion_suppr_payload");
			rebelStrength *= 1.1f;
			rebelStrength += str * VALUE_MARINES;
			govtStrength *= 0.9f;
		}

		log.info(String.format("Strengths after: %.1f, %.1f", govtStrength, rebelStrength));
	}
	
	public void fleetDefeated(SupportFleetData data)
	{
		if (isEnding() || isEnded()) return;
		if (data.success != null) return;

		data.success = false;
		data.timestampNow();

		log.info((data.forGovernment ? "Suppression fleet" : "Smugglers") + " defeated");
		log.info(String.format("Strengths before: %.1f, %.1f", govtStrength, rebelStrength));

		if (data.forGovernment)
		{
			sendUpdate(UpdateParam.FLEET_DEFEATED);
			// morale boost
			rebelStrength *= 1.2f;
			govtStrength *= 0.75f;
		} else {
			sendUpdate(UpdateParam.SMUGGLER_DEFEATED);
			// morale boost
			govtStrength *= 1.1f;
			rebelStrength *= 0.8f;
		}

		log.info(String.format("Strengths after: %.1f, %.1f", govtStrength, rebelStrength));
	}

	protected int getWantedBaseFP() {
		int sizeFactor = market.getSize() * 4;
		if (sizeFactor < 1) sizeFactor = 1;
		return sizeFactor * 6;
	}

	protected int getWantedPayloadStrength(boolean smuggler, float fp) {
		int maxStr = Math.round(12 * getSizeMod(market.getSize()));

		int str;
		if (smuggler) {
			str = (int)(govtStrength - rebelStrength/2);
			maxStr /= 2;
		}
		else {
			str = (int)(rebelStrength * 2 - govtStrength);
		}
		str = Math.max(str, (int)(fp/2));
		str = Math.min(str, maxStr);
		return str;
	}

	protected float getPayloadCreditCost(int reqStrength) {
		int numMarines = (int)(reqStrength / VALUE_MARINES);
		float baseCost = (numMarines * Global.getSettings().getCommoditySpec(Commodities.MARINES).getBasePrice()) + (numMarines/4f * Global.getSettings().getCommoditySpec(Commodities.HAND_WEAPONS).getBasePrice());
		return baseCost * SUPPRESSION_COST_MULT;
	}
	
	protected CampaignFleetAPI createFleet(SupportFleetData data)
	{
		boolean smuggler = !data.forGovernment;
		MarketAPI sourceMarket = data.source;
		String factionId = sourceMarket.getFactionId();
		if (data.indie) {
			factionId = Factions.INDEPENDENT;
		}
		int sizeFactor = market.getSize() * 4;
		if (sizeFactor < 1) sizeFactor = 1;
		float fp = getWantedBaseFP();
		if (smuggler) {
			if (sourceMarket.hasIndustry(Industries.HIGHCOMMAND))
				fp *= 1.25f;
			if (sourceMarket.hasIndustry(Industries.MILITARYBASE))
				fp *= 1.1f;
			if (sourceMarket.hasIndustry(Industries.MEGAPORT))
				fp *= 1.1f;
		}
		if (smuggler) fp /= 2;

		// fleet pool (only for non-independent fleets)
		if (!data.indie) {
			fp = FleetPoolManager.getManager().drawFromPool(factionId, new FleetPoolManager.RequisitionParams(fp, 0, -5000f, 0.5f));
			if (fp <= 0) return null;
		}

		// fleet setup
		String name = null;
		if (!smuggler) name = getFleetName("nex_suppressionFleet", factionId, fp);

		int str = getWantedPayloadStrength(smuggler, fp);
		int numMarines = (int)(str / VALUE_MARINES);
		
		float distance = NexUtilsMarket.getHyperspaceDistance(sourceMarket, market);
		int tankerFP = (int)(fp * InvasionFleetManager.TANKER_FP_PER_FLEET_FP_PER_10K_DIST * distance/10000);
		//fp -= tankerFP;

		float combatFP = fp * 0.85f;
		if (smuggler) combatFP = fp * 0.4f;
		FleetParamsV3 fleetParams = new FleetParamsV3(
				sourceMarket,
				smuggler ? FleetTypes.TRADE_SMUGGLER : "nex_suppressionFleet", // fleet type
				combatFP,
				fp*0.1f, // freighters
				tankerFP,		// tankers
				numMarines/100,		// personnel transports
				0,		// liners
				fp*0.05f,	// utility
				0);	// quality mod
		
		CampaignFleetAPI fleet = NexUtilsFleet.customCreateFleet(Global.getSector().getFaction(factionId), fleetParams);
		if (fleet == null) {
			if (!data.indie) FleetPoolManager.getManager().modifyPool(factionId, fp);
			return null;
		}
		
		fleet.getCargo().addCommodity(Commodities.HAND_WEAPONS, (int)(numMarines/4f));
		fleet.getMemoryWithoutUpdate().set("$nex_rebellion_suppr_payload", str);
		fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
		if (name != null) fleet.setName(name);
		if (!data.forGovernment) {
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SMUGGLER, true);
		}

		fleet.setAIMode(true);

		data.fleet = fleet;
		data.startingFleetPoints = fleet.getFleetPoints();
		data.target = market;
		data.intel = this;
		
		sourceMarket.getContainingLocation().addEntity(fleet);
		SectorEntityToken entity = sourceMarket.getPrimaryEntity();
		fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
		
		// add AI script
		RebellionSupportFleetAI script = new RebellionSupportFleetAI(fleet, data);
		fleet.addScript(script);
		script.giveInitialAssignment();
		
		log.info("\tSpawned suppression fleet " + data.fleet.getNameWithFaction() + " of size " + fp);
		return fleet;
	}
	
	protected MarketAPI pickFleetSource(SupportFleetData data)
	{
		boolean indie = false;
		boolean smuggler = !data.forGovernment;
		if (smuggler) {
			if (market.getFaction().isHostileTo(rebelFaction)) indie = true;
			else indie = Math.random() < INDEPENDENT_SMUGGLER_CHANCE;
		}
		data.indie = indie;

		// pick source market
		Vector2f targetLoc = market.getLocationInHyperspace();
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
		HashSet<MarketAPI> markets = new LinkedHashSet<>();
		FactionAPI sender = smuggler ? rebelFaction : govtFaction;
		FactionAPI enemy = smuggler ? govtFaction : rebelFaction;
		if (indie) {
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
				if (market.isHidden()) continue;
				if (market.getFaction().isHostileTo(Factions.INDEPENDENT)) continue;
				markets.add(market);
			}
		}
		else {
			if (AllianceManager.getFactionAlliance(sender.getId()) != null) {
				markets.addAll(AllianceManager.getFactionAlliance(sender.getId()).getAllianceMarkets());
			}
			else {
				markets.addAll(NexUtilsFaction.getFactionMarkets(sender.getId()));
			}
		}
		if (sender.isPlayerFaction() && Misc.getCommissionFaction() != null) {
			markets.addAll(NexUtilsFaction.getFactionMarkets(Misc.getCommissionFactionId()));
		}
		if (sender == Misc.getCommissionFaction()) {
			markets.addAll(NexUtilsFaction.getFactionMarkets(Factions.PLAYER));
		}

		Set<String> alliesOfEnemy = new HashSet<>();

		Alliance enemyAlliance = AllianceManager.getFactionAlliance(enemy.getId());
		if (enemyAlliance != null) {
			alliesOfEnemy.addAll(enemyAlliance.getMembersCopy());
		} else {
			alliesOfEnemy.add(enemy.getId());
		}

		for (MarketAPI maybeSource : markets)
		{
			if (maybeSource == this.market) continue;
			if (maybeSource.isHidden()) continue;
			if (!maybeSource.hasSpaceport()) continue;
			if (RebellionIntel.isOngoing(maybeSource)) continue;

			if (!sender.isPlayerFaction() && sender != PlayerFactionStore.getPlayerFaction()) {
				if (maybeSource.getFaction().isPlayerFaction() || maybeSource.isPlayerOwned()) continue;
			}

			if (alliesOfEnemy.contains(maybeSource.getFaction().getId())) continue;
			
			float dist = Misc.getDistance(maybeSource.getLocationInHyperspace(), targetLoc);
			if (dist < 5000.0f) {
				dist = 5000.0f;
			}
			float weight = 20000.0f / dist;
			weight *= maybeSource.getSize();
			
			if (sender != maybeSource.getFaction())
				weight /= 2;

			if (!smuggler) {
				if (market.hasIndustry(Industries.MILITARYBASE))
					weight *= 2;
				if (market.hasIndustry(Industries.HIGHCOMMAND))
					weight *= 3;
				if (market.hasIndustry(Industries.MEGAPORT))
					weight *= 2;
			}
			picker.add(maybeSource, weight);
		}
		MarketAPI source = picker.pick();
		data.source = source;

		if (source != null && source.getFaction().isHostileTo(market.getFaction())) {
			data.indie = true;
		}

		return source;
	}

	protected void prepFleet(boolean forGovernment) {
		SupportFleetData data;
		if (forGovernment) {
			suppressionData = new SupportFleetData(true);
			data = suppressionData;
		} else {
			smugglerData = new SupportFleetData(false);
			data = smugglerData;
		}

		boolean smuggler = !forGovernment;
		data.source = pickFleetSource(data);
		// no markets to launch fleet from, delay countdown and check again later
		if (data.source == null) {
			if (smuggler) {
				smugglerCountdown += SMUGGLER_INTERVAL * MathUtils.getRandomNumberInRange(0.2f, 0.3f);
			}
			else {
				suppressionFleetCountdown += SUPPRESSION_FLEET_INTERVAL * MathUtils.getRandomNumberInRange(0.2f, 0.3f);
			}
		}
		else
		{
			sendUpdate(smuggler ? UpdateParam.SMUGGLER_PREP : UpdateParam.FLEET_PREP);
			data.timestampNow();
			data.sentWarning = true;
		}
	}

	/**
	 * Spawns a fleet full of marines from another market to help crush the rebellion, or smugglers to send them aid.
	 */
	protected void spawnFleet(SupportFleetData data)
	{
		createFleet(data);
		sendUpdate(data.forGovernment ? UpdateParam.FLEET_SPAWNED : UpdateParam.SMUGGLER_SPAWNED);
		data.sentWarning = false;
		data.launched = true;
		data.timestampNow();
	}

	protected void updateSuppressionFleet(float days) {
		suppressionFleetCountdown -= days;
		if (!suppressionData.isActive() && !suppressionData.sentWarning && suppressionFleetCountdown < 12)
		{
			prepFleet(true);
		}

		// block suppression fleet launch if source market is no longer controlled
		if (!suppressionData.launched && suppressionData.source != null && !AllianceManager.areFactionsAllied(
				suppressionData.source.getFactionId(), govtFaction.getId()))
		{
			suppressionData.source = null;
			suppressionData.updateTimestamp = null;
			suppressionData.sentWarning = false;
			suppressionFleetCountdown = SUPPRESSION_FLEET_INTERVAL * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
		}

		if (suppressionFleetCountdown < 0 && suppressionData.source != null)
		{
			spawnFleet(suppressionData);
			suppressionFleetCountdown = SUPPRESSION_FLEET_INTERVAL * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
		}
	}

	protected void updateSmuggler(float days) {
		smugglerCountdown -= days;
		if (!smugglerData.isActive() && smugglerCountdown < 12 && !smugglerData.sentWarning)
		{
			prepFleet(false);
		}

		// block smuggler launch if source market is now under an ally of govt faction
		if (!smugglerData.launched && smugglerData.source != null && AllianceManager.areFactionsAllied(
				smugglerData.source.getFactionId(), govtFaction.getId()))
		{
			smugglerData.source = null;
			smugglerData.updateTimestamp = null;
			smugglerData.sentWarning = false;
			smugglerCountdown = SMUGGLER_INTERVAL * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
		}

		if (smugglerCountdown < 0 && smugglerData.source != null)
		{
			spawnFleet(smugglerData);
			smugglerCountdown = SMUGGLER_INTERVAL * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
		}
	}

	protected boolean instaCreateSuppressionFleet(boolean applyCost) {
		suppressionData = new SupportFleetData(true);
		suppressionData.source = pickFleetSource(suppressionData);
		if (suppressionData.source == null) {
			return false;
		}
		spawnFleet(suppressionData);
		if (applyCost) {
			int reqFP = getWantedBaseFP();
			int reqStrength = getWantedPayloadStrength(false, reqFP);
			float cost = reqFP * NexConfig.fleetRequestCostPerFP;
			cost += getPayloadCreditCost(reqStrength);

			NexUtilsCargo.makePaymentWithDebtIfNeeded((int)cost, null);
		}

		return true;
	}
	
	// =========================================================================
	// =========================================================================
	
	@Override
	public void advanceImpl(float amount) 
	{
		if (result != null) return;
		if (!market.isInEconomy()) {
			endEvent(RebellionResult.DECIVILIZED);
			return;
		}
		
		float days = Global.getSector().getClock().convertToDays(amount);
		
		if (started)
		{
			elapsed += days;
			if (elapsed > MAX_DAYS)
			{
				endEvent(RebellionResult.TIME_EXPIRED);
				return;
			}
			
			if (govtStrength < rebelStrength * 1.25f && !suppressionData.isActive()
					&& !market.getFaction().isPlayerFaction())
			{
				updateSuppressionFleet(days);
			}
			if (rebelStrength < govtStrength * 1.1f && !smugglerData.isActive()) {
				if (NexConfig.allowSmugglersForPlayerRebellions || !market.getFaction().isPlayerFaction()) {
					updateSmuggler(days);
				}
			}
		}
		else
		{
			elapsed += days;
			if (elapsed > 0)
			{
				started = true;
				sendUpdate(UpdateParam.START);
			}
		}
		
		if (started) {
			disruptInterval.advance(days);
			if (disruptInterval.intervalElapsed()) {
				attemptIndustryDisruption();
			}
		}
		
		interval.advance(days);
		if (!interval.intervalElapsed()) return;
		
		debugMessage("Updating rebellion on " + market.getName() + ": day " + (int)elapsed);
		
		// check if factions involved are still at war
		if (market.getFaction().isAtWorst(rebelFaction, RepLevel.SUSPICIOUS))
		{
			//endEvent(RebellionResult.PEACE);
			//return;
		}
		
		if (started)
			battleRound();
		else
			gatherStrength();
		
		updateConflictIntensity();
		updateStabilityPenalty();
		updateCommodityDemand();
		
		checkVictory();
	}
	
	public float getNetCommoditySold(PlayerMarketTransaction transaction, String commodityId)
	{
		return transaction.getQuantitySold(commodityId) - transaction.getQuantityBought(commodityId);
	}

	public float adjustRebelStrengthWithTradeBalance(float points) {
		if (points == 0) return 0;

		float oldTP = rebelTradePointsForStrength;
		rebelTradePointsForStrength += points;
		//log.info(String.format("Old points %s, new points %s", oldTP, rebelTradePointsForStrength));
		boolean isNegative = points < 0;

		if (ALLOW_NEGATIVE_REBEL_TRADE) {
			rebelStrength += points;
			return points;
		}

		// don't weaken the rebels if we buy more from the BM than we originally sold to them
		float pointsAbs = Math.abs(points);
		float portionAboveZero;
		if (isNegative) {
			// strength loss from trade cannot be more than what they had to lose, if any (putting them extra underwater doesn't do anything)
			portionAboveZero = Math.min(oldTP, pointsAbs);
		} else {
			// strength gain from trade cannot be more than the current positive balance, if any (the rest was spent getting their head over water)
			portionAboveZero = Math.min(rebelTradePointsForStrength, pointsAbs);
		}
		//log.info(String.format("Portion above zero is %s", portionAboveZero));
		if (portionAboveZero < 0) return 0;

		if (isNegative) portionAboveZero *= -1;
		rebelStrength += portionAboveZero;
		return portionAboveZero;
	}
	
	public void modifyPointsFromTrade(float points, boolean rebels)
	{
		boolean addTradePoints = !market.isPlayerOwned() && !market.getFaction().isPlayerFaction();
		float truePoints;
		if (rebels)
		{
			points *= 2f;
			truePoints = adjustRebelStrengthWithTradeBalance(points);

			if (addTradePoints) {
				rebelTradePoints += points;
				rebelTradePointsWithCurrentGovt += points;
			}

			if (rebelStrength < 0) rebelStrength = 0;
			if (rebelTradePoints < 0) rebelTradePoints = 0;
			if (rebelTradePointsWithCurrentGovt < 0) rebelTradePointsWithCurrentGovt = 0;
		}
		else
		{
			points *= 0.5f;
			govtStrength += points;
			if (addTradePoints) {
				govtTradePoints += points;
				govtTradePointsWithCurrentGovt += points;
			}
			if (govtStrength < 0) govtStrength = 0;
			if (govtTradePoints < 0) govtTradePoints = 0;
			if (govtTradePointsWithCurrentGovt < 0) govtTradePointsWithCurrentGovt = 0;

			truePoints = points;
		}

		printTransactionPoints(truePoints, rebels);
	}

	public void printTransactionPoints(float points, boolean rebels) {
		InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
		if (dialog != null) {
			dialog.getTextPanel().setFontSmallInsignia();
			String side = getString(rebels ? "rebel" : "government");
			String pointsStr = String.format("%.1f", points);
			LabelAPI label = dialog.getTextPanel().addPara(String.format(getString("dialogStrChange"), side, pointsStr));
			label.setHighlight(side, pointsStr);
			Color sideColor = govtFaction.getColor();
			if (rebels) {
				sideColor = getDetailLevel() >= DETAIL_LEVEL_TO_KNOW_FACTION ? rebelFaction.getColor() : Misc.getHighlightColor();
			}
			label.setHighlightColors(sideColor, Misc.getHighlightColor());
			dialog.getTextPanel().setFontInsignia();
		}
	}
	
	// Called from SectorManager
	// TODO: handle ship sales as well?
	//@Override
	public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
		if (transaction.getMarket() != market)
			return;
		debugMessage("Reporting player transaction");
		
		if (!ALLOWED_SUBMARKETS.contains(transaction.getSubmarket().getSpecId()))
			return;
		
		float points;
		
		float marinePoints = getNetCommoditySold(transaction, Commodities.MARINES) * VALUE_MARINES;
		float weaponPoints = getNetCommoditySold(transaction, Commodities.HAND_WEAPONS) * VALUE_WEAPONS;
		float supplyPoints = getNetCommoditySold(transaction, Commodities.SUPPLIES) * VALUE_SUPPLIES;
		debugMessage("  Marine points: " + marinePoints);
		debugMessage("  Weapon points: " + weaponPoints);
		debugMessage("  Supply points: " + supplyPoints);
		
		points = marinePoints + weaponPoints + supplyPoints;
		
		boolean forRebels = transaction.getSubmarket().getPlugin().isBlackMarket();
		modifyPointsFromTrade(points, forRebels);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		Color h = Misc.getHighlightColor();
		float pad = 0;
		
		int knownLevel = getDetailLevel();
		NexUtilsFaction.addFactionNamePara(info, initPad, tc, govtFaction);
		if (knownLevel >= DETAIL_LEVEL_TO_KNOW_FACTION) 
			NexUtilsFaction.addFactionNamePara(info, pad, tc, rebelFaction);
		
		// If event is completed, print result
		if (result != null) {
			switch (result) {
				case GOVERNMENT_VICTORY:
					info.addPara(getString("intelBulletGovtVictory"), tc, pad);
					break;
				case REBEL_VICTORY:
					info.addPara(getString("intelBulletRebelVictory"), tc, pad);
					break;
				case LIBERATED:
					String str = getString("intelBulletLiberated");
					if (liberatorFaction != null) {
						log.warn("Liberation faction is null, hiding bullet point");
						break;
					}
					String name = liberatorFaction.getDisplayName();
					str = StringHelper.substituteToken(str, "$faction", name);
					info.addPara(str, pad, tc, liberatorFaction.getBaseUIColor(), name);
					break;
				case MUTUAL_ANNIHILATION:
					info.addPara(getString("intelBulletMutualAnnihilation"), tc, pad);
					break;
				case TIME_EXPIRED:
					info.addPara(getString("intelBulletExpired"), tc, pad);
					break;
			}
			// also print rep changes, if this is an update
			if (listInfoParam != null) {
				if (repResultGovt != null && repResultGovt.delta != 0)
					CoreReputationPlugin.addAdjustmentMessage(repResultGovt.delta, govtFaction, null, 
							null, null, info, tc, false, pad);
				if (repResultRebel != null && repResultRebel.delta != 0)
					CoreReputationPlugin.addAdjustmentMessage(repResultRebel.delta, rebelFaction, null, 
							null, null, info, tc, false, pad);
			}
			
			return;
		}
		
		if (!started)
			info.addPara(getString("intelBulletPrep"), tc, pad);
		
		// If this is an update, print that
		String str;
		if (listInfoParam != null) {
			switch ((UpdateParam)listInfoParam) {
				case START:
					info.addPara(getString("intelBulletStart"), tc, pad);
					break;
				case FLEET_PREP:
					str = getString("intelBulletFleetPrep");
					info.addPara(str, pad, tc, suppressionData.source.getFaction().getBaseUIColor(),
							suppressionData.source.getName());
					break;
				case FLEET_SPAWNED:
					str = getString("intelBulletFleetLaunched");
					info.addPara(str, pad, tc, suppressionData.source.getFaction().getBaseUIColor(),
							suppressionData.source.getName());
					break;
				case FLEET_ARRIVED:
					info.addPara(getString("intelBulletFleetArrived"), tc, pad);
					break;
				case FLEET_DEFEATED:
					info.addPara(getString("intelBulletFleetDefeated"), tc, pad);
					break;

				case SMUGGLER_PREP:
					str = getString("intelBulletFleetPrepAlt");
					info.addPara(str, pad, tc, smugglerData.source.getFaction().getBaseUIColor(),
							smugglerData.source.getName());
					break;
				case SMUGGLER_SPAWNED:
					str = getString("intelBulletFleetLaunchedAlt");
					info.addPara(str, pad, tc, smugglerData.source.getFaction().getBaseUIColor(),
							smugglerData.source.getName());
					break;
				case SMUGGLER_ARRIVED:
					info.addPara(getString("intelBulletFleetArrivedAlt"), tc, pad);
					break;
				case SMUGGLER_DEFEATED:
					info.addPara(getString("intelBulletFleetDefeatedAlt"), tc, pad);
					break;

				case INDUSTRY_DISRUPTED:
					str = getString("intelBulletIndustryDisrupt");
					info.addPara(str, pad, tc, h, lastIndustryDisrupted.getCurrentName(), 
							Math.round(disruptTime) + "");
					break;
				case INDUSTRY_DISRUPT_FAIL:
					str = getString("intelBulletIndustryDisruptFail");
					info.addPara(str, pad, tc, h, lastIndustryDisrupted.getCurrentName());
					break;
			}
		}
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}

	protected void printBasicInfo(TooltipMakerAPI info, List<Pair<String, String>> tokens, float opad) {
		float pad = 3;

		String rebName = rebelFaction.getDisplayNameWithArticleWithoutArticle();
		String govtName = govtFaction.getDisplayNameWithArticleWithoutArticle();
		Color govtColor = govtFaction.getBaseUIColor();
		Color rebColor = rebelFaction.getBaseUIColor();
		Color hl = Misc.getHighlightColor();

		// basic description
		String str;
		if (!started) str = getString("intelDescPrep");
		else str = getString("intelDesc");
		str = StringHelper.substituteTokens(str, tokens, true);
		LabelAPI label = info.addPara(str, opad);
		label.setHighlight(govtName, market.getName());
		label.setHighlightColors(govtColor, hl);

		// tell player they can sell guns to affect the rebellion
		if (result == null) {
			str =  market.isPlayerOwned() ? getString("intelDesc2Player") : getString("intelDesc2");
			info.addPara(str, opad);
		}

		// rebellion result
		if (result != null) {
			info.addSectionHeading(getString("intelHeaderResult"), Alignment.MID, opad);
			boolean liberatorIsRebel = liberatorFaction == rebelFaction;
			String id;
			switch (result) {
				case GOVERNMENT_VICTORY:
					id = "intelDescResultGovtVictory";
					break;
				case REBEL_VICTORY:
					id = "intelDescResultRebelVictory";
					break;
				case LIBERATED:
					tokens.add(new Pair<>("$liberatorFaction", liberatorFaction.getDisplayName()));
					tokens.add(new Pair<>("$theLiberatorFaction", liberatorFaction.getDisplayNameWithArticle()));
					id = "intelDescResultLiberated";
					if (liberatorIsRebel) id += "Ally";
					break;
				case TIME_EXPIRED:
					id = "intelDescResultExpired";
					break;
				case DECIVILIZED:
				case MUTUAL_ANNIHILATION:
					id = "intelDescResultDecivilized";
					break;
				default:
					id = "intelDescResultOther";
					break;
			}
			str = getString(id);
			str = StringHelper.substituteTokens(str, tokens, true);

			label = info.addPara(str, opad);
			if (result == RebellionResult.LIBERATED) {
				if (liberatorIsRebel) {
					label.setHighlight(rebName);
					label.setHighlightColors(rebColor);
				} else {
					label.setHighlight(liberatorFaction.getDisplayNameWithArticleWithoutArticle(), rebName);
					label.setHighlightColors(liberatorFaction.getBaseUIColor(), rebColor);
				}
			}
			else if (result == RebellionResult.REBEL_VICTORY) {
				label.setHighlight(rebName);
				label.setHighlightColors(rebColor);
			}

			// reputation from trade
			if (repResultGovt != null && repResultRebel != null) {
				info.addSectionHeading(getString("intelHeaderTrade"), Alignment.MID, opad);

				if (govtTradePoints > 0) {
					str = getString(govtTradePointsWithCurrentGovt > 0 ? "intelDescTradeGovt" : "intelDescTradeGovtPrev");
					str = StringHelper.substituteTokens(str, tokens, true);
					label = info.addPara(str, opad);
					label.setHighlight(govtName, rebName);
					label.setHighlightColors(govtColor, rebColor);
				}
				if (rebelTradePoints > 0) {
					str = getString("intelDescTradeRebels");
					str = StringHelper.substituteTokens(str, tokens, true);
					label = info.addPara(str, opad);
					label.setHighlight(rebName, govtName);
					label.setHighlightColors(rebColor, govtColor);
				}

				bullet(info);
				CoreReputationPlugin.addAdjustmentMessage(repResultGovt.delta, govtFaction, null,
						null, null, info, Misc.getTextColor(), false, opad);
				CoreReputationPlugin.addAdjustmentMessage(repResultRebel.delta, rebelFaction, null,
						null, null, info, Misc.getTextColor(), false, pad);
				unindent(info);
			}
			return;
		}
	}

	protected void printSidesInfo(TooltipMakerAPI info, List<Pair<String, String>> sub, float opad) {
		float pad = 3;
		Color hl = Misc.getHighlightColor();
		Color rebColor = rebelFaction.getBaseUIColor();

		int knownLevel = getDetailLevel();
		boolean knowFaction = knownLevel >= DETAIL_LEVEL_TO_KNOW_FACTION;
		String str;

		// rebellion details, if ongoing
		info.addSectionHeading(getString("intelHeaderDetails"), Alignment.MID, opad);
		if (knownLevel < 5) {
			str = getString("intelDescIncompleteDetails");
			str = StringHelper.substituteTokens(str, sub);
			info.addPara(str, opad);

			bullet(info);
			str = getString("intelDescIncompleteDetails2");
			info.addPara(str, pad, hl, DETAIL_LEVEL_TO_KNOW_FACTION + "",
					DETAIL_LEVEL_FOR_STRENGTH_COLORS + "",
					DETAIL_LEVEL_FOR_STRENGTH_VALUES + "");
			if (rebelFaction.isPlayerFaction() || rebelFaction == Misc.getCommissionFaction())
			{
				str = getString("intelDescIncompleteDetails3");
				info.addPara(str, pad, hl, DETAIL_LEVEL_TO_KNOW_FACTION + "");
			}
			unindent(info);
		}
		if (knownLevel > 0) {
			str = getString("intelDescDetailFaction");
			if (knowFaction) {
				str = StringHelper.substituteTokens(str, sub);
				info.addPara(str, opad, rebColor, rebelFaction.getDisplayNameWithArticleWithoutArticle());
			}
			else {
				String unk = getString("intelDescDetailFactionUnknown");
				str = StringHelper.substituteToken(str, "$theRebelFaction", unk);
				info.addPara(str, opad, hl, unk);
			}

			String govtStr = "???";
			String rebStr = "???";
			if (knownLevel >= DETAIL_LEVEL_FOR_STRENGTH_VALUES) {
				govtStr = String.format("%.0f", govtStrength);
				rebStr = String.format("%.0f", rebelStrength);
			}

			Color govtStrColor = hl, rebStrColor = hl;
			if (knownLevel >= DETAIL_LEVEL_FOR_STRENGTH_COLORS)
			{
				if (govtStrength > rebelStrength * 1.25f) {
					govtStrColor = Misc.getPositiveHighlightColor();
					rebStrColor = Misc.getNegativeHighlightColor();
				}
				else if (rebelStrength > govtStrength * 1.25f) {
					govtStrColor = Misc.getNegativeHighlightColor();
					rebStrColor = Misc.getPositiveHighlightColor();
				}
			}

			str = getString("intelDescDetailGovtStrength");
			info.addPara(str, opad, govtStrColor, govtStr);
			str = getString("intelDescDetailRebelStrength");
			info.addPara(str, opad, rebStrColor, rebStr);
		}

		str = getString("intelDescDetailStability");
		info.addPara(str, opad, hl, stabilityPenalty + "");

	}

	protected void printSupportFleetInfo(TooltipMakerAPI info, SupportFleetData data, float opad) {
		Color hl = Misc.getHighlightColor();
		FactionAPI sender;
		String str;
		LabelAPI label;

		if (data.source != null) sender = data.source.getFaction();
		else if (data.fleet != null) sender = data.fleet.getFaction();
		else sender = data.forGovernment ? govtFaction : rebelFaction;

		info.addSectionHeading(getString("intelHeaderFleet" + (data.forGovernment ? "" : "Alt")),
				sender.getBaseUIColor(), sender.getDarkUIColor(), Alignment.MID, opad);

		List<Pair<String, String>> subFleet = new ArrayList<>();
		subFleet.add(new Pair<>("$senderFaction", sender.getDisplayName()));
		subFleet.add(new Pair<>("$theSenderFaction", sender.getDisplayNameWithArticle()));
		subFleet.add(new Pair<>("$senderIsOrAre", sender.getDisplayNameIsOrAre()));
		if (data.source != null) {
			subFleet.add(new Pair<>("$sourceOnOrAt", data.source.getOnOrAt()));
			subFleet.add(new Pair<>("$sourceMarket", data.source.getName()));
		}
		subFleet.add(new Pair<>("$onOrAt", market.getOnOrAt()));
		subFleet.add(new Pair<>("$market", market.getName()));

		if (data.success != null) {		// fleet arrived or defeated
			String key = Boolean.TRUE.equals(data.success) ? "intelDescFleetArrived" : "intelDescFleetDefeated";
			if (!data.forGovernment) key = key + "Alt";
			str = getString(key);
			str = StringHelper.substituteTokens(str, subFleet);
			label = info.addPara(str, opad);
			label.setHighlight(sender.getDisplayNameWithArticleWithoutArticle());
			label.setHighlightColors(sender.getBaseUIColor());
		}
		else if (data.sentWarning || data.fleet != null) {	// fleet in prep or en route
			String key = data.sentWarning ? "intelDescFleetPrep" : "intelDescFleetLaunched";
			if (!data.forGovernment) key = key + "Alt";
			str = getString(key);
			str = StringHelper.substituteTokens(str, subFleet);
			label = info.addPara(str, opad);
			if (data.sentWarning) {
				label.setHighlight(sender.getDisplayNameWithArticleWithoutArticle(), data.source.getName());
				label.setHighlightColors(sender.getBaseUIColor(), hl);
			} else {
				label.setHighlight(data.source.getName());
				label.setHighlightColors(sender.getBaseUIColor());
			}
		}

		if (ExerelinModPlugin.isNexDev) {
			info.addPara(String.format("ETA to next fleet launch: %.0f", data.forGovernment ? this.suppressionFleetCountdown : this.smugglerCountdown), opad);
		}

		info.addPara(Misc.getAgoStringForTimestamp(data.updateTimestamp) + ".", opad);
	}

	protected void printIndustryDisruptInfo(TooltipMakerAPI info, float opad) {
		Color hl = Misc.getHighlightColor();
		String str;

		info.addSectionHeading(getString("intelHeaderDisrupt"), Alignment.MID, opad);
		if (disruptTime > 0) {
			str = getString("intelDescDisruptSuccess");
			str = StringHelper.substituteToken(str, "$market", market.getName());
			info.addPara(str, opad, hl, lastIndustryDisrupted.getCurrentName(),
					Math.round(disruptTime) + "");
		}
		else {
			str = getString("intelDescDisruptFailure");
			str = StringHelper.substituteToken(str, "$market", market.getName());
			info.addPara(str, opad, hl, lastIndustryDisrupted.getCurrentName());
		}
		if (disruptionTimestamp != null)
			info.addPara(Misc.getAgoStringForTimestamp(disruptionTimestamp) + ".", opad);
	}
	
	// sidebar text description
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		int knownLevel = getDetailLevel();
		boolean knowFaction = knownLevel >= DETAIL_LEVEL_TO_KNOW_FACTION;
		
		info.addImages(width, 128, opad, opad, 
				knowFaction ? rebelFaction.getCrest() : Global.getSector().getFaction(Factions.NEUTRAL).getCrest(),
				govtFaction.getCrest());
		
		List<Pair<String, String>> sub = new ArrayList<>();
		sub.add(new Pair<>("$govtFaction", govtFaction.getDisplayName()));
		sub.add(new Pair<>("$theGovtFaction", govtFaction.getDisplayNameWithArticle()));
		sub.add(new Pair<>("$rebelFaction", rebelFaction.getDisplayName()));
		sub.add(new Pair<>("$theRebelFaction", rebelFaction.getDisplayNameWithArticle()));
		sub.add(new Pair<>("$onOrAt", market.getOnOrAt()));
		sub.add(new Pair<>("$market", market.getName()));

		printBasicInfo(info, sub, opad);
		if (isEnding() || isEnded()) return;

		printSidesInfo(info, sub, opad);
		
		// Details on suppression fleet, if present
		if (suppressionData.updateTimestamp != null) {
			printSupportFleetInfo(info, suppressionData, opad);
		}
		if (smugglerData.updateTimestamp != null) {
			printSupportFleetInfo(info, smugglerData, opad);
		}
		
		// Industry disruption
		if (lastIndustryDisrupted != null) {
			printIndustryDisruptInfo(info, opad);
		}

		// request suppression fleet
		if (!suppressionData.isActive() && (market.isPlayerOwned() || market.getFaction().isPlayerFaction() || AllianceManager.areFactionsAllied(PlayerFactionStore.getPlayerFactionId(), market.getFactionId())))
		{
			ButtonAPI button = info.addButton(StringHelper.getString("nex_rebellion", "intelButtonRequestSuppr"), BUTTON_AUTO_SUPPRESS_FLEET,
					getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
					(int)(width), 20f, opad);
			SupportFleetData temp = new SupportFleetData(true);
			if (pickFleetSource(temp) == null) {
				button.setEnabled(false);
				//info.addTooltipToPrevious();
			}
		}
		
		// Devmode: fast resolve action
		if ((Global.getSettings().isDevMode() || ExerelinModPlugin.isNexDev) && !isEnding() && !isEnded()) {
			info.addButton("Fast resolve", "fastResolve",
					getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
					(int)(width), 20f, opad * 3f);
			info.addButton("Spawn suppression fleet", "spawnSuppressionFleet",
					getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
					(int)(width), 20f, opad);
			info.addButton("Spawn smuggler fleet", "spawnSmugglerFleet",
					getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
					(int)(width), 20f, opad);
		}
	}

	protected void addSuppresionFleetButton(TooltipMakerAPI info, float width, float pad) {
		float reqStrength = 0;
		String btnStr = String.format(StringHelper.getString("exerelin_invasion", "intelButtonRequestDef"), (int)reqStrength, (int)InvasionFleetManager.getManager().getFleetRequestStock());
		FactionAPI faction = market.getFaction();
		info.addButton(btnStr, BUTTON_AUTO_SUPPRESS_FLEET, faction.getBaseUIColor(), faction.getDarkUIColor(), width, 24, pad);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		switch ((String)buttonId) {
			case "fastResolve":
				fastResolve();
				break;
			case "spawnSuppressionFleet":
				prepFleet(true);
				spawnFleet(suppressionData);
				break;
			case "spawnSmugglerFleet":
				prepFleet(false);
				spawnFleet(smugglerData);
				break;
			case BUTTON_AUTO_SUPPRESS_FLEET:
				instaCreateSuppressionFleet(true);
				break;
		}
		
		ui.updateUIForItem(this);
	}

	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		if (buttonId == BUTTON_AUTO_SUPPRESS_FLEET) {
			Color hl = Misc.getHighlightColor();
			Color bad = Misc.getNegativeHighlightColor();

			int reqFP = getWantedBaseFP();
			int reqStrength = getWantedPayloadStrength(false, reqFP);
			float cost = reqFP * NexConfig.fleetRequestCostPerFP;
			cost += getPayloadCreditCost(reqStrength);

			float currCreds = Global.getSector().getPlayerFleet().getCargo().getCredits().get();

			boolean enoughCreds = currCreds >= cost;
			String str = StringHelper.getString("nex_rebellion", "intelButtonRequestSupprConfirm");
			LabelAPI label = prompt.addPara(str,0f, hl, Misc.getDGSCredits(cost), Misc.getDGSCredits(currCreds));
			label.setHighlightColors(hl, enoughCreds ? hl : bad);

			return;
		}

		super.createConfirmationPrompt(buttonId, prompt);
	}

	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		if (buttonId == BUTTON_AUTO_SUPPRESS_FLEET) return true;
		return super.doesButtonHaveConfirmDialog(buttonId);
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(govtFaction.getId());
		if (getDetailLevel() > DETAIL_LEVEL_TO_KNOW_FACTION) {
			tags.add(rebelFaction.getId());
		}
		if (lastUpdate == UpdateParam.FLEET_PREP || lastUpdate == UpdateParam.FLEET_SPAWNED)
		{
			tags.add(Tags.INTEL_FLEET_DEPARTURES);
		}
		if (govtFaction.isPlayerFaction() || govtFaction == Misc.getCommissionFaction())
		{
			tags.add(Tags.INTEL_COLONIES);
		}
		tags.add(getString("intelTag"));
		return tags;
	}
		
	@Override
	public String getIcon() {
		return Global.getSettings().getSpriteName("intel", "nex_rebellion");
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return market.getPrimaryEntity();
	}
	
	@Override
	public List<ArrowData> getArrowData(SectorMapAPI map) {
		List<ArrowData> result = new ArrayList<ArrowData>();
		if (suppressionData.source != null) {
			ArrowData arrow = new ArrowData(suppressionData.source.getPrimaryEntity(), market.getPrimaryEntity());
			arrow.color = suppressionData.source.getFaction().getBaseUIColor();
			result.add(arrow);
		}
		if (smugglerData.source != null) {
			ArrowData arrow = new ArrowData(smugglerData.source.getPrimaryEntity(), market.getPrimaryEntity());
			arrow.color = smugglerData.source.getFaction().getBaseUIColor();
			result.add(arrow);
		}

		return result;
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return govtFaction;
	}
	
	protected String getName() {
		String str = getString("intelTitle");
		str = StringHelper.substituteToken(str, "$market", market.getName());
		if (result == RebellionResult.REBEL_VICTORY)
			str += " - " + StringHelper.getString("successful", true);
		else if (result == RebellionResult.GOVERNMENT_VICTORY)
			str += " - " + StringHelper.getString("failed", true);
		else if (isEnding() || isEnded())
			str += " - " + StringHelper.getString("over", true);
		return str;
	}
	
	public static String getString(String id)
	{
		return StringHelper.getString("nex_rebellion", id);
	}
	
	protected void debugMessage(String message)
	{
		log.info(message);
		//Global.getSector().getCampaignUI().addMessage(message);
	}
	
	//runcode exerelin.campaign.intel.rebellion.RebellionIntel.startDebugEvent("corvus_IIIa")
	public static void startDebugEvent(String id)
	{
		SectorEntityToken target = Global.getSector().getEntityById(id);
		if (target != null)
		{
			/*
			InstigateRebellion rebel = new InstigateRebellion(target.getMarket(), 
					Global.getSector().getFaction(Factions.TRITACHYON), target.getFaction(), false, null);
			rebel.setResult(CovertOpsManager.CovertActionResult.SUCCESS_DETECTED);
			rebel.onSuccess();
			*/
			RebellionCreator.getInstance().createRebellion(target.getMarket(), false);
		}
	}

	// runcode exerelin.campaign.intel.rebellion.RebellionIntel.resetWarnings();
	public static void resetWarnings() {
		for (IntelInfoPlugin plugin : Global.getSector().getIntelManager().getIntel(RebellionIntel.class)) {
			RebellionIntel rebel = (RebellionIntel)plugin;
			rebel.smugglerData.sentWarning = false;
			rebel.suppressionData.sentWarning = false;
		}
	}
	
	public static RebellionIntel getOngoingEvent(MarketAPI market)
	{
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(RebellionIntel.class))
		{
			RebellionIntel reb = (RebellionIntel)intel;
			if (!reb.isEnding() && !reb.isEnded() && reb.market == market) return reb;
		}
		return null;
	}
	
	public static boolean isOngoing(MarketAPI market)
	{
		return getOngoingEvent(market) != null;
	}

	public static class SupportFleetData {
		public RebellionIntel intel;
		public CampaignFleetAPI fleet;
		public boolean forGovernment;
		public MarketAPI source;
		public MarketAPI target;
		public float startingFleetPoints;
		public boolean indie;
		public boolean sentWarning;
		public boolean launched;
		public Boolean success;
		public Long updateTimestamp;

		public SupportFleetData(boolean forGovernment) {
			this.forGovernment = forGovernment;
		}

		protected boolean isActive() {
			return launched && success == null;
		}

		public void timestampNow() {
			updateTimestamp = Global.getSector().getClock().getTimestamp();
		}
	}
	
	protected enum UpdateParam {
		PREP, START,
		FLEET_PREP, FLEET_SPAWNED, FLEET_ARRIVED, FLEET_DEFEATED,
		SMUGGLER_PREP, SMUGGLER_SPAWNED, SMUGGLER_ARRIVED, SMUGGLER_DEFEATED,
		INDUSTRY_DISRUPTED, INDUSTRY_DISRUPT_FAIL, END
	}
	
	public enum RebellionResult {
		GOVERNMENT_VICTORY, REBEL_VICTORY, MUTUAL_ANNIHILATION, TIME_EXPIRED, LIBERATED, DECIVILIZED, OTHER
	}
}
