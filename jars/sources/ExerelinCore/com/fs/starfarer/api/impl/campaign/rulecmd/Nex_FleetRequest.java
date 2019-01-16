package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.MutableValue;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.InvasionIntel;
import exerelin.campaign.intel.OffensiveFleetIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
	
	protected MemoryAPI memory;
	protected String option;
	protected MarketAPI source;
	protected FactionAPI faction;
	protected MarketAPI target;
	protected float fp;
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
				printFleetInfo();
				break;
			case "mainMenu":
				generateMainMenu(dialog.getOptionPanel());
				break;
			case "typeMenu":
				
				break;
			case "setType":
				
				break;
			case "strengthMenu":
				showFleetStrengthMenu(dialog.getOptionPanel());
				break;
			case "setStrength":
				setFleetStrength(dialog.getOptionPanel());
				printFleetInfo();
				break;
			case "sourceMenu":
				setupDelegateDialog(dialog);
				listSources();
				break;
			case "setSource":
				String sourceMarketId = option.substring(SOURCE_OPTION_PREFIX.length());
				setSource(sourceMarketId);
				printFleetInfo();
				break;
			case "factionMenu":
				setupDelegateDialog(dialog);
				listFactions();
				break;
			case "setFaction":
				String factionId = option.substring(FACTION_OPTION_PREFIX.length());
				setFaction(factionId);
				printFleetInfo();
				break;
			case "targetMenu":
				setupDelegateDialog(dialog);
				listTargets();
				break;
			case "setTarget":
				String targetMarketId = option.substring(TARGET_OPTION_PREFIX.length());
				setTarget(targetMarketId);
				printFleetInfo();
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
		target = getTarget();
		fp = getFP();
		
		updateCost();
	}
	
	protected void initFirstTime() {
		memory.set("$nex_fleetRequest_initComplete", true, 0);
		setFleetType(FleetType.INVASION);
		setFP(150);
		getSources();
		getFactions();
		getTargets();
	}
	
	protected void resetTargets() {
		memory.unset(MEM_KEY_TARGETS);
		memory.unset(MEM_KEY_TARGET);
		getTargets();
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
		if (fleetType == FleetType.INVASION)
			cost *= 2;
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
	
	protected void showFleetStrengthMenu(OptionPanelAPI opts)
	{
		opts.clearOptions();
		opts.addSelector(getString("fleetPoints", true), "fpSelector", Color.cyan, BAR_WIDTH, 48, 100, 1500, ValueDisplayMode.VALUE, 
				null);
		opts.setSelectorValue("fpSelector", fp);
		
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
	}
	
	
	protected float getTimeToLaunch() {
		float time = InvasionFleetManager.getOrganizeTime(fp);
		if (fleetType == FleetType.INVASION)
			time *= 1.5f;
		return time;
	}
	
	protected void printFleetInfo() {
		TextPanelAPI text = dialog.getTextPanel();
		text.setFontSmallInsignia();
		String costStr = Misc.getDGSCredits(cost);
		text.addPara(getString("fleetCost", true) + ": " + costStr, Misc.getHighlightColor(), costStr);
		if (source != null && target != null)
		{
			float dist = Misc.getDistanceLY(source.getLocationInHyperspace(), target.getLocationInHyperspace());
			String distStr = String.format("%.1f", dist);
			String str = StringHelper.getStringAndSubstituteToken("nex_fleetRequest", "infoDistance", "$dist", distStr);
			text.addPara(str, Misc.getHighlightColor(), distStr);
		}
		String time = Misc.getAtLeastStringForDays((int)Math.ceil(getTimeToLaunch()));
		text.addPara(getString("timeToLaunch", true) + ": " + time, Misc.getHighlightColor(), time);
		
		text.setFontInsignia();
	}
	
	protected MarketAPI getSource() {
		String marketId = memory.getString(MEM_KEY_SOURCE);
		if (marketId == null || marketId.isEmpty())
			return null;
		
		MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
		return market;
	}
	
	protected void setSource(String marketId) {
		setSource(Global.getSector().getEconomy().getMarket(marketId));
	}
	
	protected void setSource(MarketAPI market) {
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
		
		List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(Factions.PLAYER);
		String commissioner = PlayerFactionStore.getPlayerFactionId();
		if (!commissioner.equals(Factions.PLAYER))
			markets.addAll(ExerelinUtilsFaction.getFactionMarkets(commissioner));
		
		Collections.sort(markets, marketComparatorName);
		
		if (!markets.isEmpty())
			setSource(markets.get(0));
		//else
		//	memory.set(MEM_KEY_SOURCENAME, StringHelper.getString("none"), 0);
		
		memory.set(MEM_KEY_SOURCES, markets, 0);
		
		return markets;
	}
	
	protected FactionAPI getFaction() {
		String factionId = memory.getString(MEM_KEY_FACTION);
		if (factionId == null || factionId.isEmpty())
			return null;
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		return faction;
	}
	
	protected void setFaction(String factionId) {
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
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			factionsSet.add(market.getFaction());
		}
		factionsSet.remove(Global.getSector().getPlayerFaction());
		factionsSet.remove(PlayerFactionStore.getPlayerFaction());
		
		List<FactionAPI> factions = new ArrayList<>(factionsSet);
		Collections.sort(factions, new Comparator<FactionAPI>()
		{
			@Override
			public int compare(FactionAPI f1, FactionAPI f2)
			{
				String n1 = Nex_FactionDirectoryHelper.getFactionDisplayName(f1);
				String n2 = Nex_FactionDirectoryHelper.getFactionDisplayName(f2);
				return n1.compareTo(n2);
			}
		});
		
		if (!factions.isEmpty())
			setFaction(factions.get(0).getId());
		//else
		//	memory.set(MEM_KEY_FACTIONNAME, StringHelper.getString("none"), 0);
		
		memory.set(MEM_KEY_FACTIONS, factions, 0);
		
		return factions;
	}
	
	protected MarketAPI getTarget() {
		String marketId = memory.getString(MEM_KEY_TARGET);
		if (marketId == null || marketId.isEmpty())
			return null;
		
		MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
		return market;
	}
	
	protected void setTarget(String marketId) {
		setTarget(Global.getSector().getEconomy().getMarket(marketId));
	}
	
	protected void setTarget(MarketAPI market) {
		memory.set(MEM_KEY_TARGET, market.getId(), 0);
		target = market;
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
			case BASEKILL:
				markets = ExerelinUtilsFaction.getFactionMarkets(faction.getId(), false);
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
		if (oldType != type)
			resetTargets();
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
				float defenderStrength = InvasionRound.getDefenderStrength(target, 0.5f);
				int marines = (int)(defenderStrength * InvasionFleetManager.DEFENDER_STRENGTH_MARINE_MULT);
				((InvasionIntel)intel).setMarinesPerFleet((int)(marines * 1.5f));
				break;
			case RAID:
				intel = new NexRaidIntel(attacker, source, target, fp, timeToLaunch);
				break;
			case BASEKILL:
			case DEFENSE:
			default:
				return false;
		}
		intel.init();
		
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
	
	protected void generateMainMenu(OptionPanelAPI opts) {
		opts.clearOptions();
		String fleetTypeName = Misc.ucFirst(getFleetType().getName());
		opts.addOption("Fleet type: " + fleetTypeName, "nex_fleetRequest_selectType");
		
		String fpStr = String.format("%.0f", fp);
		opts.addOption("Fleet strength: " + fpStr, "nex_fleetRequest_strengthMenu");
		
		String sourceName = source == null ? StringHelper.getString("none") : source.getName();
		opts.addOption("Source market: " + sourceName, "nex_fleetRequest_selectSource");
		
		String factionName = faction == null ? StringHelper.getString("none") 
				: Nex_FactionDirectoryHelper.getFactionDisplayName(faction);
		opts.addOption("Target faction: " + factionName, "nex_fleetRequest_selectFaction");
		
		String targetName = target == null ? StringHelper.getString("none") : target.getName();
		opts.addOption("Target market: " + targetName, "nex_fleetRequest_selectTarget");
		
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
			opts.addOptionConfirmation(OPTION_PROCEED, confirmMessage, StringHelper.getString("yes"), StringHelper.getString("no"));
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
			String text = market.getName();

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
		
		for (MarketAPI market : targets)
		{
			String optId = TARGET_OPTION_PREFIX + market.getId();
			String text = StringHelper.getString("exerelin_markets", "marketDirectoryEntryNoLocation");
			text = StringHelper.substituteToken(text, "$market", market.getName());
			text = StringHelper.substituteToken(text, "$size", market.getSize() + "");

			addOption(text, optId);
		}
		
		showOptions();
	}
	
	protected static String getString(String id) {
		return StringHelper.getString("nex_fleetRequest", id);
	}
	
	protected static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_fleetRequest", id, ucFirst);
	}
	
	protected static Comparator<MarketAPI> marketComparatorSize = new Comparator<MarketAPI>() {
		public int compare(MarketAPI m1, MarketAPI m2) {
			if (m1.getSize() != m2.getSize())
				return Integer.compare(m1.getSize(), m2.getSize());
			return m1.getName().compareTo(m2.getName());
		}};
	
	protected static Comparator<MarketAPI> marketComparatorName = new Comparator<MarketAPI>() {
		public int compare(MarketAPI m1, MarketAPI m2) {
			return m1.getName().compareTo(m2.getName());
		}};
	
	public enum FleetType {
		INVASION, BASEKILL, RAID, DEFENSE;
		
		public static FleetType getTypeFromString(String str) {
			return FleetType.valueOf(StringHelper.flattenToAscii(str.toUpperCase(Locale.ROOT)));
		}
		
		public String getName() {
			return getString("fleetType_" + StringHelper.flattenToAscii(toString().toLowerCase(Locale.ROOT)));
		}
	}
}