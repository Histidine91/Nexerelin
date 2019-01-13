package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.NexRaidIntel.log;
import static exerelin.campaign.intel.OffensiveFleetIntel.DEBUG_MODE;
import exerelin.campaign.intel.invasion.InvReturnStage;
import exerelin.campaign.intel.raid.NexRaidActionStage;
import exerelin.campaign.intel.raid.RemnantRaidAssembleStage;
import exerelin.campaign.intel.raid.RemnantRaidOrganizeStage;
import java.util.List;

public class RemnantRaidIntel extends NexRaidIntel {
	
	protected CampaignFleetAPI base;
	
	public RemnantRaidIntel(FactionAPI attacker, CampaignFleetAPI base, MarketAPI target, float fp, float orgDur) {
		super(attacker, null, target, fp, orgDur);
		this.base = base;
	}
	
	public CampaignFleetAPI getBase() {
		return base;
	}
	
	@Override
	public void init() {
		log.info("Creating Remnant raid intel");
		
		SectorEntityToken gather = base;
		
		addStage(new RemnantRaidOrganizeStage(this, null, orgDur));
		
		float successMult = 0.4f;
		RemnantRaidAssembleStage assemble = new RemnantRaidAssembleStage(this, gather);
		assemble.setSpawnFP(fp);
		assemble.setAbortFP(fp * successMult);
		addStage(assemble);
		
		SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(getFactionForUIColors(), target.getPrimaryEntity());

		TravelStage travel = new TravelStage(this, gather, raidJump, false);
		travel.setAbortFP(fp * successMult);
		addStage(travel);
		
		action = new NexRaidActionStage(this, system);
		action.setAbortFP(fp * successMult);
		addStage(action);
		
		addStage(new InvReturnStage(this));
		
		if (shouldDisplayIntel())
			queueIntelIfNeeded();
		else if (DEBUG_MODE)
		{
			Global.getSector().getCampaignUI().addMessage("Remnant raid intel from " 
					+ base.getContainingLocation().getName() + " to " + target.getName() + " concealed due to lack of sniffer");
		}
	}
	
	@Override
	public String getName() {
		String base = Misc.ucFirst(getFaction().getDisplayName()) + " Raid";
		if (isEnding()) {
			if (isSendingUpdate() && failStage >= 0) {
				return base + " - Failed";
			}
			for (RaidStage stage : stages) {
				if (stage instanceof ActionStage && stage.getStatus() == RaidStageStatus.SUCCESS) {
					return base + " - Successful";
				}
			}
			return base + " - Over";
		}
		return base;
	}
	
	@Override
	protected boolean shouldDisplayIntel()
	{
		if (INTEL_ALWAYS_VISIBLE) return true;
		if (faction.getRelationshipLevel(Factions.PLAYER).isAtWorst(RepLevel.FAVORABLE))
			return true;
		LocationAPI loc = base.getContainingLocation();
		
		List<SectorEntityToken> sniffers = Global.getSector().getIntel().getCommSnifferLocations();
		for (SectorEntityToken relay : sniffers)
		{
			if (relay.getContainingLocation() == loc)
				return true;
		}
		return false;
	}
	
	@Override
	public void checkForTermination() {
		if (outcome != null) return;
		
		if (base == null || !base.isAlive())
			terminateEvent(OffensiveOutcome.FAIL);
	}
}
