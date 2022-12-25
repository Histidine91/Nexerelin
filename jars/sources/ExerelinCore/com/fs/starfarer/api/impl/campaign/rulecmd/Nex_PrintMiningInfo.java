package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.ResourceCostPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.MiningHelperLegacy;
import exerelin.campaign.MiningHelperLegacy.MiningReport;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;


public class Nex_PrintMiningInfo extends BaseCommandPlugin {

	protected static final String STRING_CATEGORY = "exerelin_mining";
	protected static final String WING = Misc.ucFirst(StringHelper.getString("fighterWingShort"));
	public static float COST_HEIGHT = 67;
	
	@Override
	public boolean execute(String ruleId, final InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		SectorEntityToken target = dialog.getInteractionTarget();

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
			
			// doesn't really have anything to do with mining info, but this was the easiest place to put it
			case "cargo":
				dialog.getVisualPanel().showCore(CoreUITabId.CARGO, null, new CoreInteractionListener() {
					@Override
					public void coreUIDismissed() {
						dialog.dismiss();
					}
				});
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
		
		text.setFontSmallInsignia();

		text.addParagraph(StringHelper.HR);
		
		for (Map.Entry<String, Float> tmp : report.totalOutput.entrySet())
		{
			String res = tmp.getKey();
			float amount = tmp.getValue();
			String amountStr = String.format("%.2f", amount);
			String resName = StringHelper.getCommodityName(res);
			text.addParagraph(resName + ": " + amountStr);
			text.highlightInLastPara(hl, resName);
		}
 
		text.addParagraph(StringHelper.HR);
		
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
		text.addParagraph(StringHelper.HR);
		
		text.setFontInsignia();

		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		memory.set("$miningStrength", miningStrength, 0);
	}
	
	public void printFleetInfo(TextPanelAPI text)
	{
		Color hl = Misc.getHighlightColor();
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		
		text.setFontSmallInsignia();

		text.addParagraph(StringHelper.HR);
		
		for (FleetMemberAPI member : playerFleet.getFleetData().getMembersInPriorityOrder())
		{
			float strength = MiningHelperLegacy.getShipMiningStrength(member, true);
			if (strength == 0) continue;
			//float strengthRaw = MiningHelperLegacy.getShipMiningStrength(member, false);
			String strengthStr = String.format("%.2f", strength);
			//String strengthModStr = String.format("%.2f", strength - strengthRaw);
			String shipName = "";
			if (member.isFighterWing()) shipName = member.getVariant().getFullDesignationWithHullName();
			else shipName = member.getShipName() + " (" + member.getHullSpec().getHullNameWithDashClass() + ")";
			text.addParagraph(shipName + ": " + strengthStr);
			text.highlightInLastPara(hl, strengthStr);
		}
 
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
	}
	
	// Formats to 1 decimal place if necessary, integers will be printed with zero decimal places
	protected String getFormattedStrengthString(float strength)
	{
		return strength % 1 == 0 ? String.format("%.0f", strength) : String.format("%.1f", strength);
	}
	
	public void printMiningTools(TextPanelAPI text)
	{
		Color hl = Misc.getHighlightColor();
		
		text.addParagraph(StringHelper.getString(STRING_CATEGORY, "miningToolsListHeader"));
		
		text.setFontSmallInsignia();
		
		text.addParagraph(StringHelper.HR);
		
		// print ships
		List<Pair<ShipHullSpecAPI, Float>> miningHulls = new ArrayList<>();
		
		for (String variantId : Global.getSettings().getAllVariantIds())
		{
			if (!variantId.endsWith("_Hull")) continue;
			ShipVariantAPI variant = Global.getSettings().getVariant(variantId);
			if (variant.isFighter()) continue;	// we'll deal fighters separately
			if (variant.getHints().contains(ShipTypeHints.UNBOARDABLE) && variant.getHints().contains(ShipTypeHints.HIDE_IN_CODEX))
				continue;
			if (MiningHelperLegacy.isHidden(variant.getHullSpec().getHullId()) || MiningHelperLegacy.isHidden(variant.getHullSpec().getBaseHullId()))
				continue;
			
			try {
				FleetMemberAPI temp = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
				float strength = MiningHelperLegacy.getShipMiningStrength(temp, false);
				if (strength == 0) continue;
				miningHulls.add(new Pair<>(temp.getHullSpec(), strength));
			} catch (Exception ex) {
				text.addPara(StringHelper.getString(STRING_CATEGORY, "miningToolsError"), Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), variantId);
				Global.getLogger(this.getClass()).error(ex);
			}
			
		}
		
		Collections.sort(miningHulls, MINING_TOOL_COMPARATOR);
		
		for (Pair<ShipHullSpecAPI, Float> entry : miningHulls) {
			ShipHullSpecAPI hull = entry.one;
			float strength = entry.two;
			String name = hull.getHullName();
			String origin = hull.getManufacturer();
			String size = StringHelper.getString(hull.getHullSize().toString().toLowerCase());
			String strengthStr = getFormattedStrengthString(strength);
			
			String formatted = String.format("%s (%s %s): %s", name, origin, size, strengthStr);
			LabelAPI label = text.addParagraph(formatted);
			label.setHighlight(name, strengthStr);
			label.setHighlightColors(Global.getSettings().getDesignTypeColor(origin), hl);
		}
		
		text.addParagraph("");
		
		// print fighters
		List<Pair<FighterWingSpecAPI, Float>> miningWings = new ArrayList<>();
		
		for (FighterWingSpecAPI wingSpec : Global.getSettings().getAllFighterWingSpecs())
		{
			float strength = MiningHelperLegacy.getWingMiningStrength(wingSpec);
			if (strength == 0) continue;
			if (MiningHelperLegacy.isHidden(wingSpec.getVariant().getHullSpec().getHullId()) 
					|| MiningHelperLegacy.isHidden(wingSpec.getVariant().getHullSpec().getBaseHullId()))
				continue;
			
			miningWings.add(new Pair<>(wingSpec, strength));
		}
		
		Collections.sort(miningWings, MINING_TOOL_COMPARATOR);
		
		for (Pair<FighterWingSpecAPI, Float> entry : miningWings) {
			FighterWingSpecAPI wingSpec = entry.one;
			float strength = entry.two;
			
			String name = Global.getSettings().getVariant(wingSpec.getVariantId()).getHullSpec().getHullName();
			String roleDesc = wingSpec.getRoleDesc();
			if (!name.endsWith(roleDesc))
				name += " " + roleDesc;
			name += " " + WING;
			
			String strengthStr = getFormattedStrengthString(strength);
			String origin = wingSpec.getVariant().getHullSpec().getManufacturer();
			
			String formatted = String.format("%s (%s): %s", name, origin, strengthStr);
			LabelAPI label = text.addParagraph(formatted);
			label.setHighlight(name, strengthStr);
			label.setHighlightColors(Global.getSettings().getDesignTypeColor(origin), hl);
		}
		
		text.addParagraph("");
		
		// now weapons
		List<Pair<WeaponSpecAPI, Float>> miningWeapons = new ArrayList<>();
		
		for (Map.Entry<String, Float> tmp : MiningHelperLegacy.getMiningWeaponsCopy().entrySet())
		{
			String weaponId = tmp.getKey();
			float strength = tmp.getValue();
			if (strength == 0) continue;
			WeaponSpecAPI weapon;
			try {
				weapon = Global.getSettings().getWeaponSpec(weaponId);
			} catch (RuntimeException rex) {
				continue;	// doesn't exist, skip
			}
			miningWeapons.add(new Pair<>(weapon, strength));
		}
		
		Collections.sort(miningWeapons, MINING_TOOL_COMPARATOR);
		
		for (Pair<WeaponSpecAPI, Float> entry : miningWeapons) {
			WeaponSpecAPI weapon = entry.one;
			String name = weapon.getWeaponName();
			float strength = entry.two;
			
			String strengthStr = getFormattedStrengthString(strength);
			String origin = weapon.getManufacturer();
			
			String formatted = String.format("%s (%s, %s %s): %s", name, origin, weapon.getSize().getDisplayName(), weapon.getType().getDisplayName(), strengthStr);
			LabelAPI label = text.addParagraph(formatted);
			label.setHighlight(name, strengthStr);
			label.setHighlightColors(Global.getSettings().getDesignTypeColor(origin), hl);
		}
		
		text.addParagraph("");
		
		// additional help notes
		text.addParagraph(StringHelper.getString(STRING_CATEGORY, "miningToolsListAddendum"));
		
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
	}
	
	public static final Comparator<Pair> MINING_TOOL_COMPARATOR = new Comparator<Pair>() {
		@Override
		public int compare(Pair p1, Pair p2) {
			if (p1.one instanceof ShipHullSpecAPI) {
				String n1 = ((ShipHullSpecAPI)p1.one).getHullName();
				String n2 = ((ShipHullSpecAPI)p2.one).getHullName();
				return n1.compareTo(n2);
			}
			else if (p1.one instanceof FighterWingSpecAPI) {
				String n1 = ((FighterWingSpecAPI)p1.one).getWingName();
				String n2 = ((FighterWingSpecAPI)p2.one).getWingName();
				return n1.compareTo(n2);
			}
			else if (p1.one instanceof WeaponSpecAPI) {
				String n1 = ((WeaponSpecAPI)p1.one).getWeaponName();
				String n2 = ((WeaponSpecAPI)p2.one).getWeaponName();
				return n1.compareTo(n2);
			}
			else if (p1.one instanceof String) {
				return ((String)p1.one).compareTo(((String)p2.one));
			}
			
			return 0;
		}
	};
}