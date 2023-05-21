package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.MutableValue;
import exerelin.campaign.intel.hostileactivity.MercPackageActivityCause;
import exerelin.campaign.intel.hostileactivity.MercPackageIntel;
import exerelin.campaign.intel.merc.MercContractIntel;
import exerelin.campaign.intel.merc.MercDataManager;
import exerelin.campaign.intel.merc.MercDataManager.MercCompanyDef;
import exerelin.campaign.intel.merc.MercSectorManager;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static exerelin.campaign.intel.merc.MercContractIntel.getString;
import static exerelin.campaign.intel.merc.MercContractIntel.log;

public class Nex_MercHire extends BaseCommandPlugin {
	
	public static final String MEM_KEY_HIRES = "$nex_mercsForHire";
	public static final String MEM_KEY_NUM_HIRES = "$nex_mercsForHire_count";
	public static final String MEM_KEY_UNPAID = "$nex_mercsUnpaid";
	public static final String MEM_KEY_PREFIX_INSULT = "$nex_mercsInsulted_";
	public static final String OPTION_PREFIX = "nex_viewMerc_";
	public static final int PREFIX_LENGTH = OPTION_PREFIX.length();
	public static final float MERC_INFO_WIDTH = Nex_VisualCustomPanel.PANEL_WIDTH - 128 - 8;
	public static final float SHIP_ICON_WIDTH = 48;
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
				memoryMap.get(MemKeys.LOCAL).set("$nex_mercCompanyId", companyId, 0);
				memoryMap.get(MemKeys.LOCAL).set("$nex_mercCompanyHireCount", 
						MercSectorManager.getInstance().getNumTimesHired(companyId), 0);
				memoryMap.get(MemKeys.LOCAL).set("$nex_mercCompanyName", MercDataManager.getDef(companyId).name, 0);
				memoryMap.get(MemKeys.LOCAL).set("$nex_mercCompanyFactionId", MercDataManager.getDef(companyId).factionId, 0);
				viewCompany(dialog, companyId);
				break;
			case "displayCommander":
				displayCompanyCommander(dialog, memoryMap, 
						memoryMap.get(MemKeys.LOCAL).getString("$nex_mercCompanyId"));
				break;
			case "hire":
				hire(dialog, market, memoryMap.get(MemKeys.LOCAL).getString("$nex_mercCompanyId"));
				break;
			case "showHelp":
				// TODO: handled purely in rules for now
				break;
			case "reportInsult":
				reportInsult(dialog, memoryMap);
				break;
			case "processPayment":
				processPayment(dialog, memoryMap.get(MemKeys.LOCAL));
				break;
			case "reportNonPayment":
				long unpaid = params.get(1).getInt(memoryMap);
				reportNonPayment(memoryMap, -unpaid);
				break;
			case "initDebt":
				initDebt(dialog, memoryMap);
				break;
			case "payDebt":
				payDebt(dialog, memoryMap);
				break;	
			case "checkLeave":
				return checkLeave(dialog, market);
			case "haveHA":
				return HostileActivityEventIntel.get() != null;
			case "havePatrol":
				MercPackageIntel intel = MercPackageIntel.getInstance();
				return intel != null && !intel.isEnding() && !intel.isEnded();
			case "beginPatrol":
				initPatrolPackage(dialog);
				break;
			case "setMemoryValues":
				setMemoryValues(memoryMap.get(MemKeys.LOCAL));
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
		intel.accept(market, dialog.getTextPanel());
		int price = intel.getModifiedFeeUpfront();
		Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(price);
		AddRemoveCommodity.addCreditsLossText(price, dialog.getTextPanel());
		getAvailableHires(market).remove(intel);
	}
	
	public boolean checkLeave(InteractionDialogAPI dialog, MarketAPI market) {
		String key = "$mercs_leaveTimeout";
		if (Global.getSector().getMemoryWithoutUpdate().contains(key)) return false;
		
		if (market.isHidden()) return false;
		if (market.getSize() <= 3) return false;
		
		MercContractIntel curr = MercContractIntel.getOngoing();
		if (curr == null) {
			return false;
		}
		MonthlyReport report = SharedData.getData().getPreviousReport();
		boolean debt = report.getDebt() > 0;
		// only have debt if the merc company was hired for at least 45 days and was thus around long enough to have seen the debt
		// this is how base game does it for merc officers
		debt &= curr.getDaysRemaining() < Global.getSettings().getFloat("nex_merc_company_contract_period") - 45;
		
		boolean leaveDebug = false;
		if (debt || curr.isContractOver() || leaveDebug) {
			dialog.getInteractionTarget().setActivePerson(curr.getFirstOfficer());
			//dialog.getVisualPanel().showPersonInfo(getPerson(), true);
			((RuleBasedInteractionDialogPluginImpl)dialog.getPlugin()).notifyActivePersonChanged();
			
			MemoryAPI local = dialog.getPlugin().getMemoryMap().get(MemKeys.LOCAL);
			local.set("$nex_mercContract_ref", curr, 0);
			local.set("$nex_mercCompanyId", curr.getDef().id, 0);
			local.set("$nex_mercCompanyName", curr.getDef().name, 0);
			
			// money stuff
			long diff = Math.round(curr.getShipValueDiff() * MercDataManager.valueDifferencePaymentMult);
			if (diff < 0) diff = 0;
			local.set("$nex_mercValueDiffVal", diff, 0);
			local.set("$nex_mercValueDiffStr", Misc.getDGSCredits(diff), 0);
			int refund = Math.round(curr.getModifiedFeeUpfront() * MercDataManager.feeUpfrontRefundMult);
			local.set("$nex_mercRetainerRefundVal", refund, 0);
			local.set("$nex_mercRetainerRefundStr", Misc.getDGSCredits(refund), 0);
			long net = refund - diff;	// paid to player
			local.set("$nex_mercNetPaymentVal", net, 0);
			local.set("$nex_mercNetPaymentAbsVal", Math.abs(net), 0);
			local.set("$nex_mercNetPaymentStr", Misc.getDGSCredits(Math.abs(net)), 0);
			return true;
		}		
		return false;
	}
	
	public void processPayment(InteractionDialogAPI dialog, MemoryAPI local) {
		int net = local.getInt("$nex_mercNetPaymentVal");
		MutableValue creds = Global.getSector().getPlayerFleet().getCargo().getCredits();
		int debt = 0;
		if (net > 0) {
			creds.add(net);
			AddRemoveCommodity.addCreditsGainText(net, dialog.getTextPanel());
		}
		else if (net < 0) {
			int payment = -net;
			if (creds.get() < payment) {
				debt = -(int)(creds.get() - payment);
				payment = (int)creds.get();
				MonthlyReport report = SharedData.getData().getPreviousReport();
				report.setDebt(report.getDebt() + debt);
			}
			creds.subtract(payment);
			AddRemoveCommodity.addCreditsLossText(payment, dialog.getTextPanel());
			if (debt > 0) {
				String debtStr = Misc.getDGSCredits(debt);
				dialog.getTextPanel().setFontSmallInsignia();
				dialog.getTextPanel().addPara(getString("dialog_debtIncurred"), 
						Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), debtStr);
				dialog.getTextPanel().setFontInsignia();
			}
		}
	}
	
	public void reportNonPayment(Map<String, MemoryAPI> memoryMap, long amount) {
		Global.getLogger(this.getClass()).info("Reporting merc non-payment: " + amount);
		MemoryAPI player = memoryMap.get(MemKeys.PLAYER);
		
		if (player.contains(MEM_KEY_UNPAID)) {
			amount += (long)player.get(MEM_KEY_UNPAID);
		}
		player.set(MEM_KEY_UNPAID, amount);
	}
	
	public void reportInsult(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		String companyId = memoryMap.get(MemKeys.LOCAL).getString("$nex_mercCompanyId");
		String key = MEM_KEY_PREFIX_INSULT + companyId;
		memoryMap.get(MemKeys.PLAYER).set(key, true, 500);
	}
	
	public void initDebt(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		MemoryAPI player = memoryMap.get(MemKeys.PLAYER);
		MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
		long unpaid = player.getLong(MEM_KEY_UNPAID);
		String unpaidStr = Misc.getWithDGS(unpaid);
		local.set("$nex_mercDebtStr", unpaidStr, 0);
		
		if (unpaid > Global.getSector().getPlayerFleet().getCargo().getCredits().get()) {
			local.set("$nex_mercDebtColor", "bad", 0);
			dialog.getOptionPanel().setEnabled("nex_mercPayDebt", false);
		} else {
			local.set("$nex_mercDebtColor", "h", 0);
		}
	}
	
	public void payDebt(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		MemoryAPI player = memoryMap.get(MemKeys.PLAYER);
		long unpaid = player.getLong(MEM_KEY_UNPAID);
		Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(unpaid);
		AddRemoveCommodity.addCreditsLossText((int)unpaid, dialog.getTextPanel());
		player.unset(MEM_KEY_UNPAID);
	}

	public void initPatrolPackage(InteractionDialogAPI dialog) {
		MercPackageIntel intel = new MercPackageIntel();
		intel.init(dialog.getTextPanel());
	}
	
	public void setMercOfficerMemoryKeys(PersonAPI officer, MemoryAPI local) {
		local.set("$nex_mercName", officer.getName().getFullName(), 0);
		local.set("$nex_mercFirstName", officer.getName().getLast(), 0);
		local.set("$nex_mercLastName", officer.getName().getFirst(), 0);
		local.set("$nex_mercRank", officer.getRank(), 0);
		local.set("$nex_MercRank", Misc.ucFirst(officer.getRank()), 0);
		local.set("$nex_mercHeOrShe", officer.getHeOrShe(), 0);
		local.set("$nex_MercHeOrShe", Misc.ucFirst(officer.getHeOrShe()), 0);
		local.set("$nex_mercHimOrHer", officer.getHimOrHer(), 0);
		local.set("$nex_MercHimOrHer", Misc.ucFirst(officer.getHimOrHer()), 0);
		local.set("$nex_mercHisOrHer", officer.getHisOrHer(), 0);
		local.set("$nex_MercHisOrHer", Misc.ucFirst(officer.getHisOrHer()), 0);
	}

	public void setMemoryValues(MemoryAPI local) {
		local.set("$nex_HAmercPackage_days", MercPackageActivityCause.MONTHS * 30, 0);
		local.set("$nex_HAmercPackage_months", MercPackageActivityCause.MONTHS, 0);
		local.set("$nex_HAmercPackage_monthlyCredits", MercPackageActivityCause.MONTHLY_FEE, 0);
		local.set("$nex_HAmercPackage_monthlyCreditsStr", Misc.getWithDGS(MercPackageActivityCause.MONTHLY_FEE), 0);
		//local.set("$nex_HAmercPackage_progress", (int)MercPackageActivityCause.PROGRESS_PER_MONTH, 0);
		local.set("$nex_HAmercPackage_progressPercent", (int)(MercPackageActivityCause.PROGRESS_MULT_PER_MONTH * 100) + "%", 0);

		MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
		if (global.contains("$nex_HAmercPackage_onCooldown")) {
			float expire = global.getExpire("$nex_HAmercPackage_onCooldown");
			local.set("$nex_HAmercPackage_cooldownDays", (int)Math.ceil(expire), 0);
		}
	}
	
	// Do the stuff to make the CO appear
	// if have any officers, pick the first officer and show their face
	// else, create a generic officer and show that?
	public void displayCompanyCommander(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, String companyId) 
	{
		MercContractIntel intel = getSelectedCompany(dialog.getInteractionTarget().getMarket(), companyId);
		PersonAPI officer = intel.getFirstOfficer();
		dialog.getVisualPanel().showSecondPerson(officer);
		setMercOfficerMemoryKeys(officer, memoryMap.get(MemKeys.LOCAL));
		
		Boolean checkInsult = (Boolean)intel.getDef().miscData.get("angeredByInsults");
		
		if (Boolean.TRUE.equals(checkInsult))
		{
			String insultKey = MEM_KEY_PREFIX_INSULT + companyId;
			boolean insulted = memoryMap.get(MemKeys.PLAYER).contains(insultKey);
			if (insulted)
				memoryMap.get(MemKeys.LOCAL).set("$nex_mercInsulted", true, 0);
		}
	}
	
	/**
	 * Prints basic company info and list of ships to the text panel.
	 * @param dialog
	 * @param companyId
	 */
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
		
		String fee1 = Misc.getDGSCredits(intel.getModifiedFeeUpfront());
		String fee2 = Misc.getDGSCredits(def.feeMonthly);
		info.addPara(getString("intel_desc_feeUpfront") + ": " + fee1, opad, h, fee1);
		info.addPara(getString("intel_desc_feeMonthly") + ": " + fee2, pad, h, fee2);

		List<OfficerDataAPI> officers = intel.getOfferedFleet().getFleetData().getOfficersCopy();
		List<String> levels = new ArrayList<>();
		for (OfficerDataAPI officer : officers) {
			levels.add(officer.getPerson().getStats().getLevel() + "");
		}
		if (!levels.isEmpty()) {
			String str = String.format(getString("dialog_officerLevels"), StringHelper.writeStringCollection(levels));
			info.addPara(str, pad, h, levels.toArray(new String[0]));
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
			info.addPara(getString("dialog_lesserShips"), opad, h, "" + surplus);
		}
		text.addTooltip();
		
		text.setFontSmallInsignia();
		int shipsAfter = members.size() + Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().size();
		int max = Global.getSettings().getMaxShipsInFleet();
		Color color = shipsAfter > max ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
		text.addPara(getString("dialog_shipsAfterHiring"), color, "" + shipsAfter, "" + max);
		text.setFontInsignia();
	}
	
	/**
	 * Adds information of available companies to the custom visual panel, and
	 * dialog options for hiring those companies.
	 * @param dialog
	 */
	public void showOptions(InteractionDialogAPI dialog) {
		float pad = 3;
		float opad = 10;
		
		Nex_VisualCustomPanel.createPanel(dialog, true);
		dialog.getOptionPanel().clearOptions();
		
		CustomPanelAPI panel = Nex_VisualCustomPanel.panel;
		TooltipMakerAPI tooltip = Nex_VisualCustomPanel.tooltip;
		
		List<MercContractIntel> hires = getAvailableHires(dialog.getInteractionTarget().getMarket());
		int credits = (int)Global.getSector().getPlayerFleet().getCargo().getCredits().get();
		for (MercContractIntel intel : hires) {
			MercCompanyDef def = intel.getDef();
			
			NexUtilsGUI.CustomPanelGenResult panelGen = NexUtilsGUI.addPanelWithFixedWidthImage(panel, 
				null, panel.getPosition().getWidth(), 48, null, 200, 3 * 3, 
				def.getLogo(), 48, pad, null, false, null);
			
			CustomPanelAPI info = panelGen.panel;
			TooltipMakerAPI text = (TooltipMakerAPI)panelGen.elements.get(1);
			
			int feeCalc = intel.getModifiedFeeUpfront();
			boolean enough = credits >= feeCalc;
			
			//text.setParaSmallInsignia();
			text.addPara(def.name, def.getFaction().getBaseUIColor(), 0);
			//text.setParaFontDefault();
			String fee1 = Misc.getDGSCredits(feeCalc);
			String fee2 = Misc.getDGSCredits(def.feeMonthly);
			text.addPara(fee1 + " + " + fee2 + "/mth", pad, enough ? Misc.getHighlightColor() 
					: Misc.getNegativeHighlightColor(), fee1, fee2);
			if (true || ExerelinModPlugin.isNexDev) {
				String fleetVal = Misc.getDGSCredits(intel.calcOfferedFleetValue());
				text.addPara(getString("panel_shipValue") + ": " + fleetVal, pad, Misc.getHighlightColor(), fleetVal);
			}
			
			float shipAreaWidth = MERC_INFO_WIDTH - 200 - 8;
			TooltipMakerAPI shipHolder = info.createUIElement(shipAreaWidth, 0, false);
			
			//Global.getLogger(this.getClass()).info("Testing merc: " + def.id);
			List<FleetMemberAPI> ships = intel.getOfferedFleet().getFleetData().getMembersListCopy();
			
			int max = Math.min((int)(shipAreaWidth/SHIP_ICON_WIDTH), ships.size());
			ships = ships.subList(0, max);
			shipHolder.addShipList(max, 1, SHIP_ICON_WIDTH, Misc.getBasePlayerColor(), ships, pad);
			info.addUIElement(shipHolder).rightOfTop(text, pad);
			
			tooltip.addCustom(info, opad);
			
			dialog.getOptionPanel().addOption(def.name, OPTION_PREFIX + def.id);
			if (!enough) {
				dialog.getOptionPanel().setEnabled(OPTION_PREFIX + def.id, false);
			}
		}
		dialog.getOptionPanel().addOption(StringHelper.getString("goBack", true), "nex_mercRepBack");
		dialog.getOptionPanel().setShortcut("nex_mercRepBack", Keyboard.KEY_ESCAPE, false, false, false, false);
		
		NexUtils.addDevModeDialogOptions(dialog);
		
		Nex_VisualCustomPanel.addTooltipToPanel();
	}
	
	public boolean init(MarketAPI market, MemoryAPI local) {
		if (MercContractIntel.getOngoing() != null) {
			local.set("$nex_mercContractOngoing", true, 0);
			return false;
		}
		
		List<MercContractIntel> hires = getAvailableHires(market);
		local.set(MEM_KEY_NUM_HIRES, hires.size(), 0);
		return !hires.isEmpty();
	}
	
	/**
	 * Gets a list of merc companies that can be hired at this location (cached for 7 days).
	 * @param market
	 * @return
	 */
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
