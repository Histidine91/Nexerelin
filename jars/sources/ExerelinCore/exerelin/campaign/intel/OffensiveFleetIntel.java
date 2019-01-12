package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidAssignmentAI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidDelegate;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.invasion.*;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;

public abstract class OffensiveFleetIntel extends RaidIntel implements RaidDelegate {
	
	public static final Object ENTERED_SYSTEM_UPDATE = new Object();
	public static final Object OUTCOME_UPDATE = new Object();
	public static final boolean DEBUG_MODE = true;
	public static final boolean INTEL_ALWAYS_VISIBLE = true;
	
	public static Logger log = Global.getLogger(OffensiveFleetIntel.class);
	
	protected MarketAPI from;
	protected MarketAPI target;
	protected FactionAPI targetFaction;
	protected OffensiveOutcome outcome;
	protected boolean isRespawn = false;
	protected boolean intelQueuedOrAdded;
	protected float fp;
	protected float orgDur;
	
	protected InvActionStage action;
	
	public static enum OffensiveOutcome {
		TASK_FORCE_DEFEATED,
		MARKET_NO_LONGER_EXISTS,
		SUCCESS,
		FAIL,
		NO_LONGER_HOSTILE,
		OTHER;
		
		public boolean isFailed() {
			return this == TASK_FORCE_DEFEATED || this == FAIL;
		}
	}
	
	public OffensiveFleetIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(target.getStarSystem(), attacker, null);
		
		this.target = target;
		this.delegate = this;
		this.from = from;
		this.target = target;
		this.fp = fp;
		this.orgDur = orgDur;
		targetFaction = target.getFaction();
	}
	
	public void init() {
	}
	
	protected void queueIntelIfNeeded()
	{
		if (intelQueuedOrAdded) return;
		Global.getSector().getIntelManager().queueIntel(this);
		intelQueuedOrAdded = true;
	}
	
	protected void addIntelIfNeeded()
	{
		if (intelQueuedOrAdded) return;
		Global.getSector().getIntelManager().addIntel(this);
		intelQueuedOrAdded = true;
	}
	
	protected boolean shouldDisplayIntel()
	{
		if (INTEL_ALWAYS_VISIBLE) return true;
		LocationAPI loc = from.getContainingLocation();
		if (faction.isPlayerFaction()) return true;		
		if (AllianceManager.areFactionsAllied(faction.getId(), PlayerFactionStore.getPlayerFactionId()))
			return true;
		
		List<SectorEntityToken> sniffers = Global.getSector().getIntel().getCommSnifferLocations();
		for (SectorEntityToken relay : sniffers)
		{
			if (relay.getContainingLocation() == loc)
				return true;
		}
		return false;
	}
	
	public OffensiveOutcome getOutcome() {
		return outcome;
	}
	
	public float getFP() {
		return fp;
	}
	
	@Override
	public void notifyRaidEnded(RaidIntel raid, RaidStageStatus status) {
		// TBD
	}

	public void sendOutcomeUpdate() {
		addIntelIfNeeded();
		sendUpdateIfPlayerHasIntel(OUTCOME_UPDATE, false);
	}
	
	public void sendEnteredSystemUpdate() {
		queueIntelIfNeeded();
		sendUpdateIfPlayerHasIntel(ENTERED_SYSTEM_UPDATE, false);
	}
	
	@Override
	public void sendUpdateIfPlayerHasIntel(Object listInfoParam, boolean onlyIfImportant, boolean sendIfHidden) {
		if (listInfoParam == UPDATE_RETURNING) {
			// we're using sendOutcomeUpdate() to send an end-of-event update instead
			return;
		}
		super.sendUpdateIfPlayerHasIntel(listInfoParam, onlyIfImportant, sendIfHidden);
	}

	public void setOutcome(OffensiveOutcome outcome) {
		this.outcome = outcome;
	}
		
	@Override
	public RouteFleetAssignmentAI createAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route) {
		RaidAssignmentAI raidAI = new RaidAssignmentAI(fleet, route, action);
		return raidAI;
	}
		
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MILITARY);
		//tags.add(StringHelper.getString("exerelin_invasion", "invasions", true));
		if (targetFaction.isPlayerFaction())
			tags.add(Tags.INTEL_COLONIES);
		tags.add(getFaction().getId());
		tags.add(target.getFactionId());
		return tags;
	}
		
	public void terminateEvent(OffensiveOutcome outcome)
	{
		setOutcome(outcome);
		forceFail(true);
	}
	
	// check if market should still be attacked
	@Override
	protected void advanceImpl(float amount) {
		if (outcome == null)
		{
			// source captured before launch
			if (getCurrentStage() <= 0 && from.getFaction() != faction) {
				terminateEvent(OffensiveOutcome.FAIL);
			}
			else if (!faction.isHostileTo(target.getFaction())) {
				terminateEvent(OffensiveOutcome.NO_LONGER_HOSTILE);
			}
			else if (!target.isInEconomy()) {
				terminateEvent(OffensiveOutcome.MARKET_NO_LONGER_EXISTS);
			}
		}
		super.advanceImpl(amount);
	}
	
	// send fleets home
	@Override
	protected void failedAtStage(RaidStage stage) {
		BaseRaidStage stage2 = (BaseRaidStage)stage;
		stage2.giveReturnOrdersToStragglers(stage2.getRoutes());
	}
}
