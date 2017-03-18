package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class Exerelin_PrintFleetFactionRelationship extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		SectorEntityToken target = dialog.getInteractionTarget();
		if (target instanceof CampaignFleetAPI)
		{
			FactionAPI faction = target.getFaction();
			String rel = StringHelper.getString("neutral");
			Color highlightColor = Misc.getHighlightColor();
			if (faction.isAtBest(Factions.PLAYER, RepLevel.HOSTILE))
			{
				rel = StringHelper.getString("hostile");
				highlightColor = Misc.getNegativeHighlightColor();
			}
			else if (faction.isAtWorst(Factions.PLAYER, RepLevel.FAVORABLE))
			{
				rel = StringHelper.getString("friendly");
				highlightColor = Misc.getPositiveHighlightColor();
			}
			
			String factionName = Misc.ucFirst(faction.getDisplayNameWithArticle());
			String isOrAre = faction.getDisplayNameIsOrAre();
			
			String output = StringHelper.getString("exerelin_fleets", "fleetRelationship");
			output = StringHelper.substituteToken(output, "$faction", factionName);
			output = StringHelper.substituteToken(output, "$isOrAre", isOrAre);
			output = StringHelper.substituteToken(output, "$relationship", rel);
			
			dialog.getTextPanel().addParagraph(output);
			dialog.getTextPanel().highlightFirstInLastPara(rel, highlightColor);
			return true;
		}
		else return false;
	}
}