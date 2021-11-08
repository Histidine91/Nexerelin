package exerelin.console.commands;

import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.StatsTracker.DeadOfficerEntry;
import exerelin.utilities.StringHelper;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class PrintDeadOfficers implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
		
		List<DeadOfficerEntry> deadOfficers = StatsTracker.getStatsTracker().getDeadOfficersSorted();
		if (deadOfficers.isEmpty())
		{
			Console.showMessage(StringHelper.getString("exerelin_officers", "noOfficersDeadForNow"));
			return CommandResult.SUCCESS;
		}
		
		Console.showMessage(StringHelper.HR);
		for (DeadOfficerEntry dead : deadOfficers)
		{
			Console.showMessage(dead.officer.getPerson().getName().getFullName());
			
			String level = Misc.ucFirst(StringHelper.getString("level")) + " " + dead.officer.getPerson().getStats().getLevel();
			Console.showMessage("  " + level);
			
			String diedOn = StringHelper.getString("exerelin_officers", "diedOn");
			diedOn = StringHelper.substituteToken(diedOn, "$date", dead.getDeathDate());
			Console.showMessage("  " + diedOn);
			
			String lastCommand = StringHelper.getString("exerelin_officers", "lastCommand");
			lastCommand = StringHelper.substituteToken(lastCommand, "$shipName", dead.shipName);
			lastCommand = StringHelper.substituteToken(lastCommand, "$shipClass", dead.shipClass);
			Console.showMessage("  " + lastCommand);
			
			String cod = Misc.ucFirst(StringHelper.getString("exerelin_officers", "causeOfDeath")  + ": ");
			String cod2 = Misc.ucFirst(dead.causeOfDeath);
			cod += cod2;
			Console.showMessage("  " + cod);
		}
        Console.showMessage(StringHelper.HR);
        return CommandResult.SUCCESS;
    }
}
