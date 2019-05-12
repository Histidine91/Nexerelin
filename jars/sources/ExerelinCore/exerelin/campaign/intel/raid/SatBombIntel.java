package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.campaign.intel.fleets.NexReturnStage;
import exerelin.campaign.intel.fleets.NexTravelStage;
import exerelin.campaign.intel.invasion.InvAssembleStage;
import static exerelin.campaign.intel.raid.NexRaidIntel.log;
import exerelin.utilities.StringHelper;

// TODO
public class SatBombIntel extends NexRaidIntel {
		
	public SatBombIntel(FactionAPI attacker, CampaignFleetAPI base, MarketAPI target, 
			float fp, float orgDur) {
		super(attacker, null, target, fp, orgDur);
	}
		
	@Override
	public void init() {
		log.info("Creating saturation bomb intel");
		
		SectorEntityToken gather = from.getPrimaryEntity();
		
		addStage(new NexOrganizeStage(this, null, orgDur));
		
		float successMult = 0.4f;
		InvAssembleStage assemble = new InvAssembleStage(this, gather);
		assemble.setSpawnFP(fp);
		assemble.setAbortFP(fp * successMult);
		addStage(assemble);
		
		SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(getFactionForUIColors(), target.getPrimaryEntity());

		NexTravelStage travel = new NexTravelStage(this, gather, raidJump, false);
		travel.setAbortFP(fp * successMult);
		addStage(travel);
		
		action = new SatBombActionStage(this, system);
		action.setAbortFP(fp * successMult);
		addStage(action);
		
		addStage(new NexReturnStage(this));
		
		/*
		if (shouldDisplayIntel())
			queueIntelIfNeeded();
		else if (DEBUG_MODE)
		{
			Global.getSector().getCampaignUI().addMessage("Remnant raid intel from " 
					+ base.getContainingLocation().getName() + " to " + target.getName() + " concealed due to lack of sniffer");
		}
		*/
		addIntelIfNeeded();
	}
	
	@Override
	public String getActionName() {
		return StringHelper.getString("nex_satBomb", "expedition");
	}
	
	@Override
	public String getActionNameWithArticle() {
		return StringHelper.getString("nex_satBomb", "theExpedition");
	}
	
	@Override
	public String getForceType() {
		return StringHelper.getString("nex_satBomb", "expeditionForce");
	}
	
	@Override
	public String getForceTypeWithArticle() {
		return StringHelper.getString("nex_satBomb", "theExpeditionForce");
	}
	
	@Override
	public String getForceTypeHasOrHave() {
		return StringHelper.getString("nex_satBomb", "forceHasOrHave");
	}
	
	@Override
	public String getForceTypeIsOrAre() {
		return StringHelper.getString("nex_satBomb", "forceIsOrAre");
	}
			
	@Override
	public String getIcon() {
		return Global.getSettings().getSpriteName("intel", "nex_satbomb");
	}
}
