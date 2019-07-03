package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyBrain.DispositionEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.lazylib.CollectionUtils;

public class PrintDisposition implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (!context.isInCampaign()) {
			Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
			return CommandResult.WRONG_CONTEXT;
		}

		// do me!
		if (args.isEmpty())
		{
			return CommandResult.BAD_SYNTAX;
		}
		
		String[] tmp = args.split(" ");
		if (tmp.length < 2) {
			return CommandResult.BAD_SYNTAX;
		}
		
		String faction1IdRaw = tmp[0];
		String faction2IdRaw = tmp[1];
		FactionAPI faction1 = CommandUtils.findBestFactionMatch(faction1IdRaw);
		FactionAPI faction2 = CommandUtils.findBestFactionMatch(faction2IdRaw);
		
		if (faction1 == null) {
			final List<String> ids = new ArrayList<>();
			for (FactionAPI faction : Global.getSector().getAllFactions())
			{
				ids.add(faction.getId());
			}
			
			Console.showMessage("Error: no such faction '" + faction1IdRaw
					+ "'! Valid factions: " + CollectionUtils.implode(ids) + ".");
		}
		if (faction2 == null) {
			final List<String> ids = new ArrayList<>();
			for (FactionAPI faction : Global.getSector().getAllFactions())
			{
				ids.add(faction.getId());
			}
			
			Console.showMessage("Error: no such faction '" + faction2IdRaw
					+ "'! Valid factions: " + CollectionUtils.implode(ids) + ".");
		}
		
		DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(faction1.getId());
		brain.updateDisposition(faction2.getId(), 0);
		DispositionEntry entry = brain.getDisposition(faction2.getId());
		Console.showMessage(faction1.getDisplayName() + " disposition towards " + faction2.getDisplayName() 
				+ ": " + entry.disposition.getModifiedValue());
		printMods(entry.disposition.getFlatMods(), 0);
		printMods(entry.disposition.getMultMods(), 1);
		printMods(entry.disposition.getPercentMods(), 2);
		
		return CommandResult.SUCCESS;
	}
	
	public static void printMods(HashMap<String, StatMod> mods, int type) {
		for (Map.Entry<String, StatMod> tmp : mods.entrySet()) {
			StatMod mod = tmp.getValue();
			String name = mod.desc;
			String amount = "" + mod.value;
			if (type == 0)	// flat
				amount = mod.value >= 0 ? "+" + amount : amount;
			
			if (type == 1) // mult
				amount = amount + "×";
			else if (type == 2)	// percent
				amount = amount + "%";
			
			String output = "  " + name + ": " + amount;
			
			Console.showMessage(output);
		}
	}
}
