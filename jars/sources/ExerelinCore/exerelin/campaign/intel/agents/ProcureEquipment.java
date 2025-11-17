package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.CargoPodsEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.ProductionReportIntel;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.submarkets.PrismMarket;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.fs.starfarer.api.util.Misc.random;

/**
 * Obtains semi-illegally a bunch of weapons and fighter LPCs from the target faction and sends it to a specified location.
 */
@NoArgsConstructor
public class ProcureEquipment extends CovertActionIntel implements HasDestinationDialog {

	public static final float DELIVERY_TIME = 7;

	@Getter	@Setter protected CargoAPI cargo;
	@Getter @Setter protected float cartPrice;
	protected ProductionReportIntel.ProductionData data;
	protected Float timeToDeliver = null;
	protected MarketAPI destination;
	protected boolean delivered;

	public ProcureEquipment(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction,
                            boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		destination = pickDestination();
	}
	
	/**
	 * Picks an appropriate destination market to send the ship: first the player's 
	 * current gathering point, then the nearest market where player has storage access, 
	 * then the market where the agent action is taking place.
	 * @return
	 */
	public MarketAPI pickDestination() {
		FactionAPI pf = Global.getSector().getPlayerFaction();
		FactionProductionAPI prod = pf.getProduction();
		
		MarketAPI gatheringPoint = prod.getGatheringPoint();
		if (gatheringPoint != null) return gatheringPoint;
		
		MarketAPI nearest = null;
		float nearestDistSq = Integer.MAX_VALUE;
		for (MarketAPI dest : Global.getSector().getEconomy().getMarketsCopy())
		{
			if (dest.getFaction().isHostileTo(Factions.PLAYER))
				continue;
			if (!Misc.playerHasStorageAccess(dest))
				continue;
			float distSq = MathUtils.getDistanceSquared(dest.getLocationInHyperspace(), 
					market.getLocationInHyperspace());
			if (distSq < nearestDistSq) {
				nearestDistSq = distSq;
				nearest = dest;
			}
		}
		if (nearest != null) return nearest;
		return market;
	}
	
	public void setDestination(MarketAPI market) {
		destination = market;
	}
	
	public MarketAPI getDestination() {
		return destination;
	}

	public RepLevel getRequiredLevelForLegal(WeaponSpecAPI spec) {
		int tier = spec.getTier();

		if (tier >= 4) return RepLevel.COOPERATIVE;
		if (tier == 3) return RepLevel.FRIENDLY;
		if (tier == 2) return RepLevel.WELCOMING;
		if (tier == 1) return RepLevel.FAVORABLE;
		return RepLevel.SUSPICIOUS;
	}

	public RepLevel getRequiredLevelForLegal(FighterWingSpecAPI spec) {
		int tier = spec.getTier();

		if (tier >= 4) return RepLevel.COOPERATIVE;
		if (tier == 3) return RepLevel.FRIENDLY;
		if (tier == 2) return RepLevel.WELCOMING;
		if (tier == 1) return RepLevel.FAVORABLE;
		return RepLevel.SUSPICIOUS;
	}
	
	protected boolean hasCommission() {
		if (!targetFaction.getCustomBoolean(Factions.CUSTOM_OFFERS_COMMISSIONS)) return true;
		
		String cfId = Misc.getCommissionFactionId();
		String afId = agentFaction.getId();
		String tgtId = targetFaction.getId();
		//Global.getLogger(this.getClass()).info(cfId + ", " + afId + ", " + tgtId);
		
		if (AllianceManager.areFactionsAllied(afId, tgtId)) return true;
		if (cfId != null) {
			if (AllianceManager.areFactionsAllied(cfId, tgtId)) return true;
		}
		
		return false;
	}
	
	public boolean isLegal(WeaponSpecAPI spec) {
		if (spec.getTier() == 0) return true;
		if (!targetFaction.getRelationshipLevel(agentFaction).isAtWorst(getRequiredLevelForLegal(spec)))
			return false;
		
		return hasCommission();
	}

	public boolean isLegal(FighterWingSpecAPI spec) {
		if (spec.getTier() == 0) return true;
		if (!targetFaction.getRelationshipLevel(agentFaction).isAtWorst(getRequiredLevelForLegal(spec)))
			return false;

		return hasCommission();
	}

	public float getMaxCapacity() {
		return Math.min(getDef().effect.one * getLevel(), Global.getSector().getPlayerFleet().getCargo().getCredits().get());
	}
	
	public void setCargo(CargoAPI cargo) {
		this.cargo = cargo;
	}
	
	/**
	 * Create cargo pods orbiting the destination market, if it's decivilized
	 * when the delivery is made.
	 */
	protected void createCargoPods() {
		SectorEntityToken toOrbit = destination.getPrimaryEntity();
		CustomCampaignEntityAPI pods = Misc.addCargoPods(toOrbit.getContainingLocation(), toOrbit.getLocation());
		pods.setCircularOrbitWithSpin(toOrbit, random.nextFloat() * 360, toOrbit.getRadius() + 100, 30, 15, 25);
		pods.getCargo().addAll(cargo);
		Misc.makeImportant(pods, "$nex_procure_equipment_delivery");
		pods.getMemoryWithoutUpdate().set("$stabilized", true);
		CargoPodsEntityPlugin plugin = (CargoPodsEntityPlugin)pods.getCustomPlugin();
		plugin.setExtraDays(9999);
	}
	
	protected void deliver() {
		delivered = true;
		if (destination.getSubmarket(Submarkets.SUBMARKET_STORAGE) != null) {
			destination.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addAll(cargo);

			StoragePlugin plugin = (StoragePlugin)destination.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
			plugin.setPlayerPaidToUnlock(true);
		} 
		// if no storage, add as derelict around market
		else if (destination.getPrimaryEntity() != null) 
		{
			createCargoPods();
		}
		endAfterDelay();
		this.sendUpdateIfPlayerHasIntel(null, false, false);
	}
	
	@Override
	public MutableStat getCostStat() {
		MutableStat cost = new MutableStat(0);
		if (cargo == null) return cost;
		
		cost.modifyFlat("base", cartPrice, getString("costBase", true));
		
		return cost;
	}

	@Override
	protected MutableStat getSuccessChance(boolean checkSP) {
		MutableStat stat = new MutableStat(0);
		stat.modifyFlat("base", 100, getString("procureShipStatChanceLegal"));
		return stat;
	}
	
	@Override
	protected MutableStat getDetectionChance(boolean fail) {
		return new MutableStat(0);
	}
	
	@Override
	public float getAlertLevelIncrease() {
		return 0;
	}

	@Override
	protected void onSuccess() {
		timeToDeliver = DELIVERY_TIME;
		if (destination == null)
			destination = pickDestination();
		
		adjustRepIfDetected(RepLevel.FAVORABLE, null);
		reportEvent();
	}

	@Override
	protected void onFailure() {	// shouldn't happen
		reportEvent();
	}

	@Override
	public int getAbortRefund() {
		return (int)cost;	// full refund
	}
	
	// Don't end after delay, event will only end when goods are delivered
	@Override
	protected void reportEvent() {
		timestamp = Global.getSector().getClock().getTimestamp();
		if (shouldReportEvent()) {
			Global.getSector().getIntelManager().addIntel(this);
		}
	}
	
	@Override
	public void advanceImpl(float amount) {
		super.advanceImpl(amount);
		
		// update destination if current one gets decivilized
		// but not if shipment is already underway
		if (result == null && !destination.isInEconomy())
			destination = pickDestination();
		
		if (timeToDeliver != null) {
			timeToDeliver -= Misc.getDays(amount);
			if (timeToDeliver <= 0) {
				deliver();
			}
		}
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad){
		super.addBulletPoints(info, mode, isUpdate, tc, initPad);
		if (result != null && result.isSuccessful()) {
			String destName = destination.getName();
			
			if (delivered) {
				info.addPara(getString("shipDeliveryInfoCompleteShort"), 0, tc,
						destination.getTextColorForFactionOrPlanet(), destName);
				return;
			}
			
			String days = Math.round(timeToDeliver) + "";	//Misc.getStringForDays(Math.round(timeToDeliver));
			String str = getString("shipDeliveryInfoShort");
			str = StringHelper.substituteToken(str, "$market", destName);
			str = StringHelper.substituteToken(str, "$time", days);
			
			LabelAPI label = info.addPara(str, tc,0);
			label.setHighlight(destName, days);
			label.setHighlightColors(destination.getTextColorForFactionOrPlanet(), Misc.getHighlightColor());
		}
	}
	
	@Override
	public void addResultPara(TooltipMakerAPI info, float pad) {	
		if (result != null && result.isSuccessful()) {
			String destName = destination.getName();
			Color hl = Misc.getHighlightColor();
			
			String str = getString(delivered ? "equipmentDeliveryInfoComplete" : "equipmentDeliveryInfo");
			str = StringHelper.substituteToken(str, "$market", destName);
			
			if (delivered) {	// <ship> has arrived at <destination>
				LabelAPI label = info.addPara(str, pad);
				label.setHighlight(destName);
				label.setHighlightColors(destination.getTextColorForFactionOrPlanet());
			} else {			// <ship> arriving at <destination> in <days>
				String days = Math.round(timeToDeliver) + "";	// Misc.getStringForDays(Math.round(timeToDeliver));
				str = StringHelper.substituteToken(str, "$time", days);
				LabelAPI label = info.addPara(str, pad);
				label.setHighlight(destName, days);
				label.setHighlightColors(destination.getTextColorForFactionOrPlanet(), hl);
			}
			
			// show the cargo
			info.showCargo(cargo, 10, true, pad);
		}
		super.addResultPara(info, pad);
	}

	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getActionString("intelStatus_procureEquipment", false);
		String destName = destination.getName();
		String cost = getCost() + "";
		action = StringHelper.substituteToken(action, "$price", cost);
		action = StringHelper.substituteToken(action, "$market", destName);
		
		LabelAPI label = info.addPara(action, pad);
		label.setHighlight(cost, destName);
		label.setHighlightColors(Misc.getHighlightColor(), destination.getTextColorForFactionOrPlanet());
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_procureEquipment", true);
		String destName = destination.getName();
		String cost = getCost() + "";
		action = StringHelper.substituteToken(action, "$price", cost);
		action = StringHelper.substituteToken(action, "$market", destName);
		info.addPara(action, pad, color, Misc.getHighlightColor(), 
				cost, destName, Math.round(daysRemaining) + "");
	}

	@Override
	public List<Object> dialogGetTargets(AgentOrdersDialog dialog) {
		List<Object> targets = new ArrayList<>();
		targets.addAll(getEligibleTargets(market, this));
		return targets;
	}

	@Override
	public void dialogPrintActionInfo(AgentOrdersDialog dialog) {
		dialog.getText().addPara(getString("dialogInfoHeaderProcureEquipment"), Misc.getHighlightColor(), Misc.getDGSCredits(cartPrice));
		if (cargo != null) {
			TooltipMakerAPI info = dialog.getText().beginTooltip();
			info.showCargo(cargo, 10, true, 3f);
			dialog.getText().addTooltip();
		}
		super.dialogPrintActionInfo(dialog);
	}

	@Override
	public String getIcon() {
		return Global.getSettings().getCommoditySpec(Commodities.SHIP_WEAPONS).getIconName();
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		if (delivered)
			return destination.getPrimaryEntity();
		return super.getMapLocation(map);
	}
	
	@Override
	public List<ArrowData> getArrowData(SectorMapAPI map) {
		if (market != null && destination != null)
		{			
			List<ArrowData> result = new ArrayList<ArrowData>();
			ArrowData arrow = new ArrowData(market.getPrimaryEntity(), destination.getPrimaryEntity());
			arrow.color = Global.getSector().getPlayerFaction().getColor();
			arrow.width = 10f;
			result.add(arrow);
			
			return result;
		}
		
		return null;
	}

	public boolean showSuccessChance() {
		return false;
	}

	@Override
	public void dialogSetTarget(AgentOrdersDialog dialog, Object target) {
		cargo = (CargoAPI) target;
		dialog.printActionInfo();
	}

	@Override
	protected void dialogPopulateMainMenuOptions(AgentOrdersDialog dialog) {
		String str = getString("dialogOption_target");
		String target = String.format("[%s]", Misc.getDGSCredits(cartPrice));
		str = StringHelper.substituteToken(str, "$target", target);
		dialog.getOptions().addOption(str, AgentOrdersDialog.Menu.TARGET);
	}

	@Override
	protected void dialogPopulateTargetOptions(final AgentOrdersDialog dialog) {
		//dialog.addBackOption();	// fallback in case player closes menu by pressing Escape
		Set<String> fighters = new HashSet<>();
		Set<String> weapons = new HashSet<>();
		Set<String> ships = new HashSet<>();
		float maxCapacity = getMaxCapacity();
		for (Object obj : dialog.getCachedTargets()) {
			String arg = (String)obj;
			if (Global.getSettings().getFighterWingSpec(arg) != null) {
				fighters.add(arg);
			} else {
				weapons.add(arg);
			}
		}

		dialog.getDialog().showCustomProductionPicker(new BaseCustomProductionPickerDelegateImpl() {
			@Override
			public Set<String> getAvailableFighters() {
				return fighters;
			}
			@Override
			public Set<String> getAvailableShipHulls() {
				return ships;
			}
			@Override
			public Set<String> getAvailableWeapons() {
				return weapons;
			}
			@Override
			public float getCostMult() {
				return getDef().baseCost;
			}
			@Override
			public float getMaximumValue() {
				return maxCapacity;
			}
			@Override
			public void notifyProductionSelected(FactionProductionAPI production) {
				convertProdToCargo(dialog, production);
				dialog.optionSelected(null, AgentOrdersDialog.Menu.MAIN_MENU);
			}
		});
	}

	protected void convertProdToCargo(AgentOrdersDialog dialog, FactionProductionAPI prod) {
		cartPrice = prod.getTotalCurrentCost();
		data = new ProductionReportIntel.ProductionData();
		CargoAPI cargo = data.getCargo("Order manifest");

		for (FactionProductionAPI.ItemInProductionAPI item : prod.getCurrent()) {
			int count = item.getQuantity();

			if (item.getType() == FactionProductionAPI.ProductionItemType.FIGHTER) {
				cargo.addFighters(item.getSpecId(), count);
			} else if (item.getType() == FactionProductionAPI.ProductionItemType.WEAPON) {
				cargo.addWeapons(item.getSpecId(), count);
			}
		}
		dialogSetTarget(dialog, cargo);
	}

	public static List<String> getEligibleTargets(MarketAPI market, ProcureEquipment action)
	{
		List<String> targets = new ArrayList<>();
		Set<String> weaponsToCheck = new HashSet<>();
		Set<String> fightersToCheck = new HashSet<>();
		
		boolean allShips = NexConfig.agentStealAllShips;
		if (allShips) {
			weaponsToCheck.addAll(market.getFaction().getKnownWeapons());
			fightersToCheck.addAll(market.getFaction().getKnownFighters());
		}
		else {
			for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
				if (!ProcureShip.ALLOWED_SUBMARKETS.contains(submarket.getSpecId()))
					continue;
				
				//Global.getLogger(ProcureEquipment.class).info("Checking submarket " + submarket.getSpecId());
				submarket.getPlugin().updateCargoPrePlayerInteraction();	// make them refresh their cargo if needed
				
				for (CargoAPI.CargoItemQuantity<String> ciq : submarket.getCargo().getWeapons())
				{
					String weaponId = ciq.toString();
					weaponsToCheck.add(weaponId);
				}
				for (CargoAPI.CargoItemQuantity<String> ciq : submarket.getCargo().getFighters())
				{
					String fighterId = ciq.toString();
					fightersToCheck.add(fighterId);
				}
			}
		}
		
		for (String weaponId : weaponsToCheck) {
			WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
			if (spec.hasTag(Tags.RESTRICTED)) continue;
			if (PrismMarket.getRestrictedWeapons().contains(weaponId)) continue;
			if (action.agent != null && !action.isLegal(spec) && !action.agent.canStealWeapons())
				continue;

			targets.add(weaponId);
		}
		for (String fighterId : fightersToCheck) {
			FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(fighterId);
			if (spec.hasTag(Tags.RESTRICTED)) continue;
			if (PrismMarket.getRestrictedShips().contains(fighterId)) continue;
			if (action.agent != null && !action.isLegal(spec) && !action.agent.canStealWeapons())
				continue;

			targets.add(fighterId);
		}
		
		return targets;
	}

	@Override
	public void dialogInitAction(AgentOrdersDialog dialog) {
		super.dialogInitAction(dialog);
		destination = pickDestination();

		TextPanelAPI text = dialog.getText();
		text.setFontSmallInsignia();
		text.addPara(getString("dialogProcureEquipmentIntro"), Misc.getHighlightColor(),
				Math.round(getDef().baseCost * 100) + "%");
		if (!agent.canStealWeapons()) {
			text.addPara(getString("dialogProcureEquipmentIntroSpecialization"), Misc.getHighlightColor(),
					AgentIntel.Specialization.SABOTEUR.getName());
		}
		text.setFontInsignia();

		dialog.getTargets();
	}

	@Override
	public boolean dialogCanActionProceed(AgentOrdersDialog dialog) {
		return cargo != null && !cargo.isEmpty();
	}

	@Override
	public String getDefId() {
		return "procureEquipment";
	}
}
