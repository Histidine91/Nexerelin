package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.MissionCompletionRep;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.missions.DisruptMissionManager.TargetEntry;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;

public class DisruptMissionIntel extends BaseMissionIntel {
	
	public static final RepLevel MAX_REP_LEVEL = RepLevel.FAVORABLE;
	public static final int MIN_DISRUPT_TIME = 60;
	public static final int REWARD_MULT = 30000;
	
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
	
	public void init() {
		Global.getLogger(this.getClass()).info("Initiating disruption mission");
		reward = calculateReward();
		Global.getSector().addScript(this);
		initRandomCancel();
		setPostingLocation(market.getPrimaryEntity());
		Global.getSector().getIntelManager().addIntel(this);
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
		super.advanceImpl(amount);
		checkMarketState();
	}

	@Override
	public void advanceMission(float amount) {
		if (industry.getDisruptedDays() >= MIN_DISRUPT_TIME) {
			missionComplete();
		}
	}
	
	public void checkMarketState() {
		if (isEnding() || isEnded()) return;
		
		// market no longer in economy?
		if (!market.isInEconomy()) {
			cancelReason = CancelReason.NOT_IN_ECONOMY;
		}
		// industry no longer exists?
		else if (industry.getMarket() == null || !market.hasIndustry(industry.getId())) 
		{
			cancelReason = CancelReason.INDUSTRY_REMOVED;
		}
		// industry disrupted?
		else if (industry.getDisruptedDays() >= MIN_DISRUPT_TIME && !isAccepted()) {
			cancelReason = CancelReason.ALREADY_DISRUPTED;
		}
		// check reputation to see if mission should continue
		else {
			RepLevel maxRep = MAX_REP_LEVEL;
			if (reason == TargetReason.MILITARY)
				maxRep = RepLevel.HOSTILE;
			
			if (!faction.isAtBest(market.getFaction(), maxRep))
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
		int reward = market.getSize() * REWARD_MULT;
		if (industry.getSpec().hasTag(Industries.TAG_MILITARY) 
				|| industry.getSpec().hasTag(Industries.TAG_COMMAND))
			reward *= 2;
		reward *= MathUtils.getRandomNumberInRange(8, 12)/10f;
		
		return reward;
	}

	@Override
	public void missionAccepted() {
		// nothing needed
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
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);
		
		info.addPara(getName(), c, 0f);
		
		addBulletPoints(info, mode);
	}
	
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) 
	{
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		float pad = 3f;
		float opad = 10f;
		
		float initPad = pad;
		if (mode == ListInfoMode.IN_DESC) initPad = opad;
		
		Color tc = getBulletColorForMode(mode);
		
		bullet(info);
		boolean isUpdate = getListInfoParam() != null;
		
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
		
		unindent(info);
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		//info.addImage(commodity.getCommodity().getIconName(), width, 80, opad);
		
		FactionAPI targetFaction = market.getFaction();
		
		info.addImages(width, 96, opad, opad * 2f,
					faction.getCrest(),
					industry.getCurrentImage());
		
		String prefix = faction.getPersonNamePrefix();		
		
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
				sub.put("$commodity", Global.getSettings().getCommoditySpec(commodityId).getName().toLowerCase());
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
	
	public static enum TargetReason {
		ECONOMIC_COMPETITION, FREE_PORT, MILITARY
	}
	public static enum CancelReason { NOT_IN_ECONOMY, NO_LONGER_HOSTILE, INDUSTRY_REMOVED, ALREADY_DISRUPTED, OTHER }
}
