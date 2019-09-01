package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.MutableValue;
import data.scripts.campaign.bases.VayraRaiderBaseIntel;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.RespawnBaseIntel;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.lwjgl.input.Keyboard;

/**
 * Handles fleet requests: spawns fleets with specific origins and objectives based on their targets.
 */
public class Nex_FleetRequest extends PaginatedOptions {
	
	public static final String MEM_KEY_TYPE = "$nex_fleetRequest_type";
	public static final String MEM_KEY_FP = "$nex_fleetRequest_fp";
	public static final String MEM_KEY_MARINES = "$nex_fleetRequest_marines";
	public static final String MEM_KEY_SOURCES = "$nex_fleetRequest_sources";
	public static final String MEM_KEY_SOURCE = "$nex_fleetRequest_source";
	public static final String MEM_KEY_FACTIONS = "$nex_fleetRequest_factions";
	public static final String MEM_KEY_FACTION = "$nex_fleetRequest_faction";
	public static final String MEM_KEY_TARGETS = "$nex_fleetRequest_targets";
	public static final String MEM_KEY_TARGET = "$nex_fleetRequest_target";
	public static final String MEM_KEY_COST = "$nex_fleetRequest_cost";
	public static final String OPTION_MAIN = "nex_fleetRequest_main";
	public static final String OPTION_PROCEED = "nex_fleetRequest_proceed";
	public static final String TYPE_OPTION_PREFIX = "nex_fleetRequest_setType_";
	public static final String SOURCE_OPTION_PREFIX = "nex_fleetRequest_setSource_";
	public static final String TARGET_OPTION_PREFIX = "nex_fleetRequest_setTarget_";
	public static final String FACTION_OPTION_PREFIX = "nex_fleetRequest_setFaction_";
	public static final float BAR_WIDTH = 256;
	public static final float MARINE_COST_MAX_MOD = 2;
	
	protected MemoryAPI memory;
	protected String option;
	protected MarketAPI source;
	protected FactionAPI faction;
	protected MarketAPI target;
	protected float fp;
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
				printFleetInfo(false, true, false, false);
				break;
			case "factionMenu":
				setupDelegateDialog(dialog);
				listFactions();
				break;
			case "setFaction":
				String factionId = option.substring(FACTION_OPTION_PREFIX.length());
				setFaction(factionId);
				printFleetInfo(false, true, false, true);
				break;
			case "targetMenu":
				setupDelegateDialog(dialog);
				listTargets();
				break;
			case "setTarget":
				String targetMarketId = option.substring(TARGET_OPTION_PREFIX.length());
				setTarget(targetMarketId);
				printFleetInfo(false, true, false, true);
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
		faction = getFaction();
		if (faction == null)
			target = null;
		else
			target = getTarget();
		fp = getFP();
		marines = getMarines();
		
		updateCost();
	}
	
	protected void initFirstTime() {
		memory.set("$nex_fleetRequest_initComplete", true, 0);
		setFleetType(FleetType.INVASION);
		setFP(400);
		setMarines(200);
		getSources();
		getFactions();
		getTargets();
	}
	
	protected void resetTargets() {
		memory.unset(MEM_KEY_TARGETS);
		memory.unset(MEM_KEY_TARGET);
		getTargets();
	}
	
	protected void resetFactions() {
		memory.unset(MEM_KEY_FACTIONS);
		memory.unset(MEM_KEY_FACTION);
		getFactions();
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
	
	protected float updateCost() {
		cost = fp * ExerelinConfig.fleetRequestCostPerFP;
		if (fleetType == FleetType.INVASION) {
			float mult = 1 + 2 * (float)marines/InvasionIntel.MAX_MARINES;
			cost *= mult;
		}
		cost = Math.round(cost);
		
		memory.set(MEM_KEY_COST, cost, 0);
		return cost;
	}
	
	protected float getFP() {
		if (!memory.contains(MEM_KEY_FP))
			memory.set(MEM_KEY_FP, 50, 0);
		
		return memory.getFloat(MEM_KEY_FP);
	}
	
	protected void setFP(float fp) {
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
		String fpCost = Math.round(ExerelinConfig.fleetRequestCostPerFP) + "";
		String marineCost = (MARINE_COST_MAX_MOD + 1) + "";
		String str = StringHelper.getStringAndSubstituteToken("nex_fleetRequest", 
				"fleetCostHelp", "$credits", fpCost);
		if (fleetType == FleetType.INVASION) {
			str += " " + StringHelper.getStringAndSubstituteToken("nex_fleetRequest", 
					"fleetCostHelpMarines", "$marineMult", marineCost);
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
		opts.addSelector(getString("fleetPoints", true), "fpSelector", Color.cyan, 
				BAR_WIDTH, 48, 100, 1500, ValueDisplayMode.VALUE, null);
		opts.setSelectorValue("fpSelector", fp);
		
		if (fleetType == FleetType.INVASION) {
			opts.addSelector(getString("marinesPerFleet", true), "marineSelector", Color.orange, 
					BAR_WIDTH, 48, 100, InvasionIntel.MAX_MARINES, ValueDisplayMode.VALUE, 
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
		time *= Global.getSettings().getFloat("nex_fleetRequestOrganizeTimeMult");
		
		if (time < 0.1f) time = 0.1f;
		
		return time;
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
			String time = Misc.getAtLeastStringForDays((int)Math.ceil(getTimeToLaunch()));
			text.addPara(getString("timeToLaunch", true) + ": " + time, hl, time);
		}
		if (showStr && faction != null && target != null) {
			boolean isInvasion = fleetType == FleetType.INVASION;
			String key = isInvasion ? "infoTargetStrengthGround" : "infoTargetStrength";
			Map<String, String> sub = new HashMap<>();
			
			String defStr = String.format("%.0f", InvasionFleetManager.estimateDefensiveStrength(null, 
					faction, target.getStarSystem(), 0));
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
		
		List<MarketAPI> marketsTemp = ExerelinUtilsFaction.getFactionMarkets(Factions.PLAYER);
		String commissioner = PlayerFactionStore.getPlayerFactionId();
		if (!commissioner.equals(Factions.PLAYER))
			marketsTemp.addAll(ExerelinUtilsFaction.getFactionMarkets(commissioner));
		
		List<MarketAPI> markets = new ArrayList<>();
		for (MarketAPI market : marketsTemp) {
			if (!market.hasSpaceport()) continue;
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
	
	protected FactionAPI getFaction() {
		if (!memory.contains(MEM_KEY_FACTION))
			return null;
		String factionId = memory.getString(MEM_KEY_FACTION);
		if (factionId == null || factionId.isEmpty())
			return null;
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		return faction;
	}
	
	protected void setFaction(String factionId) {
		if (factionId == null) {
			memory.unset(MEM_KEY_FACTION);
			faction = null;
			return;
		}
		memory.set(MEM_KEY_FACTION, factionId, 0);
		faction = Global.getSector().getFaction(factionId);
		//memory.set(MEM_KEY_FACTIONNAME, Nex_FactionDirectoryHelper.getFactionDisplayName(factionId), 0);
		resetTargets();
	}
	
	/**
	 * Gets a list of possible target factions.
	 * @return
	 */
	protected List<FactionAPI> getFactions() {
		if (memory.contains(MEM_KEY_FACTIONS))
			return (List<FactionAPI>)memory.get(MEM_KEY_FACTIONS);
		
		Set<FactionAPI> factionsSet = new HashSet<>();
		if (fleetType == FleetType.BASESTRIKE) {
			for (MarketAPI market : getHiddenBases(null)) {
				factionsSet.add(market.getFaction());
			}
		}
		else {
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
				factionsSet.add(market.getFaction());
			}
		}
		
		factionsSet.remove(Global.getSector().getPlayerFaction());
		factionsSet.remove(PlayerFactionStore.getPlayerFaction());
		
		List<FactionAPI> factions = new ArrayList<>(factionsSet);
		Collections.sort(factions, Nex_FactionDirectoryHelper.NAME_COMPARATOR_PLAYER_FIRST);
		
		if (!factions.isEmpty())
			setFaction(factions.get(0).getId());
		else
			setFaction(null);
		
		memory.set(MEM_KEY_FACTIONS, factions, 0);
		
		return factions;
	}
	
	protected MarketAPI getTarget() {
		if (!memory.contains(MEM_KEY_TARGET))
			return null;
		
		String marketId = memory.getString(MEM_KEY_TARGET);
		if (marketId == null || marketId.isEmpty())
			return null;
		
		MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
		return market;
	}
	
	protected void setTarget(String marketId) {
		if (marketId == null)
			setTarget((MarketAPI)null);
		setTarget(Global.getSector().getEconomy().getMarket(marketId));
	}
	
	protected void setTarget(MarketAPI market) {
		if (market == null) {
			target = null;
			memory.unset(MEM_KEY_TARGET);
			return;
		}
		memory.set(MEM_KEY_TARGET, market.getId(), 0);
		target = market;
	}
	
	public static List<MarketAPI> getHiddenBases(FactionAPI faction) {
		List<MarketAPI> markets = new ArrayList<>();
		for (IntelInfoPlugin intelBase : Global.getSector().getIntelManager().getIntel()) {
			BaseIntelPlugin intel = (BaseIntelPlugin)intelBase;
			if (!intel.isPlayerVisible()) continue;
			if (intel.isEnding()) continue;
			
			if (intel instanceof PirateBaseIntel) {
				PirateBaseIntel base = (PirateBaseIntel)intel;
				if (faction != null && base.getMarket().getFaction() != faction) 
					continue;
				markets.add(base.getMarket());
			}
			else if (intel instanceof LuddicPathBaseIntel) {
				LuddicPathBaseIntel base = (LuddicPathBaseIntel)intel;
				if (faction != null && base.getMarket().getFaction() != faction) 
					continue;
				markets.add(base.getMarket());
			}
			else if (intel instanceof RespawnBaseIntel) {
				RespawnBaseIntel base = (RespawnBaseIntel)intel;
				if (faction != null && base.getMarket().getFaction() != faction) 
					continue;
				markets.add(base.getMarket());
			}
			else {
				if (Global.getSettings().getModManager().isModEnabled("vayrasector")) {
					if (intel instanceof VayraRaiderBaseIntel) {
						VayraRaiderBaseIntel base = (VayraRaiderBaseIntel)intel;
						if (faction != null && base.getMarket().getFaction() != faction) 
							continue;
						markets.add(base.getMarket());
					}
				}
			}
		}
		return markets;
	}
	
	protected List<MarketAPI> getTargets() {
		if (faction == null) return null;
		
		if (memory.contains(MEM_KEY_TARGETS))
			return (List<MarketAPI>)memory.get(MEM_KEY_TARGETS);
		
		List<MarketAPI> markets;
		switch (fleetType) {
			case INVASION:
				markets = ExerelinUtilsFaction.getFactionMarkets(faction.getId(), true);
				break;
			case BASESTRIKE:
				markets = getHiddenBases(faction);
				break;
			case DEFENSE:
			case RAID:
			default:
				markets = ExerelinUtilsFaction.getFactionMarkets(faction.getId(), false);
				break;
		}
		
		Collections.sort(markets, marketComparatorName);
		
		if (!markets.isEmpty())
			setTarget(markets.get(0).getId());
		else
			setTarget((String)null);
		
		memory.set(MEM_KEY_TARGETS, markets, 0);
		
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
		if (oldType != null && oldType != type) {
			resetFactions();
			updateCost();
			printFleetInfo(true, false, false, true);
		}
	}
	
	protected boolean launchFleet() {
		MutableValue credits = Global.getSector().getPlayerFleet().getCargo().getCredits();
		if (cost > credits.get()) {
			return false;
		}
		AddRemoveCommodity.addCreditsLossText((int)cost, dialog.getTextPanel());
		credits.subtract(cost);
		
		FactionAPI attacker = source.getFaction();	//PlayerFactionStore.getPlayerFaction();
		float timeToLaunch = getTimeToLaunch();
		OffensiveFleetIntel intel;
		switch (fleetType) {
			case INVASION:
				intel = new InvasionIntel(attacker, source, target, fp, timeToLaunch);
				((InvasionIntel)intel).setMarinesPerFleet((int)(marines));
				break;
			case RAID:
				intel = new NexRaidIntel(attacker, source, target, fp, timeToLaunch);
				break;
			case BASESTRIKE:
				intel = new BaseStrikeIntel(attacker, source, target, fp, timeToLaunch);
				break;
			case DEFENSE:
			default:
				return false;
		}
		intel.init();
		intel.setPlayerSpawned(true);
		
		dialog.getTextPanel().addPara(getString("fleetSpawnMessage"));
		Global.getSector().getIntelManager().addIntelToTextPanel(intel, dialog.getTextPanel());
		
		// make hostile if needed
		if (fleetType != FleetType.DEFENSE && !faction.isHostileTo(Factions.PLAYER)) {
			CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
			impact.delta = 0;
			impact.ensureAtBest = RepLevel.HOSTILE;
			RepActionEnvelope envelope = new RepActionEnvelope(RepActions.CUSTOM, 
					impact, null, dialog.getTextPanel(), false);
			Global.getSector().adjustPlayerReputation(envelope, faction.getId());
		}
		
		return true;
	}
	
	protected void generateMainMenu() {
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		String fleetTypeName = Misc.ucFirst(getFleetType().getName());
		opts.addOption(getString("optionFleetType") + ": " + fleetTypeName, "nex_fleetRequest_selectType");
		
		String fpStr = String.format("%.0f", fp);
		opts.addOption(getString("optionStrength") + ": " + fpStr, "nex_fleetRequest_strengthMenu");
		
		String sourceName = source == null ? StringHelper.getString("none") : source.getName();
		opts.addOption(getString("optionSource") + ": " + sourceName, "nex_fleetRequest_selectSource");
		
		String factionName = faction == null ? StringHelper.getString("none") 
				: Nex_FactionDirectoryHelper.getFactionDisplayName(faction);
		opts.addOption(getString("optionFaction") + ": " + factionName, "nex_fleetRequest_selectFaction");
		
		String targetName = target == null ? StringHelper.getString("none") : target.getName();
		opts.addOption(getString("optionTarget") + ": " + targetName, "nex_fleetRequest_selectTarget");
		if (faction == null)
			opts.setEnabled("nex_fleetRequest_selectTarget", false);
		
		opts.addOption(StringHelper.getString("proceed", true), OPTION_PROCEED);
		float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
		if (cost > credits) {
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
		else if (source == null || faction == null || target == null) {
			opts.setEnabled(OPTION_PROCEED, false);
		}
		else {
			String confirmMessage;
			if (fleetType != FleetType.DEFENSE && !faction.isHostileTo(Factions.PLAYER)) {
				confirmMessage = getString("proceedConfirmNonHostile");
				confirmMessage = StringHelper.substituteToken(confirmMessage, "$TheFaction", 
						Misc.ucFirst(faction.getDisplayNameWithArticle()));
			}
			else {
				confirmMessage = getString("proceedConfirm");
			}
			opts.addOptionConfirmation(OPTION_PROCEED, confirmMessage, 
					StringHelper.getString("yes", true), StringHelper.getString("no", true));
		}
		
		
		String exitOpt = "exerelinMarketSpecial";
		if (memory.getBoolean("$specialDialog"))
			exitOpt = "continueCutComm";		
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
	
	/**
	 * List target factions in option panel.
	 */
	protected void listFactions()
	{
		resetOpts();
		List<FactionAPI> factions = getFactions();
		
		for (FactionAPI faction : factions)
		{
			String optId = FACTION_OPTION_PREFIX + faction.getId();
			String text = Nex_FactionDirectoryHelper.getFactionDisplayName(faction);

			addOption(text, optId);
		}
		
		showOptions();
	}
	
	protected String getMarketOptionString(MarketAPI market) {
		String entry = StringHelper.getString("exerelin_markets", "marketDirectoryEntry");
		LocationAPI loc = market.getContainingLocation();
		String locName = loc.getName();
		if (loc instanceof StarSystemAPI)
				locName = ((StarSystemAPI)loc).getBaseName();
			
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
		resetOpts();
		List<MarketAPI> sources = getSources();
		
		for (MarketAPI market : sources)
		{
			String optId = SOURCE_OPTION_PREFIX + market.getId();
			String text = getMarketOptionString(market);

			addOption(text, optId);
		}
		
		showOptions();
	}
	
	/**
	 * List target markets in option panel.
	 */
	protected void listTargets()
	{
		resetOpts();
		List<MarketAPI> targets = getTargets();
		
		Map<String, String> tooltips = new HashMap<>();
		Map<String, Highlights> highlights = new HashMap<>();
		
		for (MarketAPI market : targets)
		{
			String optId = TARGET_OPTION_PREFIX + market.getId();
			
			// option text
			String text = getMarketOptionString(market);
			addOption(text, optId);
			
			// tooltip
			String tooltip;
			Highlights hl = new Highlights();
			Color hlCol = Misc.getHighlightColor();
			
			String defStr = String.format("%.1f", InvasionFleetManager.estimateDefensiveStrength(null, 
					faction, market.getStarSystem(), 0));
			if (fleetType == FleetType.INVASION) {
				tooltip = getString("targetEntryGroundTooltip");
				tooltip = StringHelper.substituteToken(tooltip, "$space", defStr);
				String groundDefStr = String.format("%.1f", InvasionRound.getDefenderStrength(market, 1));
				tooltip = StringHelper.substituteToken(tooltip, "$ground", groundDefStr);
				hl.setText(defStr, groundDefStr);
				hl.setColors(hlCol, hlCol);
			}
			else {
				tooltip = getString("targetEntryTooltip");
				tooltip = StringHelper.substituteToken(tooltip, "$space", defStr);
				hl.setText(defStr);
				hl.setColors(hlCol);
			}
			tooltips.put(optId, tooltip);
			highlights.put(optId, hl);
		}
		
		showOptions();
		for (Map.Entry<String, String> tmp : tooltips.entrySet())
		{
			String optId = tmp.getKey();
			String tooltip = tmp.getValue();
			Highlights hl = highlights.get(optId);
			dialog.getOptionPanel().setTooltip(optId, tooltip);
			dialog.getOptionPanel().setTooltipHighlights(optId, hl.getText());
			dialog.getOptionPanel().setTooltipHighlightColors(optId, hl.getColors());
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
		INVASION, BASESTRIKE, RAID, DEFENSE;
		
		public static FleetType getTypeFromString(String str) {
			return FleetType.valueOf(StringHelper.flattenToAscii(str.toUpperCase(Locale.ROOT)));
		}
		
		public String getName() {
			return getString("fleetType_" + StringHelper.flattenToAscii(toString().toLowerCase(Locale.ROOT)));
		}
	}
}