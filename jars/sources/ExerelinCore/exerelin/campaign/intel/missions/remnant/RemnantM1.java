package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.MissionCompletionRep;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidDangerLevel;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.SpecialContactIntel;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static exerelin.campaign.intel.missions.remnant.RemnantQuestUtils.getString;

public class RemnantM1 extends HubMissionWithBarEvent {
	
	public static Logger log = Global.getLogger(RemnantM1.class);
	
	public static final float CORE_PRICE_MULT = 2.5f;

	public static enum Stage {
		RETRIEVE_CORES,
		RETURN_CORES,
		COMPLETED,
		FAILED,
		FAILED_DECIV
	}
	
	protected PersonAPI dissonant;
	protected MarketAPI market;
	protected MarketAPI sourceMarket;
	protected RaidDangerLevel danger;
	
	protected Object readResolve() {
		log.info("Readresolve for Remnant M1");
		if (sourceMarket == null && dissonant != null) {
			sourceMarket = dissonant.getMarket();
		}
		return this;
	}
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		// if already accepted by the player? it's a zombie, kill it
		// this happens if we see the option in the bar but don't pick it, causing the previous global reference to hang around forever
		
		if (!setGlobalReference("$nex_remM1_ref")) {
			RemnantM1 existing = (RemnantM1)Global.getSector().getMemoryWithoutUpdate().get("$nex_remM1_ref");
			existing.abort();
			setGlobalReference("$nex_remM1_ref");
			//return false;
		}
		
		// if Prism Freeport exists, the mission must be created there
		// else, must be an indie market
		boolean atPrism = createdAt.getId().equals("nex_prismFreeport");
		if (!atPrism && Global.getSector().getEconomy().getMarket("nex_prismFreeport") != null)
		{
			return false;
		}
		String mktFactionId = createdAt.getFactionId();
		if (!atPrism && !Factions.INDEPENDENT.equals(mktFactionId)) {
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
		sourceMarket = createdAt;
		
		setStoryMission();
		
		requireMarketFaction(Factions.INDEPENDENT);
		requireMarketIsNot(createdAt);
		requireMarketNotHidden();
		requireMarketNotInHyperspace();
		preferMarketSizeAtLeast(4);
		preferMarketSizeAtMost(6);
		search.marketPrefs.add(new MarketGroundDefReq(150, 350));
		market = pickMarket();
		danger = RaidDangerLevel.HIGH;
		if (market == null) {
			log.info("Failed to find market");
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
		
		// don't use a completion stage trigger, it can't be trusted https://fractalsoftworks.com/forum/index.php?topic=5061.msg392175#msg392175
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
	
	protected int getCorePrice() {
		float base = Global.getSettings().getCommoditySpec(Commodities.BETA_CORE).getBasePrice();
		return Math.round(base * 2 * CORE_PRICE_MULT);
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
		
		int price = getCorePrice();
		set("$nex_remM1_danger", danger);
		set("$nex_remM1_corePriceVal", price);
		set("$nex_remM1_corePriceStr", Misc.getWithDGS(price));
		set("$nex_remM1_marines", Misc.getWithDGS(getMarinesRequiredForCustomObjective(market, danger)));
		set("$nex_remM1_stage", getCurrentStage());
	}
	
	// not CallAction becase we may need to return false here?
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
				setMarketMissionRef(market, "$nex_remM1_ref");
				accept(dialog, memoryMap);
				return true;
			case "cancel":
				MarketAPI market = dialog.getInteractionTarget().getMarket();
				market.removePerson(dissonant);
				abort();
				return false;
			case "raidComplete":
			case "boughtCores":
				sourceMarket.getCommDirectory().addPerson(dissonant);
				return true;
			case "hasCores":
				return Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(Commodities.BETA_CORE) >= 2;
			case "forceShowPerson":
				dialog.getVisualPanel().showPersonInfo(dissonant);
				return true;
			case "complete":
				BaseMissionHub.set(dissonant, new BaseMissionHub(dissonant));
				sourceMarket.addPerson(dissonant);	// in case we refused the mission before then re-enabled it with console, reset Midnight's market
				dissonant.getMemoryWithoutUpdate().set(BaseMissionHub.NUM_BONUS_MISSIONS, 1);
				dissonant.getMemoryWithoutUpdate().set("$nex_remM1_completed", true);	// used to trigger mission completion
				//setCurrentStage(Stage.COMPLETED, dialog, memoryMap);
				((RuleBasedDialog)dialog.getPlugin()).updateMemory();
				return true;
			case "complete2":
				dissonant.getName().setFirst(getString("dissonantName1"));
				dissonant.getName().setLast(getString("dissonantName2"));
				SpecialContactIntel intel = new SpecialContactIntel(dissonant, sourceMarket);
				Global.getSector().getIntelManager().addIntel(intel, false, dialog.getTextPanel());
				Global.getSector().getMemoryWithoutUpdate().set("$nex_remM1_missionCompleted", true);
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

				Global.getSector().getMemoryWithoutUpdate().set("$nex_remM1_missionCompleted", true);
				Global.getSector().getMemoryWithoutUpdate().set("$nex_remM1_betrayed", true);
				
				// fall through to next level
			case "refuse":
				// handle this stuff on mission failure?
				sourceMarket.getCommDirectory().removePerson(dissonant);
				sourceMarket.removePerson(dissonant);
				dissonant.getMemoryWithoutUpdate().set("$nex_remM1_failed", true);
				Global.getSector().getMemoryWithoutUpdate().set("$nex_remM1_missionCompleted", true);
				Global.getSector().getMemoryWithoutUpdate().set("$nex_remM1_betrayed", true);
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
					sourceMarket.getName(), heg.getDisplayNameWithArticle(),
					pl.getDisplayNameLongWithArticle());
			label.setHighlight(pName, sourceMarket.getName(), 
					heg.getDisplayNameWithArticleWithoutArticle(),
					pl.getDisplayNameWithArticleWithoutArticle());
			label.setHighlightColors(h, sourceMarket.getFaction().getBaseUIColor(), 
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
					sourceMarket.getTextColorForFactionOrPlanet(), 
					sourceMarket.getName());
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
	
	public static class MarketGroundDefReq implements MarketRequirement {
		
		Integer min = 0, max = Integer.MAX_VALUE;
		
		public MarketGroundDefReq(Integer min, Integer max) {
			if (min != null) this.min = min;
			if (max != null) this.max = max;
		}
		public boolean marketMatchesRequirement(MarketAPI market) {
			StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
			int str = Math.round(defender.computeEffective(0));
			//log.info("Market " + market.getName() + " has defences " + str);
			
			return str >= min && str <= max;
		}
	}
}





