package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_TransferMarket;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static exerelin.campaign.intel.missions.ConquestMissionManager.MIN_PLAYER_LEVEL;

public class ConquestMissionContact extends HubMissionWithSearch implements InvasionListener {

	public static final float SIZE_REWARD_MULT = 8000;
	public static final String BUTTON_TRANSFER = "BUTTON_TRANSFER";
	
	protected MarketAPI market;
	protected FactionAPI faction;
	protected FactionAPI lastTargetFaction;
	//protected float reward;
	protected boolean betrayed;
	protected CancelReason cancelReason = null;
	//protected IntervalUtil interval = new IntervalUtil(1, 1);
	
	public static enum Stage {
		CAPTURE,
		COMPLETED,
		FAILED,
		FAILED_NO_PENALTY,
	}
	
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		//genRandom = Misc.random;
		//if (!Misc.isMilitary(createdAt)) return false;
		
		if (Global.getSector().getPlayerStats().getLevel() < MIN_PLAYER_LEVEL)
			return false;
		
		if (!NexConfig.enableInvasions)
			return false;
		
		PersonAPI person = getPerson();
		if (person == null) return false;
		
		if (!setPersonMissionRef(person, "$nex_conquest_ref")) {
			return false;
		}

		// not used for now
		/*
		float q = getQuality();
		if (q <= 0) {
			preferMarketSizeAtMost(4);
		} else if (q <= 0.25) {
			preferMarketSizeAtMost(5);
		} else if (q <= 0.5) {
			preferMarketSizeAtMost(6);
		} else if (q <= 0.75) {
			preferMarketSizeAtMost(7);
		}
		 */

		faction = getPerson().getFaction();
		market = pickMarket();
		if (market == null) return false;
		
		if (!setMarketMissionRef(market, "$nex_conquest_ref")) {
			return false;
		}
		makeImportant(market, "$nex_conquest_target", Stage.CAPTURE);
		
		setStartingStage(Stage.CAPTURE);
		setSuccessStage(Stage.COMPLETED);
		setFailureStage(Stage.FAILED);
		
		float duration = ConquestMissionManager.getDuration(market);
		setStageOnMemoryFlag(Stage.COMPLETED, market, "$nex_conquest_completed");
		setTimeLimit(Stage.FAILED, duration, market.getStarSystem());
		
		addNoPenaltyFailureStages(Stage.FAILED_NO_PENALTY);
		connectWithMarketDecivilized(Stage.CAPTURE, Stage.FAILED_NO_PENALTY, market);
		setStageOnMarketDecivilized(Stage.FAILED_NO_PENALTY, createdAt);
		connectWithHostilitiesEnded(Stage.CAPTURE, Stage.FAILED_NO_PENALTY, person, market);
		setStageOnHostilitiesEnded(Stage.FAILED_NO_PENALTY, person, market);
		
		//setCreditReward(80000, 100000);
		setCreditReward(calculateReward(true));
		float repAmount = 0.02f * market.getSize();
		if (repAmount < 0.01f) repAmount = 0.01f;
		setRepRewardFaction(repAmount);
		setRepPenaltyFaction(repAmount/2);
		setRepRewardPerson(repAmount * 1.5f);
		setRepPenaltyPerson(repAmount * 1.5f/2);
		
		return true;
	}
	
	@Override
	public MarketAPI pickMarket(boolean resetSearch) {
		return InvasionFleetManager.getManager().getTargetMarketForFleet(
				faction, null, null, Global.getSector().getEconomy().getMarketsCopy(),
				InvasionFleetManager.EventType.INVASION);
	}

	@Override
	protected void advanceImpl(float amount) {
		checkMarketState();
	}

	@Override
	public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		Global.getSector().getListenerManager().addListener(this);
	}

	@Override
	protected void endSuccessImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		cleanup();
	}

	@Override
	protected void endFailureImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		cleanup();
	}

	@Override
	protected void endAbandonImpl() {
		cleanup();
	}

	public void checkMarketState() {
		if (currentStage != Stage.CAPTURE) return;
		
		if (!market.isInEconomy()) {
			cancelReason = CancelReason.NOT_IN_ECONOMY;
			missionCancelled();
		}
		else if (market.getFaction().isPlayerFaction() || market.getMemoryWithoutUpdate().getBoolean(GBConstants.MEMKEY_AWAIT_DECISION)) {
			// do nothing, wait for player action
		}
		else if (market.getFaction() == faction) {
			cancelReason = CancelReason.ALREADY_CAPTURED;
			missionCancelled();
		}
		else if (market.getFaction().isAtWorst(faction, RepLevel.SUSPICIOUS)) {
			cancelReason = CancelReason.NO_LONGER_HOSTILE;
			missionCancelled();
		}
	}

	protected void cleanup() {
		Global.getSector().getListenerManager().removeListener(this);
	}
	
	protected void missionCancelled() {
		result = new HubMissionResult();
		result.success = false;
		endAbandonImpl();
		endAfterDelay();
		abort();
	}
	
	protected void missionComplete() {
		setCreditReward(calculateReward(true));
		setCurrentStage(Stage.COMPLETED, null, null);
	}
	
	protected int calculateReward(boolean includeBonus) {
		float value = NexUtilsMarket.getMarketIndustryValue(market) * Global.getSettings().getFloat("industryRefundFraction");
		
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
	protected void updateInteractionDataImpl() {
		set("$nex_conquest_days", Misc.getWithDGS(timeLimit.days));
		set("$nex_conquest_time", Misc.getWithDGS(getCreditsReward()));
		set("$nex_conquest_marketName", market.getName());
		set("$nex_conquest_marketFactionColor", market.getFaction().getBaseUIColor());
		set("$nex_conquest_marketFaction", market.getFaction().getDisplayName());
		set("$nex_conquest_theMarketFaction", market.getFaction().getDisplayNameWithArticle());
		set("$nex_conquest_onOrAt", market.getOnOrAt());
		set("$nex_conquest_marketPlanetOrStation", StringHelper.getString(market.getPlanetEntity() == null ? "station" : "planet"));
	}

	@Override
	public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String action = params.get(0).getString(memoryMap);

		switch (action) {
			case "printDefenses":
				TooltipMakerAPI tooltip = dialog.getTextPanel().beginTooltip();
				//Nex_MarketCMD.printDefenderInvasionStrength(tooltip, market);
				dialog.getTextPanel().addTooltip();
				return true;
			default:
				break;
		}

		return super.callEvent(ruleId, dialog, params, memoryMap);
	}

	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
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

		String str = taken ? ConquestMissionIntel.getString("intelDesc1Alt") : ConquestMissionIntel.getString("intelDesc1");
		String marketName = market.getName();
		str = StringHelper.substituteFactionTokens(str, faction);
		str = StringHelper.substituteToken(str, "$market", marketName);
		str = StringHelper.substituteToken(str, "$location", market.getContainingLocation().getNameWithLowercaseType());
		str = StringHelper.substituteToken(str, "$theOtherFaction", targetFaction.getDisplayNameWithArticle(), true);

		LabelAPI label = info.addPara(str, opad, tc);
		label.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(),
				marketName, targetFaction.getDisplayNameWithArticleWithoutArticle());
		label.setHighlightColors(faction.getBaseUIColor(), h, targetFaction.getBaseUIColor());

		str = ConquestMissionIntel.getString("intelDesc2");
		str = StringHelper.substituteToken(str, "$market", marketName);
		str = StringHelper.substituteToken(str, "$onOrAt", market.getOnOrAt());
		info.addPara(str, opad, tc);
	}

	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		if (buttonId == BUTTON_TRANSFER) {
			String str = ConquestMissionIntel.getString("intelDialogConfirm");
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
			if (playerInvolved || oldOwner.isPlayerFaction()) missionComplete();
			else missionCancelled();
		} else if (!newOwner.isPlayerFaction()) {
			lastTargetFaction = newOwner;
		}
	}
		
	public static enum CancelReason { ALREADY_CAPTURED, NOT_IN_ECONOMY, NO_LONGER_HOSTILE, OTHER }
}
