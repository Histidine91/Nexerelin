package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.ExerelinSetupData.HomeworldPickMode;
import exerelin.campaign.ExerelinSetupData.StartRelationsMode;
import exerelin.campaign.RevengeanceManager;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsGUI.CustomPanelGenResult;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static exerelin.campaign.ui.CustomPanelPluginWithInput.ButtonEntry;
import static exerelin.campaign.ui.CustomPanelPluginWithInput.RadioButtonEntry;

public class Nex_NGCPopulateCustomPanelOptions extends BaseCommandPlugin {
	
	public static final float X_PADDING = 3;
	public static final float ITEM_WIDTH = Nex_VisualCustomPanel.PANEL_WIDTH - X_PADDING * 2;
	public static final float ITEM_HEIGHT = 32;
	public static final float TEXT_WIDTH = 240;
	public static final float BUTTON_WIDTH = 72;
	
	@Override
	public boolean execute(String ruleId, final InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		switch (arg)
		{
			case "faction":
				addFactionOptions(memoryMap);
				return true;
			
			case "other":
				addOtherOptions(memoryMap);
				return true;		
		}
				
		return false;
	}
	
	public void addFactionOptions(final Map<String, MemoryAPI> memoryMap) {
		CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
		TooltipMakerAPI info = Nex_VisualCustomPanel.getTooltip();
		InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
		final ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		// random start relations
		addRandomRelationsOptions(panel, info, plugin);
		
		// faction respawn
		addFactionRespawnOptions(panel, info, plugin);
		
		// faction weights
		if (!data.corvusMode) {
			addFactionWeightOptions(panel, info, plugin);
		}
		
		info.addPara(getString("infoCustomPanel"), 10);
		
		Nex_VisualCustomPanel.addTooltipToPanel();
	}
	
	public void addRandomRelationsOptions(CustomPanelAPI panel, TooltipMakerAPI info,
			InteractionDialogCustomPanelPlugin plugin) 
	{
		int NUM_OPTS = 3;
		CustomPanelAPI buttonPanel = prepOption(panel, info, getString("optionStartingRelations"),
				"graphics/icons/intel/peace.png", plugin, 
				createTooltip(getString("tooltipStartingRelations"), null, null));
		
		final List<ButtonAPI> buttons = new ArrayList<>();
		TooltipMakerAPI lastHolder = null;
		final ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		// determine which button should be highlighted
		int reqIndex = data.startRelationsMode.ordinal();
		for (int i=0; i<NUM_OPTS; i++) {
			String name = Misc.ucFirst(getString("btnStartingRelations" + i));
			lastHolder = initRadioButton("nex_startingRelations_" + i, name, i == reqIndex, 
					buttonPanel, lastHolder, buttons);
		}
		TooltipMakerAPI checkPirateHolder = buttonPanel.createUIElement(48, ITEM_HEIGHT, false);
		ButtonAPI checkPirate = checkPirateHolder.addCheckbox(48, ITEM_HEIGHT, getString("btnStartingRelationsPirate"), 
				ButtonAPI.UICheckboxSize.TINY, 0);
		checkPirate.setChecked(data.applyStartRelationsModeToPirates);
		plugin.addButton(new ButtonEntry(checkPirate, "nex_startingRelationsPirate") {
			@Override
			public void onToggle() {
				data.applyStartRelationsModeToPirates = button.isChecked();
			}
		});
		buttonPanel.addUIElement(checkPirateHolder).rightOfTop(lastHolder, 3);
		
		final List<RadioButtonEntry> buttonEntries = new ArrayList<>();
		
		for (int i=0; i<NUM_OPTS; i++) {
			final int index = i;
			RadioButtonEntry radio = new RadioButtonEntry(buttons.get(i), "nex_startingRelations_" + i, buttonEntries)
			{
				@Override
				public void onToggleImpl() {
					data.startRelationsMode = StartRelationsMode.values()[index];
				}
			};
			buttonEntries.add(radio);
			plugin.addButton(radio);
		}
	}
	
	public void addFactionRespawnOptions(CustomPanelAPI panel, TooltipMakerAPI info,
			InteractionDialogCustomPanelPlugin plugin) 
	{
		int NUM_OPTS = 3;
		
		List<String> highlights = new ArrayList<>();
		for (int i=0; i<NUM_OPTS; i++) {
			highlights.add(Misc.ucFirst(getString("btnFactionRespawn" + i)));
		}
		TooltipCreator tooltip = createTooltip(String.format(
				getString("tooltipFactionRespawn"), highlights.toArray()),
				highlights, null);
		
		CustomPanelAPI buttonPanel = prepOption(panel, info, getString("optionFactionRespawn"),
				"graphics/exerelin/icons/intel/invasion.png", plugin, tooltip);
		
		final List<ButtonAPI> buttons = new ArrayList<>();
		TooltipMakerAPI lastHolder = null;
		final ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		// determine which button should be highlighted
		int reqIndex = 0;
		if (data.respawnFactions) {
			reqIndex = data.onlyRespawnStartingFactions ? 1 : 2;
		}
		for (int i=0; i<NUM_OPTS; i++) {
			String name = Misc.ucFirst(getString("btnFactionRespawn" + i));
			lastHolder = initRadioButton("nex_factionRespawn_" + i, name, i == reqIndex, 
					buttonPanel, lastHolder, buttons);
		}
		final List<RadioButtonEntry> buttonEntries = new ArrayList<>();
		
		for (int i=0; i<NUM_OPTS; i++) {
			final int index = i;
			RadioButtonEntry radio = new RadioButtonEntry(buttons.get(i), "nex_factionRespawn_" + i, buttonEntries)
			{
				@Override
				public void onToggleImpl() {
					data.respawnFactions = index > 0;
					data.onlyRespawnStartingFactions = index < 2;
				}
			};
			buttonEntries.add(radio);
			plugin.addButton(radio);
		}
	}
	
	public void addFactionWeightOptions(CustomPanelAPI panel, TooltipMakerAPI info,
			InteractionDialogCustomPanelPlugin plugin) 
	{
		int NUM_OPTS = 3;
		
		CustomPanelAPI buttonPanel = prepOption(panel, info, getString("optionFactionWeights"),
				"graphics/factions/crest_domain.png", plugin, 
				createTooltip(getString("tooltipFactionWeights"), null, null));
		
		final List<ButtonAPI> buttons = new ArrayList<>();
		TooltipMakerAPI lastHolder = null;
		final ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		int reqIndex = 0;
		if (data.useFactionWeights) {
			reqIndex = data.randomFactionWeights? 2 : 1;
		}		
		for (int i=0; i<NUM_OPTS; i++) {
			String name = Misc.ucFirst(getString("btnFactionWeights" + i));
			lastHolder = initRadioButton("nex_factionWeights_" + i, name, i == reqIndex, 
					buttonPanel, lastHolder, buttons);
		}
		final List<RadioButtonEntry> buttonEntries = new ArrayList<>();
		
		for (int i=0; i<NUM_OPTS; i++) {
			final int index = i;
			RadioButtonEntry radio = new RadioButtonEntry(buttons.get(i), "nex_factionWeights_" + i, buttonEntries)
			{
				@Override
				public void onToggleImpl() {
					data.useFactionWeights = index > 0;
					data.randomFactionWeights = index == 2;
				}
			};
			buttonEntries.add(radio);
			plugin.addButton(radio);
		}
	}
	
	public void addOtherOptions(final Map<String, MemoryAPI> memoryMap) {
		CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
		TooltipMakerAPI info = Nex_VisualCustomPanel.getTooltip();
		InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
		final ExerelinSetupData data = ExerelinSetupData.getInstance();
				
		// Prism Freeport
		addCheckboxOption(panel, info, getString("optionPrismFreeport"), "nex_prismFreeport", 
				data.prismMarketPresent, "graphics/SCY/stations/SCY_prismFreeport.png", plugin, new ButtonEntry() {
						@Override
						public void onToggle() {
							data.prismMarketPresent = button.isChecked();
							//MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
							//memory.set("$randomStartShips", data.randomStartShips, 0);
						}
				}, 
				createTooltip(getString("tooltipPrismFreeport"), 
						null, null)
		);
		
		// easy mode
		//float offEasy = Global.getSettings().getFloat("easyOfficerLevelMult");
		float salEasy = Global.getSettings().getFloat("easySalvageMult");
		float sensEasy = Global.getSettings().getFloat("easySensorBonus");
		float damEasy = Global.getSettings().getFloat("easyPlayerDamageTakenMult");
		
		List<String> highlights = new ArrayList<>();
		highlights.add(Math.round((1 - damEasy) * 100) + "%");
		highlights.add(Math.round(sensEasy) + "");
		highlights.add(Math.round((salEasy - 1) * 100) + "%");
		//info.addPara("lol " + getString("tooltipEasyMode"), 3);
		String tooltipStr = String.format(getString("tooltipEasyMode"), highlights.toArray());
		
		addCheckboxOption(panel, info, getString("optionEasyMode"), "nex_easyMode", 
				data.easyMode, "graphics/ships/aeroshuttle/aeroshuttle_base.png", plugin, new ButtonEntry() {
						@Override
						public void onToggle() {
							boolean easy = button.isChecked();
							data.easyMode = easy;
							MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
							memory.set("$easyMode", easy, 0);
							CharacterCreationData data = (CharacterCreationData) memory.get("$characterData");
							if (easy) data.setDifficulty("easy");
							else data.setDifficulty("normal");
						}
				}, 
				createTooltip(tooltipStr, highlights, null)
		);
		
		// hard mode
		float growthHard = NexConfig.hardModeColonyGrowthMult;
		float incomeHard = NexConfig.hardModeColonyIncomeMult;
		float vengHard = RevengeanceManager.HARD_MODE_MULT;
		
		highlights = new ArrayList<>();	// important: replace the array instead of just clearing it, due to pass-by-reference
		highlights.add(Math.round((1 - growthHard) * 100) + "%");
		highlights.add(Math.round((1 - incomeHard) * 100) + "%");
		highlights.add(Math.round((vengHard - 1) * 100) + "%");
		tooltipStr = String.format(getString("tooltipHardMode"), highlights.toArray());
		
		addCheckboxOption(panel, info, getString("optionHardMode"), "nex_hardMode", 
				data.hardMode, "graphics/ships/dominator/dominator_hegemony.png", plugin, new ButtonEntry() {
						@Override
						public void onToggle() {
							data.hardMode = button.isChecked();
						}
				}, 
				createTooltip(tooltipStr, highlights, null)
		);
		
		// spacer obligation
		highlights = new ArrayList<>();
		tooltipStr = String.format(getString("tooltipSpacerObligation"), highlights.toArray());
		addCheckboxOption(panel, info, getString("optionSpacerObligation"), "nex_spacerObligation", 
				data.spacerObligation, Global.getSettings().getSpriteName("income_report", "generic_expense"), plugin, new ButtonEntry() {
						@Override
						public void onToggle() {
							data.spacerObligation = button.isChecked();
						}
				}, 
				createTooltip(tooltipStr, highlights, null)
		);

		// stipend
		highlights.add(getString("tooltipStipendHighlight"));
		tooltipStr = String.format(getString("tooltipStipend"), highlights.toArray());
		addCheckboxOption(panel, info, getString("optionStipend"), "nex_stipend",
				data.enableStipend, Global.getSettings().getSpriteName("income_report", "generic_income"), plugin, new ButtonEntry() {
					@Override
					public void onToggle() {
						data.enableStipend = button.isChecked();
					}
				},
				createTooltip(tooltipStr, highlights, null)
		);
		
		// random start location
		highlights = new ArrayList<>();
		addCheckboxOption(panel, info, getString("optionRandomStartLocation"), "nex_randomStartLocation", 
				data.randomStartLocation, "graphics/icons/intel/new_planet_info.png", plugin, new ButtonEntry() {
						@Override
						public void onToggle() {
							data.randomStartLocation = button.isChecked();
						}
				}, 
				createTooltip(getString("tooltipRandomStartLocation"), 
						null, null));
		
		addDModOptions(panel, info, plugin);
		addHomeworldOptions(panel, info, plugin);
		
		// Antioch
		if (!data.corvusMode && Global.getSector().getFaction("templars") != null) {
			addCheckboxOption(panel, info, getString("optionRandomAntioch"), "nex_randomAntioch", 
					data.randomAntiochEnabled, "graphics/templars/factions/crest_knights_templar.png", 
					plugin, new ButtonEntry() {
							@Override
							public void onToggle() {
								data.randomAntiochEnabled = button.isChecked();
							}
					}, 
					null);
		}
		
		// skip story
		if (data.corvusMode) {
			addCheckboxOption(panel, info, getString("optionSkipStory"), "nex_skipStory", 
					data.skipStory, "graphics/icons/missions/ga_intro.png", 
					plugin, new ButtonEntry() {
							@Override
							public void onToggle() {
								data.skipStory = button.isChecked();
							}
					}, 
					createTooltip(getString("tooltipSkipStory"), highlights, null));
		}
		
		
		
		info.addPara(getString("infoCustomPanel"), 10);
		
		Nex_VisualCustomPanel.addTooltipToPanel();
	}
	
	public void addDModOptions(CustomPanelAPI panel, TooltipMakerAPI info,
			InteractionDialogCustomPanelPlugin plugin) 
	{
		CustomPanelAPI buttonPanel = prepOption(panel, info, getString("optionStartingDMods"),
			"graphics/hullmods/illadvised.png", plugin,
			createTooltip(getString("tooltipStartingDMods"), null, null));
		
		final List<ButtonAPI> buttons = new ArrayList<>();
		TooltipMakerAPI lastHolder = null;
		final ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		for (int i=0; i<ExerelinSetupData.NUM_DMOD_LEVELS; i++) {
			String name = Misc.ucFirst(ExerelinSetupData.getDModCountText(i));
			lastHolder = initRadioButton("nex_startingDMods_" + i, name, i == ExerelinSetupData.getInstance().dModLevel, 
					buttonPanel, lastHolder, buttons);
		}
		final List<RadioButtonEntry> buttonEntries = new ArrayList<>();
		
		for (int i=0; i<ExerelinSetupData.NUM_DMOD_LEVELS; i++) {
			final int index = i;
			RadioButtonEntry radio = new RadioButtonEntry(buttons.get(i), "nex_startingDMods_" + i, buttonEntries)
			{
				@Override
				public void onToggleImpl() {
					data.dModLevel = index;
					//Global.getLogger(this.getClass()).info("D-mod level: " + data.dModLevel);
				}
			};
			buttonEntries.add(radio);
			plugin.addButton(radio);
		}
	}
	
	public void addHomeworldOptions(CustomPanelAPI panel, TooltipMakerAPI info,
			InteractionDialogCustomPanelPlugin plugin) 
	{
		int NUM_OPTS = 3;
		
		CustomPanelAPI buttonPanel = prepOption(panel, info, getString("optionHomeworldPick"),
			"graphics/icons/intel/stars.png", plugin,
			createTooltip(getString("tooltipHomeworldPick"), null, null));
				
		final List<ButtonAPI> buttons = new ArrayList<>();
		TooltipMakerAPI lastHolder = null;
		final ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		// determine which button should be highlighted
		int reqIndex = data.homeworldPickMode.ordinal();
		for (int i=0; i<NUM_OPTS; i++) {
			String name = Misc.ucFirst(getString("btnHomeworldPickMode" + i));
			lastHolder = initRadioButton("nex_homeworldPickMode_" + i, name, i == reqIndex, 
					buttonPanel, lastHolder, buttons);
		}
		
		final List<RadioButtonEntry> buttonEntries = new ArrayList<>();
		
		for (int i=0; i<NUM_OPTS; i++) {
			final int index = i;
			RadioButtonEntry radio = new RadioButtonEntry(buttons.get(i), "nex_homeworldPickMode_" + i, buttonEntries)
			{
				@Override
				public void onToggleImpl() {
					data.homeworldPickMode = HomeworldPickMode.values()[index];
				}
			};
			buttonEntries.add(radio);
			plugin.addButton(radio);
		}

		// homeworld neighbors setting
		String tooltipStr = getString("btnHomeworldNeighborsTooltip");

		addCheckboxOption(panel, info, getString("btnHomeworldNeighbors"), "nex_homeworldPickNeighbors",
				data.homeworldAllowNeighbors, "graphics/factions/crest_neutral_traders.png", plugin, new ButtonEntry() {
					@Override
					public void onToggle() {
						data.homeworldAllowNeighbors = button.isChecked();
					}
				},
				createTooltip(tooltipStr, null, null)
		);
	}
	
	/**
	 *
	 * @param name
	 * @param id
	 * @param checked Is the button checked at start?
	 * @param buttonPanel
	 * @param rightOf
	 * @param buttons List of buttons to which the generated {@code ButtonAPI} should be added.
	 * @return The {@code TooltipMakerAPI} holding the button.
	 */
	public static TooltipMakerAPI initRadioButton(String id, String name, boolean checked,
			CustomPanelAPI buttonPanel, TooltipMakerAPI rightOf, List<ButtonAPI> buttons) {
		TooltipMakerAPI holder = buttonPanel.createUIElement(BUTTON_WIDTH, ITEM_HEIGHT, false);
		FactionAPI faction = Global.getSector().getPlayerFaction();
		
		ButtonAPI button = holder.addAreaCheckbox(name, 
				id, faction.getBaseUIColor(),	faction.getDarkUIColor(),
				faction.getBrightUIColor(), BUTTON_WIDTH, ITEM_HEIGHT, 0);
		button.setChecked(checked);
		buttons.add(button);
		
		if (rightOf == null) {
			buttonPanel.addUIElement(holder).inTL(0, 0);
		} else {
			buttonPanel.addUIElement(holder).rightOfTop(rightOf, X_PADDING);
		}
		return holder;
	}
	
	public static void addCheckboxOption(CustomPanelAPI panel, TooltipMakerAPI info, String name,
			String buttonId, boolean initSetting, String imagePath, 
			InteractionDialogCustomPanelPlugin plugin, ButtonEntry be, TooltipCreator tooltip) 
	{
		FactionAPI faction = Global.getSector().getPlayerFaction();
		
		CustomPanelAPI buttonHolder = prepOption(panel, info, name, imagePath, plugin, tooltip);
				
		// checkbox
		TooltipMakerAPI checkboxHolder = buttonHolder.createUIElement(BUTTON_WIDTH, ITEM_HEIGHT, false);
		ButtonAPI button = checkboxHolder.addAreaCheckbox(StringHelper.getString("enable", true), 
				buttonId, faction.getBaseUIColor(),	faction.getDarkUIColor(),
				faction.getBrightUIColor(), BUTTON_WIDTH, ITEM_HEIGHT, 0);
		button.setChecked(initSetting);
		be.button = button;
		be.id = buttonId;
		plugin.addButton(be);
		buttonHolder.addUIElement(checkboxHolder).inTL(0, 0);
	}
	
	public static CustomPanelAPI prepOption(CustomPanelAPI panel, TooltipMakerAPI info, String name,
			String imagePath, InteractionDialogCustomPanelPlugin plugin, TooltipCreator tooltip) {
		return prepOption(panel, info, name, imagePath, Misc.getTextColor(), plugin, tooltip);
	}
	
	/**
	 * Generates a {@code CustomPanelAPI} containing the GUI elements of the option, except the button(s).
	 * @param panel
	 * @param info
	 * @param name
	 * @param imagePath
	 * @param textColor
	 * @param plugin
	 * @param tooltip
	 * @return A {@code CustomPanelAPI} to which buttons may be added.
	 */
	public static CustomPanelAPI prepOption(CustomPanelAPI panel, TooltipMakerAPI info, String name,
			String imagePath, Color textColor, InteractionDialogCustomPanelPlugin plugin, 
			TooltipCreator tooltip) 
	{
		float pad = 3;
		float opad = 10;
		
		CustomPanelGenResult panelGen = NexUtilsGUI.addPanelWithFixedWidthImage(panel, 
				null, ITEM_WIDTH, ITEM_HEIGHT, name, TEXT_WIDTH, X_PADDING * 3, 
				imagePath, ITEM_HEIGHT, pad, textColor, true, tooltip);
		CustomPanelAPI row = panelGen.panel;
		TooltipMakerAPI title = (TooltipMakerAPI)panelGen.elements.get(panelGen.elements.size() - 1);
				
		// button holder
		CustomPanelAPI buttonRow = row.createCustomPanel(ITEM_WIDTH - ITEM_HEIGHT - TEXT_WIDTH - X_PADDING * 5, ITEM_HEIGHT, null);
		row.addComponent(buttonRow).rightOfTop(title, X_PADDING);
		
		info.addCustom(row, opad);
		
		return buttonRow;
	}
	
	public static TooltipCreator createTooltip(final String text, 
			final Collection<String> highlights, final Collection<Color> hlColors) 
	{
		return new TooltipCreator() {
			@Override
			public boolean isTooltipExpandable(Object tooltipParam) {
				return false;
			}

			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return 360;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				LabelAPI label = tooltip.addPara(text, 0);
				if (highlights != null) {
					label.setHighlight(highlights.toArray(new String[0]));
					if (hlColors != null)
						label.setHighlightColors(hlColors.toArray(new Color[0]));
				}
			}
		};
	}
	
	public static String getString(String id) {
		return StringHelper.getString("exerelin_ngc", id);
	}
}
