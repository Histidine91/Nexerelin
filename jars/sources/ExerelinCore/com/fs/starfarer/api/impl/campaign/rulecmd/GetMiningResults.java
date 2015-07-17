package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.StringHelper;
import exerelin.campaign.MiningHelper;
import exerelin.campaign.MiningHelper.CacheResult;
import exerelin.campaign.MiningHelper.CacheType;
import exerelin.campaign.MiningHelper.MiningResult;
import java.util.Iterator;


public class GetMiningResults extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
		TextPanelAPI text = dialog.getTextPanel();

		if (!MiningHelper.canMine(target)) return false;
		
		Color hl = Misc.getHighlightColor();
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		MiningResult results = MiningHelper.getMiningResults(playerFleet, target, true);
		Map<String, Float> resources = results.resources;
		EconomyAPI economy = Global.getSector().getEconomy();
		
		//text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_mining", "miningReport")));
		
		text.setFontVictor();
		text.setFontSmallInsignia();

		text.addParagraph("-----------------------------------------------------------------------------");
		
		Iterator<String> iter = resources.keySet().iterator();
		while (iter.hasNext())
		{
			String res = iter.next();
			int amount = (int)(float)resources.get(res);
			
			playerFleet.getCargo().addCommodity(res, amount);
			
			//String amountStr = String.format("%.0f", amount);
			String resName = economy.getCommoditySpec(res).getName();
			text.addParagraph(resName + ": " + amount);
			text.highlightInLastPara(hl, resName);
		}
		
		if (!results.cachesFound.isEmpty())
		{
			text.addParagraph("");
			text.addParagraph(StringHelper.getString("exerelin_mining", "cacheFound"));
			
			for (CacheResult cache: results.cachesFound)
			{
				String displayStr = "- " + cache.name;
				if (cache.def.type != CacheType.FRIGATE)
				{
					displayStr += (" x " + cache.numItems);
				}
				text.addParagraph(displayStr);
				text.highlightInLastPara(hl, cache.name);
			}
		}
 
		text.addParagraph("-----------------------------------------------------------------------------");
		text.setFontInsignia();

		return true;
	}
}