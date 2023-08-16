package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.MissionCompletionRep;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_TransferMarket;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ConquestMissionIntel extends BaseMissionIntel implements InvasionListener {
	
	public static final float SIZE_REWARD_MULT = 6000;
	public static final String BUTTON_TRANSFER = "BUTTON_TRANSFER";
	public static final String MEMKEY_CONQUEST_VALUE_BONUS = "$nex_conquest_value_bonus";
	
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
		initRandomCancel();
		
		// considering only having conquest have a posting range, a lot more rare than disrupt missions
		// actually I'm not sure if posting location is required at all, maybe for intel queueing?
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
		Misc.makeUnimportant(market.getPrimaryEntity(), "nex_conquest");
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
		else if (market.getFaction().isPlayerFaction() || market.getMemoryWithoutUpdate().getBoolean(GBConstants.MEMKEY_AWAIT_DECISION)) {
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
		int reward = calculateReward(market, true);
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
	
	public static int calculateReward(MarketAPI market, boolean includeBonus) {
		float value = NexUtilsMarket.getMarketIndustryValue(market);
		if (market.getMemoryWithoutUpdate().contains(MEMKEY_CONQUEST_VALUE_BONUS)) {
			value += market.getMemoryWithoutUpdate().getFloat(MEMKEY_CONQUEST_VALUE_BONUS);
		}

		value *= Global.getSettings().getFloat("industryRefundFraction");
		
		value += NexUtilsMarket.getIncomeNetPresentValue(market, 6, 0);
		
		if (includeBonus) {
			float sizeBonus = (float)(Math.pow(market.getSize(), 2) * SIZE_REWARD_MULT);
			float stabilityMult = (market.getStabilityValue() + 5)/15;
			value += (sizeBonus * stabilityMult);
		}
		
		if (value < 0) value = 0;
		return (int)value;
	}
	
	protected void transferViaButton() {
		String oldFactionId = market.getFactionId();
		String factionId = faction.getId();
		FactionAPI oldFaction = Global.getSector().getFaction(oldFactionId);
		
		float repChange = Nex_TransferMarket.getRepChange(market).getModifiedValue() * 0.01f;
		if (factionId.equals(Nex_TransferMarket.getRecentlyCapturedFromId(market)))
			repChange *= Global.getSettings().getFloat("nex_transferMarket_recentlyCapturedMult");
		else if (factionId.equals(NexUtilsMarket.getOriginalOwner(market)))
			repChange *= Global.getSettings().getFloat("nex_transferMarket_originalOwnerMult");
		
		SectorManager.transferMarket(market, faction, oldFaction, true, false, 
				new ArrayList<>(Arrays.asList(factionId)), repChange);
		DiplomacyManager.getManager().getDiplomacyBrain(factionId).reportDiplomacyEvent(oldFactionId, repChange);
	}

	@Override
	public void missionAccepted() {
		Misc.makeImportant(market.getPrimaryEntity(), "nex_conquest");
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
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad){
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
						Misc.getDGSCredits(calculateReward(market, false)));
				addDays(info, getString("intelDescDuration_post_short"), duration - elapsedDays, tc, 0);
			}
		}
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
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
			
			if (isAccepted() && market.getFaction().isPlayerFaction()) {
				ButtonAPI button = info.addButton(getString("intelButtonTransfer"), BUTTON_TRANSFER, 
						faction.getBaseUIColor(), faction.getDarkUIColor(),
					  (int)(width), 20f, opad * 2f);
				button.setShortcut(Keyboard.KEY_T, true);
			}
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
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		if (buttonId == BUTTON_TRANSFER) {
			String str = getString("intelDialogConfirm");
			str = StringHelper.substituteFactionTokens(str, faction);
			prompt.addPara(str, 0, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
		}
		else super.createConfirmationPrompt(buttonId, prompt);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_TRANSFER) {
			transferViaButton();
			ui.updateUIForItem(this);
			return;
		}
		super.buttonPressConfirmed(buttonId, ui);
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
	
	public static String getString(String id) {
		return StringHelper.getString("nex_conquestMission", id);
	}
	
	// runcode exerelin.campaign.intel.missions.ConquestMissionIntel.debug("jangala", "persean")
	public static void debug(String marketId, String factionId) {
		MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
		ConquestMissionIntel intel = new ConquestMissionIntel(market, Global.getSector().getFaction(factionId), 5);
		intel.init();
		Global.getSector().addScript(intel);
	}
	
	public static enum CancelReason { ALREADY_CAPTURED, NOT_IN_ECONOMY, NO_LONGER_HOSTILE, OTHER }
}
