package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_StabilizePackage;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.impl.campaign.rulecmd.Nex_StabilizePackage.MEMORY_KEY_RECENT;
import static com.fs.starfarer.api.impl.campaign.rulecmd.Nex_StabilizePackage.STABILIZE_INTERVAL;

public class ReliefFleetIntelAlt extends BaseIntelPlugin {
	
	public static final Object LAUNCHED_UPDATE = new Object();
	public static final Object ENDED_UPDATE = new Object();
	
	public static Logger log = Global.getLogger(ReliefFleetIntelAlt.class);
	
	protected String factionId;
	protected String targetFactionId;	// for displaying relationship change, in case market changes hands after delivery
	protected MarketAPI source;
	protected MarketAPI target;
	protected EndReason endReason;
	protected int cargoSize;
	@Getter	@Setter	protected int playerFee;
	protected boolean assembling = true;
	protected float daysToLaunch;
	protected final float daysToLaunchFixed;
	protected CampaignFleetAPI fleet;
	protected int unrestReduction;
	protected float relation;
	protected ExerelinReputationAdjustmentResult repResult;
	
	public ReliefFleetIntelAlt(MarketAPI source, MarketAPI target) {
		this.source = source;
		this.target = target;
		factionId = source.getFactionId();
		targetFactionId = target.getFactionId();
		
		int food = Nex_StabilizePackage.getNeededCommodityAmount(target, Commodities.FOOD);
		int supplies = Nex_StabilizePackage.getNeededCommodityAmount(target, Commodities.SUPPLIES);
		int guns = Nex_StabilizePackage.getNeededCommodityAmount(target, Commodities.HAND_WEAPONS);
		
		cargoSize = food + supplies + guns;
		log.info("Relief cargo size: " + cargoSize);
		
		daysToLaunch = 7 + (cargoSize/100);
		daysToLaunchFixed = daysToLaunch;
	}
	
	protected FactionAPI getFaction()
	{
		return Global.getSector().getFaction(factionId);
	}
	
	protected FactionAPI getTargetFaction()
	{
		if (target.isInEconomy()) return target.getFaction();
		return Global.getSector().getFaction(targetFactionId);
	}
	
	protected String getString(String id)
	{
		return StringHelper.getString("nex_reliefFleet", id);
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	protected String getName() {
		String str = getString("intelTitle");
		str = StringHelper.substituteToken(str, "$market", target.getName());
		if (endReason == EndReason.COMPLETE)
			str += " - " + StringHelper.getString("successful", true);
		else if (endReason == EndReason.OTHER)
			str += " - " + StringHelper.getString("over", true);
		else if (endReason != null)
			str += " - " + StringHelper.getString("failed", true);
		return str;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		FactionAPI faction = getFaction();
		FactionAPI targetFaction = getTargetFaction();
		boolean over = isEnding() || isEnded();
		
		float pad = 0;
		String name = Misc.ucFirst(faction.getDisplayName());
		info.addPara(name, initPad, tc, faction.getBaseUIColor(), name);
		if (faction != targetFaction) {
			name = Misc.ucFirst(targetFaction.getDisplayName());
			info.addPara(name, pad, tc, targetFaction.getBaseUIColor(), name);
		}
				
		String key = "intelBullet";
		Map<String, String> sub = new HashMap<>();
		
		if (over)
		{
			switch (endReason)
			{
				case FAILED_TO_SPAWN:
					key += "FailedToSpawn";
					break;
				case TARGET_DESTROYED:
					key += "TargetDestroyed";
					break;
				case HOSTILE:
					key += "Hostile";
					break;
				case DEFEATED:
					key += "Defeated";
					break;
				case COMPLETE:
					key += "Success";
					break;
			}
			addBullet(info, key, sub, pad, tc);
		}
		else if (listInfoParam == LAUNCHED_UPDATE)
		{
			key += "Launched";
			sub.put("$market", source.getName());
			LabelAPI label = addBullet(info, key, sub, pad, tc, source.getName());
			label.setHighlight(source.getName());
			label.setHighlightColors(getFaction().getBaseUIColor());
		}
		else if (assembling)
		{
			key += "Assembling";
			String dtl = getDays(daysToLaunch) + " " + getDaysString(daysToLaunch);
			sub.put("$days", dtl);
			sub.put("$market", source.getName());
			LabelAPI label = addBullet(info, key, sub, pad, tc, source.getName(), getDays(daysToLaunch));
			label.setHighlight(source.getName(), getDays(daysToLaunch));
			label.setHighlightColors(getFaction().getBaseUIColor(), Misc.getHighlightColor());
		}
		
		// show ETA
		if (!over) {
			int eta = getETA();
			if (eta >= 1) {
				key = "intelBulletETA";
				sub.put("$days", eta + " " + getDaysString(eta));
				addBullet(info, key, sub, pad, tc, eta + "");
			}
		}
	}
	
	protected LabelAPI addBullet(TooltipMakerAPI info, String key, Map<String, String> sub, float pad, Color color, String... highlights)
	{
		String str = getString(key);
		str = StringHelper.substituteTokens(str, sub);
		
		return info.addPara(str, pad, color, Misc.getHighlightColor(), highlights);
	}
	
	// sidebar text description
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		FactionAPI faction = getFaction();
		FactionAPI otherFaction = getTargetFaction();
		Color hl = Misc.getHighlightColor();
		Map<String, String> sub = new HashMap<>();
		
		info.addImages(width, 128, opad, opad, faction.getCrest(), otherFaction.getCrest());
		
		String str = getString("intelDesc");
		sub.put("$theFaction", faction.getDisplayNameWithArticle());
		sub.put("$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
		sub.put("$theOtherFaction", otherFaction.getDisplayNameWithArticle());
		sub.put("$TheOtherFaction", Misc.ucFirst(otherFaction.getDisplayNameWithArticle()));
		sub.put("$isOrAre", faction.getDisplayNameIsOrAre());
		sub.put("$market", target.getName());
		sub.put("$location", target.getContainingLocation().getNameWithLowercaseType());
		str = StringHelper.substituteTokens(str, sub);
		
		LabelAPI para = info.addPara(str, opad);
		para.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(), target.getName(), 
				otherFaction.getDisplayNameWithArticleWithoutArticle());
		para.setHighlightColors(faction.getBaseUIColor(), hl, otherFaction.getBaseUIColor());
		
		info.addSectionHeading(StringHelper.getString("status", true), 
				faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);
		
		String key = "intelStatus";
		
		List<String> highlights = new ArrayList<>();
		
		if (isEnding() || isEnded())
		{
			switch (endReason)
			{
				case FAILED_TO_SPAWN:
					key += "FailedToSpawn";
					break;
				case DEFEATED:
					key += "Defeated";
					break;
				case TARGET_DESTROYED:
					key += "TargetDestroyed";
					break;
				case HOSTILE:
					key += "Hostile";
					hl = faction.getBaseUIColor();
					highlights.add(faction.getDisplayNameWithArticleWithoutArticle());
					break;
				case COMPLETE:
					key += "Success";
					break;
				case OTHER:
					key += "Unknown";
					break;
			}
		}
		else if (assembling)
		{
			key += "Assembling";
			sub.put("$days", getDays(daysToLaunch) + " " + getDaysString(daysToLaunch));
			
			sub.put("$market", source.getName());
			highlights.add(source.getName());
			
			highlights.add(getDays(daysToLaunch));
		}
		else
		{
			key += "Travelling";
		}
		str = getString(key);
		str = StringHelper.substituteTokens(str, sub);
		
		info.addPara(str, opad, hl, highlights.toArray(new String[0]));
		
		if (endReason == EndReason.COMPLETE) {
			str = getString("intelStatusUnrestReduction");
			str = StringHelper.substituteToken(str, "$market", target.getName());
			info.addPara(str, opad, hl, unrestReduction + "");
			
			if (repResult != null && repResult.delta != 0) {
				DiplomacyIntel.addRelationshipChangePara(info, factionId, targetFactionId, 
						relation, repResult, opad);
			}
		}
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(factionId);
		tags.add(target.getFactionId());
		tags.add(Tags.INTEL_FLEET_DEPARTURES);
		return tags;
	}
	
	@Override
	public String getIcon() {
		//return getFaction().getCrest();
		if (target.getSize() > 5)
			return Global.getSettings().getSpriteName("intel", "tradeFleet_valuable");
		return Global.getSettings().getSpriteName("intel", "tradeFleet_other");
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return getFaction();
	}

	@Override
	protected float getBaseDaysAfterEnd() {
		return 7;
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return target.getPrimaryEntity();
	}
	
	@Override
	public List<ArrowData> getArrowData(SectorMapAPI map) {
		List<ArrowData> result = new ArrayList<ArrowData>();
		ArrowData arrow = new ArrowData(source.getPrimaryEntity(), target.getPrimaryEntity());
		arrow.color = getFaction().getColor();
		result.add(arrow);

		return result;
	}
	
	@Override
	public void advanceImpl(float amount) {
		if (isEnding()) return;
		
		checkAbort();
		
		if (isEnding()) return;
		
		if (assembling) {
			daysToLaunch -= Global.getSector().getClock().convertToDays(amount);
			if (daysToLaunch < 0)
			{
				assembling = false;
				fleet = spawnFleet();
				if (fleet != null) {
					//sendUpdateIfPlayerHasIntel(LAUNCHED_UPDATE, true);
				}
				else
					endEvent(EndReason.FAILED_TO_SPAWN);
			}
			return;
		}

		if (!fleet.isAlive()) {
			endEvent(EndReason.DEFEATED);
			return;
		}
		
		targetFactionId = target.getFactionId();
	}

	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		refundPlayerFeeIfNeeded();
	}

	public void init() {
		int nexIntelQueued = NexConfig.nexIntelQueued;
		switch (nexIntelQueued) {

			case 0:
				Global.getSector().getIntelManager().addIntel(this);
				break;

			case 1:
				if 	(factionId.equals(Factions.PLAYER)
							||targetFactionId.equals(Factions.PLAYER)
							||targetFactionId.equals(PlayerFactionStore.getPlayerFactionId())
							||factionId.equals(PlayerFactionStore.getPlayerFactionId()))
					{
						Global.getSector().getIntelManager().addIntel(this);
					}
				else Global.getSector().getIntelManager().queueIntel(this);
				break;
				
			case 2:
				Global.getSector().getIntelManager().queueIntel(this);
				break;
				
			default:
				Global.getSector().getIntelManager().addIntel(this);
				Global.getSector().getCampaignUI().addMessage("Switch statement within init(), in ReliefFleetIntel, " +
						"defaulted. This is not supposed to happen. If your nexIntelQueued setting within ExerelinConfig " +
						"is below 0 or above 2, that is the likely cause. Otherwise, please contact the mod author!");
		}
		Global.getSector().addScript(this);
	}
	
	public void checkAbort() 
	{
		if (assembling) {
			if (!source.getFactionId().equals(factionId))
				endEvent(EndReason.FAILED_TO_SPAWN);
			else if (!source.isInEconomy() || !source.getPrimaryEntity().isAlive())
				endEvent(EndReason.FAILED_TO_SPAWN);
		}
		if (getFaction().isHostileTo(getTargetFaction()))
			endEvent(EndReason.HOSTILE);
		else if (!target.isInEconomy())
			endEvent(EndReason.TARGET_DESTROYED);
	}

	protected void refundPlayerFeeIfNeeded() {
		if (playerFee <= 0) return;
		if (endReason != null && !endReason.isCancelled()) return;

		float refundMult = 0.75f;
		if (assembling) refundMult = 1;

		if (refundMult <= 0) return;
		int refund = Math.round(playerFee * refundMult);
		log.info(String.format("Refunding %s credits for action cancellation (mult %.1f)", refund, refundMult));
		Global.getSector().getPlayerFleet().getCargo().getCredits().add(refund);
	}
	
	/**
	 * Used only to generate an estimate of the fleet's FP for external code. 
	 * Should be a copy of the code from <code>spawnFleet</code>.
	 * @return
	 */
	public int calcFP() {
		float dist = NexUtilsMarket.getHyperspaceDistance(source, target);
		int freighter = (int)Math.max(Math.ceil(cargoSize/60f) * 2, 5);
		int combat = 5 + freighter * 2;
		if (target.hasCondition(Conditions.PIRATE_ACTIVITY)) {
			combat = Math.min(combat, 30);
		}
		int utility = freighter/4;
		int total = freighter + combat + utility;
		int tanker = Math.round(total * InvasionFleetManager.TANKER_FP_PER_FLEET_FP_PER_10K_DIST * dist/10000);
		total += tanker;
		
		return total;
	}
	
	protected CampaignFleetAPI spawnFleet()
	{
		float dist = NexUtilsMarket.getHyperspaceDistance(source, target);
		
		// Buffalo is 5 FP for 300 cargo, or 60 cargo/FP
		int freighter = (int)Math.max(Math.ceil(cargoSize/60f) * 2, 5);
		int combat = 5 + freighter * 2;
		if (target.hasCondition(Conditions.PIRATE_ACTIVITY)) {
			combat = Math.min(combat, 30);
		}
		int utility = freighter/4;
		int total = freighter + combat + utility;
		int tanker = Math.round(total * InvasionFleetManager.TANKER_FP_PER_FLEET_FP_PER_10K_DIST * dist/10000);
		total += tanker;
				
		FleetParamsV3 params = new FleetParamsV3(source, // source
												"nex_reliefFleet",
												combat, // combatPts
												freighter, // freighterPts
												tanker, // tankerPts
												0f, // transportPts
												0f, // linerPts
												utility, // utilityPts
												0 // qualityMod
												);
		
		params.ignoreMarketFleetSizeMult = true;	// only use doctrine size, not source source size
		params.modeOverride = ShipPickMode.PRIORITY_THEN_ALL;
		fleet = NexUtilsFleet.customCreateFleet(getFaction(), params);

		if (fleet == null)
			return null;
		
		fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
		
		source.getPrimaryEntity().getContainingLocation().addEntity(fleet);
		fleet.setLocation(source.getPrimaryEntity().getLocation().x, source.getPrimaryEntity().getLocation().y);
		
		ReliefFleetAI script = new ReliefFleetAI(fleet, this);
		fleet.addScript(script);
		script.giveInitialAssignment();
		
		// add cargo
		int food = Nex_StabilizePackage.getNeededCommodityAmount(target, Commodities.FOOD);
		int supplies = Nex_StabilizePackage.getNeededCommodityAmount(target, Commodities.SUPPLIES);
		int guns = Nex_StabilizePackage.getNeededCommodityAmount(target, Commodities.HAND_WEAPONS);
		fleet.getCargo().addCommodity(Commodities.FOOD, food);
		fleet.getCargo().addCommodity(Commodities.SUPPLIES, supplies);
		fleet.getCargo().addCommodity(Commodities.HAND_WEAPONS, guns);
		
		return fleet;
	}
	
	protected void endEvent(EndReason reason) {
		endReason = reason;
		endAfterDelay();
		sendUpdateIfPlayerHasIntel(ENDED_UPDATE, false);
	}
	
	protected void deliver() 
	{
		RecentUnrest ru = RecentUnrest.get(target);
		if (ru != null) {
			int before = ru.getPenalty();
			ru.add(-Math.min(before, Nex_StabilizePackage.getStabilizePackageEffect(target)), 
					StringHelper.getString("exerelin_markets", "stabilizeRecentUnrestEntry"));
			unrestReduction = before - ru.getPenalty();
		}
		
		if (getFaction() != getTargetFaction() && unrestReduction > 0) {
			float rep = Nex_StabilizePackage.getReputation(target);
			repResult = DiplomacyManager.adjustRelations(getFaction(), getTargetFaction(), rep, null, null, null);
			relation = getFaction().getRelationship(target.getFactionId());
			
			DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(target.getFactionId());
			if (brain != null) brain.reportDiplomacyEvent(factionId, repResult.delta);
			brain = DiplomacyManager.getManager().getDiplomacyBrain(factionId);
			if (brain != null) brain.reportDiplomacyEvent(target.getFactionId(), repResult.delta);
		}
		
		fleet.getCargo().clear();
		target.getMemoryWithoutUpdate().set(MEMORY_KEY_RECENT, true, STABILIZE_INTERVAL);
		
		
		
		endEvent(EndReason.COMPLETE);
	}
	
	protected int getETA() {
		float eta = daysToLaunch;
		float distHyper = fleet != null 
				? Misc.getDistanceLY(fleet.getLocationInHyperspace(), target.getLocationInHyperspace()) 
				: Misc.getDistanceLY(source.getLocationInHyperspace(), target.getLocationInHyperspace());
		eta += distHyper/2;
		
		return Math.round(eta);
	}
	
	public static ReliefFleetIntelAlt createEvent(MarketAPI source, MarketAPI dest) 
	{
		ReliefFleetIntelAlt intel = new ReliefFleetIntelAlt(source, dest);
		intel.init();
		return intel;
	}
	
	public enum EndReason {
		FAILED_TO_SPAWN, DEFEATED, TARGET_DESTROYED, HOSTILE, COMPLETE, OTHER;

		public boolean isCancelled() {
			return this == TARGET_DESTROYED || this == HOSTILE || this == OTHER;
		}
	}
}