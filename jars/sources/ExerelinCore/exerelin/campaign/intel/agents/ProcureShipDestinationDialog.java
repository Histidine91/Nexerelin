package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.utilities.NexUtilsAstro;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static exerelin.campaign.intel.agents.AgentOrdersDialog.getString;

public class ProcureShipDestinationDialog implements InteractionDialogPlugin {
	
	public static Logger log = Global.getLogger(ProcureShipDestinationDialog.class);
	
	public static final int ENTRIES_PER_PAGE = 6;
	
	protected AgentIntel agent;
	protected IntelUIAPI ui;
	protected ProcureShip action;
	protected InteractionDialogAPI dialog;
	protected TextPanelAPI text;
	protected OptionPanelAPI options;
	protected int currentPage = 1;
	protected List<Pair<String, Object>> optionsList = new ArrayList<>();
	protected List<MarketAPI> markets;
	protected MarketAPI destination;
	protected Menu currentMenu;
	
	protected enum Menu
	{
		DESTINATION,
		NEXT_PAGE,
		PREVIOUS_PAGE,
		BACK,
		CANCEL,
		CONFIRM,
	}
	
	
	public ProcureShipDestinationDialog(AgentIntel agent, ProcureShip action, 
			MarketAPI currDestination, IntelUIAPI ui) {
		this.agent = agent;
		destination = currDestination;
		this.action = action;
		this.ui = ui;
	}
	
	protected void getMarkets() {
		markets = new ArrayList<>();
		List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
		
		//Collections.sort(markets, Nex_FleetRequest.marketComparatorName);
		for (MarketAPI market : allMarkets) {
			if (market.isHidden()) continue;
			markets.add(market);
		}
	}
	
	protected void populateOptions() {
		options.clearOptions();
		populateMainMenuOptions();
	}
	
	protected void populateTargetOptions() {
		optionsList.clear();

		for (MarketAPI market : markets) 
		{
			// don't overcomplicate it by displaying distance etc.
			// we presume the player already knows where they want to send the agent

			// changed my mind, distance is pretty important in random sector
			String name = market.getName() + ", " + NexUtilsAstro.getLocationName(market.getContainingLocation(), false);
			optionsList.add(new Pair<String, Object>(name, market));
		}
		showPaginatedMenu();
	}
	
	protected void populateMainMenuOptions() {
		String none = StringHelper.getString("none");
		
		String str = getString("dialogOption_target");
		String market = destination != null? destination.getName() : none;
		str = StringHelper.substituteToken(str, "$target", market);
		options.addOption(str, Menu.DESTINATION);
		
		// confirm
		options.addOption(StringHelper.getString("confirm", true), Menu.CONFIRM);
		options.setShortcut(Menu.CONFIRM, Keyboard.KEY_RETURN,
				false, false, false, true);
		
		// cancel
		options.addOption(StringHelper.getString("cancel", true), Menu.CANCEL);
		options.setShortcut(Menu.CANCEL, Keyboard.KEY_ESCAPE,
				false, false, false, true);
	}
	
	protected void showPaginatedMenu() {
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
	}
	
	protected void addBackOption() {
		options.addOption(StringHelper.getString("back", true), Menu.BACK);
		options.setShortcut(Menu.BACK, Keyboard.KEY_ESCAPE,
				false, false, false, true);
	}

	protected List<IntelInfoPlugin.ArrowData> getDestinationArrows() {
		List<IntelInfoPlugin.ArrowData> arrows = new ArrayList<>();
		if (destination != null) {
			arrows.add(new IntelInfoPlugin.ArrowData(action.market.getPrimaryEntity(), destination.getPrimaryEntity()));
		}
		return arrows;
	}
	
	protected void openDestinationPicker() {
		List<SectorEntityToken> dests = new ArrayList<>();
		for (MarketAPI market : markets) 
		{
			dests.add(market.getPrimaryEntity());
		}
		NexUtilsMarket.pickEntityDestination(dialog, dests, 
				StringHelper.getString("confirm", true), new NexUtilsMarket.CampaignEntityPickerWrapper(){
					@Override
					public void reportEntityPicked(SectorEntityToken token) {
						setDestination(token.getMarket());
						populateOptions();
					}

					@Override
					public void reportEntityPickCancelled() {}

					@Override
					public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) 
					{
						String str = StringHelper.getString("exerelin_markets", "marketDirectoryEntryForPickerNoDist");
						MarketAPI market = entity.getMarket();
						String factionName = market.getFaction().getDisplayName();
						String size = market.getSize() + "";
						str = StringHelper.substituteToken(str, "$market", market.getName());
						str = StringHelper.substituteToken(str, "$faction", factionName);
						str = StringHelper.substituteToken(str, "$size", size);

						Color hl = Misc.getHighlightColor();
						LabelAPI text = info.addPara(str, 0);
						text.setHighlight(factionName, size);
						text.setHighlightColors(market.getFaction().getBaseUIColor(), hl);
					}
				}, getDestinationArrows());
		populateOptions();
	}

	@Override
	public void init(InteractionDialogAPI dialog) {
		this.dialog = dialog;
		this.options = dialog.getOptionPanel();
		this.text = dialog.getTextPanel();
		dialog.getVisualPanel().setVisualFade(0.25f, 0.25f);
		dialog.getVisualPanel().showPlanetInfo(destination.getPrimaryEntity());
		
		text.addParagraph(StringHelper.getString("nex_agentActions", "dialogProcureShipDestinationIntro"));
		
		getMarkets();
		
		populateOptions();
		dialog.setPromptText(Misc.ucFirst(StringHelper.getString("options")));
	}

	@Override
	public void optionSelected(String optionText, Object optionData) {
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

			if (optionData == Menu.DESTINATION) {
				openDestinationPicker();
				return;				
			} else if (optionData == Menu.BACK) {
				// do nothing except populate options
				currentMenu = null;
			} else if (optionData == Menu.CANCEL) {
				dialog.dismissAsCancel();
				return;
			} else if (optionData == Menu.CONFIRM) {
				proceed();
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
	
	protected void setDestination(MarketAPI market) {
		destination = market;
		if (destination != null)
			dialog.getVisualPanel().showPlanetInfo(destination.getPrimaryEntity());
	}
	
	protected void proceed() {
		action.setDestination(destination);
		ui.updateUIForItem(agent);
		dialog.dismiss();
	}

	@Override
	public void optionMousedOver(String optionText, Object optionData) {
		
	}

	@Override
	public void advance(float amount) {
		
	}

	@Override
	public void backFromEngagement(EngagementResultAPI battleResult) {
		
	}

	@Override
	public Object getContext() {
		return null;
	}

	@Override
	public Map<String, MemoryAPI> getMemoryMap() {
		return null;
	}
	
}
