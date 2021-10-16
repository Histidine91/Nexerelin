package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.DelayedFleetEncounter;
import com.fs.starfarer.api.impl.campaign.missions.DelayedFleetEncounter.EncounterType;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsAstro;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class MiscEventsManager extends BaseCampaignEventListener implements DiscoverEntityListener {
	
	public static final boolean USE_OMEGA_DFE = true;
	
	public MiscEventsManager() {
		super(false);
	}
	
	public static MiscEventsManager create() {
		MiscEventsManager manager = new MiscEventsManager();
		Global.getSector().addTransientListener(manager);
		Global.getSector().getListenerManager().addListener(manager, true);
		return manager;
	}

	@Override
	public void reportEntityDiscovered(SectorEntityToken entity) {
		if (entity.hasTag(Tags.CORONAL_TAP))
			spawnShuntFleet();
	}
	
	public void spawnShuntFleet() {
		FactionAPI faction = Global.getSector().getFaction(Factions.OMEGA);
		float maxPointsForFaction = faction.getApproximateMaxFPPerFleet(FactionAPI.ShipPickMode.PRIORITY_THEN_ALL);
		
		float playerStr = NexUtilsFleet.calculatePowerLevel(Global.getSector().getPlayerFleet());
		int capBonus = Math.round(NexUtilsFleet.getPlayerLevelFPBonus());

		int combat = Math.round(playerStr/5 * MathUtils.getRandomNumberInRange(0.6f, 0.7f) + capBonus);
		combat *= 0.4f;
		//Global.getLogger(this.getClass()).info("Player strength: " + playerStr);
		//Global.getLogger(this.getClass()).info("Omega estimated desired combat points: " + combat);
		//Global.getLogger(this.getClass()).info("Omega max combat points: " + maxPoints);
		combat = Math.min(70, combat);
		combat = Math.max(12, combat);	// at least a shard
		
		// preferred in most ways since it automates various behaviors
		// but has problems in that no way to set variant tags, needs to outsource to a listener
		if (USE_OMEGA_DFE) {			
			DelayedFleetEncounter e = new DelayedFleetEncounter(null, "hist");
			e.setTypes(EncounterType.OUTSIDE_SYSTEM, EncounterType.JUMP_IN_NEAR_PLAYER, 
					EncounterType.IN_HYPER_EN_ROUTE, EncounterType.FROM_SOMEWHERE_IN_SYSTEM);
			e.setDelayNone();
			e.setLocationAnywhere(false, Factions.REMNANTS);
			e.setDoNotAbortWhenPlayerFleetTooStrong();
			e.beginCreate();
			e.triggerCreateFleet(HubMissionWithTriggers.FleetSize.SMALL, 
					HubMissionWithTriggers.FleetQuality.VERY_HIGH, 
					Factions.OMEGA, 
					FleetTypes.PATROL_SMALL, 
					new Vector2f());
			//e.triggerSetAdjustStrengthBasedOnQuality(false, 1);
			float fraction = Math.max(combat/maxPointsForFaction/0.75f, HubMissionWithTriggers.FleetSize.TINY.maxFPFraction);
			fraction = Math.min(fraction, 1);
			e.triggerSetFleetSizeFraction(fraction);
			e.triggerSetFleetMaxShipSize(2);
			e.triggerSetFleetFaction(Factions.REMNANTS);
			e.triggerSetStandardAggroInterceptFlags();
			e.triggerMakeNoRepImpact();
			e.triggerSetFleetGenericHailPermanent("Nex_HistorianOmegaHail");
			e.triggerSetFleetFlagPermanent("$nex_omega_hypershunt_complication");
			//e.triggerSetFleetFaction(Factions.REMNANTS);
			e.endCreate();
		}
		else {
			CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
			Vector2f locInHyper = playerFleet.getLocationInHyperspace();
			
			FleetParamsV3 params = new FleetParamsV3(locInHyper,
					Factions.OMEGA,
					null,
					FleetTypes.PATROL_SMALL,
					combat, // combatPts
					0, // freighterPts
					0, // tankerPts
					0, // transportPts
					0, // linerPts
					0, // utilityPts
					0);
			params.ignoreMarketFleetSizeMult = true;
			params.qualityOverride = 1.2f;
			params.maxShipSize = 2;

			CampaignFleetAPI fleet = NexUtilsFleet.customCreateFleet(faction, params);

			if (fleet == null)
				return;

			String targetName = StringHelper.getString("yourFleet");			
			
			fleet.getMemoryWithoutUpdate().set("$genericHail", true);
			fleet.getMemoryWithoutUpdate().set("$genericHail_openComms", "Nex_HistorianOmegaHail");
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY, true);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);

			Vector2f pos = MathUtils.getPointOnCircumference(playerFleet.getLocation(), 
					playerFleet.getMaxSensorRangeToDetect(fleet) * MathUtils.getRandomNumberInRange(1.25f, 1.6f),
					NexUtilsAstro.getRandomAngle());
			fleet.setLocation(pos.x, pos.y);

			fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, playerFleet, 0.5f);	// make it get a little closer
			fleet.addAssignment(FleetAssignment.INTERCEPT, playerFleet, 15,
					StringHelper.getFleetAssignmentString("intercepting", targetName));
			Misc.giveStandardReturnToSourceAssignments(fleet, false);
			fleet.setLocation(pos.getX(), pos.getY());
			playerFleet.getContainingLocation().addEntity(fleet);
			
		}
		
		//Global.getLogger(this.getClass()).info("Creating Omega complication");
		//Global.getSector().getCampaignUI().addMessage("Creating Omega complication");
	}

	@Override
	public void reportFleetSpawned(CampaignFleetAPI fleet) {
		//Global.getLogger(this.getClass()).info("Fleet spawned: " + fleet.getNameWithFactionKeepCase());
		if (fleet.getMemoryWithoutUpdate().contains("$nex_omega_hypershunt_complication")) {
			//Global.getSector().getCampaignUI().addMessage("Omega fleet spawned");
			for (FleetMemberAPI member : fleet.getFleetData().getMembersListWithFightersCopy()) {
				member.setVariant(member.getVariant().clone(), false, false);
				member.getVariant().setSource(VariantSource.REFIT);
				member.getVariant().addTag(Tags.SHIP_LIMITED_TOOLTIP);
				member.getVariant().addTag(Tags.VARIANT_CONSISTENT_WEAPON_DROPS);
			}
		}
	}
}
