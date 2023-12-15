package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.ResourceCostPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.intel.PlayerOutpostIntel;
import exerelin.utilities.CrewReplacerUtils;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Map;

public class Nex_PlayerOutpost extends BaseCommandPlugin {
	
	public static float COST_HEIGHT = 67;
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg) {
			case "buildInfo":
				return buildOutpostInfo(dialog.getInteractionTarget(), dialog.getTextPanel(), memoryMap);
			case "build":
				return buildOutpost(dialog.getInteractionTarget(), dialog);
			case "dismantleInfo":
				dismantleOutpostInfo(dialog.getInteractionTarget(), dialog.getTextPanel());
				return true;
			case "dismantle":
				dismantleOutpost(dialog.getInteractionTarget(), dialog);
		}
			
		return true;
	}
	
	protected ResourceCostPanelAPI makeCostPanel(TextPanelAPI text, Color color, Color color2, boolean dismantle) 
	{
		String key = dismantle ? "resourcesRecovered" : "resourcesAvailable";
        ResourceCostPanelAPI cost = text.addCostPanel(Misc.ucFirst(PlayerOutpostIntel.getString(key)),
			COST_HEIGHT, color, color2);
		cost.setNumberOnlyMode(true);
		cost.setWithBorder(false);
		cost.setAlignment(Alignment.LMID);
		return cost;
    }
	
	protected boolean addCostEntry(ResourceCostPanelAPI cost, String commodityId, int needed)
	{
		int available = (int) Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(commodityId);
		if (Commodities.CREW.equals(commodityId)) {
			available = (int)CrewReplacerUtils.getAvailableCommodity(Global.getSector().getPlayerFleet(), commodityId, "salvage_crew");
		}

		Color curr = Global.getSector().getPlayerFaction().getColor();
		if (needed > available) {
			curr = Misc.getNegativeHighlightColor();
		}
		cost.addCost(commodityId, "" + needed + " (" + available + ")", curr);
		return available >= needed;
	}
	
	protected void addCommodityEntry(ResourceCostPanelAPI cost, String commodityId, int amount)
	{
		Color c = Global.getSector().getPlayerFaction().getColor();
		cost.addCost(commodityId, "" + amount, c);
	}
	
	protected boolean hasResources(String commodityId, int needed)
	{
		int available = (int) Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(commodityId);
		return available >= needed;
	}
	
	protected void removeCommodity(String commodityId, int amount, TextPanelAPI text)
	{
		Global.getSector().getPlayerFleet().getCargo().removeCommodity(commodityId, amount);
		AddRemoveCommodity.addCommodityLossText(commodityId, amount, text);
	}
	
	/**
	 * Performs checks for outpost construction and displays info.
	 * @param target Entity the outpost will orbit.
	 * @param text
	 * @param memoryMap
	 * @return True if outpost can be built, false otherwise.
	 */
	protected boolean buildOutpostInfo(SectorEntityToken target, TextPanelAPI text, Map<String, MemoryAPI> memoryMap)
	{
		text.setFontSmallInsignia();
		text.addParagraph(StringHelper.HR);
		
		FactionAPI playerFaction = Global.getSector().getPlayerFaction();
		Color color = playerFaction.getColor();
		Color darkColor = playerFaction.getDarkUIColor();
		ResourceCostPanelAPI cost = makeCostPanel(text, color, darkColor, false);
		boolean enoughMetals = addCostEntry(cost, Commodities.METALS, PlayerOutpostIntel.getMetalsRequired());
		boolean enoughMachinery = addCostEntry(cost, Commodities.HEAVY_MACHINERY, PlayerOutpostIntel.getMachineryRequired());
		boolean enoughSupplies = addCostEntry(cost, Commodities.SUPPLIES, PlayerOutpostIntel.getSuppliesRequired());
		cost.update();
		
		cost = makeCostPanel(text, color, darkColor, false);
		boolean enoughCrew = addCostEntry(cost, Commodities.CREW, PlayerOutpostIntel.getCrewRequired());
		boolean enoughCores = addCostEntry(cost, Commodities.GAMMA_CORE, PlayerOutpostIntel.getGammaCoresRequired());
		cost.update();
 
		boolean canBuild = (enoughMetals && enoughMachinery && enoughSupplies && enoughCrew && enoughCores);
		text.addParagraph(PlayerOutpostIntel.getString("resourceConsumeNote"));
		if (!canBuild)
		{
			String str = StringHelper.getStringAndSubstituteToken("nex_playerOutpost", "insufficientResources", 
					"$shipOrFleet", StringHelper.getShipOrFleet(Global.getSector().getPlayerFleet()));
			text.addParagraph(str);
		}
		text.addPara(PlayerOutpostIntel.getString("upkeepNote"), Misc.getHighlightColor(), 
				Misc.getDGSCredits(PlayerOutpostIntel.getUpkeep()));
		
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
		
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$nex_canBuildOutpost", canBuild, 0);
		
		return canBuild;
	}
	
	protected boolean buildOutpost(SectorEntityToken target, InteractionDialogAPI dialog)
	{
		TextPanelAPI text = dialog.getTextPanel();
		
		PlayerOutpostIntel intel = new PlayerOutpostIntel();
		
		// create entity
		SectorEntityToken outpost = intel.createOutpost(Global.getSector().getPlayerFleet(), target);
		removeCommodity(Commodities.METALS, PlayerOutpostIntel.getMetalsRequired(), text);
		removeCommodity(Commodities.HEAVY_MACHINERY, PlayerOutpostIntel.getMachineryRequired(), text);
		removeCommodity(Commodities.SUPPLIES, PlayerOutpostIntel.getSuppliesRequired(), text);
		removeCommodity(Commodities.GAMMA_CORE, PlayerOutpostIntel.getGammaCoresRequired(), text);
		
		dialog.getVisualPanel().showImageVisual(outpost.getCustomInteractionDialogImageVisual());
		dialog.getTextPanel().addParagraph(PlayerOutpostIntel.getString("outpostComplete"));
		dialog.setInteractionTarget(outpost);
		Global.getSector().getIntelManager().addIntelToTextPanel(intel, text);
		return true;
	}
	
	protected void dismantleOutpostInfo(SectorEntityToken target, TextPanelAPI text) 
	{
		text.setFontSmallInsignia();
		text.addParagraph(StringHelper.HR);
		
		FactionAPI playerFaction = Global.getSector().getPlayerFaction();
		Color color = playerFaction.getColor();
		Color darkColor = playerFaction.getDarkUIColor();
		ResourceCostPanelAPI cost = makeCostPanel(text, color, darkColor, true);
		addCommodityEntry(cost, Commodities.METALS, PlayerOutpostIntel.getMetalsRequired()/2);
		addCommodityEntry(cost, Commodities.HEAVY_MACHINERY, PlayerOutpostIntel.getMachineryRequired()/2);
		//cost.update();
		
		//cost = makeCostPanel(text, color, darkColor, true);
		addCommodityEntry(cost, Commodities.SUPPLIES, PlayerOutpostIntel.getSuppliesRequired()/2);
		addCommodityEntry(cost, Commodities.GAMMA_CORE, PlayerOutpostIntel.getGammaCoresRequired());
		cost.update();
		
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
	}
	
	protected boolean dismantleOutpost(SectorEntityToken target, final InteractionDialogAPI dialog) {
		final PlayerOutpostIntel intel = (PlayerOutpostIntel)target.getMarket().getMemoryWithoutUpdate()
				.get(PlayerOutpostIntel.MARKET_MEMORY_FLAG);
		
		CargoAPI cargo = intel.getMarket().getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();
		cargo.addCommodity(Commodities.METALS, PlayerOutpostIntel.getMetalsRequired()/2);
		cargo.addCommodity(Commodities.HEAVY_MACHINERY, PlayerOutpostIntel.getMachineryRequired()/2);
		cargo.addCommodity(Commodities.SUPPLIES, PlayerOutpostIntel.getSuppliesRequired()/2);
		cargo.addCommodity(Commodities.GAMMA_CORE, PlayerOutpostIntel.getGammaCoresRequired());
		
		dialog.getVisualPanel().showLoot(Misc.ucFirst(StringHelper.getString("salvage")),
				cargo, false, true, true, new CoreInteractionListener() {
			public void coreUIDismissed() {
				intel.dismantleOutpost();
				dialog.dismiss();
			}
		});
		
		return true;
	}
}
