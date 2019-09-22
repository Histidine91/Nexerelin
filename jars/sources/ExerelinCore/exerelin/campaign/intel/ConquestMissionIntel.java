package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.MissionCompletionRep;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Set;

public class ConquestMissionIntel extends BaseMissionIntel implements InvasionListener {
	
	public static final float SIZE_REWARD_MULT = 5000;
	
	protected MarketAPI market;
	protected FactionAPI faction;
	protected FactionAPI lastTargetFaction;
	//protected float reward;
	protected boolean betrayed;
	protected CancelReason cancelReason = null;
	//protected IntervalUtil interval = new IntervalUtil(1, 1);
	
	public ConquestMissionIntel(MarketAPI market, FactionAPI faction, float duration) {
		Global.getLogger(this.getClass()).info("Instantiating conquest mission");
		this.market = market;
		this.faction = faction;
		this.duration = duration;
		lastTargetFaction = market.getFaction();
	}
	
	public void init() {
		Global.getLogger(this.getClass()).info("Initiating conquest mission");
		Global.getSector().addScript(this);
		initRandomCancel();
		setPostingLocation(market.getPrimaryEntity());
		Global.getSector().getIntelManager().addIntel(this);
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
		super.advanceImpl(amount);
		checkMarketState();
	}

	@Override
	public void advanceMission(float arg0) {
		
	}
	
	public void checkMarketState() {
		if (isEnding() || isEnded()) return;
		
		if (!market.isInEconomy()) {
			cancelReason = CancelReason.NOT_IN_ECONOMY;
			missionCancelled();
			endMissionWithUpdate(true);
		}
		else if (market.getFaction().isPlayerFaction()) {
			// do nothing, wait for player action
		}
		else if (market.getFaction() == faction) {
			cancelReason = CancelReason.ALREADY_CAPTURED;
			missionCancelled();
			endMissionWithUpdate(true);
		}
		else if (market.getFaction().isAtWorst(faction, RepLevel.SUSPICIOUS)) {
			cancelReason = CancelReason.NO_LONGER_HOSTILE;
			missionCancelled();
			endMissionWithUpdate(true);
		}
	}
	
	protected void missionCancelled() {
		setMissionState(MissionState.CANCELLED);
		setMissionResult(createAbandonedResult(false));
	}
	
	protected void missionComplete() {
		int reward = calculateReward(true);
		float repAmount = 0.02f * market.getSize();
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
	
	protected int calculateReward(boolean includeBonus) {
		float value = ExerelinUtilsMarket.getMarketIndustryValue(market) * Global.getSettings().getFloat("industryRefundFraction");
		if (includeBonus) {
			float sizeBonus = (float)(Math.pow(market.getSize(), 2) * SIZE_REWARD_MULT);
			float stabilityMult = (market.getStabilityValue() + 5)/15;
			value += (sizeBonus * stabilityMult);
		}
		return (int)value;
	}

	@Override
	public void missionAccepted() {
		// nothing needed
	}

	@Override
	protected MissionResult createAbandonedResult(boolean withPenalty) {
		if (withPenalty) {
			float repAmount = 0.02f * market.getSize();
			if (repAmount < 0.01f) repAmount = 0.01f;
			
			betrayed = market.getFaction().isPlayerFaction();
			if (betrayed) repAmount *= 2;
			
			MissionCompletionRep completionRep = new MissionCompletionRep(repAmount, RepLevel.WELCOMING, -repAmount, RepLevel.INHOSPITABLE);
			
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
	public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved, 
			boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {
		if (market != this.market) return;
		
		if (newOwner == faction) {
			if (playerInvolved) missionComplete();
		} else if (!newOwner.isPlayerFaction()) {
			lastTargetFaction = newOwner;
		}
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
						Misc.getDGSCredits(calculateReward(false)));
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
		
		boolean taken = market.getFaction() == faction;
		FactionAPI targetFaction = taken ? lastTargetFaction : market.getFaction();
		
		if (!market.isInEconomy()) {
			info.addImage(faction.getLogo(), width, 128, opad);
		} else {
			info.addImages(width, 128, opad, opad * 2f,
					   faction.getCrest(),
					   targetFaction.getCrest());
		}
		
		String prefix = faction.getPersonNamePrefix();		
		
		String str = taken ? getString("intelDesc1Alt") : getString("intelDesc1");
		String marketName = market.getName();
		str = StringHelper.substituteFactionTokens(str, faction);
		str = StringHelper.substituteToken(str, "$market", marketName);
		str = StringHelper.substituteToken(str, "$location", market.getContainingLocation().getNameWithLowercaseType());
		str = StringHelper.substituteToken(str, "$theOtherFaction", targetFaction.getDisplayNameWithArticle(), true);
		
		LabelAPI label = info.addPara(str, opad, tc);
		label.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(), 
				marketName, targetFaction.getDisplayNameWithArticleWithoutArticle());
		label.setHighlightColors(faction.getBaseUIColor(), h, targetFaction.getBaseUIColor());
		
		str = getString("intelDesc2");
		str = StringHelper.substituteToken(str, "$market", marketName);
		str = StringHelper.substituteToken(str, "$onOrAt", market.getOnOrAt());
		info.addPara(str, opad, tc);
		
		addBulletPoints(info, ListInfoMode.IN_DESC);
		
		if (isPosted() || isAccepted()) {
			addGenericMissionState(info);
			
			addAcceptOrAbandonButton(info, width);
			
			str = getString("intelDesc3");
			str = StringHelper.substituteToken(str, "$market", marketName);
			info.addPara(str, opad, g);
		} else {
			if (cancelReason == CancelReason.NOT_IN_ECONOMY) {
				str = getString("intelDescOutcome_noLongerExists");
				str = StringHelper.substituteToken(str, "$market", marketName);
				info.addPara(str, opad, g);
			} else if (cancelReason == CancelReason.NO_LONGER_HOSTILE) {
				str = getString("intelDescOutcome_noLongerHostile");
				str = StringHelper.substituteToken(str, "$market", marketName);
				info.addPara(str, opad, g);
			} else if (cancelReason == CancelReason.ALREADY_CAPTURED) {
				str = getString("intelDescOutcome_alreadyTaken");
				str = StringHelper.substituteToken(str, "$market", marketName);
				str = StringHelper.substituteFactionTokens(str, faction);
				info.addPara(str, opad, g);
			} else {
				addGenericMissionState(info);
			}
		}

	}
	
	@Override
	public String getSortString() {
		return StringHelper.getString("conquest", true);
	}
	
	public String getName() {
		return getString("intelTitle") + getPostfixForState();
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
		return StringHelper.getString("nex_conquestMission", id);
	}
	
	// runcode exerelin.campaign.intel.ConquestMissionIntel.debug("jangala", "persean")
	public static void debug(String marketId, String factionId) {
		MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
		new ConquestMissionIntel(market, Global.getSector().getFaction(factionId), 5).init();
	}
	
	public static enum CancelReason { ALREADY_CAPTURED, NOT_IN_ECONOMY, NO_LONGER_HOSTILE, OTHER }
}
