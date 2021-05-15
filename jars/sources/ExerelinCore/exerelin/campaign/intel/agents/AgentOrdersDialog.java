package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.IndustryPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCells;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateActivity;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FleetRequest;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionDef;
import exerelin.campaign.CovertOpsManager.CovertActionType;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.agents.CovertActionIntel.StoryPointUse;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

public class AgentOrdersDialog implements InteractionDialogPlugin
{
	public static final int ENTRIES_PER_PAGE = 6;
	public static Logger log = Global.getLogger(AgentOrdersDialog.class);
	
	protected IntelUIAPI ui;
	protected InteractionDialogAPI dialog;
	protected TextPanelAPI text;
	protected OptionPanelAPI options;
	protected Menu lastSelectedMenu;
	protected List<Pair<String, Object>> optionsList = new ArrayList<>();
	protected int currentPage = 1;
	
	protected AgentIntel agent;
	protected MarketAPI agentMarket;
	protected CovertActionIntel action;
	protected boolean isQueue;
	protected List<FactionAPI> factions = new ArrayList<>();
	protected List<Object> targets = new ArrayList<>();
	protected FactionAPI thirdFaction;	// for travel and lower relations
	protected MarketAPI travelDest;
	protected String commodityToDestroy;
	protected Industry industryToSabotage;
	protected FleetMemberAPI shipToProcure;

	protected enum Menu
	{
		ACTION_TYPE,
		FACTION,
		TARGET,
		NEXT_PAGE,
		PREVIOUS_PAGE,
		BACK,
		CONFIRM,
		CONFIRM_SP,
		CONFIRM_SP_SUCCESS,
		CONFIRM_SP_DETECTION,
		CONFIRM_SP_BOTH,
		CANCEL,
		DONE,
	}
	
	public AgentOrdersDialog(AgentIntel agent, MarketAPI agentMarket, 
			IntelUIAPI ui, boolean isQueue) {
		this.agent = agent;
		this.agentMarket = agentMarket;
		this.ui = ui;
		this.isQueue = isQueue;
	}
	
	/**
	 * Gets a list of possible target factions.
	 * @return
	 */
	protected List<FactionAPI> getFactions() {
		
		Set<FactionAPI> factionsSet = new HashSet<>();
		if (action.getDefId().equals(CovertActionType.LOWER_RELATIONS)) 
		{
			for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
				NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
				if (!conf.allowAgentActions) continue;
				factionsSet.add(Global.getSector().getFaction(factionId));
			}
			// don't allow lowering relationship with self or commissioning faction
			// nor the target faction
			factionsSet.remove(PlayerFactionStore.getPlayerFaction());
			factionsSet.remove(Global.getSector().getPlayerFaction());
			factionsSet.remove(agentMarket.getFaction());
		}
		else if (action.getDefId().equals(CovertActionType.RAISE_RELATIONS)) 
		{
			for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
				NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
				if (!conf.allowAgentActions) continue;
				factionsSet.add(Global.getSector().getFaction(factionId));
			}
			factionsSet.add(Global.getSector().getFaction(Factions.PLAYER));
			// don't allow raising relationship of target faction with itself
			factionsSet.remove(agentMarket.getFaction());
		}
		else // travel: can pick any faction that allows agent actions
		{	
			Set<FactionAPI> temp = new HashSet<>();
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
				if (market.isHidden()) continue;
				temp.add(market.getFaction());
			}
			
			for (FactionAPI faction : temp)	{
				NexFactionConfig conf = NexConfig.getFactionConfig(faction.getId());
				if (conf.allowAgentActions)
					factionsSet.add(faction);
			}
		}
		
		List<FactionAPI> factions = new ArrayList<>(factionsSet);
		Collections.sort(factions, Nex_FactionDirectoryHelper.NAME_COMPARATOR_PLAYER_FIRST);
		
		// pick first available faction, if needed
		if (!factions.isEmpty()) {
			if (thirdFaction == null || !factionsSet.contains(thirdFaction))
				selectFaction(factions.get(0));
		}
		else
			thirdFaction = null;
		
		this.factions = factions;
		
		return factions;
	}
	
	protected List<Object> getTargets() {
		List<Object> targets = new ArrayList<>();
		//log.info("Generating targets for action def " + action.getDefId());
		
		switch (action.getDefId()) {
			case CovertActionType.TRAVEL:
				Set<FactionAPI> validFactions = new HashSet<>(getFactions());
				for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
				{
					if (market.isHidden()) continue;
					if (market == agent.getMarket()) continue;
					if (!validFactions.contains(market.getFaction())) continue;
					targets.add(market);
				}
				break;
			case CovertActionType.DESTROY_COMMODITY_STOCKS:
				for (CommodityOnMarketAPI commodity : agentMarket.getCommoditiesCopy()) {
					if (commodity.isNonEcon() || commodity.isIllegal()) continue;
					if (commodity.isPersonnel()) continue;
					if (commodity.getAvailable() < 2) continue;
					targets.add(commodity.getId());
					//log.info("Adding commodity target: " + commodity.getId());
				}
				break;
			case CovertActionType.SABOTAGE_INDUSTRY:
				for (Industry ind : agentMarket.getIndustries()) {
					if (!ind.canBeDisrupted()) continue;
					if (ind.getSpec().hasTag(Industries.TAG_STATION))
						continue;
					targets.add(ind);
					//log.info("Adding industry target: " + ind.getCurrentName());
				}
				break;
			case CovertActionType.PROCURE_SHIP:
				targets.addAll(ProcureShip.getEligibleTargets(agentMarket, (ProcureShip)action));
				Collections.sort(targets, PROCURE_SHIP_COMPARATOR);
				break;
		}
		
		this.targets = targets;
		autopickTargetIfNeeded();
		//log.info("Target count: " + this.targets.size());
		return targets;
	}
	
	protected void autopickTargetIfNeeded() {
		if (targets.isEmpty()) {
			switch (action.getDefId()) {
				case CovertActionType.TRAVEL:
					setTravelDestination(null);
					break;
				case CovertActionType.DESTROY_COMMODITY_STOCKS:
					setCommodityToDestroy(null);
					break;
				case CovertActionType.SABOTAGE_INDUSTRY:
					setIndustryToSabotage(null);
					break;
			}
			optionsList.clear();	// tells the main menu to disable the target menu
			return;
		}
		
		switch (action.getDefId()) {
			case CovertActionType.TRAVEL:
				if (travelDest == null || !targets.contains(travelDest)) {
					//setTravelDestination((MarketAPI)targets.get(0));
				}
				break;
			case CovertActionType.DESTROY_COMMODITY_STOCKS:
				if (commodityToDestroy == null || !targets.contains(commodityToDestroy))
					setCommodityToDestroy((String)targets.get(0));
				break;
			case CovertActionType.SABOTAGE_INDUSTRY:
				if (industryToSabotage == null || !targets.contains(industryToSabotage))
					setIndustryToSabotage((Industry)targets.get(0));
				break;
		}
	}
	
	protected void setTravelDestination(MarketAPI market) {
		travelDest = market;
		((Travel)action).setMarket(market);
		printActionInfo();
	}
	
	protected void setIndustryToSabotage(Industry ind) {
		industryToSabotage = ind;
		((SabotageIndustry)action).setIndustry(ind);
		printActionInfo();
	}
	
	protected void setCommodityToDestroy(String commodityId) {
		commodityToDestroy = commodityId;
		((DestroyCommodityStocks)action).setCommodity(commodityId);
		printActionInfo();
	}
	
	protected void setShipToProcure(FleetMemberAPI ship) {
		shipToProcure = ship;
		((ProcureShip)action).setShip(ship);
		printActionInfo();
	}
	
	protected void printActionInfo() {
		if (!hasSpecifiedTarget())
			return;
		
		Color hl = Misc.getHighlightColor(), neg = Misc.getNegativeHighlightColor();
		String header = "dialogInfoHeader";
		FactionAPI mktFaction = agentMarket != null ? agentMarket.getFaction() 
				: Global.getSector().getFaction(Factions.NEUTRAL);
		FactionAPI tgtFaction = action.getTargetFaction();
		String mktName = agentMarket != null ? agentMarket.getName() : getString("unknownLocation");
		String factionName = Nex_FactionDirectoryHelper.getFactionDisplayName(mktFaction);
		String other = thirdFaction != null ? thirdFaction.getId() : "";
		Color factionColor = mktFaction.getBaseUIColor();
		
		String actionId = action.getDefId();
		
		text.setFontSmallInsignia();
		switch (actionId) {
			case CovertActionType.TRAVEL:
				text.addPara(getString(header + "Travel"), travelDest.getFaction().getBaseUIColor(), 
						travelDest.getName());
				break;
			case CovertActionType.DESTABILIZE_MARKET:
				text.addPara(getString(header + "DestabilizeMarket"), factionColor, mktName);
				addEffectPara(0, 1);				
				break;
			case CovertActionType.RAISE_RELATIONS:
				text.addPara(getString(header + "RaiseRelations"), factionColor, factionName);
				addEffectPara(0, 100);
				
				// print max relationship if applicable
				if (other.equals(Factions.PLAYER))
					other = PlayerFactionStore.getPlayerFactionId();
				if (!DiplomacyManager.haveRandomRelationships(action.getTargetFaction().getId(), other)) 
				{
					float max = NexFactionConfig.getMaxRelationship(action.getTargetFaction().getId(),	other);
					if (max < 1) {
						String str = StringHelper.getString("exerelin_factions", "relationshipLimit");
						str = StringHelper.substituteToken(str, "$faction1", 
								NexUtilsFaction.getFactionShortName(tgtFaction));
						str = StringHelper.substituteToken(str, "$faction2", 
								NexUtilsFaction.getFactionShortName(thirdFaction));
						String maxStr = NexUtilsReputation.getRelationStr(max);
						str = StringHelper.substituteToken(str, "$relationship", maxStr);
						text.addPara(str, NexUtilsReputation.getRelColor(max), maxStr);
					}
				}
				// print current relationship
				text.addPara(StringHelper.getString("exerelin_factions", "relationshipCurr"), 
						tgtFaction.getRelColor(thirdFaction.getId()), 
						NexUtilsReputation.getRelationStr(tgtFaction, thirdFaction));
				break;
				
			case CovertActionType.LOWER_RELATIONS:
				String thirdName = Nex_FactionDirectoryHelper.getFactionDisplayName(thirdFaction);
				
				text.addPara(getString(header + "LowerRelations"), hl, factionName, thirdName);
				setHighlights(factionName, thirdName, factionColor, thirdFaction.getBaseUIColor());
				addEffectPara(0, 100);
				
				// print min relationship if applicable
				if (other.equals(Factions.PLAYER))
					other = PlayerFactionStore.getPlayerFactionId();
				
				if (!DiplomacyManager.haveRandomRelationships(action.getTargetFaction().getId(), other)) 
				{
					float min = NexFactionConfig.getMinRelationship(action.getTargetFaction().getId(),
							other);
					if (min > -1) {
						String str = StringHelper.getString("exerelin_factions", "relationshipLimit");
						str = StringHelper.substituteToken(str, "$faction1", 
								NexUtilsFaction.getFactionShortName(tgtFaction));
						str = StringHelper.substituteToken(str, "$faction2", 
								NexUtilsFaction.getFactionShortName(thirdFaction));
						String minStr = NexUtilsReputation.getRelationStr(min);
						str = StringHelper.substituteToken(str, "$relationship", minStr);
						text.addPara(str, NexUtilsReputation.getRelColor(min), minStr);
					}
				}
				// print current relationship
				text.addPara(StringHelper.getString("exerelin_factions", "relationshipCurr"),
						tgtFaction.getRelColor(thirdFaction.getId()), 
						NexUtilsReputation.getRelationStr(tgtFaction, thirdFaction));
				
				break;
			case CovertActionType.DESTROY_COMMODITY_STOCKS:
				String commodityName = ((DestroyCommodityStocks)action).getCommodityName();
				
				text.addPara(getString(header + "DestroyCommodities"), hl, commodityName, mktName);
				setHighlights(commodityName, mktName, hl, factionColor);
				addEffectPara(0, 1);
				break;
			case CovertActionType.SABOTAGE_INDUSTRY:
				String industryName = industryToSabotage.getCurrentName();
				
				text.addPara(getString(header + "SabotageIndustry"), hl, industryName, mktName);
				setHighlights(industryName, mktName, hl, factionColor);
				addEffectPara(0, 1);
				break;
			case CovertActionType.PROCURE_SHIP:
				String shipName = shipToProcure.getHullSpec().getNameWithDesignationWithDashClass();
				text.addPara(getString(header + "ProcureShip"), hl, shipName);
				break;
			case CovertActionType.INSTIGATE_REBELLION:
				text.addPara(getString(header + "InstigateRebellion"), factionColor, mktName);
				
				int stability = (int)agentMarket.getStabilityValue(), required = InstigateRebellion.MAX_STABILITY;
				String stabilityStr = getString("dialogInfoRebellionStability");
				LabelAPI label = text.addPara(stabilityStr, hl, stability + "", required + "");
				label.setHighlight(stability + "", required + "");
				label.setHighlightColors(stability > required ? neg : hl, hl);
				
				String effectStr = getString("dialogInfoEffectRebellion");
				float strMult = action.getEffectMultForLevel();
				text.addPara(effectStr, strMult < 1 ? neg : hl, String.format("%.2f", strMult));
				break;
		}
		
		// print chance of success
		if (action.showSuccessChance()) {
			MutableStat success = action.getSuccessChance();
			float successF = success.getModifiedValue();
			Color chanceCol = hl;
			if (successF >= 70f)
				chanceCol = Misc.getPositiveHighlightColor();
			else if (successF <= 40f)
				chanceCol = neg;
			
			String successStr = String.format("%.0f", successF) + "%";
			text.addPara(getString("dialogInfoSuccessChance"), chanceCol, successStr);
			printStat(success, true);
		}
		
		// time and cost
		String days = String.format("%.0f", action.getTimeNeeded());
		text.addPara(getString("dialogInfoTimeNeeded"), hl, days);
		if (actionId.equals(CovertActionType.TRAVEL)) {
			Travel travel = (Travel)action;
			printStat(travel.getDepartTime(), false);
			printStat(travel.getTravelTime(), false);
			printStat(travel.getArriveTime(), false);
		}
		
		MutableStat cost = action.getCostStat();
		int costInt = cost.getModifiedInt();
		if (costInt > 0) {
			String costDGS = Misc.getDGSCredits(costInt);
			text.addPara(getString("dialogInfoCost"), hasEnoughCredits() ? hl : 
					Misc.getNegativeHighlightColor(), costDGS);
			printStat(cost, true);
		}
		
		if (actionId.equals(CovertActionType.RAISE_RELATIONS) || actionId.equals(CovertActionType.LOWER_RELATIONS)) 
		{
			float cooldown = RaiseRelations.getModifyRelationsCooldown(mktFaction);
			if (cooldown > 0) {
				text.addPara(getString("dialogInfoModifyingRelationsCooldown"));
			}
		}
		
		text.setFontInsignia();
	}
	
	protected void printStat(MutableStat stat, boolean color) {
		if (stat == null) {
			return;
		}
			
		TooltipMakerAPI info = text.beginTooltip();
		info.setParaSmallInsignia();
		info.addStatModGrid(360, 60, 10, 0, stat, true, NexUtils.getStatModValueGetter(color, 0));
		text.addTooltip();
	}
	
	protected void setHighlights(String str1, String str2, Color col1, Color col2) {
		Highlights high = new Highlights();
		high.setColors(col1, col2);
		high.setText(str1, str2);
		text.setHighlightsInLastPara(high);
	}
	
	protected void addEffectPara(int decimalPlaces, float valueMult) 
	{
		float mult = action.getEffectMultForLevel();
		
		float eff1 = action.getDef().effect.one * mult * valueMult;
		float eff2 = action.getDef().effect.two * mult * valueMult;
		if (action.getDefId().equals(CovertActionType.SABOTAGE_INDUSTRY) && industryToSabotage != null)
		{
			// show disrupt time delta instead of absolute final time?
			// think about this later
		}
		
		String effectStr = getString("dialogInfoEffect");
		String str1 = String.format("%." + decimalPlaces + "f", eff1);
		String str2 = String.format("%." + decimalPlaces + "f", eff2);
		
		text.addPara(effectStr, Misc.getHighlightColor(), str1, str2);
	}
	
	/**
	 * Generates a CovertActionIntel and stores it. This allows us to get
	 * some needed values from the intel, and readies it for the player's go-ahead.
	 * @param def
	 */
	protected void prepAction(CovertActionDef def) {
		if (action != null && action.getDef() == def) {
			text.addPara("Doing nothing");
			return;	// don't remake action unnecessarily
		}
		// Allow to printActionInfo for the desired action if the player want to select a new action_type
		this.factions = null;
		this.targets = null;
		this.thirdFaction = null;
		this.industryToSabotage = null;
		this.travelDest = null;
		this.commodityToDestroy = null;
		
		// agent faction should not be commissioning faction if target is also commissioning faction
		FactionAPI agentFaction = PlayerFactionStore.getPlayerFaction();
		MarketAPI market = agentMarket;
		FactionAPI mktFaction = market != null ? market.getFaction() : null;
		if (agentFaction == mktFaction)
			agentFaction = Global.getSector().getPlayerFaction();
		
		switch (def.id) {
			case CovertActionType.TRAVEL:
				action = new Travel(agent, null, agentFaction, mktFaction, true, null);
				action.init();
				getFactions();
				break;
			case CovertActionType.RAISE_RELATIONS:
				action = new RaiseRelations(agent, market, agentFaction, mktFaction, agentFaction, true, null);
				action.init();
				getFactions();
				//selectFaction(agentFaction);
				//printActionInfo();
				break;
			case CovertActionType.LOWER_RELATIONS:
				action = new LowerRelations(agent, market, agentFaction, mktFaction, null, true, null);
				action.init();
				getFactions();
				//printActionInfo();
				break;
			case CovertActionType.DESTABILIZE_MARKET:
				action = new DestabilizeMarket(agent, market, agentFaction, mktFaction, true, null);
				action.init();
				printActionInfo();
				break;
			case CovertActionType.SABOTAGE_INDUSTRY:
				action = new SabotageIndustry(agent, market, null, agentFaction, mktFaction, true, null);
				action.init();
				getTargets();
				
				text.setFontSmallInsignia();
				text.addPara(getString("dialogInfoSabotageIndustryMission"));
				text.setFontInsignia();
				
				break;
			case CovertActionType.DESTROY_COMMODITY_STOCKS:
				action = new DestroyCommodityStocks(agent, market, null, agentFaction, mktFaction, true, null);
				action.init();
				getTargets();
				break;
			case CovertActionType.INFILTRATE_CELL:
				FactionAPI targetFaction = Global.getSector().getFaction(Factions.LUDDIC_PATH);
				
				action = new InfiltrateCell(agent, market, agentFaction, targetFaction, true, null);
				action.init();
				printActionInfo();
				break;
			case CovertActionType.PROCURE_SHIP:
				action = new ProcureShip(agent, market, null, agentFaction, mktFaction, true, null);
				action.init();
				
				text.setFontSmallInsignia();
				text.addPara(getString("dialogProcureShipIntro"));
				text.addPara(getString("dialogProcureShipIntro2"));
				text.addPara(getString("dialogProcureShipIntro3"), Misc.getHighlightColor(), 
						(int)(ProcureShip.FAILURE_REFUND_MULT * 100) + "%");
				if (!agent.canStealShip()) {
					text.addPara(getString("dialogProcureShipIntroSpecialization"), Misc.getHighlightColor(), 
							AgentIntel.Specialization.NEGOTIATOR.getName());
				}
				text.setFontInsignia();
				getTargets();
				break;
			case CovertActionType.FIND_PIRATE_BASE:
				action = new FindPirateBase(agent, market, agentFaction, true, null);
				action.init();
				printActionInfo();
				break;
			case CovertActionType.INSTIGATE_REBELLION:				
				action = new InstigateRebellion(agent, market, agentFaction, mktFaction, true, null);
				action.init();
				printActionInfo();
				break;
		}
	}
	
	protected void selectFaction(FactionAPI faction) {
		thirdFaction = faction;
		if (action.getDefId().equals(CovertActionType.LOWER_RELATIONS)) {
			((LowerRelations)action).setThirdFaction(thirdFaction);
			printActionInfo();
		} else if (action.getDefId().equals(CovertActionType.RAISE_RELATIONS)) {
			((RaiseRelations)action).setThirdFaction(thirdFaction);
			printActionInfo();
		}
		
		getTargets();
	}

	protected void populateOptions() {
		options.clearOptions();
		
		if (lastSelectedMenu == Menu.ACTION_TYPE) {			
			populateActionOptions();
		}
		else if (lastSelectedMenu == Menu.FACTION) {
			populateFactionOptions();
		} 
		else if (lastSelectedMenu == Menu.TARGET) {
			populateTargetOptions();
		}
		else if (lastSelectedMenu == Menu.CONFIRM_SP){
			populateSPOptions();
		}
		else {
			populateMainMenuOptions();
		}
	}
	
	protected void addActionOption(String actionId) {
		CovertActionDef def = CovertOpsManager.getDef(actionId);
		if (NexConfig.useAgentSpecializations && !def.canAgentExecute(agent)) {
			return;
		}
		optionsList.add(new Pair<String, Object>(Misc.ucFirst(def.name.toLowerCase()), def));
	}

	protected void populateSPOptions() {
		optionsList.clear();

		options.addOption(getString("dialogSPOptionSuccessText"), Menu.CONFIRM_SP_SUCCESS);
		SetStoryOption.set(dialog, 1, Menu.CONFIRM_SP_SUCCESS, "agentOrderSuccess", "ui_char_spent_story_point_combat", null);

		options.addOption(getString("dialogSPOptionDetectionText"), Menu.CONFIRM_SP_DETECTION);
		SetStoryOption.set(dialog, 1, Menu.CONFIRM_SP_DETECTION, "agentOrderDetection", "ui_char_spent_story_point_combat", null);

		options.addOption(getString("dialogSPOptionsText"), Menu.CONFIRM_SP_BOTH);
		SetStoryOption.set(dialog, 1, Menu.CONFIRM_SP_BOTH, "agentOrderBoth", "ui_char_spent_story_point_combat", null);

		addBackOption();
	}
	
	protected void populateActionOptions() {
		optionsList.clear();
		addActionOption(CovertActionType.TRAVEL);
		if (canConductLocalActions()) {
			addActionOption(CovertActionType.RAISE_RELATIONS);
			addActionOption(CovertActionType.LOWER_RELATIONS);
			addActionOption(CovertActionType.DESTABILIZE_MARKET);
			addActionOption(CovertActionType.SABOTAGE_INDUSTRY);
			addActionOption(CovertActionType.DESTROY_COMMODITY_STOCKS);
			addActionOption(CovertActionType.PROCURE_SHIP);
			if (RebellionCreator.ENABLE_REBELLIONS && NexUtilsMarket.canBeInvaded(agentMarket, true))
				addActionOption(CovertActionType.INSTIGATE_REBELLION);
		}
		if (agentMarket != null && agentMarket.hasCondition(Conditions.PATHER_CELLS)) {
			MarketConditionAPI cond = agentMarket.getCondition(Conditions.PATHER_CELLS);
			LuddicPathCells cellCond = (LuddicPathCells)(cond.getPlugin());
			LuddicPathCellsIntel cellIntel = cellCond.getIntel();
			if (cellIntel.getSleeperTimeout() <= 90)
				addActionOption(CovertActionType.INFILTRATE_CELL);
		}
		if (agentMarket != null && agentMarket.hasCondition(Conditions.PIRATE_ACTIVITY)) {
			MarketConditionAPI cond = agentMarket.getCondition(Conditions.PIRATE_ACTIVITY);
			PirateActivity activityCond = (PirateActivity)(cond.getPlugin());
			PirateBaseIntel baseIntel = activityCond.getIntel();
			if (!baseIntel.isEnding() && !baseIntel.isEnded() && !baseIntel.isPlayerVisible())
				addActionOption(CovertActionType.FIND_PIRATE_BASE);
		}
		
		showPaginatedMenu();
	}
	
	/**
	 * Display a paginated list of the available target factions for the selected action type.
	 */
	protected void populateFactionOptions() {
		optionsList.clear();
		for (FactionAPI faction : factions) {
			String name = Nex_FactionDirectoryHelper.getFactionDisplayName(faction);
			optionsList.add(new Pair<String, Object>(name, faction));
		}
		showPaginatedMenu();
	}
	
	/**
	 * Display a paginated list of the available targets for the selected action type.
	 */
	protected void populateTargetOptions() {
		optionsList.clear();
		if (CovertActionType.TRAVEL.equals(action.getDefId())) {
			List<SectorEntityToken> dests = new ArrayList<>();
			for (Object marketRaw : targets) 
			{
				MarketAPI market = (MarketAPI)marketRaw;
				dests.add(market.getPrimaryEntity());
			}
			NexUtilsMarket.pickEntityDestination(dialog, dests, 
				StringHelper.getString("confirm", true), new NexUtilsMarket.CampaignEntityPickerWrapper(){
					@Override
					public void reportEntityPicked(SectorEntityToken token) {
						optionSelected(null, token.getMarket());
					}

					@Override
					public void reportEntityPickCancelled() {}

					@Override
					public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) 
					{
						MarketAPI market = agent.getMarket();
						Nex_FleetRequest.createInfoTextBasic(info, entity, market != null ? market.getPrimaryEntity() : null);
					}
				});
			lastSelectedMenu = null;						
			populateOptions();
			return;
		}
		
		switch (action.getDefId()) {
			
			case CovertActionType.DESTROY_COMMODITY_STOCKS:
				for (Object commod : targets) {
					String commodityId = (String)commod;
					String name = Global.getSettings().getCommoditySpec(commodityId).getName();
					optionsList.add(new Pair<String, Object>(name, commodityId));
				}
				break;
			case CovertActionType.SABOTAGE_INDUSTRY:
				addBackOption();	// fallback in case player closes menu by pressing Escape
				List<Industry> industries = new ArrayList<>();
				for (Object obj : targets)
					industries.add((Industry)obj);
				
				dialog.showIndustryPicker(getString("dialogIndustryPickerHeader"), 
						StringHelper.getString("select", true), agentMarket, 
						industries, new IndustryPickerListener() {
					public void pickedIndustry(Industry industry) {
						setIndustryToSabotage(industry);
						lastSelectedMenu = null;
						populateOptions();
					}
					public void cancelledIndustryPicking() {
						lastSelectedMenu = null;
						populateOptions();
					}
				});
				return;
			case CovertActionType.PROCURE_SHIP:
				addBackOption();	// fallback in case player closes menu by pressing Escape
				List<FleetMemberAPI> ships = new ArrayList<>();
				for (Object obj : targets) {
					ships.add((FleetMemberAPI)obj);
				}
				
				dialog.showFleetMemberPickerDialog(getString("dialogShipPickerHeader"), 
						StringHelper.getString("confirm", true),
						StringHelper.getString("cancel", true),
						5, 9, 96, // 3, 7, 58 or so
						true, false, ships, 
						new FleetMemberPickerListener() {
							public void pickedFleetMembers(List<FleetMemberAPI> members) {
								if (members != null && !members.isEmpty()) {
									setShipToProcure(members.get(0));
								}
								lastSelectedMenu = null;
								populateOptions();
							}
							public void cancelledFleetMemberPicking() {
								lastSelectedMenu = null;
								populateOptions();
							}
						});
				return;
		}
		
		showPaginatedMenu();
	}
	
	protected void populateMainMenuOptions() {
		String none = StringHelper.getString("none");
		
		// action selection option
		String str = getString("dialogOption_action");
		str = StringHelper.substituteToken(str, "$action", action != null ? 
				Misc.ucFirst(action.getDef().name.toLowerCase()) : none);
		options.addOption(str, Menu.ACTION_TYPE);
		
		// target faction and/or target object, if relevant
		if (action != null) {
			String id = action.getDefId();
			if (id.equals(CovertActionType.RAISE_RELATIONS) 
					|| id.equals(CovertActionType.LOWER_RELATIONS))
			{
				str = getString("dialogOption_faction");
				str = StringHelper.substituteToken(str, "$faction", thirdFaction != null ? 
						thirdFaction.getDisplayName() : none);
				options.addOption(str, Menu.FACTION);
				if (factions.isEmpty()) {
					options.setEnabled(Menu.FACTION, false);
				}
			}
				
			if (id.equals(CovertActionType.TRAVEL) || id.equals(CovertActionType.DESTROY_COMMODITY_STOCKS)
					|| id.equals(CovertActionType.SABOTAGE_INDUSTRY) || id.equals(CovertActionType.PROCURE_SHIP)) 
			{
				str = getString("dialogOption_target");
				String target = null;
				switch (id) {
					case CovertActionType.TRAVEL:
						target = travelDest != null? travelDest.getName() : none;
						break;
					case CovertActionType.DESTROY_COMMODITY_STOCKS:
						target = commodityToDestroy != null? 
								Global.getSettings().getCommoditySpec(commodityToDestroy).getName()
								: none;
						break;
					case CovertActionType.SABOTAGE_INDUSTRY:
						target = industryToSabotage != null? 
								industryToSabotage.getCurrentName() : none;
						break;
					case CovertActionType.PROCURE_SHIP:
						target = shipToProcure != null?
								shipToProcure.getHullSpec().getNameWithDesignationWithDashClass() : none;
				}
				
				str = StringHelper.substituteToken(str, "$target", target);
				options.addOption(str, Menu.TARGET);
				// Disable target button if no targets available
				if (optionsList.isEmpty() && !id.equals(CovertActionType.SABOTAGE_INDUSTRY)
						&& !id.equals(CovertActionType.PROCURE_SHIP) 
						&& !id.equals(CovertActionType.TRAVEL)) 
				{
					options.setEnabled(Menu.TARGET, false);
				}
			}
		}

		// Confirm_option_sp
		if (action != null && !(!action.showSuccessChance() || action.getSuccessChance().getModifiedValue() >= 100)) {
			options.addOption(getString("dialogConfirmOptionSPText"), Menu.CONFIRM_SP, Misc.getStoryOptionColor(), null);
			if (!canProceed() || !hasEnoughCredits()) {
				options.setEnabled(Menu.CONFIRM_SP, false);
			}
			options.setTooltip(Menu.CONFIRM_SP, getString("dialogConfirmOptionSPTooltip"));
		}

		// Confirm option
		options.addOption(StringHelper.getString("confirm", true), Menu.CONFIRM);
		if (!canProceed() || !hasEnoughCredits()) {
			options.setEnabled(Menu.CONFIRM, false);
		} else {
			options.addOptionConfirmation(Menu.CONFIRM, getString("dialogConfirmText"), 
					StringHelper.getString("yes", true), StringHelper.getString("no", true));
			options.setShortcut(Menu.CONFIRM, Keyboard.KEY_RETURN,
				false, false, false, true);
		}
		
		// Cancel option
		options.addOption(StringHelper.getString("cancel", true), Menu.CANCEL);
		options.setShortcut(Menu.CANCEL, Keyboard.KEY_ESCAPE,
				false, false, false, true);
	}
	
	/**
	 * Generates a menu with pages, using the contents of {@code optionsList}.
	 * Adapted from Version Checker's UpdateNotificationScript.
	 */
	protected void showPaginatedMenu()
	{
		if (optionsList.isEmpty()) {
			addBackOption();
			return;
		}
		
		options.clearOptions();
		int offset = (currentPage - 1) * ENTRIES_PER_PAGE,
				max = Math.min(offset + ENTRIES_PER_PAGE, optionsList.size()),
				numPages = 1 + ((optionsList.size() - 1) / ENTRIES_PER_PAGE);
				
		if (currentPage > numPages) {
			currentPage = numPages;
			offset = (currentPage - 1) * ENTRIES_PER_PAGE;
		}
		
		for (int x = offset, y = 1; x < max; x++, y++)
		{
			Pair<String, Object> entry = optionsList.get(x);
			if (lastSelectedMenu == Menu.FACTION) {
				options.addOption(entry.one, entry.two, ((FactionAPI)entry.two).getBaseUIColor(), null);
			}
			else
				options.addOption(entry.one, entry.two);
			
		}
		
		if (currentPage > 1)
		{
			options.addOption(StringHelper.getString("previousPage", true), Menu.PREVIOUS_PAGE);
			options.setShortcut(Menu.PREVIOUS_PAGE, Keyboard.KEY_LEFT,
					false, false, false, true);
		}
		if (currentPage < numPages)
		{
			options.addOption(StringHelper.getString("nextPage", true), Menu.NEXT_PAGE);
			options.setShortcut(Menu.NEXT_PAGE, Keyboard.KEY_RIGHT,
					false, false, false, true);
		}
		
		if (lastSelectedMenu == Menu.ACTION_TYPE) {
			disableActionOptionsIfNeeded();
		}

		// Show page number in prompt if multiple pages are present
		//dialog.setPromptText("Select a mod to go to its forum thread"
		//		+ (numPages > 1 ? " (page " + currentPage + "/" + numPages + ")" : "") + ":");
		addBackOption();
	}
	
	protected void disableActionOptionsIfNeeded() {
		if (CovertOpsManager.DEBUG_MODE) return;
		FactionAPI faction = agentMarket.getFaction();
		// 
		if (!RaiseRelations.canModifyRelations(faction, agent)) {
			CovertActionDef raise = CovertOpsManager.getDef(CovertActionType.RAISE_RELATIONS);
			CovertActionDef lower = CovertOpsManager.getDef(CovertActionType.LOWER_RELATIONS);
			options.setEnabled(raise, false);
			options.setEnabled(lower, false);
			String tooltip = getString("dialogTooltipAlreadyModifyingRelations");
			options.setTooltip(raise, tooltip);
			options.setTooltip(lower, tooltip);
		}
	}
	
	protected void addBackOption() {
		options.addOption(StringHelper.getString("back", true), Menu.BACK);
		options.setShortcut(Menu.BACK, Keyboard.KEY_ESCAPE,
				false, false, false, true);
	}
	
	protected boolean canConductLocalActions() {
		if (agentMarket == null || !agentMarket.isInEconomy())
			return false;
		FactionAPI faction = agentMarket.getFaction();
		if (faction.isPlayerFaction())
			return false;
		if (!NexConfig.getFactionConfig(faction.getId()).allowAgentActions)
			return false;
		
		return true;
	}
	
	protected boolean hasSpecifiedTarget() {
		if (action == null) return false;
		
		switch (action.getDefId()) {
			case CovertActionType.TRAVEL:
				return travelDest != null;
			case CovertActionType.RAISE_RELATIONS:
			case CovertActionType.LOWER_RELATIONS:
				return thirdFaction != null;
			case CovertActionType.DESTROY_COMMODITY_STOCKS:
				return commodityToDestroy != null;
			case CovertActionType.SABOTAGE_INDUSTRY:
				return industryToSabotage != null;
			case CovertActionType.PROCURE_SHIP:
				return shipToProcure != null;
		}
		return true;
	}
	
	protected boolean canProceed() {
		if (!hasSpecifiedTarget()) return false;
		if (action.getDefId().equals(CovertActionType.INSTIGATE_REBELLION)) {
			return agentMarket.getStabilityValue() <= InstigateRebellion.MAX_STABILITY;
		}
		
		return true;
	}
	
	protected boolean hasEnoughCredits() {
		if (action == null) return false;
		if (action.getCost() <= 0) return true;
		return Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= action.getCost();
	}
	
	protected void proceed() {
		text.addPara(getString("dialogFinalText"));
		int cost = action.getCost();
		if (cost > 0) {
			Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
			AddRemoveCommodity.addCreditsLossText(cost, text);
		}
		
		if (isQueue) {
			agent.setQueuedAction(action);
		}
		else {
			agent.setCurrentAction(action);
			action.activate();
		}
		
		Global.getSector().getIntelManager().addIntelToTextPanel(action, text);
		ui.updateUIForItem(agent);
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_agentActions", id, ucFirst);
	}

	@Override
	public void init(InteractionDialogAPI dialog)
	{
		this.dialog = dialog;
		this.options = dialog.getOptionPanel();
		this.text = dialog.getTextPanel();
		dialog.getVisualPanel().setVisualFade(0.25f, 0.25f);
		dialog.getVisualPanel().showPersonInfo(agent.getAgent());

		text.addParagraph(getString("dialogIntro"));

		//dialog.setTextWidth(Display.getWidth() * .9f);

		populateOptions();
		dialog.setPromptText(Misc.ucFirst(StringHelper.getString("options")));
	}
	
	
	@Override
	public void optionSelected(String optionText, Object optionData)
	{		
		if (optionText != null) {
			text.addParagraph(optionText, Global.getSettings().getColor("buttonText"));
		}
		
		try {
			if (optionData == Menu.NEXT_PAGE) {
				currentPage++;
				showPaginatedMenu();
				return;
			} else if (optionData == Menu.PREVIOUS_PAGE) {
				currentPage--;
				showPaginatedMenu();
				return;
			}

			Menu lastSelectedMenuTemp = lastSelectedMenu;
			lastSelectedMenu = null;
			currentPage = 1;

			// covert action type selected
			if (optionData instanceof CovertActionDef) {
				prepAction((CovertActionDef)optionData);
				populateOptions();
				return;
			}
			// faction selected
			else if (optionData instanceof FactionAPI) {
				selectFaction((FactionAPI)optionData);
				populateOptions();
				return;
			}
			// travel destination 
			else if (optionData instanceof MarketAPI) {
				setTravelDestination((MarketAPI)optionData);
				populateOptions();
				return;
			}
			// commodity to destroy
			else if (lastSelectedMenuTemp == Menu.TARGET && optionData instanceof String) {
				setCommodityToDestroy((String)optionData);
				populateOptions();
				return;
			}

			if (optionData == Menu.ACTION_TYPE)	{
				lastSelectedMenu = Menu.ACTION_TYPE;
			} else if (optionData == Menu.FACTION) {
				lastSelectedMenu = Menu.FACTION;
			} else if (optionData == Menu.TARGET) {
				lastSelectedMenu = Menu.TARGET;
			} else if (optionData == Menu.BACK) {
				// do nothing except populate options
			} else if(optionData == Menu.CONFIRM_SP){
				lastSelectedMenu = Menu.CONFIRM_SP;
			} else if(optionData == Menu.CONFIRM_SP_BOTH){
				action.sp = StoryPointUse.BOTH;
				proceedAfterSelectedOption();
				return;
			} else if(optionData == Menu.CONFIRM_SP_SUCCESS){
				action.sp = StoryPointUse.SUCCESS;
				proceedAfterSelectedOption();
				return;
			} else if(optionData == Menu.CONFIRM_SP_DETECTION){
				action.sp = StoryPointUse.DETECTION;
				proceedAfterSelectedOption();
				return;
			} else if (optionData == Menu.CONFIRM) {
				proceedAfterSelectedOption();
				return;
			} else if (optionData == Menu.CANCEL) {
				dialog.dismissAsCancel();
				return;
			} else if (optionData == Menu.DONE) {
				dialog.dismiss();
				return;
			}
			populateOptions();
		
		} catch (Exception ex) {
			text.addPara(ex.toString());
			log.error(ex, ex);
			options.addOption(StringHelper.getString("cancel", true), Menu.CANCEL);
			options.setShortcut(Menu.CANCEL, Keyboard.KEY_ESCAPE,
					false, false, false, true);
		}
	}

	protected void proceedAfterSelectedOption(){
		proceed();
		options.clearOptions();
		options.addOption(StringHelper.getString("done", true), Menu.DONE);
		options.setShortcut(Menu.DONE, Keyboard.KEY_RETURN,
				false, false, false, true);
	}

	@Override
	public void optionMousedOver(String optionText, Object optionData)
	{
	}

	@Override
	public void advance(float amount)
	{
	}

	@Override
	public void backFromEngagement(EngagementResultAPI battleResult)
	{
	}

	@Override
	public Object getContext()
	{
		return null;
	}

	@Override
	public Map<String, MemoryAPI> getMemoryMap()
	{
		return null;
	}
	
	public static final Comparator PROCURE_SHIP_COMPARATOR = new Comparator<Object>() {
		@Override
		public int compare(Object obj1, Object obj2) {
			FleetMemberAPI fm1 = (FleetMemberAPI)obj1;
			FleetMemberAPI fm2 = (FleetMemberAPI)obj2;
			
			int compare = fm1.getHullSpec().getHullSize().compareTo(fm2.getHullSpec().getHullSize());
			if (compare != 0) return -compare;
			
			compare = Float.compare(fm1.getBaseDeploymentCostSupplies(), fm2.getBaseDeploymentCostSupplies());
			if (compare != 0) return -compare;
			
			compare = fm1.getHullSpec().getHullName().compareTo(fm2.getHullSpec().getHullName());
			return compare;
		}
	};
}