package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.RevengeanceManager;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin.ButtonEntry;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin.RadioButtonEntry;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Nex_NGCPopulateCustomPanelOptions extends BaseCommandPlugin {
	
	public static final float X_PADDING = 2;
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
	}
	
	public void addRandomRelationsOptions(CustomPanelAPI panel, TooltipMakerAPI info,
			InteractionDialogCustomPanelPlugin plugin) 
	{
		int NUM_OPTS = 3;
		CustomPanelAPI buttonPanel = prepOption(panel, info, getString("optionRandomRelations"),
				"graphics/icons/intel/peace.png", plugin, 
				createTooltip(getString("tooltipRandomRelations"), null, null));
		
		final List<ButtonAPI> buttons = new ArrayList<>();
		TooltipMakerAPI lastHolder = null;
		final ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		for (int i=0; i<NUM_OPTS; i++) {
			String name = Misc.ucFirst(getString("btnRandomRelations" + i));
			lastHolder = initRadioButton("nex_randomRelations_" + i, name, i == 0, 
					buttonPanel, lastHolder, buttons);
		}
		final List<RadioButtonEntry> buttonEntries = new ArrayList<>();
		
		for (int i=0; i<NUM_OPTS; i++) {
			final int index = i;
			RadioButtonEntry radio = new RadioButtonEntry(buttons.get(i), "nex_randomRelations_" + i) 
			{
				@Override
				public void onToggleImpl() {
					data.randomStartRelationships = index > 0;
					data.randomStartRelationshipsPirate = index == 2;
				}
			};
			buttonEntries.add(radio);
		}
		for (RadioButtonEntry entry : buttonEntries) {
			entry.buttons = buttonEntries;
			plugin.addButton(entry);
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
		
		for (int i=0; i<NUM_OPTS; i++) {
			String name = Misc.ucFirst(getString("btnFactionRespawn" + i));
			lastHolder = initRadioButton("nex_factionRespawn_" + i, name, i == 0, 
					buttonPanel, lastHolder, buttons);
		}
		final List<RadioButtonEntry> buttonEntries = new ArrayList<>();
		
		for (int i=0; i<NUM_OPTS; i++) {
			final int index = i;
			RadioButtonEntry radio = new RadioButtonEntry(buttons.get(i), "nex_factionRespawn_" + i) 
			{
				@Override
				public void onToggleImpl() {
					data.respawnFactions = index > 0;
					data.onlyRespawnStartingFactions = index < 2;
				}
			};
			buttonEntries.add(radio);
		}
		for (RadioButtonEntry entry : buttonEntries) {
			entry.buttons = buttonEntries;
			plugin.addButton(entry);
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
		
		for (int i=0; i<NUM_OPTS; i++) {
			String name = Misc.ucFirst(getString("btnFactionWeights" + i));
			lastHolder = initRadioButton("nex_factionWeights_" + i, name, i == 1, 
					buttonPanel, lastHolder, buttons);
		}
		final List<RadioButtonEntry> buttonEntries = new ArrayList<>();
		
		for (int i=0; i<NUM_OPTS; i++) {
			final int index = i;
			RadioButtonEntry radio = new RadioButtonEntry(buttons.get(i), "nex_factionWeights_" + i) 
			{
				@Override
				public void onToggleImpl() {
					data.useFactionWeights = index > 0;
					data.randomFactionWeights = index == 2;
				}
			};
			buttonEntries.add(radio);
		}
		for (RadioButtonEntry entry : buttonEntries) {
			entry.buttons = buttonEntries;
			plugin.addButton(entry);
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
		
		// random ships
		addCheckboxOption(panel, info, getString("optionRandomStartShips"), "nex_randomStartShips", 
				data.randomStartShips, "graphics/fx/question_mark.png", plugin, new ButtonEntry() {
						@Override
						public void onToggle() {
							data.randomStartShips = button.isChecked();
							MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
							memory.set("$randomStartShips", data.randomStartShips, 0);
						}
				}, 
				createTooltip(getString("tooltipRandomStartShips"), 
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
		
		highlights = new ArrayList<>();
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
		
		// random start location
		addCheckboxOption(panel, info, getString("optionRandomStartLocation"), "nex_randomStartLocation", 
				data.randomStartLocation, "graphics/icons/intel/new_planet_info.png", plugin, new ButtonEntry() {
						@Override
						public void onToggle() {
							data.randomStartLocation = button.isChecked();
						}
				}, 
				null
		);
		
		addDModOptions(panel, info, plugin);
		
		// Antioch
		if (!data.corvusMode && Global.getSector().getFaction("templars") != null) {
			addCheckboxOption(panel, info, getString("optionRandomAntioch"), "nex_randomAntioch", 
					data.randomAntiochEnabled, "graphics/templars/factions/crest_knights_templar.png", 
					plugin, new ButtonEntry() {
							@Override
							public void onToggle() {
								data.randomAntiochEnabled= button.isChecked();
							}
					}, 
					null
			);
		}		
		
		info.addPara(getString("infoCustomPanel"), 10);
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
			lastHolder = initRadioButton("nex_startingDMods_" + i, name, i == 0, 
					buttonPanel, lastHolder, buttons);
		}
		final List<RadioButtonEntry> buttonEntries = new ArrayList<>();
		
		for (int i=0; i<ExerelinSetupData.NUM_DMOD_LEVELS; i++) {
			final int index = i;
			RadioButtonEntry radio = new RadioButtonEntry(buttons.get(i), "nex_startingDMods_" + i) 
			{
				@Override
				public void onToggleImpl() {
					data.dModLevel = index;
					Global.getLogger(this.getClass()).info("D-mod level: " + data.dModLevel);
				}
			};
			buttonEntries.add(radio);
		}
		for (RadioButtonEntry entry : buttonEntries) {
			entry.buttons = buttonEntries;
			plugin.addButton(entry);
		}
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
	public TooltipMakerAPI initRadioButton(String id, String name, boolean checked,
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
		float pad = 3;
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
	
	/**
	 * Generates a {@code CustomPanelAPI} containing the GUI elements of the option, except the button(s).
	 * @param panel
	 * @param info
	 * @param name
	 * @param imagePath
	 * @param plugin
	 * @param tooltip
	 * @return A {@code CustomPanelAPI} to which buttons may be added.
	 */
	public static CustomPanelAPI prepOption(CustomPanelAPI panel, TooltipMakerAPI info, String name,
			String imagePath, InteractionDialogCustomPanelPlugin plugin, TooltipCreator tooltip) 
	{
		float pad = 3;
		float opad = 10;
		FactionAPI faction = Global.getSector().getPlayerFaction();
		
		CustomPanelAPI row = panel.createCustomPanel(ITEM_WIDTH, ITEM_HEIGHT, null);
		
		// image
		TooltipMakerAPI image = row.createUIElement(ITEM_HEIGHT, ITEM_HEIGHT, false);
		if (imagePath != null) 
			image.addImage(imagePath, ITEM_HEIGHT, 0);
		row.addUIElement(image).inTL(0, 0);
		
		// option name
		TooltipMakerAPI title = row.createUIElement(TEXT_WIDTH, ITEM_HEIGHT, false);
		title.setParaSmallInsignia();
		title.addPara(name, pad);
		if (tooltip != null)
			title.addTooltipToPrevious(tooltip, TooltipMakerAPI.TooltipLocation.BELOW);
		row.addUIElement(title).rightOfTop(image, X_PADDING * 4);
		
		// button holder
		CustomPanelAPI buttonRow = row.createCustomPanel(ITEM_WIDTH - ITEM_HEIGHT - TEXT_WIDTH - X_PADDING * 6, ITEM_HEIGHT, null);
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
					LabelAPI label = tooltip.addPara(text, 3);
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
