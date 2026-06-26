package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.utilities.NexUtils;
import org.lazywizard.console.*;

import java.util.ArrayList;
import java.util.List;

public class CreateAlliance implements BaseCommandWithSuggestion {

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

        if (tmp.length < 2 || tmp.length > 3)
        {
            return CommandResult.BAD_SYNTAX;
        }

        String faction1 = tmp[0];
        String faction2 = tmp[1];

        Alignment alignment = (Alignment) NexUtils.getRandomListElement(Alignment.getAlignments());
        if (tmp.length == 3)
        {
            try
            {
                alignment = Alignment.valueOf(tmp[2].toUpperCase());
            }
            catch (Exception ex)
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

        AllianceManager.createAlliance(fac1.getId(), fac2.getId(), alignment);
        Console.showMessage("Created " + alignment.toString().toLowerCase() + " alliance between "
                + CommandUtils.getFactionName(fac1) + " and "
                + CommandUtils.getFactionName(fac2));
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, BaseCommand.CommandContext context) {
        List<String> suggestions = new ArrayList<>();
        if (parameter == 0 || parameter == 1) suggestions.addAll(Global.getSettings().getAllFactionSpecs().stream().map(it -> it.getId()).toList());
        else if (parameter == 2) suggestions.addAll(Alliance.Alignment.getAlignments().stream().map(it -> it.toString()).toList());
        return suggestions;
    }
}
