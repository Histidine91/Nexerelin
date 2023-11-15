package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionDef;
import exerelin.campaign.intel.agents.CovertActionIntel.StoryPointUse;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Interaction dialog for giving agent orders. Modularization in progress, needs testing.
 */
public class AgentOrdersDialog implements InteractionDialogPlugin
{
	public static final int ENTRIES_PER_PAGE = 6;
	public static Logger log = Global.getLogger(AgentOrdersDialog.class);

	@Getter	protected IntelUIAPI ui;
	@Getter	protected InteractionDialogAPI dialog;
	@Getter	protected TextPanelAPI text;
	@Getter	protected OptionPanelAPI options;
	@Getter protected Menu lastSelectedMenu;
	@Getter protected List<Pair<String, Object>> optionsList = new ArrayList<>();
	//@Getter protected Map<Object, Highlights> disabledOptions = new HashMap<>();
	protected int currentPage = 1;
	
	@Getter protected AgentIntel agent;
	@Getter protected MarketAPI agentMarket;
	@Getter protected CovertActionIntel action;
	@Getter protected boolean isQueue;
	protected List<FactionAPI> factions = new ArrayList<>();
	protected List<Object> targets = new ArrayList<>();
	@Getter protected Object target;

	public enum Menu
	{
		MAIN_MENU,
		ACTION_TYPE,
		FACTION,
		TARGET,
		NEXT_PAGE,
		PREVIOUS_PAGE,
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

	protected List<FactionAPI> getCachedFactions() {
		return factions;
	}

	protected List<Object> getCachedTargets() {
		return targets;
	}

	/**
	 * Gets a list of possible target factions.
	 * @return
	 */
	protected List<FactionAPI> getFactions() {
		Set<FactionAPI> factionsSet = action.dialogGetFactions(this);
		List<FactionAPI> factions = new ArrayList<>(factionsSet);
		Collections.sort(factions, Nex_FactionDirectoryHelper.NAME_COMPARATOR_PLAYER_FIRST);
		
		// pick first available faction, if needed
		if (!factions.isEmpty()) {
			if (!factionsSet.contains(action.thirdFaction))
				selectFaction(factions.get(0));
		}

		this.factions = factions;
		
		return factions;
	}

	// TODO disambiguate this shit
	protected List<Object> getTargets() {
		List<Object> targets = action.dialogGetTargets(this);
		//log.info("Generating targets for action def " + action.getDefId());
		
		this.targets = targets;
		action.dialogAutopickTarget(this, targets);
		//log.info("Target count: " + this.targets.size());
		return targets;
	}
	
	protected void printActionInfo() {
		text.setFontSmallInsignia();
		action.dialogPrintActionInfo(this);
		text.setFontInsignia();
	}
	
	public void printStat(MutableStat stat, boolean color) {
		if (stat == null) {
			return;
		}
			
		TooltipMakerAPI info = text.beginTooltip();
		info.setParaSmallInsignia();
		info.addStatModGrid(360, 60, 10, 0, stat, true, NexUtils.getStatModValueGetter(color, 0));
		text.addTooltip();
	}
	
	public void setHighlights(String str1, String str2, Color col1, Color col2) {
		Highlights high = new Highlights();
		high.setColors(col1, col2);
		high.setText(str1, str2);
		text.setHighlightsInLastPara(high);
	}
	
	public void addEffectPara(int decimalPlaces, float valueMult)
	{
		float mult = action.getEffectMultForLevel();
		
		float eff1 = action.getDef().effect.one * mult * valueMult;
		float eff2 = action.getDef().effect.two * mult * valueMult;
		/*
		if (action.getDefId().equals(CovertActionType.SABOTAGE_INDUSTRY) && industryToSabotage != null)
		{
			// show disrupt time delta instead of absolute final time?
			// think about this later
		}
		 */
		
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
			//text.addPara("Doing nothing");
			return;	// don't remake action unnecessarily
		}
		// Allow to printActionInfo for the desired action if the player want to select a new action_type
		this.factions = new ArrayList<>();
		this.targets = new ArrayList<>();
		this.target = null;

		action = CovertActionIntel.instantiateActionForDialog(this, def);
		action.dialogInitAction(this);
		action.init();
	}
	
	protected void selectFaction(FactionAPI faction) {
		action.dialogSetFaction(this, faction);
		getTargets();
	}

	protected void populateOptions() {
		clearOptions();
		options.clearOptions();
		
		if (lastSelectedMenu == null || lastSelectedMenu == Menu.MAIN_MENU) {
			populateMainMenuOptions();
			return;
		}
		else switch (lastSelectedMenu) {
			case ACTION_TYPE:
				populateActionOptions();
				break;
			case FACTION:
				populateFactionOptions();
				break;
			case TARGET:
				// handled by action
				// populateTargetOptions();
				break;
			case CONFIRM_SP:
				populateSPOptions();
				return;
			default:
				populateMainMenuOptions();
				return;
		}

		showPaginatedMenu();

		if (lastSelectedMenu == Menu.ACTION_TYPE) {
			for (CovertActionDef def : CovertOpsManager.actionDefs) {
				CovertActionIntel tempAction = CovertActionIntel.instantiateActionForDialog(this, def);
				if (tempAction == null) {
					continue;
				}
				tempAction.dialogPaginatedMenuShown(this, lastSelectedMenu);
			}
		}
		else if (action != null) action.dialogPopulateOptions(this, lastSelectedMenu);
	}
	
	protected void addActionOption(String actionId) {
		CovertActionDef def = CovertOpsManager.getDef(actionId);
		if (NexConfig.useAgentSpecializations && !def.canAgentExecute(agent)) {
			return;
		}
		optionsList.add(new Pair<String, Object>(Misc.ucFirst(def.name.toLowerCase()), def));
	}

	protected void populateSPOptions() {
		options.addOption(getString("dialogSPOptionSuccessText"), Menu.CONFIRM_SP_SUCCESS);
		SetStoryOption.set(dialog, 1, Menu.CONFIRM_SP_SUCCESS, "agentOrderSuccess", "ui_char_spent_story_point_combat", null);

		options.addOption(getString("dialogSPOptionDetectionText"), Menu.CONFIRM_SP_DETECTION);
		SetStoryOption.set(dialog, 1, Menu.CONFIRM_SP_DETECTION, "agentOrderDetection", "ui_char_spent_story_point_combat", null);

		if (action.getDef().detectionChance > 0) {
			options.addOption(getString("dialogSPOptionsText"), Menu.CONFIRM_SP_BOTH);
			SetStoryOption.set(dialog, 1, Menu.CONFIRM_SP_BOTH, "agentOrderBoth", "ui_char_spent_story_point_combat", null);
		}

		addBackOption();
	}

	protected void populateActionOptions() {
	    for (CovertActionDef def : CovertOpsManager.actionDefs) {
	    	//log.info("Trying to add action " + def.id);
	        CovertActionIntel tempAction = CovertActionIntel.instantiateActionForDialog(this, def);
	        if (tempAction == null) {
	        	// FIXME
				continue;
			}
	        if (tempAction.dialogCanShowAction(this)) {
                addActionOption(def.id);
            }
        }
	}
	
	/**
	 * Display a paginated list of the available target factions for the selected action type.
	 */
	protected void populateFactionOptions() {
		for (FactionAPI faction : factions) {
			String name = Nex_FactionDirectoryHelper.getFactionDisplayName(faction);
			optionsList.add(new Pair<String, Object>(name, faction));
		}
	}
	
	protected void populateMainMenuOptions() {
		String none = StringHelper.getString("none");
		
		// action selection option
		String str = getString("dialogOption_action");
		str = StringHelper.substituteToken(str, "$action", action != null ? 
				Misc.ucFirst(action.getDef().name.toLowerCase()) : none);
		options.addOption(str, Menu.ACTION_TYPE);

		if (action != null) {
			action.dialogPopulateOptions(this, Menu.MAIN_MENU);
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

	public void clearOptions() {
		optionsList.clear();
		//disabledOptions.clear();
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

		// Show page number in prompt if multiple pages are present
		//dialog.setPromptText("Select a mod to go to its forum thread"
		//		+ (numPages > 1 ? " (page " + currentPage + "/" + numPages + ")" : "") + ":");
		addBackOption();

		if (action != null) action.dialogPaginatedMenuShown(this, lastSelectedMenu);
	}
	
	protected void addBackOption() {
		options.addOption(StringHelper.getString("back", true), Menu.MAIN_MENU);
		options.setShortcut(Menu.MAIN_MENU, Keyboard.KEY_ESCAPE,
				false, false, false, true);
	}
	
	public boolean canConductLocalActions() {
		if (agentMarket == null || !agentMarket.isInEconomy())
			return false;
		FactionAPI faction = agentMarket.getFaction();
		if (faction.isPlayerFaction())
			return false;
		if (!NexConfig.getFactionConfig(faction.getId()).allowAgentActions)
			return false;
		
		return true;
	}
	
	protected boolean canProceed() {
		if (action == null) return false;
		if (!action.dialogCanActionProceed(this)) return false;
		
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
		
		agent.addAction(action);
		if (isQueue) {
			//agent.setQueuedAction(action);
		}
		else {
			//agent.setCurrentAction(action);
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
			
			if (optionData == Menu.ACTION_TYPE)	{
				lastSelectedMenu = Menu.ACTION_TYPE;
			} else if (optionData == Menu.FACTION) {
				lastSelectedMenu = Menu.FACTION;
			} else if (optionData == Menu.TARGET) {
				lastSelectedMenu = Menu.TARGET;
			} else if (optionData == Menu.MAIN_MENU) {
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
			else if (lastSelectedMenuTemp == Menu.TARGET) {
				action.dialogSetTarget(this, optionData);
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
}