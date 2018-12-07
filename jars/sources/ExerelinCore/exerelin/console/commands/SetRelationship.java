package exerelin.console.commands;

import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.utilities.NexUtilsReputation;
import org.lazywizard.console.commands.SetRelation;

public class SetRelationship extends SetRelation {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		CommandResult result = super.runCommand(args, context);
		if (result == CommandResult.SUCCESS)
		{
			String[] tmp = args.split(" ");
			String factionId1 = tmp[0];
			String factionId2 = tmp[1];
			//String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
			if (tmp.length == 2)
			{
				factionId2 = Factions.PLAYER;
			}
			if (factionId1.equals(Factions.PLAYER) || factionId2.equals(Factions.PLAYER))
				NexUtilsReputation.syncFactionRelationshipsToPlayer();
			else// if (factionId1.equals(alignedFactionId) || factionId2.equals(Factions.PLAYER))
				NexUtilsReputation.syncPlayerRelationshipsToFaction();
		}
		return result;
	}
}
