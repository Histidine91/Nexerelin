package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexFactionConfig.StartFleetType;
import exerelin.utilities.StringHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.Map;


public class Nex_NGCStartResources extends BaseCommandPlugin {
	
	public static final float BAR_WIDTH = 320;
	public static int MAX_LEVEL = 8;
	public static int MAX_CREDITS_MULT = 300;
	public static int MAX_OFFICERS = 4;
	public static int MAX_OPERATIVES = 2;
	
	protected String getString(String id)
	{
		return StringHelper.getString("exerelin_ngc", id);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
		switch (arg) {
			case "createOptions":
				createSliders(dialog.getOptionPanel(), memoryMap);
				return true;
			case "save":
				saveValues(dialog.getOptionPanel(), dialog.getTextPanel(), local);
				return true;
			case "reroll":
				rerollRandomShips(memoryMap);
				return true;
			case "randomize":
				randomizeValues(dialog.getOptionPanel(), dialog.getTextPanel(), local);
				return true;
			case "reset":
				resetValues(local);
				return true;
			default:
				return false;
		}
	}
	
	protected void createSliders(OptionPanelAPI opts, Map<String, MemoryAPI> memoryMap)
	{
		CharacterCreationData charData = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		opts.addSelector(getString("startingLevelTitle"), "startLevelSelector", Color.GREEN, BAR_WIDTH, 48, 
				1, MAX_LEVEL,	// min, max
				ValueDisplayMode.VALUE, null);
		opts.setSelectorValue("startLevelSelector", charData.getPerson().getStats().getLevel());
		
		opts.addSelector(getString("startingCreditsTitle"), "startCreditsSelector", Color.YELLOW, BAR_WIDTH, 48, 
				0, MAX_CREDITS_MULT,	// min, max
				ValueDisplayMode.VALUE, null);
		int credits = (int)Math.max(charData.getStartingCargo().getCredits().get()/1000, 20);
		opts.setSelectorValue("startCreditsSelector", credits);
		//Global.getLogger(this.getClass()).info(String.format("Wants %s credits, current slider value is %s", credits, opts.getSelectorValue("startCreditsSelector")));
		
		opts.addSelector(getString("startingOfficersTitle"), "startOfficersSelector", Color.CYAN, BAR_WIDTH, 48, 
				0, MAX_OFFICERS,	// min, max
				ValueDisplayMode.VALUE, null);
		opts.setSelectorValue("startOfficersSelector", data.numStartingOfficers);

		opts.addSelector(getString("startingOperativesTitle"), "startOperativesSelector", Color.CYAN, BAR_WIDTH, 48,
				0, MAX_OPERATIVES,	// min, max
				ValueDisplayMode.VALUE, null);
		opts.setSelectorValue("startOperativesSelector", data.numStartingOperatives);
		
		MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
		if (true && local.contains("$nex_lastSelectedFleetType")) 
		{
			StartFleetType type = StartFleetType.getType(local.getString("$nex_lastSelectedFleetType"));
			if (type != StartFleetType.CUSTOM && type != StartFleetType.SUPER) {
				opts.addOption(Misc.ucFirst(StringHelper.getString("exerelin_ngc",
						"fleetRandomReroll")), "nex_NGCStep4FleetReroll");
			}
			else if (type == StartFleetType.CUSTOM) {
				opts.addOption(Misc.ucFirst(StringHelper.getString("exerelin_ngc",
						"fleetCustomRepick")), "nex_NGCStep4FleetReroll");
			}
		}
		
		opts.addOption(StringHelper.getString("back", true), "nex_NGCStartBack");
		opts.setShortcut("nex_NGCStartBack", Keyboard.KEY_ESCAPE, false, false, false, false);
	}

	protected void saveValues(OptionPanelAPI opts, TextPanelAPI text, MemoryAPI mem, int level, long xp, int credits, int officers, int operatives) {
		CharacterCreationData charData = (CharacterCreationData) mem.get("$characterData");
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();

		charData.getPerson().getStats().addXP(xp);
		Nex_NGCAddLevel.addXPGainText(xp, text);

		int storyPoints = (level - 1) * Global.getSettings().getInt("storyPointsPerLevel");
		charData.getPerson().getStats().addStoryPoints(storyPoints, text, false);

		charData.getStartingCargo().getCredits().add(credits);
		AddRemoveCommodity.addCreditsGainText(credits, text);

		setupData.numStartingOfficers = officers;
		NGCSetNumStartingOfficers.addOfficersGainText(officers, text);

		setupData.numStartingOperatives = operatives;
		NGCSetNumStartingOfficers.addOfficersGainText(operatives, text, true);
	}
	
	protected void saveValues(OptionPanelAPI opts, TextPanelAPI text, MemoryAPI mem)
	{
		int level = Math.round(opts.getSelectorValue("startLevelSelector"));
		long xp = Global.getSettings().getLevelupPlugin().getXPForLevel(level);
		int credits = Math.round(opts.getSelectorValue("startCreditsSelector")) * 1000;
		int officers = Math.round(opts.getSelectorValue("startOfficersSelector"));
		int operatives = Math.round(opts.getSelectorValue("startOperativesSelector"));
		//Global.getLogger(this.getClass()).info(String.format("bla: %s, %s, %s, %s", level, xp, credits, officers));
		
		saveValues(opts, text, mem, level, xp, credits, officers, operatives);
	}

	protected void resetValues(MemoryAPI mem) {
		CharacterCreationData charData = (CharacterCreationData) mem.get("$characterData");
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();

		charData.getPerson().getStats().setXP(0);
		charData.getPerson().getStats().setLevel(1);
		charData.getPerson().getStats().setStoryPoints(0);

		charData.getStartingCargo().getCredits().set(0);
		setupData.numStartingOfficers = 0;
		setupData.numStartingOperatives = 0;
	}

	protected void randomizeValues(OptionPanelAPI opts, TextPanelAPI text, MemoryAPI mem) {
		int level = MathUtils.getRandomNumberInRange(1, MAX_LEVEL);
		long xp = Global.getSettings().getLevelupPlugin().getXPForLevel(level);
		int credits = MathUtils.getRandomNumberInRange(20, MAX_CREDITS_MULT) * 1000;
		int officers = MathUtils.getRandomNumberInRange(0, MAX_OFFICERS);
		int operatives = MathUtils.getRandomNumberInRange(0, MAX_OPERATIVES);

		saveValues(opts, text, mem, level, xp, credits, officers, operatives);
	}
	
	protected void rerollRandomShips(Map<String, MemoryAPI> memoryMap) {
		String fleetTypeStr = memoryMap.get(MemKeys.LOCAL).getString("$nex_lastSelectedFleetType");
		
		NexFactionConfig factionConf = NexConfig.getFactionConfig(
				PlayerFactionStore.getPlayerFactionIdNGC());
		List<String> startingVariants = factionConf.getStartFleetForType(fleetTypeStr, false, -1);
		memoryMap.get(MemKeys.LOCAL).set("$startShips_" + fleetTypeStr, startingVariants);
	}
}