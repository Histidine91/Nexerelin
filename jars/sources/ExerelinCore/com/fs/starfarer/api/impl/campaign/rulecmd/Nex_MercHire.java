package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.merc.MercContractIntel;
import static exerelin.campaign.intel.merc.MercContractIntel.getString;
import exerelin.campaign.intel.merc.MercDataManager;
import exerelin.campaign.intel.merc.MercDataManager.MercCompanyDef;
import exerelin.campaign.intel.merc.MercSectorManager;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Nex_MercHire extends BaseCommandPlugin {
	
	public static final String MEM_KEY_HIRES = "$nex_mercsForHire";
	public static final String MEM_KEY_NUM_HIRES = "$nex_mercsForHire_count";
	public static final String OPTION_PREFIX = "nex_viewMerc_";
	public static final int PREFIX_LENGTH = OPTION_PREFIX.length();
	public static final int SHIP_ROW_LENGTH = 7;
	public static final float MERC_INFO_WIDTH = Nex_VisualCustomPanel.PANEL_WIDTH - 128 - 8;
	//public static final float MERC_INFO_HEIGHT = 48;
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		String arg = params.get(0).getString(memoryMap);
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		switch (arg) {
			case "init":
				return init(market, memoryMap.get(MemKeys.LOCAL));
			case "showOptions":
				showOptions(dialog);
				break;
			case "view":
				String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
				String companyId = option.substring(PREFIX_LENGTH);
				memoryMap.get(MemKeys.LOCAL).set("$nex_selectedMercCompanyId", companyId, 0);
				memoryMap.get(MemKeys.LOCAL).set("$nex_selectedMercCompanyName", MercDataManager.getDef(companyId).name, 0);
				viewCompany(dialog, companyId);
				break;
			case "displayCommander":
				displayCompanyCommander(dialog, memoryMap.get(MemKeys.LOCAL).getString("$nex_selectedMercCompanyId"));
				break;
			case "hire":
				hire(dialog, market, memoryMap.get(MemKeys.LOCAL).getString("$nex_selectedMercCompanyId"));
				break;
			case "showHelp":
				// TODO
				break;
		}
		return true;
	}
	
	public MercContractIntel getSelectedCompany(MarketAPI market, String id) {
		List<MercContractIntel> hires = getAvailableHires(market);
		for (MercContractIntel intel : hires) {
			if (intel.getDef().id.equals(id)) {
				return intel;
			}
		}
		return null;
	}
	
	public void hire(InteractionDialogAPI dialog, MarketAPI market, String companyId) {
		MercContractIntel intel = getSelectedCompany(market, companyId);
		intel.accept(dialog.getTextPanel());
		int price = intel.getDef().feeUpfront;
		Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(price);
		AddRemoveCommodity.addCreditsLossText(price, dialog.getTextPanel());
	}
	
	// Do the stuff to make the CO appear
	// if have any officers, pick the first officer and show their face
	// else, create a generic officer and show that?
	public void displayCompanyCommander(InteractionDialogAPI dialog, String companyId) 
	{
		MercContractIntel intel = getSelectedCompany(dialog.getInteractionTarget().getMarket(), companyId);
		if (intel.getOfferedFleet().getFleetData().getOfficersCopy().isEmpty()) 
		{
			// FIXME
			PersonAPI temp = intel.getOfferedFleet().getMembersWithFightersCopy().get(0).getCaptain();
			dialog.getVisualPanel().showSecondPerson(temp);
		} 
		else 
		{
			OfficerDataAPI officer = intel.getOfferedFleet().getFleetData().getOfficersCopy().get(0);
			dialog.getVisualPanel().showSecondPerson(officer.getPerson());
			Global.getLogger(this.getClass()).info("Showing second person: " + officer.getPerson());
		}
	}
	
	public void viewCompany(InteractionDialogAPI dialog, String companyId) 
	{
		MercContractIntel intel = getSelectedCompany(dialog.getInteractionTarget().getMarket(), companyId);
		
		MercCompanyDef def = intel.getDef();
		TextPanelAPI text = dialog.getTextPanel();
		TooltipMakerAPI info = text.beginTooltip();
		//info.setParaSmallInsignia();
		float pad = 3, opad = 10;
		Color h = Misc.getHighlightColor();

		info.addPara(def.desc, pad);

		String fee1 = Misc.getDGSCredits(def.feeUpfront);
		String fee2 = Misc.getDGSCredits(def.feeMonthly);
		info.addPara(getString("intel_desc_feeUpfront") + ": " + fee1, opad, h, fee1);
		info.addPara(getString("intel_desc_feeMonthly") + ": " + fee2, pad, h, fee2);

		List<OfficerDataAPI> officers = intel.getOfferedFleet().getFleetData().getOfficersCopy();
		List<String> levels = new ArrayList<>();
		for (OfficerDataAPI officer : officers) {
			levels.add(officer.getPerson().getStats().getLevel() + "");
		}
		if (!levels.isEmpty()) {
			info.addPara("Officer levels: [" + StringHelper.writeStringCollection(levels) + "]", pad, h, 
					levels.toArray(new String[0]));
		}

		List<FleetMemberAPI> list = new ArrayList<FleetMemberAPI>();
		List<FleetMemberAPI> members = intel.getOfferedFleet().getFleetData().getMembersListCopy();
		int cols = 7;
		float iconSize = 440 / cols;

		for (FleetMemberAPI member : members) {
			if (list.size() >= cols) break;
			if (member.isFighterWing()) continue;
			list.add(member);
		}

		info.addShipList(cols, 1, iconSize, intel.getDef().getFaction().getBaseUIColor(), list, opad);

		info.setParaSmallInsignia();
		int surplus = members.size() - list.size();
		if (surplus > 0) {
			info.addPara("The company includes %s other ships of lesser significance.", opad, h, "" + surplus);
		}
		text.addTooltip();
	}
	
	
	public void showOptions(InteractionDialogAPI dialog) {
		float pad = 3;
		
		Nex_VisualCustomPanel.createPanel(dialog, true);
		dialog.getOptionPanel().clearOptions();
		
		TooltipMakerAPI panelTooltip = Nex_VisualCustomPanel.getTooltip();
		List<MercContractIntel> hires = getAvailableHires(dialog.getInteractionTarget().getMarket());
		int credits = (int)Global.getSector().getPlayerFleet().getCargo().getCredits().get();
		for (MercContractIntel intel : hires) {
			MercCompanyDef def = intel.getDef();
			TooltipMakerAPI entryHolder = panelTooltip.beginImageWithText(def.getLogo(), 48);
			
			CustomPanelAPI info = Nex_VisualCustomPanel.getPanel().createCustomPanel(MERC_INFO_WIDTH, 48, null);
			TooltipMakerAPI text = info.createUIElement(160, 0, false);
			
			boolean enough = credits >= def.feeUpfront;
			
			text.setParaSmallInsignia();
			text.addPara(def.name, def.getFaction().getBaseUIColor(), 0);
			text.setParaFontDefault();
			String fee1 = Misc.getDGSCredits(def.feeUpfront);
			String fee2 = Misc.getDGSCredits(def.feeMonthly);
			text.addPara(fee1 + " + " + fee2 + "/mth", 3, enough ? Misc.getHighlightColor() 
					: Misc.getNegativeHighlightColor(), fee1, fee2);
			info.addUIElement(text).inTL(0, 0);
			
			TooltipMakerAPI shipHolder = info.createUIElement(MERC_INFO_WIDTH - 160 - 8, 0, false);
			int max = Math.min(SHIP_ROW_LENGTH, intel.getOfferedFleet().getMembersWithFightersCopy().size());
			List<FleetMemberAPI> ships = intel.getOfferedFleet().getMembersWithFightersCopy().subList(0, max);
			shipHolder.addShipList(SHIP_ROW_LENGTH, 1, 48, Misc.getBasePlayerColor(), ships, pad);
			info.addUIElement(shipHolder).rightOfTop(text, pad);
			
			entryHolder.addCustom(info, 0);
			
			panelTooltip.addImageWithText(pad);
			
			dialog.getOptionPanel().addOption(def.name, OPTION_PREFIX + def.id);
			if (!enough) {
				dialog.getOptionPanel().setEnabled(OPTION_PREFIX + def.id, false);
			}
		}
		dialog.getOptionPanel().addOption("Go back", "nex_mercRepBack");
		
		Nex_VisualCustomPanel.addTooltipToPanel();
	}
	
	public boolean init(MarketAPI market, MemoryAPI local) {
		if (MercContractIntel.hasOngoing()) {
			local.set("$nex_mercContractOngoing", true, 0);
			return false;
		}
		
		List<MercContractIntel> hires = getAvailableHires(market);
		Global.getLogger(this.getClass()).info("Hires: " + hires.size());
		local.set(MEM_KEY_NUM_HIRES, hires.size(), 0);
		return !hires.isEmpty();
	}
	
	public List<MercContractIntel> getAvailableHires(MarketAPI market) 
	{
		List<MercContractIntel> hires = (List<MercContractIntel>)market.getMemoryWithoutUpdate().get(MEM_KEY_HIRES);
		if (hires == null) {
			hires = MercSectorManager.getInstance().getAvailableHires(market);
			market.getMemoryWithoutUpdate().set(MEM_KEY_HIRES, hires, 7f);
		}
		return hires;
	}
}
