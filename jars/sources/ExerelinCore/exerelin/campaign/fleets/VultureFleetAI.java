package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageEntityGeneratorOld;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial.ExtraSalvage;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipRecoverySpecialData;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.fleets.VultureFleetManager.ShipRecoverySpecialNPC;
import exerelin.campaign.fleets.VultureFleetManager.VultureFleetData;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.log4j.Logger;

public class VultureFleetAI implements EveryFrameScript
{
	public static Logger log = Global.getLogger(VultureFleetAI.class);
	public static final float UPDATE_INTERVAL = 0.25f;
	
	protected final VultureFleetData data;
	
	protected float daysTotal;
	protected final CampaignFleetAPI fleet;
	protected boolean orderedReturn = false;
	protected CampaignFleetAPI following;
	
	public VultureFleetAI(CampaignFleetAPI fleet, VultureFleetData data)
	{
		this.fleet = fleet;
		this.data = data;
		giveInitialAssignment();
	}
		
	float interval = 0;
	
	@Override
	public void advance(float amount)
	{
		float days = Global.getSector().getClock().convertToDays(amount);
		this.daysTotal += days;
		if (this.daysTotal > 60)
		{
			giveStandDownOrders();
			return;
		}
		
		interval += days;
		if (interval >= UPDATE_INTERVAL) interval -= UPDATE_INTERVAL;
		else return;
		
		FleetAssignmentDataAPI assignment = this.fleet.getAI().getCurrentAssignment();
		if (assignment != null)
		{
			float fp = this.fleet.getFleetPoints();
			if (fp < this.data.startingFleetPoints / 2.0F) {
				giveStandDownOrders();
			}
			CargoAPI cargo = fleet.getCargo();
			if (cargo.getSpaceUsed() / cargo.getMaxCapacity() > 0.9f)
			{
				giveStandDownOrders();
			}
			
			if (orderedReturn)
			{
				return;
			}
			
			if (data.target == null || !data.target.isAlive()) {
				data.target = null;
			}
			return;
		}
		
		// no orders, let's scavenge something
		// but first check if we have something to scavenge
		if (data.target == null || !data.target.isAlive()) {
			data.target = VultureFleetManager.getClosestScavengable(fleet.getLocation(), fleet.getContainingLocation());
			if (data.target == null) {
				checkIdle();
				return;
			}
		}
		
		SectorEntityToken target = data.target;
		String tgtName = target.getName().toLowerCase();
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target, 30, StringHelper.getFleetAssignmentString("movingToScavenge", tgtName));
		fleet.addAssignment(FleetAssignment.HOLD, target, 0.25f, StringHelper.getFleetAssignmentString("scavenging", tgtName), 
				getScavengeScript(target));
		//fleet.addAssignment(FleetAssignment.HOLD, target, 0.05f);
	}
	
	protected void checkIdle() {
		if (!VultureFleetManager.hasOngoingRaids(data.source.getContainingLocation())) 
		{
			log.info(fleet.getName() + ": No target found and no ongoing raids, going home");
			giveStandDownOrders();
			return;
		}
		if (following == null || !following.isAlive()) {
			following = VultureFleetManager.getRandomRaidFleet(fleet);
		}
		
		if (following != null) {
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, following, 0.34f);
		}
		fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, fleet, 0.34f);
	}
	
	// From ScavengeAbility.java:
	// the debris field spec is shared between all debris fields and all the relevant values in it
	// are set here, before it's actually used for any given instance of a debris field
	protected void handleDebrisSalvageSpec(DebrisFieldTerrainPlugin debris) {
		SalvageEntityGenDataSpec spec = SalvageEntityGeneratorOld.getSalvageSpec(Entities.DEBRIS_FIELD_SHARED);

		spec.getDropValue().clear();
		spec.getDropRandom().clear();

		spec.getDropValue().addAll(debris.getEntity().getDropValue());
		spec.getDropRandom().addAll(debris.getEntity().getDropRandom());

		spec.setProbDefenders(debris.getParams().defenderProb * debris.getParams().density);
		spec.setMinStr(debris.getParams().minStr * debris.getParams().density);
		spec.setMaxStr(debris.getParams().maxStr * debris.getParams().density);
		spec.setMaxDefenderSize(debris.getParams().maxDefenderSize);
		spec.setDefFaction(debris.getParams().defFaction);
	}
	
	protected void printCargo(CargoAPI cargo) {
		for (CargoStackAPI stack : cargo.getStacksCopy()) {
			log.info(fleet.getName() + " loading cargo: " + stack.getDisplayName() + ", " + stack.getSize());
		}
	}
	
	protected void takeExtraSalvage(MemoryAPI memory, CargoAPI salvage) {
		ExtraSalvage extra = BaseSalvageSpecial.getExtraSalvage(memory);
		if (extra != null) {
			salvage.addAll(extra.cargo);
			BaseSalvageSpecial.clearExtraSalvage(memory);
		}
	}
	
	// based on SalvageEntity.performSalvage()
	protected void performSalvage(SectorEntityToken entity) {
		log.info(fleet.getName() + " salvaging " + entity.getName());
		boolean isDebris = entity instanceof CampaignTerrainAPI;
		ShipRecoverySpecialData recovData = ShipRecoverySpecial.getSpecialData(entity, "", VultureFleetManager.DEBUG_MODE, false);
		
		if (isDebris) {
			CampaignTerrainAPI terrain = (CampaignTerrainAPI)entity;
			DebrisFieldTerrainPlugin debris = (DebrisFieldTerrainPlugin) terrain.getPlugin();
			handleDebrisSalvageSpec(debris);
			
			// recover or scavenge any ships we find in the debris
			List<PerShipData> ships = VultureFleetManager.getShipsInDebris(terrain);
			if (ships != null) {
				salvageShipsInDebris(entity, ships);
			}
		}
		
		MemoryAPI memory = entity.getMemoryWithoutUpdate();
		long seed = memory.getLong(MemFlags.SALVAGE_SEED);
		Random random = Misc.getRandom(seed, 100);
		
		String specId = entity.getCustomEntityType();
		if (isDebris) {
			specId = Entities.DEBRIS_FIELD_SHARED;
		}
		if (specId == null || entity.getMemoryWithoutUpdate().contains(MemFlags.SALVAGE_SPEC_ID_OVERRIDE)) {
			specId = entity.getMemoryWithoutUpdate().getString(MemFlags.SALVAGE_SPEC_ID_OVERRIDE);
		}
		
		SalvageEntityGenDataSpec spec = SalvageEntityGeneratorOld.getSalvageSpec(specId);
				
		List<DropData> dropValue = new ArrayList<DropData>(spec.getDropValue());
		List<DropData> dropRandom = new ArrayList<DropData>(spec.getDropRandom());
		dropValue.addAll(entity.getDropValue());
		dropRandom.addAll(entity.getDropRandom());
		
		CargoAPI salvage;
		// if this is a ship that can be recovered, do so
		if (!isDebris && recovData != null && !recovData.ships.isEmpty()) {
			salvage = Global.getFactory().createCargo(false);
			PerShipData shipData = recovData.ships.get(0);
			addStuffFromShip(salvage, shipData);
		}
		else {
			salvage = SalvageEntity.generateSalvage(random, 1, 1, 1, 1, dropValue, dropRandom);
		}
		
		takeExtraSalvage(memory, salvage);
		//printCargo(salvage);
		
		fleet.getCargo().addAll(salvage);
		
		Misc.fadeAndExpire(entity);
	}
	
	// based on ShipRecoverySpecial.addExtraSalvageFromUnrecoveredShips
	protected void salvageShipsInDebris(SectorEntityToken entity, List<PerShipData> ships) {
		if (ships == null || ships.isEmpty()) return;
		
		ExtraSalvage es = BaseSalvageSpecial.getExtraSalvage(entity);
		CargoAPI extra = Global.getFactory().createCargo(true);
		if (es != null) extra = es.cargo;
		
		for (PerShipData ship : ships) {
			addStuffFromShip(extra, ship);
		}
		BaseSalvageSpecial.setExtraSalvage(extra, entity.getMemoryWithoutUpdate(), 0);
	}
	
	// based on ShipRecoverySpecial.addStuffFromMember
	protected void addStuffFromShip(CargoAPI cargo, PerShipData shipData) {
		FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, shipData.variantId);
		new ShipRecoverySpecialNPC().prepareMember(member, shipData);
		
		if (shouldRecover(member)) {
			addShipToFleet(member, shipData);
			return;
		}
		
		cargo.addCommodity(Commodities.SUPPLIES, member.getRepairTracker().getSuppliesFromScuttling());
		cargo.addCommodity(Commodities.FUEL, member.getRepairTracker().getFuelFromScuttling());
		cargo.addCommodity(Commodities.HEAVY_MACHINERY, member.getRepairTracker().getHeavyMachineryFromScuttling());
		
		ShipVariantAPI variant = member.getVariant();
		for (String slotId : variant.getNonBuiltInWeaponSlots()) {
			cargo.addWeapons(variant.getWeaponId(slotId), 1);
		}
		
		int index = 0;
		for (String wingId : variant.getWings()) {
			if (wingId != null && !wingId.isEmpty() && !variant.getHullSpec().isBuiltInWing(index)) {
				cargo.addFighters(wingId, 1);
			}
			index++;
		}
	}
	
	protected void addShipToFleet(FleetMemberAPI member, PerShipData shipData) {
		log.info(fleet.getName() + " adding ship: " + member.getHullId());
		
		member.setShipName(shipData.shipName);

		float hull = (float) Math.random() * (0.2f) + 0.2f;
		member.getStatus().setHullFraction(hull);
		member.getRepairTracker().setMothballed(true);

		fleet.getFleetData().addFleetMember(member);
		fleet.getFleetData().setSyncNeeded();
		fleet.getFleetData().syncIfNeeded();
	}
	
	// TODO
	protected boolean shouldRecover(FleetMemberAPI member) {
		return true;
	}
	
	public Script getScavengeScript(final SectorEntityToken target) {
		return new Script() {
			public void run() {
				performSalvage(target);
				data.target = null;
			}
		};
	}
	
	public Script getDefenderTagScript(final SectorEntityToken target) {
		return new Script() {
			public void run() {
				target.getMemoryWithoutUpdate().set("$nex_vultureSalvaging", fleet, 0.25f);
				target.getMemoryWithoutUpdate().set("$hasDefenders", true, 0.25f);
			}
		};
	}
	
	@Override
	public boolean isDone()
	{
		return !fleet.isAlive();
	}
	
	@Override
	public boolean runWhilePaused()
	{
		return false;
	}
	
	protected void giveInitialAssignment()
	{
		if (data.noWait) return;
		float daysToOrbit = ExerelinUtilsFleet.getDaysToOrbit(fleet)/2;
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, this.data.source.getPrimaryEntity(), 
				daysToOrbit, StringHelper.getFleetAssignmentString("preparingFor", data.source.getName(), "missionVulture"));
	}
	
	protected void debugLocal(String str)
	{
		if (fleet.getContainingLocation() != Global.getSector().getPlayerFleet().getContainingLocation())
			return;
		Global.getSector().getCampaignUI().addMessage(str);
	}
	
	protected void giveStandDownOrders()
	{
		if (!orderedReturn)
		{
			orderedReturn = true;
			fleet.clearAssignments();
			
			SectorEntityToken destination = data.source.getPrimaryEntity();
			fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, destination, 1000.0F, StringHelper.getFleetAssignmentString("returningTo", destination.getName()));			
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, ExerelinUtilsFleet.getDaysToOrbit(fleet), 
					StringHelper.getFleetAssignmentString("miningUnload", null),
					MiningFleetAI.getUnloadScript(fleet, data.source, true));
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
		}
	}
}

