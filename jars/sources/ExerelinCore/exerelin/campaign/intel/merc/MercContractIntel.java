package exerelin.campaign.intel.merc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction.ShipSaleInfo;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.merc.MercDataManager.MercCompanyDef;
import exerelin.utilities.ModPluginEventListener;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

public class MercContractIntel extends BaseIntelPlugin implements EconomyTickListener, ColonyInteractionListener, ModPluginEventListener
{
	public static Logger log = Global.getLogger(MercContractIntel.class);
	
	public static final float MERC_AVAILABLE_TIME = 60;
	public static final float SHIP_ICON_WIDTH = 48;
	public static final Object UPDATE_EXPIRE = new Object();
	public static final Object BUTTON_DISMISS = new Object();
	
	protected Long seed;
	
	protected boolean contractOver;
	protected boolean annihilated;
	protected String companyId;
	
	protected List<FleetMemberAPI> ships = new ArrayList<>();
	protected List<PersonAPI> officers = new ArrayList<>();
	protected transient MercFleetGenPlugin fleetPlugin;
	protected CampaignFleetAPI offeredFleet;
	protected Map<FleetMemberAPI, CargoAPI> stored = new HashMap<>();
	
	protected long startingShipValue;
	@Getter protected float daysRemaining;
	
	public MercContractIntel(String companyId) {
		this.companyId = companyId;
		this.seed = Misc.genRandomSeed();
	}
	
	protected Object readResolve() {
		if (stored == null) stored = new HashMap<>();
		fleetPlugin = MercFleetGenPlugin.createPlugin(this);
		return this;
	}
	
	public void init(MarketAPI market) {
		init(market, false);
	}

	public void init(MarketAPI market, boolean force) {
		fleetPlugin = MercFleetGenPlugin.createPlugin(this);
		if (force || fleetPlugin.isAvailableAt(market)) {
			offeredFleet = fleetPlugin.generateFleet(market);
			startingShipValue = calcOfferedFleetValue();
		}
	}
	
	public CampaignFleetAPI getOfferedFleet() {
		if (offeredFleet != null && !offeredFleet.isInflated()) 
			fleetPlugin.inflateFleet(offeredFleet);
		return offeredFleet;
	}
	
	public float getDaysRemaining() {
		return daysRemaining;
	}
	
	public PersonAPI getFirstOfficer() {
		if (offeredFleet != null) {	// not yet accepted
			if (getOfferedFleet().getFleetData().getOfficersCopy().isEmpty()) 
			{
				return getOfferedFleet().getMembersWithFightersCopy().get(0).getCaptain();
			} 
			else 
			{
				return getOfferedFleet().getFleetData().getOfficersCopy().get(0).getPerson();
			}
		}
		else {
			if (officers.isEmpty()) 
			{
				return ships.get(0).getCaptain();
			} 
			else 
			{
				return officers.get(0);
			}
		}
	}
	
	public boolean isContractOver() {
		return contractOver;
	}
	
	/**
	 * Check if the specified ship is still in the storage where we recorded it as being. 
	 * If it isn't, remove it (note: can probably cause ConcurrentModificationException).
	 * @param ship
	 * @return True if ship was found in storage, false otherwise.
	 */
	public boolean validateStoredShip(FleetMemberAPI ship) {
		if (!stored.containsKey(ship)) return false;
		
		CargoAPI cargo = stored.get(ship);
		//log.info("Checking cargo " + cargo.toString());
		if (!cargo.getMothballedShips().getMembersListCopy().contains(ship)) {
			//log.info("Removing ship " + ship.getShipName());
			try {
				stored.remove(ship);
			} catch (Exception ex) {
				log.error("Failed to remove stored merc ship " + ship.getShipName(), ex);
			}
			
			return false;
		}
		return true;
	}
	
	public long calcShipsValue() {
		float amt = 0;
		Set<FleetMemberAPI> currentMembers = new HashSet<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());
		for (FleetMemberAPI member : ships) {
			if (currentMembers.contains(member))
				amt += member.getBaseBuyValue();
			else {
				boolean have = validateStoredShip(member);
				if (have) amt += member.getBaseBuyValue();
			}
		}
		return Math.round(amt); 
	}
	
	public long calcOfferedFleetValue() {
		if (offeredFleet == null) return 0;
		
		float amt = 0;
		for (FleetMemberAPI member : offeredFleet.getFleetData().getMembersListCopy()) {
			amt += member.getBaseBuyValue();
		}
		return Math.round(amt); 
	}
	
	public long getShipValueDiff() {
		return startingShipValue - calcShipsValue();
	}
	
	/**
	 * Includes the fleet cost multiplied by the fleet fee mult, if any.
	 * @return
	 */
	public int getModifiedFeeUpfront() {
		MercCompanyDef def = this.getDef();
		return def.feeUpfront + Math.round(startingShipValue * def.fleetFeeMult);
	}
	
	public void accept(MarketAPI market, TextPanelAPI text) {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		String faction = getDef().factionId;
		
		for (FleetMemberAPI member : offeredFleet.getFleetData().getMembersListCopy()) 
		{
			offeredFleet.getFleetData().removeFleetMember(member);
			player.getFleetData().addFleetMember(member);
			ships.add(member);
			member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
			
			PersonAPI officer = member.getCaptain();
			if (officer != null && !officer.isDefault() && !officer.isAICore()) {
				player.getFleetData().addOfficer(officer);
				offeredFleet.getFleetData().removeOfficer(officer);
				officers.add(officer);
				officer.setFaction(faction);
			}
			else if (officer != null) {
				officer.setFaction(Factions.PLAYER);
			}
			player.getCargo().addCrew((int)member.getMinCrew());
						
			Misc.setMercHiredNow(officer);
		}
		
		fleetPlugin.accept();
		
		daysRemaining = Global.getSettings().getFloat("nex_merc_company_contract_period");
		startingShipValue = calcShipsValue();
		
		offeredFleet = null;
		
		int rep = getDef().feeUpfront/20000;
		
		NexUtilsReputation.adjustPlayerReputation(Global.getSector().getFaction(faction), null, rep * 0.01f,
						0, null, text);
		
		Global.getSector().getIntelManager().addIntel(this, false, text);
		Global.getSector().addScript(this);
		Global.getSector().getListenerManager().addListener(this);
		MercSectorManager.getInstance().reportMercHired(INDENT, market);
	}
	
	/**
	 * Called when the contract period expires. Mercs will remain with the fleet
	 * until returning to a suitable market.
	 */
	public void endContractPeriod() {
		sendUpdateIfPlayerHasIntel(listInfoParam, true);
		contractOver = true;
	}
	
	/**
	 * Called when the mercs leave the party.
	 * @param marketId The ID of the market where the mercs are departing.
	 */
	public void endEvent(String marketId) {
		MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
		
		TextPanelAPI text = null;
		if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) {
			text = Global.getSector().getCampaignUI().getCurrentInteractionDialog().getTextPanel();
		}
		
		FleetDataAPI data = Global.getSector().getPlayerFleet().getFleetData();
		
		// remove any officers that were assigned to player ships, from said ships
		// otherwise they'll be stuck there forever
		for (FleetMemberAPI member : data.getMembersListCopy()) {
			if (member.getCaptain() != null && officers.contains(member.getCaptain())) {
				member.setCaptain(null);
			}
		}
		
		for (PersonAPI officer : officers) {
			data.removeOfficer(officer);
		}
		//officers.clear();
		
		// calculate crew removal
		int currCrew = Global.getSector().getPlayerFleet().getCargo().getCrew();
		int skeletonNonMerc = 0;
		int skeletonMerc = 0;
		for (FleetMemberAPI member : data.getMembersListCopy()) {
			if (ships.contains(member))
				skeletonMerc += member.getMinCrew();
			else
				skeletonNonMerc += member.getMinCrew();
		}
		int toRemove;
		if (currCrew - skeletonMerc > skeletonNonMerc) {
			toRemove = skeletonMerc;	// enough crew, go ahead and remove all
		} else {
			// not enough, remove on pro rata basis
			float skeletonTotal = skeletonNonMerc + skeletonMerc;
			toRemove = Math.round(currCrew * (skeletonMerc/skeletonTotal));
		}
		Global.getSector().getPlayerFleet().getCargo().removeCrew(toRemove);
		AddRemoveCommodity.addCommodityLossText(Commodities.CREW, toRemove, text);
		
		// yeet the ships from player fleet
		Set<FleetMemberAPI> currentMembers = new HashSet<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());
		for (FleetMemberAPI member : ships) {
			data.removeFleetMember(member);
			// this removes the officer from stored ships, although it's not needed now that we're removing the ships themselves
			member.setCaptain(null);
			if (currentMembers.contains(member))
				AddRemoveCommodity.addFleetMemberLossText(member, text);
			
			// yeet them from storage too!
			else if (stored.containsKey(member)) {
				boolean have = validateStoredShip(member);
				if (!have) continue;
				CargoAPI cargo = stored.get(member);
				cargo.getMothballedShips().removeFleetMember(member);
				String name = member.getShipName() + ", " + member.getVariant().getHullSpec().getHullNameWithDashClass() 
						+ " " + member.getVariant().getHullSpec().getDesignation();
				if (text != null) {
					text.setFontSmallInsignia();
					text.addPara(getString("dialog_removedFromStorage"), Misc.getNegativeHighlightColor(),
							Misc.getHighlightColor(), name);
					text.setFontInsignia();
				}
			}
		}		
		//ships.clear();
		
		fleetPlugin.endEvent();
		
		MercSectorManager.getInstance().reportMercLeft(getDef().id, market);
		endAfterDelay();
	}
	
	@Override
	protected void notifyEnding() {
		Global.getSector().getListenerManager().removeListener(this);
	}
	
	// TODO: handle case where company is annihilated

	@Override
	protected void advanceImpl(float amount) {
		if (contractOver) return;
		
		boolean reset = daysRemaining > Global.getSettings().getFloat("officerMercContractDur");
		if (reset) {
			for (PersonAPI officer : officers) {
				Misc.setMercHiredNow(officer);
			}
		}
		
		float days = Global.getSector().getClock().convertToDays(amount);
		daysRemaining -= days;
		if (daysRemaining <= 0) {
			endContractPeriod();
		}
	}

	@Override
	public void reportEconomyTick(int iterIndex) {
		//log.info("Merc economy tick");
		// apply fee
		MonthlyReport report = SharedData.getData().getCurrentReport();
		
		float numIter = Global.getSettings().getFloat("economyIterPerMonth");
		float f = 1f / numIter;
		MercCompanyDef def = getDef();
		
		float fee = def.feeMonthly * f;
		MonthlyReport.FDNode fleetNode = report.getNode(MonthlyReport.FLEET);
		
		MonthlyReport.FDNode mercNode = report.getNode(fleetNode, "nex_merc_" + def.id);
		mercNode.upkeep += fee;
		mercNode.name = String.format(getString("reportNode_name"), def.name);
		mercNode.icon = def.getLogo();
		
		Global.getLogger(this.getClass()).info(String.format("Applying fee %s at iter %s, current total %s",
				fee, iterIndex, mercNode.upkeep));
	}

	@Override
	public void reportEconomyMonthEnd() {}
	
	@Override
	public void reportPlayerClosedMarket(MarketAPI market) {}
	
	@Override
	public void reportPlayerOpenedMarket(MarketAPI market) {}

	@Override
	public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {}

	@Override
	public void reportPlayerMarketTransaction(PlayerMarketTransaction transact) 
	{
		boolean storage = transact.getSubmarket().getPlugin().isFreeTransfer();
		
		if (storage) {
			for (ShipSaleInfo sale : transact.getShipsSold()) {
				FleetMemberAPI sold = sale.getMember();
				PersonAPI captain = sold.getCaptain();
				if (officers.contains(captain) && Misc.isUnremovable(captain)) {
					Global.getSector().getPlayerFleet().getFleetData().removeOfficer(captain);
				}
				
				if (ships.contains(sold)) {
					//log.info("Storing ship " + sold.getShipName());
					stored.put(sold, transact.getSubmarket().getCargo());
				}
			}
			for (ShipSaleInfo buy : transact.getShipsBought()) {
				FleetMemberAPI bought = buy.getMember();
				PersonAPI captain = bought.getCaptain();
				if (officers.contains(captain) && Misc.isUnremovable(captain)) {
					Global.getSector().getPlayerFleet().getFleetData().addOfficer(captain);
				}
				
				if (ships.contains(bought)) {
					stored.remove(bought);
				}
			}
			return;
		}
		
		
		for (ShipSaleInfo sale : transact.getShipsSold().toArray(new ShipSaleInfo[]{})) {
			FleetMemberAPI sold = sale.getMember();
			if (!storage && ships.contains(sold)) {
				//Global.getLogger(this.getClass()).info("Sale value: " + sale.getPrice());
				//Global.getLogger(this.getClass()).info("Sale value with tariff: " + sale.getPrice() * (1 + transact.getSubmarket().getTariff()));
				//Global.getLogger(this.getClass()).info("Tariff: " + transact.getSubmarket().getTariff());
				
				transact.getSubmarket().getCargo().getMothballedShips().removeFleetMember(sold);
				Global.getSector().getPlayerFleet().getFleetData().addFleetMember(sold);
				sold.getRepairTracker().setMothballed(false);
				float salePriceRecalc = sold.getBaseSellValue() * (1 - transact.getSubmarket().getTariff());
				if (storage) salePriceRecalc = 0;
				//Global.getLogger(this.getClass()).info("Estimated true sale price: " + salePriceRecalc);
				Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(salePriceRecalc);
				
								
				String str = String.format(getString("message_reverseSale"), sold.getShipName() + ", " + sold.getHullSpec().getHullNameWithDashClass());
				Global.getSector().getCampaignUI().getMessageDisplay().addMessage(str);
				// fail attempt to refresh GUI
				//Global.getSector().getCampaignUI().showCoreUITab(Global.getSector().getCampaignUI().getCurrentCoreTab());
			}
		}
	}
	
	@Override
	public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String action = params.get(0).getString(memoryMap);
		if (action != null) {
			switch (action) {
				case "endEvent":
					endEvent(params.get(1).getString(memoryMap));
					break;
			}
		}
		return false;
	}
	
	public MercCompanyDef getDef() {
		return MercDataManager.getDef(companyId);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad){
		if (!contractOver)
			info.addPara(getString("intel_bullet_daysRemaining"), 3, Misc.getHighlightColor(), (int)daysRemaining + "");
	}
	
	
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float pad = 3, opad = 10;
		Color h = Misc.getHighlightColor();
		MercCompanyDef def = getDef();
		
		if (officers.isEmpty()) {
			info.addImages(width, 96, opad, opad, def.getLogo());
		} else {
			info.addImages(width, 96, opad, opad, def.getLogo(), officers.get(0).getPortraitSprite());
		}
		
		if (isEnding() || isEnded()) {
			String str = getString("intel_desc_paraEnded");
			info.addPara(str, opad, def.getFaction().getBaseUIColor(), def.name);
			return;
		}
		
		String str = getString("intel_desc_para1");
		if (contractOver) str = getString("intel_desc_paraEnded");
		str = StringHelper.substituteToken(str, "$playerName", Global.getSector().getPlayerPerson().getNameString());
		info.addPara(str, opad, def.getFaction().getBaseUIColor(), def.name);
		
		String fee1 = Misc.getDGSCredits(getModifiedFeeUpfront());
		String fee2 = Misc.getDGSCredits(def.feeMonthly);
		info.addPara(getString("intel_desc_feeUpfront") + ": " + fee1, opad, h, fee1);
		info.addPara(getString("intel_desc_feeMonthly") + ": " + fee2, pad, h, fee2);
		
		if (!contractOver)
			info.addPara(getString("intel_desc_daysRemaining"), pad, h, (int)daysRemaining + "");
		else
			info.addPara(getString("intel_desc_daysExpired"), pad);
		
		FleetDataAPI data = Global.getSector().getPlayerFleet().getFleetData();
		List<FleetMemberAPI> playerShips = data.getMembersListCopy();
		List<FleetMemberAPI> curr = new ArrayList<>();
		List<FleetMemberAPI> lost = new ArrayList<>();
		List<FleetMemberAPI> stored = new ArrayList<>();
		for (FleetMemberAPI ship : ships) {
			if (playerShips.contains(ship)) {
				curr.add(ship);
			}
			else if (validateStoredShip(ship)) {
				stored.add(ship);
			}
			else {
				lost.add(ship);
			}
		}
		//log.info(String.format("Current ships %s, stored %s, lost %s", curr.size(), stored.size(), lost.size()));
		if (!curr.isEmpty()) {
			info.addPara(getString("intel_desc_ships") + ":", opad);
			NexUtilsGUI.addShipList(info, width, curr, SHIP_ICON_WIDTH, pad);
		}
		if (!stored.isEmpty()) {
			info.addPara(getString("intel_desc_shipsStored") + ":", pad);
			NexUtilsGUI.addShipList(info, width, stored, SHIP_ICON_WIDTH, pad);
		}
		if (!lost.isEmpty()) {
			info.addPara(getString("intel_desc_shipsMissing") + ":", pad);
			NexUtilsGUI.addShipList(info, width, lost, SHIP_ICON_WIDTH, pad);
		}
		
		long currValue = calcShipsValue();
		String v1 = Misc.getDGSCredits(startingShipValue);
		String v2 = Misc.getDGSCredits(currValue);
		String v3 = Misc.getDGSCredits(startingShipValue - currValue);
		LabelAPI label = info.addPara(getString("intel_desc_baseValue"), opad,
				h, v1, v2, v3);
		label.setHighlight(v1, v2, v3);
		label.setHighlightColors(h, h, currValue >= startingShipValue 
				? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor());
		
		if (!contractOver)
			info.addButton(StringHelper.getString("dismiss", true), BUTTON_DISMISS, width, 24, opad);
	}
	
	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		String str = getString("intel_desc_dismissPrompt");
		prompt.addPara(str, 0);
	}

	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return true;
	}

	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_DISMISS) {
			contractOver = true;
			ui.updateUIForItem(this);
		}
	}
	
	@Override
	public IntelSortTier getSortTier() {
		return IntelSortTier.TIER_2;
	}
	
	@Override
	public String getIcon() {
		return getDef().getLogo();
	}
	
	@Override
	protected String getName() {
		String str = String.format(getString("intel_title"), getDef().name);
		if (contractOver || isEnding() || isEnded())
			str += " - " + StringHelper.getString("over", true);
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		//tags.add(getString("intel_tag"));
		tags.add(Tags.INTEL_FLEET_LOG);
		tags.add(Tags.INTEL_ACCEPTED);
		tags.add(Tags.INTEL_AGREEMENTS);
		tags.add(StringHelper.getString("exerelin_misc", "intelTagPersonal"));
		return tags;
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_mercs", id);
	}
	
	public static MercContractIntel getOngoing() {
		for (IntelInfoPlugin iip : Global.getSector().getIntelManager().getIntel(MercContractIntel.class))
		{
			MercContractIntel merc = (MercContractIntel)iip;
			if (merc.isEnding() || merc.isEnded()) continue;
			return merc;
		}
		return null;
	}

	@Override
	public void onGameLoad(boolean newGame) {
		// Trying a way to fix disappearing S-mods
		fleetPlugin = MercFleetGenPlugin.createPlugin(this);
		if (seed == null) {
			seed = Misc.genRandomSeed();
		}
		try {
			if (offeredFleet != null && !getDef().noAutofit) {
				FactionAPI faction = offeredFleet.getFaction();
				fleetPlugin.inflateFleet(offeredFleet);
			}
		} catch (Throwable t) {
			log.warn("Failed to inflate fleet", t);
		}
	}

	@Override
	public void beforeGameSave() {}

	@Override
	public void afterGameSave() {}

	@Override
	public void onGameSaveFailed() {}

	@Override
	public void onNewGameAfterProcGen() {}

	@Override
	public void onNewGameAfterEconomyLoad() {}

	@Override
	public void onNewGameAfterTimePass() {}
}
