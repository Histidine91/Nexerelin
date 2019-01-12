package exerelin.console.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.DiplomacyManager;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class CreateDiplomacyEvent implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
        if (args.isEmpty())
        {
            return CommandResult.BAD_SYNTAX;
        }

        String[] tmp = args.split(" ");

        if (tmp.length < 2)
        {
            return CommandResult.BAD_SYNTAX;
        }

        String faction1 = tmp[0];
        String faction2 = tmp[1];

        FactionAPI fac1 = CommandUtils.findBestFactionMatch(faction1);
        FactionAPI fac2 = CommandUtils.findBestFactionMatch(faction2);

        if (fac1 == null)
        {
            Console.showMessage("Error: no such faction '" + faction1 + "'!");
            return CommandResult.ERROR;
        }
        if (fac2 == null)
        {
            Console.showMessage("Error: no such faction '" + faction2 + "'!");
            return CommandResult.ERROR;
        }
		
		String eventId = null;
		if (tmp.length >= 3) eventId = tmp[2];
        DiplomacyManager.createDiplomacyEvent(fac1, fac2, eventId, null);
        Console.showMessage("Creating diplomacy event between "
                + CommandUtils.getFactionName(fac1) + " and "
                + CommandUtils.getFactionName(fac2));
        return CommandResult.SUCCESS;
    }
}
