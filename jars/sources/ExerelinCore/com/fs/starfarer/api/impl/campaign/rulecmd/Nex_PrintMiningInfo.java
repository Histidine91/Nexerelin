package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.ResourceCostPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.MiningHelperLegacy;
import exerelin.campaign.MiningHelperLegacy.MiningReport;
import exerelin.utilities.StringHelper;
import java.text.MessageFormat;
import java.util.Iterator;


public class Nex_PrintMiningInfo extends BaseCommandPlugin {

	protected static final String STRING_CATEGORY = "exerelin_mining";
	public static float COST_HEIGHT = 67;
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();

		if (!MiningHelperLegacy.canMine(target)) return false;
		
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg) {
			case "fleet":
				printFleetInfo(dialog.getTextPanel());
				break;
			case "tools":
				printMiningTools(dialog.getTextPanel());
				break;
			case "planet":
				printPlanetInfo(dialog.getInteractionTarget(), dialog.getTextPanel(), memoryMap);
				break;
			default:
				printPlanetInfo(dialog.getInteractionTarget(), dialog.getTextPanel(), memoryMap);
		}
		return true;
	}
	
	public void printPlanetInfo(SectorEntityToken target, TextPanelAPI text, Map<String, MemoryAPI> memoryMap)
	{
		Color hl = Misc.getHighlightColor();
		Color red = Misc.getNegativeHighlightColor();
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		float miningStrength = MiningHelperLegacy.getFleetMiningStrength(playerFleet);
		String miningStrengthStr = String.format("%.1f", miningStrength);
		MiningReport report = MiningHelperLegacy.getMiningReport(playerFleet, target, 1);
		float danger = report.danger;
		String dangerStr = MessageFormat.format("{0,number,#%}", danger);
		float exhaustion = report.exhaustion;
		String exhaustionStr = String.format("%.1f", exhaustion * 100) + "%";
		
		EconomyAPI economy = Global.getSector().getEconomy();
		String planetType = target.getName();
		if (target instanceof PlanetAPI)
		{
			planetType = ((PlanetAPI)target).getSpec().getName();
		}

		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "planetType")) + ": " + planetType);
		text.highlightInLastPara(hl, planetType);
		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "miningStrength")) + ": " + miningStrengthStr);
		text.highlightInLastPara(hl, miningStrengthStr);
		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "danger")) + ": " + dangerStr);
		if (danger > 0.5) text.highlightInLastPara(red, dangerStr);
		else text.highlightInLastPara(hl, dangerStr);
		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "exhaustion")) + ": " + exhaustionStr);
		text.highlightInLastPara(hl, exhaustionStr);
		
		text.setFontVictor();
		text.setFontSmallInsignia();

		text.addParagraph("-----------------------------------------------------------------------------");
		
		for (Map.Entry<String, Float> tmp : report.totalOutput.entrySet())
		{
			String res = tmp.getKey();
			float amount = tmp.getValue();
			String amountStr = String.format("%.2f", amount);
			String resName = economy.getCommoditySpec(res).getName();
			text.addParagraph(resName + ": " + amountStr);
			text.highlightInLastPara(hl, resName);
		}
 
		text.addParagraph("-----------------------------------------------------------------------------");
		
		FactionAPI playerFaction = Global.getSector().getPlayerFaction();
		Color color = playerFaction.getColor();
		ResourceCostPanelAPI cost = text.addCostPanel(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "machineryAvailable")),
				COST_HEIGHT, color, playerFaction.getDarkUIColor());
		cost.setNumberOnlyMode(true);
		cost.setWithBorder(false);
		cost.setAlignment(Alignment.LMID);
		
		int usable = (int)Math.ceil(MiningHelperLegacy.getRequiredMachinery(miningStrength));
		int available = (int) playerFleet.getCargo().getCommodityQuantity(Commodities.HEAVY_MACHINERY);
		Color curr = color;
		if (usable > available) {
			curr = Misc.getNegativeHighlightColor();
			String warning = StringHelper.getStringAndSubstituteToken(STRING_CATEGORY, "insufficientMachineryWarning", 
					"$shipOrFleet", StringHelper.getShipOrFleet(playerFleet));
			text.addParagraph(warning);
		}
		cost.addCost(Commodities.HEAVY_MACHINERY, "" + usable + " (" + available + ")", curr);
		cost.update();
		text.addParagraph("-----------------------------------------------------------------------------");
		
		text.setFontInsignia();

		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		memory.set("$miningStrength", miningStrength, 0);
	}
	
	public void printFleetInfo(TextPanelAPI text)
	{
		Color hl = Misc.getHighlightColor();
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		
		text.setFontVictor();
		text.setFontSmallInsignia();

		text.addParagraph("-----------------------------------------------------------------------------");
		
		for (FleetMemberAPI member : playerFleet.getFleetData().getMembersInPriorityOrder())
		{
			float strength = MiningHelperLegacy.getShipMiningStrength(member, true);
			if (strength == 0) continue;
			float strengthRaw = MiningHelperLegacy.getShipMiningStrength(member, false);
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
	}
	
	public void printMiningTools(TextPanelAPI text)
	{
		Color hl = Misc.getHighlightColor();
		
		text.addParagraph(StringHelper.getString(STRING_CATEGORY, "miningToolsListHeader"));
		
		text.setFontVictor();
		text.setFontSmallInsignia();
		
		text.addParagraph("-----------------------------------------------------------------------------");
		
		Iterator<Map.Entry<String, Float>> iter = MiningHelperLegacy.getMiningShipsCopy().entrySet().iterator();
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
		
		Iterator<Map.Entry<String, Float>> iter2 = MiningHelperLegacy.getMiningWeaponsCopy().entrySet().iterator();
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
	}
}