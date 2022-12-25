package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.MissionCompletionRep;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.missions.DisruptMissionManager.TargetEntry;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;

@Deprecated
public class DisruptMissionIntel extends BaseMissionIntel implements ColonyPlayerHostileActListener {
	
	public static final RepLevel MAX_REP_LEVEL = RepLevel.FAVORABLE;
	public static final int MIN_DISRUPT_TIME = 60;
	
	protected MarketAPI market;
	protected FactionAPI faction;
	protected Industry industry;
	protected String commodityId;
	protected TargetReason reason;
	@Deprecated protected float value;	// meh
	protected int reward;
	protected CancelReason cancelReason = null;
	//protected IntervalUtil interval = new IntervalUtil(1, 1);
	
	public DisruptMissionIntel(TargetEntry entry, FactionAPI faction, float duration) {
		Global.getLogger(this.getClass()).info("Instantiating disruption mission");
		
		this.faction = faction;
		market = entry.market;
		industry = entry.industry;
		commodityId = entry.commodityId;
		reason = entry.reason;
		value = entry.value;
		this.duration = duration;
	}

/*	public void LocationOfDisruptMarket() {
		Global.getSector().getCampaignUI().addMessage("Location of disruption mission is " + market.getContainingLocation().getNameWithLowercaseType());
	} */

	public void init() {
		Global.getLogger(this.getClass()).info("Initiating disruption mission");
		reward = calculateReward();
		initRandomCancel();
		setPostingLocation(market.getPrimaryEntity());
		setPostingRangeLY(9999999999f);
		boolean queuedNexMissions = NexConfig.queuedNexMissions;

		if (queuedNexMissions) {
			Global.getSector().getIntelManager().queueIntel(this);
		}
		else {
			Global.getSector().getIntelManager().addIntel(this);
		}
		Global.getSector().getListenerManager().addListener(this);
	}

	@Override
	public void endMission() {
		Global.getSector().getListenerManager().removeListener(this);
		endAfterDelay();
	}
	
	public void endMissionWithUpdate(boolean onlyIfImportant) {
		if (isEnding() || isEnded()) return;
		endMission();
		sendUpdateIfPlayerHasIntel(missionResult, onlyIfImportant);
	}
	
	// not advanceMission because that doesn't advance when mission is in posted state
	@Override
	public void advanceImpl(float amount) {		
		checkMarketState();
		if (!isEnding() && !isEnded())
			super.advanceImpl(amount);
	}

	@Override
	public void advanceMission(float amount) {
		
	}
	
	public void checkMarketState() {
		if (isEnding() || isEnded()) return;
		
		if (this.isAccepted() && industry.getDisruptedDays() >= MIN_DISRUPT_TIME) {
			missionComplete();
			return;
		}
		
		// count as disrupted if issuing faction owns the market
		if (market.getFaction() == faction) {
			missionComplete();
			return;
		}
		
		// market no longer in economy?
		if (!market.isInEconomy()) {
			cancelReason = CancelReason.NOT_IN_ECONOMY;
		}
		// industry no longer exists?
		else if (industry.getMarket() == null || !market.hasIndustry(industry.getId()) || industry.isHidden())
		{
			// count as disrupted if we hold the market
			if (market.getFaction().isPlayerFaction()) {
				missionComplete();
				return;
			}
			cancelReason = CancelReason.INDUSTRY_REMOVED;
		}
		// industry disrupted?
		else if (industry.getDisruptedDays() >= MIN_DISRUPT_TIME && !isAccepted()) {
			cancelReason = CancelReason.ALREADY_DISRUPTED;
		}
		// check reputation to see if mission should continue
		else {
			if (!DisruptMissionManager.isRepLowEnough(faction, market.getFaction(), reason))
				cancelReason = CancelReason.NO_LONGER_HOSTILE;
		}
		
		if (cancelReason != null) {
			missionCancelled();
			endMissionWithUpdate(true);
		}
	}
	
	protected void missionCancelled() {
		setMissionState(MissionState.CANCELLED);
		setMissionResult(createAbandonedResult(false));
	}
	
	protected void missionComplete() {
		float repAmount = 0.01f * market.getSize();
		if (repAmount < 0.01f) repAmount = 0.01f;

		MissionCompletionRep completionRep = new MissionCompletionRep(repAmount, null, -repAmount, RepLevel.INHOSPITABLE);

		ReputationAdjustmentResult repF = Global.getSector().adjustPlayerReputation(
				new RepActionEnvelope(RepActions.MISSION_SUCCESS, completionRep,
									  null, null, true, false),
									  faction.getId());
		Global.getSector().getPlayerFleet().getCargo().getCredits().add(reward);

		setMissionResult(new MissionResult(reward, repF));
		setMissionState(MissionState.COMPLETED);
			
		endMissionWithUpdate(false);
	}
	
	// don't overcomplicate this for now
	protected int calculateReward() {
		int amount = calculateBaseReward(market, industry);
		amount *= MathUtils.getRandomNumberInRange(8, 12)/10f;
		
		// round it
		amount /= 50;
		amount *= 50;
		
		return amount;
	}
	
	public static int calculateBaseReward(MarketAPI market, Industry industry) {
		int reward = (int)(1 + (market.getSize()/2) * Global.getSettings().getFloat("nex_disruptMissionRewardMult"));
		if (industry.getSpec().hasTag(Industries.TAG_MILITARY) 
				|| industry.getSpec().hasTag(Industries.TAG_COMMAND))
			reward *= 2;
		
		float multMod = getDefenderStrengthMult(market) - 1;
		reward *= 1 + (multMod/4);
		
		return reward;
	}
	
	public static float getDefenderStrengthMult(MarketAPI market)
	{
		StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);		
		float mult = defender.getMult();
		//Global.getLogger(DisruptMissionIntel.class).info(market.getName() + " defense mult: " + mult);
		return mult;
	}

	@Override
	public void missionAccepted() {
		Global.getSector().getListenerManager().addListener(this);
	}

	@Override
	protected MissionResult createAbandonedResult(boolean withPenalty) {
		if (withPenalty) {
			float repAmount = 0.01f * market.getSize();
			if (repAmount < 0.01f) repAmount = 0.01f;
			
			MissionCompletionRep completionRep = new MissionCompletionRep(repAmount, null, -repAmount, RepLevel.INHOSPITABLE);
			
			ReputationAdjustmentResult repF = Global.getSector().adjustPlayerReputation(
					new RepActionEnvelope(RepActions.MISSION_FAILURE, completionRep,
										  null, null, true, false),
										  faction.getId());
			
			return new MissionResult(0, repF);
		}
		
		return new MissionResult();
	}

	@Override
	protected MissionResult createTimeRanOutFailedResult() {
		return createAbandonedResult(true);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad)
	{
		Color h = Misc.getHighlightColor();
		
		// not in the small description panel
		if (mode != ListInfoMode.IN_DESC) {
			info.addPara(market.getName(), initPad, tc, market.getFaction().getBaseUIColor(), market.getName());
			initPad = 0f;
			//info.addPara(getString("intelBulletTarget"), initPad, tc, 
			//market.getFaction().getBaseUIColor(), market.getFaction().getDisplayName());
		}
		
		if (isUpdate) {
			// 3 possible updates: de-posted/expired, failed, completed
			if (isCompleted()) {
				if (missionResult.payment > 0) {
					info.addPara(getString("intelBulletReward_received"), initPad, 
							tc, h, Misc.getDGSCredits(missionResult.payment));
				}
				CoreReputationPlugin.addAdjustmentMessage(missionResult.rep1.delta, faction, null, 
														  null, null, info, tc, isUpdate, 0f);
			}
			unindent(info);
			return;
		} else {
			// either in small description, or in tooltip/intel list
			
			if (missionResult != null) {
				if (missionResult.payment > 0) {
					info.addPara(getString("intelBulletReward_received"), initPad, 
							tc, h, Misc.getDGSCredits(missionResult.payment));
					initPad = 0f;
				}
				
				if (missionResult.rep1 != null) {
					CoreReputationPlugin.addAdjustmentMessage(missionResult.rep1.delta, faction, null, 
													  null, null, info, tc, isUpdate, initPad);
					initPad = 0f;
				}
			} else {
				// not in the small description panel
				if (mode != ListInfoMode.IN_DESC) {
					info.addPara(StringHelper.getString("faction", true) + ": " + faction.getDisplayName(), 
							initPad, tc,
							faction.getBaseUIColor(),
							faction.getDisplayName());
					initPad = 0f;
				}
				info.addPara(getString("intelBulletReward"), 
						initPad, tc,
						h,
						Misc.getDGSCredits(reward));
				addDays(info, getString("intelDescDuration_post_short"), duration - elapsedDays, tc, 0);
			}
		}
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		float opad = 10f;
		
		//info.addImage(commodity.getCommodity().getIconName(), width, 80, opad);
		
		FactionAPI targetFaction = market.getFaction();
		
		info.addImages(width, 96, opad, opad * 2f,
					faction.getCrest(),
					industry.getCurrentImage());
			
		
		String str = getString("intelDesc1");
		String marketName = market.getName();
		String industryName = industry.getCurrentName();
		
		
		str = StringHelper.substituteFactionTokens(str, faction);
		str = StringHelper.substituteToken(str, "$onOrAt", market.getOnOrAt());
		str = StringHelper.substituteToken(str, "$size", market.getSize() + "");
		str = StringHelper.substituteToken(str, "$location", market.getContainingLocation().getNameWithLowercaseType());
		
		Map<String, String> sub = new HashMap<>();
		sub.put("$industry", industryName);
		sub.put("$market", marketName);
		sub.put("$theOtherFaction", targetFaction.getDisplayNameWithArticle());
		sub.put("$TheOtherFaction", Misc.ucFirst(targetFaction.getDisplayNameWithArticle()));
		str = StringHelper.substituteTokens(str, sub);
		
		LabelAPI label = info.addPara(str, opad);
		label.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(), industryName,
				marketName, market.getSize() + "", targetFaction.getDisplayNameWithArticleWithoutArticle());
		label.setHighlightColors(faction.getBaseUIColor(), h, h, h, targetFaction.getBaseUIColor());
		
		addBulletPoints(info, ListInfoMode.IN_DESC);
				
		str = getString("intelDescReason");
		String reasonKey = "intelDescReason";
		switch (reason) {
			case ECONOMIC_COMPETITION:
				reasonKey += "Economic";
				sub.put("$commodity", StringHelper.getCommodityName(commodityId).toLowerCase());
				break;
			case MILITARY:
				reasonKey += "Military";
				break;
			case FREE_PORT:
				reasonKey += "FreePort";
				break;
		}
		str += " " + getString(reasonKey);
		str = StringHelper.substituteFactionTokens(str, faction);
		str = StringHelper.substituteTokens(str, sub);
		info.addPara(str, opad);
		
		str = getString("intelDesc3");
		str = StringHelper.substituteToken(str, "$industry", industryName);
		str = StringHelper.substituteToken(str, "$disruptionTime", MIN_DISRUPT_TIME + "");
		info.addPara(str, opad, Misc.getHighlightColor(), MIN_DISRUPT_TIME + "");
		
		if (isPosted() || isAccepted()) {
			addGenericMissionState(info);
			
			addAcceptOrAbandonButton(info, width);
		} else if (cancelReason != null) {
			switch (cancelReason) {
				case NOT_IN_ECONOMY:
					str = getString("intelDescOutcome_noLongerExists");
					str = StringHelper.substituteToken(str, "$market", marketName);
					info.addPara(str, opad, g);
					break;
				case NO_LONGER_HOSTILE:
					str = getString("intelDescOutcome_noLongerHostile");
					str = StringHelper.substituteToken(str, "$market", marketName);
					info.addPara(str, opad, g);
					break;
				case INDUSTRY_REMOVED:
					str = getString("intelDescOutcome_industryRemoved");
					str = StringHelper.substituteToken(str, "$market", marketName);
					str = StringHelper.substituteToken(str, "$onOrAt", market.getOnOrAt());
					info.addPara(str, opad, g);
					break;
				case ALREADY_DISRUPTED:
					str = getString("intelDescOutcome_alreadyDisrupted");
					info.addPara(str, opad, g);
					break;
			}
		} else {
			addGenericMissionState(info);
		}
	}
	
	@Override
	public String getSortString() {
		return StringHelper.getString("disruption", true);
	}
	
	public String getName() {
		String str = getString("intelTitle");
		str = StringHelper.substituteToken(str, "$industry", industry.getCurrentName());
		return str + getPostfixForState();
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return faction;
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	@Override
	public String getIcon() {
		return faction.getCrest();
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MISSIONS);
		tags.add(faction.getId());
		return tags;
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return market.getPrimaryEntity();
	}
	
	protected String getString(String id) {
		return StringHelper.getString("nex_disruptMission", id);
	}

	@Override
	public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {
	}

	@Override
	public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, 
			MarketCMD.TempData actionData, Industry industry) {
		if (market == this.market && industry == this.industry)
			checkMarketState();
	}

	@Override
	public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {
		if (market == this.market)
			checkMarketState();
	}

	@Override
	public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {
		if (market == this.market)
			checkMarketState();
	}
	
	public static enum TargetReason {
		ECONOMIC_COMPETITION, FREE_PORT, MILITARY
	}
	public static enum CancelReason { NOT_IN_ECONOMY, NO_LONGER_HOSTILE, INDUSTRY_REMOVED, ALREADY_DISRUPTED, OTHER }
}
