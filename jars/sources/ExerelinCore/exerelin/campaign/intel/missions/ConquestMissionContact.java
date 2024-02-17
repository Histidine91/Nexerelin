package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_TransferMarket;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.EventCancelReason;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.*;

import static exerelin.campaign.intel.missions.ConquestMissionManager.MIN_PLAYER_LEVEL;

@Log4j
public class ConquestMissionContact extends HubMissionWithSearch implements InvasionListener {

	public static final float SIZE_REWARD_MULT = 8000;
	public static final String BUTTON_TRANSFER = "BUTTON_TRANSFER";
	
	protected MarketAPI market;
	protected FactionAPI faction;
	protected FactionAPI lastTargetFaction;
	//protected float reward;
	//protected IntervalUtil interval = new IntervalUtil(1, 1);
	
	public static enum Stage {
		CAPTURE,
		COMPLETED,
		FAILED,
		//FAILED_NO_PENALTY,	// use an EventCancelReason instead
	}
	
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		if (Global.getSector().getPlayerStats().getLevel() < MIN_PLAYER_LEVEL)
			return false;

		if (!NexConfig.enableHostileFleetEvents) return false;
		if (!NexConfig.enableInvasions)	return false;
		
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
		else if (market.getFaction().isPlayerFaction() || market.getFaction() == Misc.getCommissionFaction())
		{
			//log.info("Target market belongs to player, retry later");
			return false;
		}
		if (market.isInvalidMissionTarget()) return false;

		float currReward = ConquestMissionIntel.calculateReward(market, true);
		if (currReward <= 0) return false;
		
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
		
		addNoPenaltyFailureStages(EventCancelReason.NOT_IN_ECONOMY, EventCancelReason.ALREADY_CAPTURED, EventCancelReason.RELATIONS_TOO_HIGH);
		connectWithMarketDecivilized(Stage.CAPTURE, EventCancelReason.NOT_IN_ECONOMY, market);
		
		//setCreditReward(80000, 100000);
		setCreditReward(ConquestMissionIntel.calculateReward(market, true));
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
				InvasionFleetManager.EventType.INVASION, false, genRandom);
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
	protected void notifyEnding() {
		Global.getSector().getListenerManager().removeListener(this);
	}

	public void checkMarketState() {
		if (currentStage != Stage.CAPTURE) return;
		
		if (market.getFaction().isPlayerFaction() || market.getMemoryWithoutUpdate().getBoolean(GBConstants.MEMKEY_AWAIT_DECISION)) {
			// do nothing, wait for player action
		}
		else if (market.getFaction() == faction) {
			missionCancelled(EventCancelReason.ALREADY_CAPTURED);
		}
		else if (market.getFaction().isAtWorst(faction, RepLevel.SUSPICIOUS)) {
			missionCancelled(EventCancelReason.RELATIONS_TOO_HIGH);
		}
	}
	
	protected void missionCancelled(EventCancelReason reason) {
		this.addNoPenaltyFailureStages(reason);
		this.setCurrentStage(reason, null, null);
	}
	
	protected void missionComplete() {
		setCurrentStage(Stage.COMPLETED, null, null);
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
		set("$nex_conquest_reward", Misc.getWithDGS(getCreditsReward()));
		set("$nex_conquest_marketName", market.getName());
		set("$nex_conquest_marketFactionColor", market.getFaction().getBaseUIColor());
		set("$nex_conquest_marketFaction", market.getFaction().getDisplayName());
		set("$nex_conquest_marketLocation", market.getContainingLocation().getNameWithLowercaseType());
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
				Nex_MarketCMD.printDefenderInvasionStrength(tooltip, market);
				dialog.getTextPanel().addTooltip();
				return true;
			default:
				break;
		}

		return super.callEvent(ruleId, dialog, params, memoryMap);
	}

	@Override
	public String getBaseName() {
		return ConquestMissionIntel.getString("intelTitle") + ": " + market.getName();
	}

	@Override
	public void addDescriptionForCurrentStage(TooltipMakerAPI info, float width, float height) {
		super.addDescriptionForCurrentStage(info, width, height);
		float opad = 10f;

		if (currentStage instanceof EventCancelReason) {
			String str = ((EventCancelReason)currentStage).getReason();
			if (str == null) return;
			str = StringHelper.substituteToken(str, "$market", market.getName());
			str = StringHelper.substituteFactionTokens(str, faction);
			info.addPara(str, opad);
		}
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

		if (currentStage == Stage.CAPTURE && market.getFaction().isPlayerFaction()) {
			ButtonAPI button = info.addButton(ConquestMissionIntel.getString("intelButtonTransfer"), BUTTON_TRANSFER,
					faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad * 2f);
			button.setShortcut(Keyboard.KEY_T, true);
		}
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
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(market.getFactionId());
		return tags;
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
			else missionCancelled(EventCancelReason.ALREADY_CAPTURED);
		} else if (!newOwner.isPlayerFaction()) {
			lastTargetFaction = newOwner;
		}
	}
}
