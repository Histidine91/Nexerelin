package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.ui.CustomPanelPluginWithInput.ButtonEntry;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexFactionConfig.StartFleetType;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Nex_NGCStartFleetOptionsV2 extends BaseCommandPlugin {
	
	protected static final float PANEL_WIDTH_1 = 240;
	protected static final float PANEL_WIDTH_2 = Nex_VisualCustomPanel.PANEL_WIDTH - PANEL_WIDTH_1 - 8;
	protected static final float SHIP_ICON_WIDTH = 48;
	protected static final float ARROW_BUTTON_WIDTH = 20, BUTTON_HEIGHT = 20;
	protected static final float SELECT_BUTTON_WIDTH = 60;
	
	protected static final Map<String, Integer> FLEET_INDEXES = new HashMap<>();
	
	protected static final String[] FLEET_TYPES = {"SOLO", "COMBAT_SMALL", "COMBAT_LARGE", "TRADE_SMALL", 
		"TRADE_LARGE", "CARRIER_SMALL", "CARRIER_LARGE", "EXPLORER_SMALL", "EXPLORER_LARGE",
		"SUPER", "GRAND_FLEET"};
	protected static final String[] DIALOG_ENTRIES = {"Solo", "CombatSmall", "CombatLarge", "TradeSmall", 
		"TradeLarge", "CarrierSmall", "CarrierLarge", "ExplorerSmall", "ExplorerLarge", "Super", "GrandFleet"};
	protected static final Map<String, String> OPTION_TEXTS = new HashMap<>();
	protected static final List<Misc.Token> EMPTY_PARAMS = new ArrayList<>();
	
	static {
		for (int i=0; i<DIALOG_ENTRIES.length; i++)
		{
			OPTION_TEXTS.put(FLEET_TYPES[i], StringHelper.getString("exerelin_ngc", 
					"fleet" + DIALOG_ENTRIES[i]));
		}
		for (String type : FLEET_TYPES)
		{
			FLEET_INDEXES.put(type, 0);
		}
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		for (String fleetType : FLEET_TYPES) {
			memoryMap.get(MemKeys.LOCAL).unset("$startShips_" + fleetType);
		}
		addOptions(dialog, memoryMap);
		return true;
	}
	
	public int getFleetIndex(String fleetType) {
		return FLEET_INDEXES.get(fleetType);
	}
	
	public void setFleetIndex(String fleetType, int index) {
		FLEET_INDEXES.put(fleetType, index);
	}
	
	public void updateFleetType(String fleetType, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		memoryMap.get(MemKeys.LOCAL).unset("$startShips_" + fleetType);
		addOptions(dialog, memoryMap);
	}
	
	/**
	 * Generate the dialog options for each starting fleet type.
	 * @param dialog
	 * @param memoryMap
	 */
	public void addOptions(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap)
	{
		dialog.getOptionPanel().clearOptions();
		Nex_VisualCustomPanel.clearPanel(dialog, memoryMap);
		Nex_VisualCustomPanel.createPanel(dialog, true, Nex_VisualCustomPanel.PANEL_WIDTH,
				Nex_VisualCustomPanel.PANEL_HEIGHT + 60);
		
		NexFactionConfig factionConf = NexConfig.getFactionConfig(
				PlayerFactionStore.getPlayerFactionIdNGC());
		TooltipMakerAPI panelTooltip = Nex_VisualCustomPanel.getTooltip();
		
		for (int i=0; i<FLEET_TYPES.length; i++)
		{
			try {
			final String fleetTypeStr = FLEET_TYPES[i];
			final String dialogOptId = "nex_NGCFleet" + DIALOG_ENTRIES[i];
			
			final int maxIndex = factionConf.getNumStartFleetsForType(fleetTypeStr) - 1;
			if (maxIndex < 0) continue;
			
			int index = FLEET_INDEXES.get(fleetTypeStr);
			if (index > maxIndex) index = 0;
			
			//Global.getLogger(this.getClass()).info("Fetching variants with index " + index);
			
			// load variants from cache (the cache is so we don't re-randomize other fleets each time)
			List<String> startingVariants = (List<String>)memoryMap.get(MemKeys.LOCAL).get("$startShips_" + fleetTypeStr);
			if (startingVariants == null) startingVariants = factionConf.getStartFleetForType(fleetTypeStr, false, index);
			if (startingVariants == null) {
				//dialog.getOptionPanel().setEnabled(option, false);
				continue;
			}
			
			String optionName = OPTION_TEXTS.get(fleetTypeStr);
			
			// create panel
			CustomPanelAPI fleetPanel = Nex_VisualCustomPanel.getPanel().createCustomPanel(PANEL_WIDTH_1, 48, 
					new FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), true));
			TooltipMakerAPI text = fleetPanel.createUIElement(PANEL_WIDTH_1 - SELECT_BUTTON_WIDTH - 8, 0, false);
			text.setParaSmallInsignia();
			text.addPara(optionName, 0);
			fleetPanel.addUIElement(text).inTL(0, 0);
			
			TooltipMakerAPI anchorForRandomButton;
			
			float counterWidth = 40;
			if (maxIndex > 0) {	
				// previous button
				TooltipMakerAPI leftButtonHolder = fleetPanel.createUIElement(
						ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, false);
				String buttonId = "button_prev_" + fleetTypeStr;
				ButtonAPI leftButton = leftButtonHolder.addButton("<", buttonId, 
						ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
				ButtonEntry entry = new ButtonEntry(leftButton, buttonId) {
					@Override
					public void onToggle() {
						int newIndex = getFleetIndex(fleetTypeStr);
						newIndex = newIndex - 1;
						if (newIndex < 0) newIndex = maxIndex;
						setFleetIndex(fleetTypeStr, newIndex);
						updateFleetType(fleetTypeStr, dialog, memoryMap);
					}
				};
				Nex_VisualCustomPanel.getPlugin().addButton(entry);
				fleetPanel.addUIElement(leftButtonHolder).belowLeft(text, 3);
				
				// number text
				TooltipMakerAPI counter = fleetPanel.createUIElement(counterWidth, BUTTON_HEIGHT, false);
				String indexStr = " " + (index + 1) + "";
				if (index < 0) indexStr = " -";
				LabelAPI label = counter.addPara(indexStr + " / " + (maxIndex + 1), 0);
				label.getPosition().inTMid(2);
				fleetPanel.addUIElement(counter).rightOfTop(leftButtonHolder, 6);
				
				// next button
				TooltipMakerAPI rightButtonHolder = fleetPanel.createUIElement(
						ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, false);
				buttonId = "button_next_" + fleetTypeStr;
				ButtonAPI rightButton = rightButtonHolder.addButton(">", buttonId, 
						ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
				entry = new ButtonEntry(rightButton, buttonId) {
					@Override
					public void onToggle() {
						int newIndex = getFleetIndex(fleetTypeStr);
						newIndex = newIndex + 1;
						if (newIndex > maxIndex) newIndex = 0;
						setFleetIndex(fleetTypeStr, newIndex);
						updateFleetType(fleetTypeStr, dialog, memoryMap);
					}
				};
				Nex_VisualCustomPanel.getPlugin().addButton(entry);
				fleetPanel.addUIElement(rightButtonHolder).rightOfTop(counter, 0);
				
				anchorForRandomButton = rightButtonHolder;
			}
			else {
				// only one option selectable, just add a button that allows us to undo randomizing the fleet
				if (StartFleetType.getType(fleetTypeStr) == StartFleetType.SUPER) {
					TooltipMakerAPI spacer = fleetPanel.createUIElement(counterWidth 
						+ ARROW_BUTTON_WIDTH * 2 + 6, BUTTON_HEIGHT, false);
					fleetPanel.addUIElement(spacer).belowLeft(text, 3);
					anchorForRandomButton = spacer;
				}
				else {
					TooltipMakerAPI derandomButtonHolder = fleetPanel.createUIElement(
						counterWidth + ARROW_BUTTON_WIDTH * 2 + 6, BUTTON_HEIGHT, false);
					String buttonId = "button_prev_" + fleetTypeStr;
					ButtonAPI button = derandomButtonHolder.addButton("<", buttonId, 
							ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
					ButtonEntry entry = new ButtonEntry(button, buttonId) {
						@Override
						public void onToggle() {
							setFleetIndex(fleetTypeStr, 0);
							updateFleetType(fleetTypeStr, dialog, memoryMap);
						}
					};
					Nex_VisualCustomPanel.getPlugin().addButton(entry);
					fleetPanel.addUIElement(derandomButtonHolder).belowLeft(text, 3);

					anchorForRandomButton = derandomButtonHolder;
				}
			}
			
			// random button
			if (StartFleetType.getType(fleetTypeStr) != StartFleetType.SUPER) {
				TooltipMakerAPI randomButtonHolder = fleetPanel.createUIElement(60, BUTTON_HEIGHT, false);
				String buttonId = "button_next_" + fleetTypeStr;
				ButtonAPI randomButton = randomButtonHolder.addButton(StringHelper.getString("random", true), 
						buttonId, 60, BUTTON_HEIGHT, 0);
				ButtonEntry entry = new ButtonEntry(randomButton, buttonId) {
					@Override
					public void onToggle() {
						setFleetIndex(fleetTypeStr, -1);
						updateFleetType(fleetTypeStr, dialog, memoryMap);
					}
				};
				Nex_VisualCustomPanel.getPlugin().addButton(entry);
				fleetPanel.addUIElement(randomButtonHolder).rightOfTop(anchorForRandomButton, 6);
			}
			
			// actual selection button
			TooltipMakerAPI selectButtonHolder = fleetPanel.createUIElement(96, BUTTON_HEIGHT * 2, false);
			String buttonId = "button_select_" + fleetTypeStr;
			ButtonAPI selectButton = selectButtonHolder.addButton(StringHelper.getString("select", true), 
					buttonId, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT * 2, 4);
			ButtonEntry entry = entry = new ButtonEntry(selectButton, buttonId) {
				@Override
				public void onToggle() {
					memoryMap.get(MemKeys.LOCAL).set("$option", dialogOptId);
					FireBest.fire(null, dialog, memoryMap, "NewGameOptionSelected");
				}
			};
			Nex_VisualCustomPanel.getPlugin().addButton(entry);
			fleetPanel.addUIElement(selectButtonHolder).rightOfTop(text, 0);
			
			// fleet display
			float shipAreaWidth = PANEL_WIDTH_2;
			TooltipMakerAPI shipHolder = fleetPanel.createUIElement(shipAreaWidth, 0, false);
			List<FleetMemberAPI> ships = new ArrayList<>();
			for (String variantId : startingVariants) {
				ships.add(Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId));
			}
			
			int max = Math.min((int)(shipAreaWidth/SHIP_ICON_WIDTH), ships.size());
			ships = ships.subList(0, max);
			shipHolder.addShipList(max, 1, SHIP_ICON_WIDTH, Misc.getBasePlayerColor(), ships, 0);
			fleetPanel.addUIElement(shipHolder).rightOfTop(text, SELECT_BUTTON_WIDTH + 4);
			
			panelTooltip.addCustom(fleetPanel, 3);
			
			memoryMap.get(MemKeys.LOCAL).set("$startShips_" + fleetTypeStr, startingVariants);
			
			} catch (Exception ex) {
				Global.getLogger(this.getClass()).error("Failed to add start ship option", ex);
			}
		}

		ButtonAPI custom = panelTooltip.addButton(Nex_NGCCustomStartFleet.getString("customFleetButton"), "custom", 96, 24, 3);
		ButtonEntry entry = new ButtonEntry(custom, "custom") {
			@Override
			public void onToggle() {
				Nex_NGCCustomStartFleet.createDialog(dialog, memoryMap, PlayerFactionStore.getPlayerFactionIdNGC());
			}
		};
		Nex_VisualCustomPanel.getPlugin().addButton(entry);
		
		Nex_VisualCustomPanel.addTooltipToPanel();
		
		dialog.getOptionPanel().addOption(StringHelper.getString("goBack", true), "nex_NGCFleetBack");
		dialog.getOptionPanel().setShortcut("nex_NGCFleetBack", Keyboard.KEY_ESCAPE, false, false, false, false);
		
		NexUtils.addDevModeDialogOptions(dialog);
	}
}