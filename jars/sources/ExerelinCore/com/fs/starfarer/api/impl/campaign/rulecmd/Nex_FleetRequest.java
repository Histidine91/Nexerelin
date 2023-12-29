package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.MutableValue;
import data.scripts.campaign.bases.VayraRaiderBaseIntel;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.RespawnBaseIntel;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.ReliefFleetIntelAlt;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.campaign.ui.FieldOptionsScreenScript;
import exerelin.utilities.*;
import exerelin.utilities.NexUtilsMarket.CampaignEntityPickerWrapper;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Handles fleet requests: spawns fleets with specific origins and objectives based on their targets.
 */
public class Nex_FleetRequest extends PaginatedOptionsPlus {
	
	public static final String MEM_KEY_TYPE = "$nex_fleetRequest_type";
	public static final String MEM_KEY_FP = "$nex_fleetRequest_fp";
	public static final String MEM_KEY_MARINES = "$nex_fleetRequest_marines";
	public static final String MEM_KEY_SOURCES = "$nex_fleetRequest_sources";
	public static final String MEM_KEY_SOURCE = "$nex_fleetRequest_source";
	public static final String MEM_KEY_TARGET = "$nex_fleetRequest_target";
	public static final String MEM_KEY_COST = "$nex_fleetRequest_cost";
	public static final String MEM_KEY_BASE_STRIKE_TARGET = "$nex_canBaseStrike";
	public static final String OPTION_MAIN = "nex_fleetRequest_main";
	public static final String OPTION_PROCEED = "nex_fleetRequest_proceed";
	public static final String TYPE_OPTION_PREFIX = "nex_fleetRequest_setType_";
	public static final String SOURCE_OPTION_PREFIX = "nex_fleetRequest_setSource_";
	public static final String TARGET_OPTION_PREFIX = "nex_fleetRequest_setTarget_";
	public static final String FACTION_OPTION_PREFIX = "nex_fleetRequest_setFaction_";
	public static final float BAR_WIDTH = 320;
	public static final float MARINE_COST_MULT = 0.25f;	// each marine costs a quarter of its market value
	public static final float RELIEF_COST_MULT = 2;
	public static final float MIN_FP = 100;
	
	protected MemoryAPI memory;
	protected String option;
	protected MarketAPI source;
	protected MarketAPI target;
	protected int fp;
	protected int maxFP;
	protected int marines;
	protected float cost;
	protected FleetType fleetType;
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		//MarketAPI market = dialog.getInteractionTarget().getMarket();
		initVars(dialog, memoryMap);
		
		switch(arg)
		{
			case "init":
				initFirstTime();
				printBudget();
				printFleetInfo(true, true, true, true);
				break;
			case "mainMenu":
				generateMainMenu();
				break;
			case "typeMenu":
				
				break;
			case "setType":
				String type = option.substring(TYPE_OPTION_PREFIX.length());
				setFleetType(FleetType.getTypeFromString(type));
				break;
			case "strengthMenu":
				showFleetStrengthMenu();
				break;
			case "setStrength":
				setFleetStrength(dialog.getOptionPanel());
				printFleetInfo(true, false, true, false);
				break;
			case "sourceMenu":
				setupDelegateDialog(dialog);
				listSources();
				break;
			case "setSource":
				String sourceMarketId = option.substring(SOURCE_OPTION_PREFIX.length());
				setSource(sourceMarketId);
				printFleetInfo(false, true, true, false);
				break;
			case "targetMenu":
				setupDelegateDialog(dialog);
				listTargets();
				break;
			case "setTarget":
				String targetMarketId = option.substring(TARGET_OPTION_PREFIX.length());
				setTarget(targetMarketId);
				printFleetInfo(false, true, true, true);
				break;
			case "proceed":
				boolean success = launchFleet();
				return success;
		}
		return true;
	}
	
	protected void initVars(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		this.dialog = dialog;  
		this.memoryMap = memoryMap;
		
		memory = memoryMap.get(MemKeys.LOCAL);
		option = memoryMap.get(MemKeys.LOCAL).getString("$option");
		fleetType = getFleetType();
		source = getSource();
		target = getTarget();
		fp = getFP();
		marines = getMarines();
		maxFP = (int)InvasionFleetManager.getManager().getFleetRequestStock();
		if (Global.getSettings().isDevMode()) maxFP = 5000;
		
		updateCost();
	}
	
	protected void initFirstTime() {
		memory.set("$nex_fleetRequest_initComplete", true, 0);
		if (NexConfig.enableInvasions)
			setFleetType(FleetType.INVASION);
		else
			setFleetType(FleetType.BASESTRIKE);
		setFP(400);
		setMarines(200);
		getSources();
	}
	
	/**
	 * To be called only when paginated dialog options are required. 
	 * Otherwise we get nested dialogs that take multiple clicks of the exit option to actually exit.
	 * @param dialog
	 */
	protected void setupDelegateDialog(InteractionDialogAPI dialog)
	{
		originalPlugin = dialog.getPlugin();  

		dialog.setPlugin(this);  
		init(dialog);
	}
	
	protected float getColonyCost() {
		float sup = 200 * Global.getSettings().getCommoditySpec(Commodities.SUPPLIES).getBasePrice();
		float mach = 100 * Global.getSettings().getCommoditySpec(Commodities.HEAVY_MACHINERY).getBasePrice();
		float crew = 1000 * Global.getSettings().getCommoditySpec(Commodities.CREW).getBasePrice();
		return sup + mach + crew;
	}
	
	protected float getCostPerMarine() {
		return Global.getSettings().getCommoditySpec(Commodities.MARINES).getBasePrice() * MARINE_COST_MULT;
	}
	
	protected float updateCost() {
		if (fleetType == FleetType.RELIEF) {
			if (target != null) cost = Nex_StabilizePackage.getNominalCost(target);
			else cost = 0;
			cost *= RELIEF_COST_MULT;
		}
		else {
			cost = fp * NexConfig.fleetRequestCostPerFP;
			if (fleetType == FleetType.INVASION) {
				cost += marines * getCostPerMarine();
			}
			else if (fleetType == FleetType.COLONY) {
				cost *= 2;
				cost += getColonyCost();
			}
		}
		
		cost = Math.round(cost);
		
		memory.set(MEM_KEY_COST, cost, 0);
		return cost;
	}
	
	protected int getFP() {
		if (!memory.contains(MEM_KEY_FP))
			memory.set(MEM_KEY_FP, 50, 0);
		
		return (int)memory.getFloat(MEM_KEY_FP);
	}
	
	protected void setFP(int fp) {
		if (fp > maxFP) fp = maxFP;
		memory.set(MEM_KEY_FP, fp, 0);
		this.fp = fp;
		updateCost();
	}
	
	protected int getMarines() {
		if (!memory.contains(MEM_KEY_MARINES))
			memory.set(MEM_KEY_MARINES, 100, 0);
		
		return (int)memory.getLong(MEM_KEY_MARINES);
	}
	
	protected void setMarines(int marines) {
		memory.set(MEM_KEY_MARINES, marines, 0);
		this.marines = marines;
		updateCost();
	}
	
	/**
	 * Writes a paragraph briefly explaining task force cost calculation.
	 * @param text
	 */
	protected void addCostHelpPara(TextPanelAPI text) {
		text.setFontSmallInsignia();
		String fpCost = (int)Math.round(NexConfig.fleetRequestCostPerFP) + "";
		String marineCost = String.format("%.0f", getCostPerMarine());
		String str = StringHelper.getStringAndSubstituteToken("nex_fleetRequest", 
				"fleetCostHelp", "$credits", fpCost);
		if (fleetType == FleetType.INVASION) {
			str += " " + StringHelper.getStringAndSubstituteToken("nex_fleetRequest", 
					"fleetCostHelpMarines", "$credits", marineCost);
			text.addPara(str, Misc.getHighlightColor(), fpCost, marineCost);
		}
		else
			text.addPara(str, Misc.getHighlightColor(), fpCost);
		text.setFontInsignia();
	}
	
	/**
	 * Shows the menu for setting fleet size and marine count.
	 */
	protected void showFleetStrengthMenu()
	{
		addCostHelpPara(dialog.getTextPanel());
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		float max = Math.min(maxFP, 1500);
		if (max < MIN_FP) max = MIN_FP;
		
		opts.addSelector(getString("fleetPoints", true), "fpSelector", Color.cyan, 
				BAR_WIDTH, 48, MIN_FP, max, ValueDisplayMode.VALUE, null);
		opts.setSelectorValue("fpSelector", fp);
		
		if (fleetType == FleetType.INVASION) {
			opts.addSelector(getString("marines", true), "marineSelector", Color.orange, 
					BAR_WIDTH, 48, 100, InvasionIntel.MAX_MARINES_TOTAL, ValueDisplayMode.VALUE, 
				null);
			opts.setSelectorValue("marineSelector", marines);
		}
		
		opts.addOption(StringHelper.getString("back", true), "nex_fleetRequest_setStrength");
		//ExerelinUtils.addDevModeDialogOptions(dialog, true);
	}
	
	/**
	 * Sets fleet points and (later?) marine count from the GUI sliders' values.
	 * @param opts
	 */
	protected void setFleetStrength(OptionPanelAPI opts)
	{		
		setFP(Math.round(opts.getSelectorValue("fpSelector")));
		if (fleetType == FleetType.INVASION)
			setMarines(Math.round(opts.getSelectorValue("marineSelector")));
	}
	
	
	protected float getTimeToLaunch() {
		float time = InvasionFleetManager.getOrganizeTime(fp);
		if (fleetType == FleetType.INVASION)
			time *= 1.25f;
		else if (fleetType == FleetType.COLONY)
			time *= 2f;
		time *= Global.getSettings().getFloat("nex_fleetRequestOrganizeTimeMult");
		
		if (time < 0.1f) time = 0.1f;
		
		if (Global.getSettings().isDevMode()) {
			time = 0.5f;
		}
		
		return time;
	}
	
	protected float getTimeToArrive() {
		float launchTime = getTimeToLaunch();
		
		// estimate travel time
		// assemble stage;
		float travelTime = 3f + 3f * (float) Math.random();
		// travel stage
		travelTime += RouteLocationCalculator.getTravelDays(source.getPrimaryEntity(), target.getPrimaryEntity());
		
		return launchTime + travelTime;
	}
	
	protected void printBudget() {
		TextPanelAPI text = dialog.getTextPanel();
		int capacity = InvasionFleetManager.getManager().getFleetRequestCapacity();
		String available = maxFP + "";
		LabelAPI label = text.addPara(getString("fleetBudget", true) + ": " + available
				+ "/" + capacity);
		label.setHighlight(available, capacity + "");
		
		Color color1;
		if (maxFP == 0)
			color1 = Misc.getGrayColor();
		else if (maxFP < MIN_FP)
			color1 = Misc.getNegativeHighlightColor();
		else 
			color1 = Misc.getHighlightColor();
		Color color2 = capacity > 0 ? Misc.getHighlightColor() : Misc.getGrayColor();
		label.setHighlightColors(color1, color2);
	}
	
	protected void printFleetInfo(boolean showCost, boolean showDist, boolean showTime, boolean showStr) {
		TextPanelAPI text = dialog.getTextPanel();
		Color hl = Misc.getHighlightColor();
		text.setFontSmallInsignia();
		if (showCost){
			String costStr = Misc.getDGSCredits(cost);
			text.addPara(getString("fleetCost", true) + ": " + costStr, hl, costStr);
		}
		if (source != null && target != null)
		{
			float dist = Misc.getDistanceLY(source.getLocationInHyperspace(), target.getLocationInHyperspace());
			String distStr = String.format("%.1f", dist);
			String str = StringHelper.getStringAndSubstituteToken("nex_fleetRequest", "infoDistance", "$dist", distStr);
			text.addPara(str, hl, distStr);
		}
		if (showTime) {
			if (source != null && target != null) {
				String time = Misc.getAtLeastStringForDays((int)Math.ceil(getTimeToArrive()));
				text.addPara(getString("timeToArrive", true) + ": " + time, hl, time);
			}
			else {
				String time = Misc.getAtLeastStringForDays((int)Math.ceil(getTimeToLaunch()));
				text.addPara(getString("timeToLaunch", true) + ": " + time, hl, time);
			}
		}
		if (showStr && target != null && fleetType.isCombat) {
			boolean isInvasion = fleetType == FleetType.INVASION;
			String key = isInvasion ? "infoTargetStrengthGround" : "infoTargetStrength";
			Map<String, String> sub = new HashMap<>();
			
			String defStr = String.format("%.0f", InvasionFleetManager.estimatePatrolStrength(null, 
					target.getFaction(), target.getStarSystem(), 0) + InvasionFleetManager.estimateStationStrength(target));
			String defStrGround =  String.format("%.0f", InvasionRound.getDefenderStrength(target, 1));
			sub.put("$space", defStr);
			if (isInvasion) sub.put("$ground", defStrGround);
			
			String str = StringHelper.getStringAndSubstituteTokens("nex_fleetRequest", key, sub);
			text.addPara(str, hl, defStr, defStrGround);
		}
		
		
		text.setFontInsignia();
	}
	
	protected MarketAPI getSource() {
		if (!memory.contains(MEM_KEY_SOURCE))
			return null;
		String marketId = memory.getString(MEM_KEY_SOURCE);
		if (marketId == null || marketId.isEmpty())
			return null;
		
		MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
		return market;
	}
	
	protected void setSource(String marketId) {
		if (marketId == null)
			setSource((MarketAPI)null);
		setSource(Global.getSector().getEconomy().getMarket(marketId));
	}
	
	protected void setSource(MarketAPI market) {
		if (market == null) {
			memory.unset(MEM_KEY_SOURCE);
			source = null;
			return;
		}
		memory.set(MEM_KEY_SOURCE, market.getId(), 0);
		source = market;
	}
	
	/**
	 * Gets a list of allowed source markets for the fleet.
	 * @return
	 */
	protected List<MarketAPI> getSources() {
		if (memory.contains(MEM_KEY_SOURCES))
			return (List<MarketAPI>)memory.get(MEM_KEY_SOURCES);
		
		Set<MarketAPI> marketsTemp = new HashSet<>();
		marketsTemp.addAll(NexUtilsFaction.getFactionMarkets(Factions.PLAYER));
		Alliance alliance = AllianceManager.getPlayerAlliance(true);
		if (alliance != null) {
			marketsTemp.addAll(alliance.getAllianceMarkets());
		}
		else {
			String commissioner = PlayerFactionStore.getPlayerFactionId();
			if (!commissioner.equals(Factions.PLAYER))
				marketsTemp.addAll(NexUtilsFaction.getFactionMarkets(commissioner));
		}
		
		List<MarketAPI> markets = new ArrayList<>();
		for (MarketAPI market : marketsTemp) {
			if (!NexUtilsMarket.hasWorkingSpaceport(market)) continue;
			if (market.hasCondition(Conditions.ABANDONED_STATION)) continue;
			markets.add(market);
		}
		
		Collections.sort(markets, marketComparatorName);
		
		if (!markets.isEmpty())
			setSource(markets.get(0));
		//else
		//	memory.set(MEM_KEY_SOURCENAME, StringHelper.getString("none"), 0);
		
		memory.set(MEM_KEY_SOURCES, markets, 0);
		
		return markets;
	}
	
	protected MarketAPI getTarget() {
		if (!memory.contains(MEM_KEY_TARGET))
			return null;
		
		MarketAPI market = (MarketAPI)memory.get(MEM_KEY_TARGET);
		return market;
	}
	
	@Deprecated
	protected void setTarget(String marketId) {
		if (marketId == null)
			setTarget((MarketAPI)null);
		else
			setTarget(Global.getSector().getEconomy().getMarket(marketId));
	}
	
	protected void setTarget(MarketAPI market) {
		if (market == null) {
			target = null;
			memory.unset(MEM_KEY_TARGET);
			return;
		}
		memory.set(MEM_KEY_TARGET, market, 0);
		target = market;
		if (fleetType == FleetType.RELIEF) {
			if (source != null && target != null)
				fp = new ReliefFleetIntelAlt(source, target).calcFP();
			updateCost();
			printFleetInfo(true, false, true, false);
		}
	}
	
	public static List<MarketAPI> getHiddenBases() {
		List<MarketAPI> markets = new ArrayList<>();
		for (IntelInfoPlugin intelBase : Global.getSector().getIntelManager().getIntel()) {
			BaseIntelPlugin intel = (BaseIntelPlugin)intelBase;
			if (!intel.isPlayerVisible()) continue;
			if (intel.isEnding()) continue;
			
			if (intel instanceof PirateBaseIntel) {
				PirateBaseIntel base = (PirateBaseIntel)intel;
				markets.add(base.getMarket());
			}
			else if (intel instanceof LuddicPathBaseIntel) {
				LuddicPathBaseIntel base = (LuddicPathBaseIntel)intel;
				markets.add(base.getMarket());
			}
			else if (intel instanceof RespawnBaseIntel) {
				RespawnBaseIntel base = (RespawnBaseIntel)intel;
				markets.add(base.getMarket());
			}
			else {
				if (Global.getSettings().getModManager().isModEnabled("vayrasector")) {
					if (intel instanceof VayraRaiderBaseIntel) {
						VayraRaiderBaseIntel base = (VayraRaiderBaseIntel)intel;
						markets.add(base.getMarket());
					}
				}
			}
		}
		// memory key check
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) 
		{
			if (market.getMemoryWithoutUpdate().getBoolean(MEM_KEY_BASE_STRIKE_TARGET))
				markets.add(market);
		}
		
		return markets;
	}
	
	protected List<MarketAPI> getValidTargets() {
		
		List<MarketAPI> markets;
		switch (fleetType) {
			case INVASION:
				markets = new ArrayList<>();
				for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
					if (NexUtilsMarket.canBeInvaded(market, true))
						markets.add(market);
				}
				break;
			case BASESTRIKE:
				markets = getHiddenBases();
				break;
			case RELIEF:
				markets = new ArrayList<>();
				for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
					if (Nex_StabilizePackage.isAllowed(market))
						markets.add(market);
				}
				break;
			case COLONY:
				markets = new ArrayList<>();
				for (StarSystemAPI system : Global.getSector().getStarSystems()) {
					if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
					for (PlanetAPI planet : system.getPlanets()) {
						MarketAPI market = planet.getMarket();
						if (market == null || !market.isPlanetConditionMarketOnly()) continue;
						if (market.getSurveyLevel() != SurveyLevel.FULL) continue;
						markets.add(market);
					}
				}
				break;
			case DEFENSE:
			case RAID:
			default:
				markets = Global.getSector().getEconomy().getMarketsCopy();
				break;
		}
		List<MarketAPI> toRemove = new ArrayList<>();
		for (MarketAPI market : markets) {
			if (market.getContainingLocation() == null) {
				toRemove.add(market);
			}
			else if (market.getContainingLocation().isHyperspace()) {
				toRemove.add(market);
			}
			// maybe it should also cover the other non-base-strike missions, but leave it in for now?
			else if (market.isHidden() && !fleetType.allowTargetHidden()) 
			{
				toRemove.add(market);
			}
		}
		markets.removeAll(toRemove);
		
		return markets;
	}	
	
	protected FleetType getFleetType() {
		if (!memory.contains(MEM_KEY_TYPE))
			setFleetType(FleetType.INVASION);
		return (FleetType)memory.get(MEM_KEY_TYPE);
	}
	
	protected void setFleetType(FleetType type) {
		memory.set(MEM_KEY_TYPE, type, 0);
		FleetType oldType = fleetType;
		fleetType = type;
		//memory.set(MEM_KEY_TYPENAME, Misc.ucFirst(type.getName()), 0);
		if (fleetType == FleetType.COLONY) {
			setFP(100);
		}
		if (oldType != null && oldType != type) {
			updateCost();
			setTarget((MarketAPI)null);
			
			if (fleetType == FleetType.RELIEF) {
				
			}
			else {
				printFleetInfo(true, false, true, true);
			}
		}
	}
	
	protected boolean launchFleet() {
		MutableValue credits = Global.getSector().getPlayerFleet().getCargo().getCredits();
		if (cost > credits.get() && !Global.getSettings().isDevMode()) {
			return false;
		}
		AddRemoveCommodity.addCreditsLossText((int)cost, dialog.getTextPanel());
		credits.subtract(cost);
		
		FactionAPI attacker = source.getFaction();
		if (fleetType == FleetType.COLONY)
			attacker = Global.getSector().getPlayerFaction();
		
		float timeToLaunch = getTimeToLaunch();
		
		if (fleetType == FleetType.RELIEF) {
			ReliefFleetIntelAlt intel = ReliefFleetIntelAlt.createEvent(source, target);
			setFP(intel.calcFP());
			dialog.getTextPanel().addPara(getString("fleetSpawnMessage"));
			Global.getSector().getIntelManager().addIntelToTextPanel(intel, dialog.getTextPanel());
			intel.setPlayerFee(Math.round(cost));
		}
		else {
			OffensiveFleetIntel intel;
			switch (fleetType) {
				case INVASION:
					intel = new InvasionIntel(attacker, source, target, fp, timeToLaunch);
					//((InvasionIntel)intel).setMarinesTotal((int)(marines));	/ overriden by init
					break;
				case RAID:
					intel = new NexRaidIntel(attacker, source, target, fp, timeToLaunch);
					break;
				case BASESTRIKE:
					intel = new BaseStrikeIntel(attacker, source, target, fp, timeToLaunch);
					break;
				case DEFENSE:
					intel = new DefenseFleetIntel(attacker, source, target, fp, timeToLaunch);
					break;
				case COLONY:
					intel = new ColonyExpeditionIntel(attacker, source, target, fp, timeToLaunch);
					break;
				default:
					return false;
			}
			intel.setPlayerFee(Math.round(cost));
			intel.init();
			if (fleetType == FleetType.INVASION) {
				((InvasionIntel)intel).setMarinesTotal((int)(marines));
			}
			intel.setPlayerSpawned(true);
			setFP(fp);
			dialog.getTextPanel().addPara(getString("fleetSpawnMessage"));
			Global.getSector().getIntelManager().addIntelToTextPanel(intel, dialog.getTextPanel());
		}
		
		InvasionFleetManager.getManager().modifyFleetRequestStock(-fp);
		maxFP = (int)InvasionFleetManager.getManager().getFleetRequestStock();
		if (Global.getSettings().isDevMode()) maxFP = 5000;
				
		// make hostile if needed
		if (fleetType.isAggressive && !target.getFaction().isHostileTo(Factions.PLAYER)) {
			CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
			impact.delta = 0;
			impact.ensureAtBest = RepLevel.HOSTILE;
			RepActionEnvelope envelope = new RepActionEnvelope(RepActions.CUSTOM, 
					impact, null, dialog.getTextPanel(), false);
			Global.getSector().adjustPlayerReputation(envelope, target.getFaction().getId());
		}
		
		printBudget();
		//generateMainMenu();
		
		return true;
	}
	
	protected void generateMainMenu() {
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		String fleetTypeName = Misc.ucFirst(getFleetType().getName());
		opts.addOption(getString("optionFleetType") + ": " + fleetTypeName, "nex_fleetRequest_selectType");
		
		String fpStr = fp + "";
		opts.addOption(getString("optionStrength") + ": " + fpStr, "nex_fleetRequest_strengthMenu");
		opts.setEnabled("nex_fleetRequest_strengthMenu", fleetType != FleetType.RELIEF && fleetType != FleetType.COLONY);
		
		String sourceName = source == null ? StringHelper.getString("none") : source.getName();
		opts.addOption(getString("optionSource") + ": " + sourceName, "nex_fleetRequest_selectSource");
		
		String targetName = target == null ? StringHelper.getString("none") : target.getName();
		if (target != null) {
			targetName += ", " + target.getContainingLocation().getNameWithTypeIfNebula();
		}
		opts.addOption(getString("optionTarget") + ": " + targetName, "nex_fleetRequest_selectTarget");
		
		opts.addOption(StringHelper.getString("proceed", true), OPTION_PROCEED);
		float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
		boolean devmode = Global.getSettings().isDevMode();
		if (maxFP < MIN_FP && !devmode) {
			opts.setEnabled("nex_fleetRequest_strengthMenu", false);
			opts.setEnabled(OPTION_PROCEED, false);
			String tooltip = getString("tooltipInsufficientFP");
			opts.setTooltip(OPTION_PROCEED, tooltip);
		}		
		else if (cost > credits && !devmode) {
			opts.setEnabled(OPTION_PROCEED, false);
			String tooltip = getString("tooltipInsufficientFunds");
			String costStr = Misc.getWithDGS(cost);
			String creditsStr = Misc.getWithDGS(credits);
			tooltip = StringHelper.substituteToken(tooltip, "$cost", costStr);
			tooltip = StringHelper.substituteToken(tooltip, "$credits", creditsStr);
			opts.setTooltip(OPTION_PROCEED, tooltip);
			opts.setTooltipHighlights(OPTION_PROCEED, costStr, creditsStr);
			opts.setTooltipHighlightColors(OPTION_PROCEED, Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
		}
		else if (source == null || target == null) {
			opts.setEnabled(OPTION_PROCEED, false);
		}
		else {
			String confirmMessage;
			if (fleetType.isAggressive && !target.getFaction().isHostileTo(Factions.PLAYER)) {
				confirmMessage = getString("proceedConfirmNonHostile");
				confirmMessage = StringHelper.substituteToken(confirmMessage, "$TheFaction", 
						Misc.ucFirst(target.getFaction().getDisplayNameWithArticle()));
			}
			else {
				confirmMessage = getString("proceedConfirm");
			}
			opts.addOptionConfirmation(OPTION_PROCEED, confirmMessage, 
					StringHelper.getString("yes", true), StringHelper.getString("no", true));
		}
		
		Object exitOpt = "exerelinMarketSpecial";
		if (memory.getBoolean("$nex_specialDialog")) {
			//dialog.getTextPanel().addPara("Trying special exit opt");
			exitOpt = FieldOptionsScreenScript.FactionDirectoryDialog.Menu.INIT;
		}
			
		opts.addOption(Misc.ucFirst(StringHelper.getString("cancel")), exitOpt);
		opts.setShortcut(exitOpt, Keyboard.KEY_ESCAPE, false, false, false, false);
		//ExerelinUtils.addDevModeDialogOptions(dialog, true);
	}
	
	/**
	 * Clears the {@code options} and {@code optionsAllPages} lists, and adds the back button.
	 */
	protected void resetOpts() {
		options.clear();
		optionsAllPages.clear();
		addOptionAllPages(StringHelper.getString("back", true), "nex_fleetRequest_main");
	}
	
	protected String getMarketOptionString(MarketAPI market) {
		String entry = StringHelper.getString("exerelin_markets", "marketDirectoryEntry");
		LocationAPI loc = market.getContainingLocation();
		String locName = NexUtilsAstro.getLocationName(loc, true);
			
		entry = StringHelper.substituteToken(entry, "$market", market.getName());
		entry = StringHelper.substituteToken(entry, "$location", locName);
		entry = StringHelper.substituteToken(entry, "$size", market.getSize() + "");
		
		return entry;
	}
	
	/**
	 * List source markets in option panel.
	 */
	protected void listSources()
	{
		List<MarketAPI> sources = getSources();
		List<SectorEntityToken> sourceTokens = new ArrayList<>();
		for (MarketAPI market : sources) {
			sourceTokens.add(market.getPrimaryEntity());
		}
		
		final InteractionDialogAPI dialogF = dialog;
		NexUtilsMarket.pickEntityDestination(dialogF, sourceTokens, StringHelper.getString("confirm", true), 
				new CampaignEntityPickerWrapper(){
			@Override
			public void reportEntityPicked(SectorEntityToken token) {
				MarketAPI prevSource = source;
				setSource(token.getMarket());
				dialogF.getTextPanel().addPara(getString("optionSource") + ": %s", 
						Misc.getHighlightColor(), source.getName());
				boolean sameSource = prevSource == source;
				if (!sameSource && source != null && target != null)
				{
					printFleetInfo(false, false, true, true);
				}
				// this works around not being able to exit the menu because our
				// option selections are no longer reaching the FactionDirectoryDialog
				// picking another rules dialog option fixes it for some reason
				dialogF.getPlugin().optionSelected(null, "nex_fleetRequest_main");
			}

			@Override
			public void reportEntityPickCancelled() {
				dialogF.getPlugin().optionSelected(null, "nex_fleetRequest_main");
			}

			@Override
			public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) 
			{
				createInfoTextExt(info, entity, target != null ? target.getPrimaryEntity() : null, false);
			}
		}, getMapArrows());
	}

	protected List<IntelInfoPlugin.ArrowData> getMapArrows() {
		List<IntelInfoPlugin.ArrowData> arrows = new ArrayList<>();
		if (source != null && target != null) {
			IntelInfoPlugin.ArrowData arrow = new IntelInfoPlugin.ArrowData(source.getPrimaryEntity(), target.getPrimaryEntity());
			arrow.alphaMult = Math.min(arrow.alphaMult * 2, 1);
			arrows.add(arrow);
		}
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(RaidIntel.class)) {
			try {
				RaidIntel raid = (RaidIntel)intel;
				IntelInfoPlugin.ArrowData arrow = new IntelInfoPlugin.ArrowData(raid.getAssembleStage().getSources().get(0).getPrimaryEntity(),
						raid.getSystem().getHyperspaceAnchor());
				arrow.color = raid.getFaction().getBaseUIColor();
				arrows.add(arrow);
			} catch (Exception ex) {
				// probably something doesn't exist, do nothing
			}

		}

		return arrows;
	}
	
	/**
	 * Generate map for picking target.
	 */
	protected void listTargets()
	{
		List<MarketAPI> targets = getValidTargets();
		List<SectorEntityToken> targetTokens = new ArrayList<>();
		for (MarketAPI market : targets) {
			targetTokens.add(market.getPrimaryEntity());
		}
		
		final InteractionDialogAPI dialogF = dialog;
		NexUtilsMarket.pickEntityDestination(dialogF, targetTokens, 
				StringHelper.getString("confirm", true), new CampaignEntityPickerWrapper(){
			@Override
			public void reportEntityPicked(SectorEntityToken token) {
				MarketAPI prevTarget = target;
				setTarget(token.getMarket());
				dialogF.getTextPanel().addPara(getString("optionTarget") + ": %s", 
						Misc.getHighlightColor(), target.getName());
				boolean sameTarget = prevTarget == target;
				if (!sameTarget && source != null && target != null)
				{
					printFleetInfo(false, false, true, true);
				}
				// this works around not being able to exit the menu because our
				// option selections are no longer reaching the FactionDirectoryDialog
				// picking another rules dialog option fixes it for some reason
				dialogF.getPlugin().optionSelected(null, "nex_fleetRequest_main");
			}

			@Override
			public void reportEntityPickCancelled() {
				dialogF.getPlugin().optionSelected(null, "nex_fleetRequest_main");
			}

			@Override
			public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) 
			{
				createInfoTextExt(info, entity, source != null ? source.getPrimaryEntity() : null, true);
			}
		}, getMapArrows());
	}
	
	public void createInfoTextExt(TooltipMakerAPI info, SectorEntityToken entity, 
			SectorEntityToken other, boolean isTarget) 
	{
		try {
			createInfoTextBasic(info, entity, other);
			MarketAPI market = entity.getMarket();

			if (isTarget && fleetType != FleetType.RELIEF) {
				String infoStr;
				LabelAPI text;
				float def = InvasionFleetManager.estimatePatrolStrength(null, 
						entity.getFaction(), market.getStarSystem(), 0) 
						+ InvasionFleetManager.estimateStationStrength(market);
				def = NexUtils.getEstimateNum(def, 10);
				String defStr = String.format("%.0f", def);
				if (fleetType == FleetType.INVASION) {
					infoStr = getString("targetEntryGroundTooltip");
					infoStr = StringHelper.substituteToken(infoStr, "$space", defStr);
					float groundDef = InvasionRound.getDefenderStrength(market, 1);
					groundDef = NexUtils.getEstimateNum(groundDef, 10);
					String groundDefStr = String.format("%.0f", groundDef);
					infoStr = StringHelper.substituteToken(infoStr, "$ground", groundDefStr);

					text = info.addPara(infoStr, 0);
					text.setHighlight(defStr, groundDefStr);
				}
				else if (fleetType == FleetType.COLONY) {
					infoStr = getString("targetEntryColonyTooltip");
					String typeStr = Misc.ucFirst(market.getPlanetEntity().getTypeNameWithWorld());
					infoStr = StringHelper.substituteToken(infoStr, "$type", typeStr);
					text = info.addPara(infoStr, 0);
					text.setHighlight(typeStr);
					text.setHighlightColors(market.getPlanetEntity().getSpec().getIconColor());
				}
				else {
					infoStr = getString("targetEntryTooltip");
					infoStr = StringHelper.substituteToken(infoStr, "$space", defStr);
					text = info.addPara(infoStr, 0);
					text.setHighlight(defStr);
				}
			}
		} catch (Exception ex) {
			Global.getLogger(this.getClass()).error("Error printing target picked info", ex);
		}
	}
	
	public static void createInfoTextBasic(TooltipMakerAPI info, SectorEntityToken entity, 
			SectorEntityToken other) {
		MarketAPI market = entity.getMarket();
		String distStr;
		if (other != null) {
			float dist = Misc.getDistanceLY(other, entity);
			distStr = Math.round(dist) + "";
		}
		else {
			distStr = "?";
		}
		String str;
		if (market != null) {
			str = StringHelper.getString("exerelin_markets", "marketDirectoryEntryForPicker");
			String factionName = market.getFaction().getDisplayName();
			String size = market.getSize() + "";
			str = StringHelper.substituteToken(str, "$market", market.getName());
			str = StringHelper.substituteToken(str, "$faction", factionName);
			str = StringHelper.substituteToken(str, "$size", size);
			str = StringHelper.substituteToken(str, "$distance", distStr);
			
			Color hl = Misc.getHighlightColor();
			LabelAPI text = info.addPara(str, 0);
			text.setHighlight(factionName, size, distStr);
			text.setHighlightColors(market.getFaction().getBaseUIColor(), hl, hl);
		}
		else {
			str = StringHelper.getString("exerelin_markets", "marketDirectoryEntryForPickerNoMarket");
			str = StringHelper.substituteToken(str, "$target", entity.getName());
			str = StringHelper.substituteToken(str, "$distance", distStr);
			
			Color hl = Misc.getHighlightColor();
			info.addPara(str, 0, hl, distStr);
		}	
	}
	
	protected static String getString(String id) {
		return StringHelper.getString("nex_fleetRequest", id);
	}
	
	protected static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_fleetRequest", id, ucFirst);
	}
	
	public static final Comparator<MarketAPI> marketComparatorName = new Comparator<MarketAPI>() {
		public int compare(MarketAPI m1, MarketAPI m2) {
			return m1.getName().compareTo(m2.getName());
		}};
	
	public enum FleetType {
		INVASION(true, true), 
		BASESTRIKE(true, true),
		RAID(true, true), 
		DEFENSE(false, true), 
		COLONY(false, false), 
		RELIEF(false, false);
		
		public final boolean isAggressive;
		public final boolean isCombat;
		
		private FleetType(boolean isAggressive, boolean isCombat) {
			this.isAggressive = isAggressive;
			this.isCombat = isCombat;
		}
		
		public boolean allowTargetHidden() {
			return this == BASESTRIKE;
		}
		
		public static FleetType getTypeFromString(String str) {
			return FleetType.valueOf(StringHelper.flattenToAscii(str.toUpperCase(Locale.ROOT)));
		}
		
		public String getName() {
			return getString("fleetType_" + StringHelper.flattenToAscii(toString().toLowerCase(Locale.ROOT)));
		}
	}
}