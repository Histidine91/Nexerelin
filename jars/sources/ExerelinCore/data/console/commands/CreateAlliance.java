package data.console.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.AllianceManager.Alignment;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.lazylib.MathUtils;

public class CreateAlliance implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (context != CommandContext.CAMPAIGN_MAP) {
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

        Alignment alignment = Alignment.values()[MathUtils.getRandomNumberInRange(0, Alignment.values().length - 1)];
        if (tmp.length == 3)
        {
            try
            {
                alignment = Alignment.valueOf(tmp[2].toUpperCase());
            }
            catch (NumberFormatException ex)
            {
                Console.showMessage("Error: invalid alignment for alliance!");
                return CommandResult.BAD_SYNTAX;
            }
        }

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

        AllianceManager.createAlliance(faction1, faction2, alignment);
        Console.showMessage("Created " + alignment.toString().toLowerCase() + " alliance between "
                + CommandUtils.getFactionName(fac1) + " and "
                + CommandUtils.getFactionName(fac2));
        return CommandResult.SUCCESS;
    }
}
