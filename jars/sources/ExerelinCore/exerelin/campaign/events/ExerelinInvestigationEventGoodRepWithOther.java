package exerelin.campaign.events;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.events.InvestigationEventGoodRepWithOther;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;

// don't investigate if in same alliance, etc.
public class ExerelinInvestigationEventGoodRepWithOther extends InvestigationEventGoodRepWithOther {
	
	@Override
	public void startEvent() {
		InvestigationGoodRepData targetLocal = (InvestigationGoodRepData) eventTarget.getCustom();
		FactionAPI thisFaction = targetLocal.getFaction();
		FactionAPI otherFaction = targetLocal.getOther();
		String thisFactionId = thisFaction.getId();
		String otherFactionId = otherFaction.getId();
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		
		boolean shouldProceed = true;
		
		if (otherFaction.getId().equals(playerAlignedFactionId))
			shouldProceed = false;
		else if (thisFaction.getId().equals(playerAlignedFactionId))
			shouldProceed = false;
		if (AllianceManager.areFactionsAllied(thisFactionId, otherFactionId))
			shouldProceed = false;
		else
		{
			// I call this the "hypocrite check"
			//float factionRel = faction.getRelationship(otherFaction.getId());
			//float playerRep = otherFaction.getRelationship(Factions.PLAYER);
		
			//if (factionRel >= playerRep)
			//	shouldProceed = false;
			RepLevel factionRel = thisFaction.getRelationshipLevel(otherFaction);
			RepLevel playerRep = otherFaction.getRelationshipLevel(Factions.PLAYER);
			
			if (factionRel == RepLevel.FRIENDLY) shouldProceed = false;	// could still do if player is COOPERATIVE but that gets the full penalty so meh
			// comment out because then there's no warning before you hit COOPERATIVE and get the full penalty
			//else if (factionRel == RepLevel.WELCOMING && playerRep.isAtBest(RepLevel.FRIENDLY)) shouldProceed = false;
			else if (factionRel.isAtWorst(RepLevel.FAVORABLE) && playerRep.isAtBest(RepLevel.WELCOMING)) shouldProceed = false;
		}
		
		if (!shouldProceed)
		{
			endEvent();
			return;
		}
		
		super.startEvent();
	}
}
