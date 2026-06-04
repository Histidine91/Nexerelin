package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import org.lazywizard.console.*;

import java.util.ArrayList;
import java.util.List;

public class LeaveAlliance implements BaseCommandWithSuggestion {

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

        if (tmp.length != 1)
        {
            return CommandResult.BAD_SYNTAX;
        }

        String factionArg = tmp[0];

        FactionAPI fac = CommandUtils.findBestFactionMatch(factionArg);

        if (fac == null)
        {
            Console.showMessage("Error: no such faction '" + factionArg + "'!");
            return CommandResult.ERROR;
        }

        String factionId = fac.getId();
        Alliance alliance = AllianceManager.getFactionAlliance(factionId);
		if (alliance == null) {
			Console.showMessage(CommandUtils.getFactionName(fac) + " is not in an alliance");
            return CommandResult.ERROR;
		}
		AllianceManager.getManager().leaveAlliance(factionId, alliance, false, true);
		
        Console.showMessage(CommandUtils.getFactionName(fac) + " has left their alliance.");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, BaseCommand.CommandContext context) {
        List<String> suggestions = new ArrayList<>();
        if (parameter == 0) suggestions.addAll(Global.getSettings().getAllFactionSpecs().stream().map(it -> it.getId()).toList());
        return suggestions;
    }
}
