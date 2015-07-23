package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.StringHelper;
import exerelin.campaign.MiningHelper;
import java.util.Iterator;


public class PrintMiningInfo extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
		TextPanelAPI text = dialog.getTextPanel();

		if (!MiningHelper.canMine(target)) return false;
	
		Color hl = Misc.getHighlightColor();
		Color red = Misc.getNegativeHighlightColor();
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		float miningStrength = MiningHelper.getFleetMiningStrength(playerFleet);
		String miningStrengthStr = String.format("%.1f", miningStrength);
		float danger = MiningHelper.getDanger(target);
		String dangerStr = String.format("%.1f", danger);
		Map<String, Float> resources = MiningHelper.getResources(target);
		EconomyAPI economy = Global.getSector().getEconomy();
		String planetType = target.getName();
		if (target instanceof PlanetAPI)
		{
			planetType = ((PlanetAPI)target).getSpec().getName();
		}

		text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_mining", "planetType")) + ": " + planetType);
		text.highlightInLastPara(hl, planetType);
		text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_mining", "miningStrength")) + ": " + miningStrengthStr);
		text.highlightInLastPara(hl, miningStrengthStr);
		text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_mining", "danger")) + ": " + dangerStr);
		if (danger > 0.5) text.highlightInLastPara(red, dangerStr);
		else text.highlightInLastPara(hl, dangerStr);
		
		text.setFontVictor();
		text.setFontSmallInsignia();

		text.addParagraph("-----------------------------------------------------------------------------");
		
		Iterator<String> iter = resources.keySet().iterator();
		while (iter.hasNext())
		{
			String res = iter.next();
			float amount = resources.get(res);
			String amountStr = String.format("%.2f", amount);
			String resName = economy.getCommoditySpec(res).getName();
			text.addParagraph(resName + ": " + amountStr);
			text.highlightInLastPara(hl, resName);
		}
 
		text.addParagraph("-----------------------------------------------------------------------------");
		text.setFontInsignia();
	
	MemoryAPI memory = memoryMap.get(MemKeys.GLOBAL);
		memory.set("$miningStrength", miningStrength, 0);

		return true;
	}
}