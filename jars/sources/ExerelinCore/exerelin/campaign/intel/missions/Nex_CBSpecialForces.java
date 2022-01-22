package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.missions.cb.BaseCustomBountyCreator;
import com.fs.starfarer.api.impl.campaign.missions.cb.CBStats;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.intel.specialforces.SpecialForcesIntel;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.getString;
import exerelin.campaign.intel.specialforces.SpecialForcesManager;
import java.awt.Color;

public class Nex_CBSpecialForces extends BaseCustomBountyCreator {
	
	public static final float PAYOUT_MULT = 3.5f;
	public static final float DAYS_TO_COMPLETE = 365;
	
	public SpecialForcesIntel getSFIntel(CustomBountyData data) {
		return ((SpecialForcesIntel)data.custom1);
	}
	
	
	@Override
	public void addTargetLocationAndDescriptionBulletPoint(TooltipMakerAPI info,
			Color tc, float pad, HubMissionWithBarEvent mission,
			CustomBountyData data) {
		Color h = Misc.getHighlightColor();
		
		SpecialForcesIntel intel = getSFIntel(data);
		RouteManager.RouteSegment segment = intel.getRoute().getCurrent();
		
		boolean anyDetails = false;
		
		CampaignFleetAPI fleet = intel.getRoute().getActiveFleet();
		if (fleet != null && fleet.isAlive() && !fleet.isInHyperspace()) {
			info.addPara(getString("bountyBulletLocation"), pad, tc, h, fleet.getContainingLocation().getNameWithTypeShort());
			anyDetails = true;
		}
		
		if (segment != null && segment.to != null) {
			SectorEntityToken to = segment.to;
			LocationAPI loc = to.getContainingLocation();
			info.addPara(getString("bountyBulletDestination"), pad, tc, h, to.getName(), loc.getNameWithTypeIfNebula());
			anyDetails = true;
		}
		
		if (!anyDetails) {
			info.addPara(getString("bountyBulletUnknown"), tc, pad);
		}
	}
	
	@Override
	public void addTargetLocationAndDescription(TooltipMakerAPI info, float width, 
			float height, HubMissionWithBarEvent mission, CustomBountyData data) {
		float pad = 3f;
		Color h = Misc.getHighlightColor();
		SpecialForcesIntel intel = getSFIntel(data);
		RouteManager.RouteSegment segment = intel.getRoute().getCurrent();
		
		boolean anyDetails = false;
		
		CampaignFleetAPI fleet = intel.getRoute().getActiveFleet();
		if (fleet != null && fleet.isAlive() && !fleet.isInHyperspace()) {
			info.addPara(getString("bountyParaLocation"), pad, h, fleet.getContainingLocation().getNameWithLowercaseType());
			anyDetails = true;
		}
		
		if (segment != null && segment.to != null) {
			SectorEntityToken to = segment.to;
			LocationAPI loc = to.getContainingLocation();
			info.addPara(getString("bountyParaDestination"), pad, h, to.getName(), loc.getNameWithTypeIfNebula());
			anyDetails = true;
		}
		
		if (!anyDetails) {
			info.addPara(getString("bountyBulletUnknown"), pad);
		}
	}
	
	@Override
	public CustomBountyData createBounty(MarketAPI createdAt, HubMissionWithBarEvent mission, int difficulty, Object bountyStage) {
		CustomBountyData data = new CustomBountyData();
		data.difficulty = difficulty;
				
		data.fleet = createFleet(mission, data);
		if (data.fleet == null) {
			Global.getLogger(this.getClass()).info("fleet is null");
			return null;
		}
		
		setRepChangesBasedOnDifficulty(data, difficulty);
		data.baseReward = CBStats.getBaseBounty(difficulty, PAYOUT_MULT, mission);
		
		return data;
	}
	
	@Override
	public float getBountyDays() {
		return DAYS_TO_COMPLETE;
	}
	
	@Override
	protected CampaignFleetAPI createFleet(HubMissionWithBarEvent mission, CustomBountyData data) {
		WeightedRandomPicker<SpecialForcesIntel> picker = new WeightedRandomPicker<>();
		
		for (SpecialForcesIntel intel : SpecialForcesManager.getManager().getActiveIntelCopy()) {
			if (!intel.getFaction().isHostileTo(mission.getPerson().getFaction()))
				continue;
			if (intel.getRoute().getActiveFleet() == null)
				continue;
			
			Float dam = intel.getRoute().getExtra().damage;
			if (dam != null && dam > 0.2) continue;
			float weight = 1;
			if (intel.getFaction().getRelationshipLevel(mission.getPerson().getFaction()) == RepLevel.VENGEFUL)
				weight = 2;
			picker.add(intel, weight);
		}
		
		SpecialForcesIntel picked = picker.pick();
		if (picked == null) return null;
		//picked.forceSpawn(false);
		data.custom1 = picked;
		return picked.getRoute().getActiveFleet();
	}	
	
	@Override
	public String getBountyNamePostfix(HubMissionWithBarEvent mission, CustomBountyData data) {
		return " - " + getSFIntel(data).getFleetName();
	}
	
	public void cleanupFleet(HubMissionWithBarEvent mission, CustomBountyData data) {
		getSFIntel(data).allowDespawn();
	}
	
	@Override
	public void notifyAccepted(MarketAPI createdAt, HubMissionWithBarEvent mission, CustomBountyData data) {
		//getSFIntel(data).forceSpawn(true);
		CampaignFleetAPI active = getSFIntel(data).getRoute().getActiveFleet();
		if (active != null) active.setNoAutoDespawn(true);
	}
	
	@Override
	public void notifyCompleted(HubMissionWithBarEvent mission, CustomBountyData data) {
		cleanupFleet(mission, data);
	}

	@Override
	public void notifyFailed(HubMissionWithBarEvent mission, CustomBountyData data) {
		cleanupFleet(mission, data);
	}
	
	@Override
	public void updateInteractionData(HubMissionWithBarEvent mission, CustomBountyData data) {
		String id = mission.getMissionId();
		String faction = data.fleet.getFaction().getPersonNamePrefix();
		Color factionColor = data.fleet.getFaction().getBaseUIColor();
		mission.set("$" + id + "_targetFaction", faction);
		mission.set("$bcb_targetFaction", faction);
		mission.set("$" + id + "_targetFactionColor", factionColor);
		mission.set("$bcb_targetFactionColor", factionColor);
	}

	@Override
	public int getMaxDifficulty() {
		return super.getMaxDifficulty();
	}

	@Override
	public int getMinDifficulty() {
		return 7;
	}
}
