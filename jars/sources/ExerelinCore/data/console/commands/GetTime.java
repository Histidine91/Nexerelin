package data.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class GetTime implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (context != CommandContext.CAMPAIGN_MAP) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        // do me!
        CampaignClockAPI clock = Global.getSector().getClock();
        Console.showMessage("Timestamp: " + clock.getTimestamp());
        Console.showMessage("Elapsed days since D0: " + clock.getElapsedDaysSince(0));
        Console.showMessage("Date string: " + clock.getDateString());
        
        return CommandResult.SUCCESS;
    }
}
