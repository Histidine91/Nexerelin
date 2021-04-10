package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import java.awt.Color;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.MissionCompletionRep;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidDangerLevel;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.missions.remnant.RemnantQuestUtils.getString;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

public class RemnantM1 extends HubMissionWithBarEvent {
	
	public static Logger log = Global.getLogger(RemnantM1.class);

	public static enum Stage {
		RETRIEVE_CORES,
		RETURN_CORES,
		COMPLETED,
		FAILED,
		FAILED_DECIV
	}
	
	protected PersonAPI dissonant;
	protected MarketAPI market;
	protected RaidDangerLevel danger;
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		// if already accepted by the player, abort
		if (!setGlobalReference("$nex_remM1_ref")) {
			return false;
		}
		
		// if Prism Freeport exists, the mission must be created there
		if (!createdAt.getId().equals("nex_prismFreeport") 
				&& Global.getSector().getEconomy().getMarket("nex_prismFreeport") != null)
		{
			return false;
		}
		
		String mktFactionId = createdAt.getFactionId();
		if (!Factions.INDEPENDENT.equals(mktFactionId)) {
			return false;
		}
		
		if (Global.getSector().getImportantPeople().getData(RemnantQuestUtils.PERSON_DISSONANT) == null) 
		{
			RemnantQuestUtils.createDissonant(createdAt);
		}
		dissonant = getImportantPerson(RemnantQuestUtils.PERSON_DISSONANT);
		
		if (dissonant == null) {
			log.info("Person is null");
			return false;
		}
		personOverride = dissonant;
		
		setStoryMission();
		
		requireMarketFaction(Factions.INDEPENDENT);
		requireMarketIsNot(createdAt);
		requireMarketNotHidden();
		requireMarketNotInHyperspace();
		preferMarketSizeAtLeast(4);
		preferMarketSizeAtMost(6);
		market = pickMarket();
		danger = RaidDangerLevel.HIGH;
		if (market == null) {
			log.info("Failed to find market");
			return false;
		}
		
		if (!setMarketMissionRef(market, "$nex_remM1_ref")) {
			log.info("Mission ref already set");
			return false;
		}
		
		int marines = getMarinesRequiredForCustomObjective(market, danger);
		if (!isOkToOfferMissionRequiringMarines(marines)) {
			//return false;
		}
		
		makeImportant(market, "$nex_remM1_target", Stage.RETRIEVE_CORES);
		makeImportant(dissonant, "$nex_remM1_returnHere", Stage.RETURN_CORES);
		
		setStartingStage(Stage.RETRIEVE_CORES);
		addSuccessStages(Stage.COMPLETED);
		addFailureStages(Stage.FAILED);
		
		connectWithMemoryFlag(Stage.RETRIEVE_CORES, Stage.RETURN_CORES, market, "$nex_remM1_needToReturn");
		setStageOnMemoryFlag(Stage.COMPLETED, dissonant, "$nex_remM1_completed");
		
		setStageOnMemoryFlag(Stage.FAILED, dissonant, "$nex_remM1_failed");
		
		addNoPenaltyFailureStages(Stage.FAILED_DECIV);
		connectWithMarketDecivilized(Stage.RETRIEVE_CORES, Stage.FAILED_DECIV, market);
		setStageOnMarketDecivilized(Stage.FAILED_DECIV, createdAt);
		
		/*
		beginStageTrigger(Stage.COMPLETED);
		triggerSetGlobalMemoryValue("$nex_remM1_missionCompleted", true);
		endTrigger();
		*/

		this.setRepPersonChangesHigh();
		this.setRepFactionChangesMedium();
		setCreditReward(CreditReward.HIGH);
		
		if (true) {
			triggerCreateMediumPatrolAroundMarket(market, Stage.RETRIEVE_CORES, 0f);
		}
		
		return true;
	}
	
	@Override
	protected void updateInteractionDataImpl() {
		set("$nex_remM1_personName", dissonant.getNameString());
		set("$nex_remM1_manOrWoman", dissonant.getManOrWoman());
		set("$nex_remM1_reward", Misc.getWithDGS(getCreditsReward()));
		
		set("$nex_remM1_systemName", market.getStarSystem().getNameWithLowercaseTypeShort());
		set("$nex_remM1_marketName", market.getName());
		set("$nex_remM1_marketOnOrAt", market.getOnOrAt());
		set("$nex_remM1_dist", getDistanceLY(market));
		
		set("$nex_remM1_danger", danger);
		set("$nex_remM1_marines", Misc.getWithDGS(getMarinesRequiredForCustomObjective(market, danger)));
		set("$nex_remM1_stage", getCurrentStage());
	}
	
	@Override
	public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String action = params.get(0).getString(memoryMap);
		
		if (null != action) switch (action) {
			case "beginIntro":
				dialog.getInteractionTarget().setActivePerson(dissonant);
				dialog.getVisualPanel().showPersonInfo(dissonant, true);
				updateInteractionData(dialog, memoryMap);
				return false;
			case "accept":
				accept(dialog, memoryMap);
				return true;
			case "cancel":
				MarketAPI market = dialog.getInteractionTarget().getMarket();
				market.removePerson(dissonant);
				abort();
				return false;
			case "raidComplete":
				dissonant.getMarket().getCommDirectory().addPerson(dissonant);
				return true;
			case "hasCores":
				return Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(Commodities.BETA_CORE) >= 2;
			case "forceShowPerson":
				dialog.getVisualPanel().showPersonInfo(dissonant);
				return true;
			case "complete":
				BaseMissionHub.set(dissonant, new BaseMissionHub(dissonant));
				dissonant.getMemoryWithoutUpdate().set(BaseMissionHub.NUM_BONUS_MISSIONS, 1);
				
				dissonant.getMemoryWithoutUpdate().set("$nex_remM1_completed", true);
				((RuleBasedDialog)dialog.getPlugin()).updateMemory();
				return true;
			case "complete2":
				dissonant.getName().setFirst(getString("dissonantName1"));
				dissonant.getName().setLast(getString("dissonantName2"));
				return true;
			case "betray":
				PersonAPI person = dialog.getInteractionTarget().getActivePerson();
				FactionAPI pFaction = person.getFaction();
				
				// rep with faction you gave the cores to
				float repMult = pFaction.getCustomFloat("AICoreRepMult");
				MissionCompletionRep repPerson = new MissionCompletionRep(
						getRepRewardSuccessPerson() * repMult, getRewardLimitPerson(),
						-getRepPenaltyFailurePerson(), getPenaltyLimitPerson());
				MissionCompletionRep repFaction = new MissionCompletionRep(
						getRepRewardSuccessFaction() * repMult, getRewardLimitFaction(),
						-getRepPenaltyFailureFaction(), getPenaltyLimitFaction());
				
				Global.getSector().adjustPlayerReputation(
					new CoreReputationPlugin.RepActionEnvelope(RepActions.MISSION_SUCCESS, 
							repPerson, dialog.getTextPanel(), true), person);
				Global.getSector().adjustPlayerReputation(
					new CoreReputationPlugin.RepActionEnvelope(RepActions.MISSION_SUCCESS, 
							repFaction, dialog.getTextPanel(), true), pFaction.getId());
				
				// credits
				float bounty = Global.getSettings().getCommoditySpec(Commodities.BETA_CORE).getBasePrice();
				bounty *= 2 * pFaction.getCustomFloat("AICoreValueMult");
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(bounty);
				AddRemoveCommodity.addCreditsGainText((int)bounty, dialog.getTextPanel());
				
				// fall through to next level
			case "refuse":
				dissonant.getMarket().getCommDirectory().removePerson(dissonant);
				dissonant.getMarket().removePerson(dissonant);
				dissonant.getMemoryWithoutUpdate().set("$nex_remM1_failed", true);
				return false;
			default:
				break;
		}
		
		return super.callEvent(ruleId, dialog, params, memoryMap);
	}
	
	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();
		String pName = dissonant.getNameString();
		FactionAPI heg = Global.getSector().getFaction(Factions.HEGEMONY);
		FactionAPI pl = Global.getSector().getFaction(Factions.PERSEAN);
		
		if (currentStage == Stage.RETRIEVE_CORES) {
			info.addPara(getString("m1_stage1Desc"), opad, h, market.getName());
		}
		else if (currentStage == Stage.RETURN_CORES) {
			LabelAPI label = info.addPara(getString("m1_stage2Desc"), opad, h, pName, 
					dissonant.getMarket().getName(), heg.getDisplayNameWithArticle(),
					pl.getDisplayNameLongWithArticle());
			label.setHighlight(pName, dissonant.getMarket().getName(), 
					heg.getDisplayNameWithArticleWithoutArticle(),
					pl.getDisplayNameWithArticleWithoutArticle());
			label.setHighlightColors(h, dissonant.getMarket().getFaction().getBaseUIColor(), 
					heg.getBaseUIColor(), pl.getBaseUIColor());
		}
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		Color h = Misc.getHighlightColor();
		if (currentStage == Stage.RETRIEVE_CORES) {
			info.addPara(getString("m1_stage1NextStep"), pad, tc, h, market.getName());
			return true;
		}
		else if (currentStage == Stage.RETURN_CORES) {
			info.addPara(getString("m1_stage2NextStep"), pad, tc,
					dissonant.getMarket().getTextColorForFactionOrPlanet(), 
					dissonant.getMarket().getName());
		}
		return false;
	}

	@Override
	public String getBaseName() {
		return getString("m1_name");
	}

	@Override
	public String getPostfixForState() {
		if (startingStage != null) {
			return "";
		}
		return super.getPostfixForState();
	}
	
}





