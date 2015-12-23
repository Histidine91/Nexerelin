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
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.StringHelper;
import exerelin.campaign.MiningHelper;
import java.util.Iterator;


public class PrintMiningToolsList extends BaseCommandPlugin {

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
		text.addParagraph(StringHelper.getString(STRING_CATEGORY, "miningToolsListHeader"));
		
		text.setFontVictor();
		text.setFontSmallInsignia();

		text.addParagraph("-----------------------------------------------------------------------------");
		
		Iterator<Map.Entry<String, Float>> iter = MiningHelper.getMiningShipsCopy().entrySet().iterator();
		while (iter.hasNext())
		{
            Map.Entry<String, Float> tmp = iter.next();
			String shipId = tmp.getKey();
			float strength = tmp.getValue();
			if (strength == 0) continue;
			ShipVariantAPI variant;
			try {
				variant = Global.getSettings().getVariant(shipId + "_Hull");
			} catch (RuntimeException rex) {
				continue;	// doesn't exist, skip
			}
			String name = variant.getHullSpec().getHullName();
			
			String strengthStr = String.format("%.1f", strength);
			text.addParagraph(name + ": " + strengthStr);
			text.highlightInLastPara(hl, name);
		}
		text.addParagraph("");
		
		Iterator<Map.Entry<String, Float>> iter2 = MiningHelper.getMiningWeaponsCopy().entrySet().iterator();
		while (iter2.hasNext())
		{
            Map.Entry<String, Float> tmp = iter2.next();
			String weaponId = tmp.getKey();
			float strength = tmp.getValue();
			if (strength == 0) continue;
			WeaponSpecAPI weapon;
			try {
				weapon = Global.getSettings().getWeaponSpec(weaponId);
			} catch (RuntimeException rex) {
				continue;	// doesn't exist, skip
			}
			String name = weapon.getWeaponName();
			
			String strengthStr = String.format("%.1f", strength);
			text.addParagraph(name + ": " + strengthStr);
			text.highlightInLastPara(hl, name);
		}
 
		text.addParagraph("-----------------------------------------------------------------------------");
		text.setFontInsignia();

		return true;
	}
}