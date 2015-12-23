package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.StringHelper;
import exerelin.campaign.MiningHelper;


public class PrintMiningFleetInfo extends BaseCommandPlugin {

	protected static final String STRING_CATEGORY = "exerelin_mining";
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
		TextPanelAPI text = dialog.getTextPanel();

		if (!MiningHelper.canMine(target)) return false;
	
		Color hl = Misc.getHighlightColor();
		Color red = Misc.getNegativeHighlightColor();
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		
		text.setFontVictor();
		text.setFontSmallInsignia();

		text.addParagraph("-----------------------------------------------------------------------------");
		
		for (FleetMemberAPI member : playerFleet.getFleetData().getMembersInPriorityOrder())
		{
			float strength = MiningHelper.getShipMiningStrength(member, true);
			if (strength == 0) continue;
			float strengthRaw = MiningHelper.getShipMiningStrength(member, false);
			String strengthStr = String.format("%.2f", strength);
			String strengthModStr = String.format("%.2f", strength - strengthRaw);
			String shipName = "";
			if (member.isFighterWing()) shipName = member.getVariant().getFullDesignationWithHullName();
			else shipName = member.getShipName() + " (" + member.getHullSpec().getHullName() + "-class)";
			text.addParagraph(shipName + ": " + strengthStr);
			text.highlightInLastPara(hl, strengthStr);
		}
 
		text.addParagraph("-----------------------------------------------------------------------------");
		text.setFontInsignia();

		return true;
	}
}