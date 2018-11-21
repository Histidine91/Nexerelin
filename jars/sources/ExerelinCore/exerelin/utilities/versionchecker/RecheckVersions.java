package exerelin.utilities.versionchecker;

import com.fs.starfarer.api.Global;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

public class RecheckVersions implements BaseCommand
{
    @Override
    public CommandResult runCommand(String args, CommandContext context)
    {
		try
		{
			VCModPluginCustom.onApplicationLoad();

			// Remove any existing notification plugin and replace with the newly created one
			if (context.isCampaignAccessible())
			{
				Global.getSector().removeScriptsOfClass(UpdateNotificationScript.class);
				VCModPluginCustom.onGameLoad(false);
			}

			Console.showMessage("Update check started successfully.");
			return CommandResult.SUCCESS;
		}
		catch (Exception ex)
		{
			Console.showException("Something went wrong!", ex);
			return CommandResult.ERROR;
		}
    }
}