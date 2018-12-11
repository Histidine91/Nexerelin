package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import static com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin.getDaysString;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidAssignmentAI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidDelegate;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.fleets.InvasionFleetManager;
import static exerelin.campaign.fleets.InvasionFleetManager.DEFENDER_STRENGTH_MARINE_MULT;
import static exerelin.campaign.fleets.InvasionFleetManager.TANKER_FP_PER_FLEET_FP_PER_10K_DIST;
import exerelin.campaign.intel.invasion.*;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

public class InvasionIntel extends RaidIntel implements RaidDelegate {
	
	public static final Object ENTERED_SYSTEM_UPDATE = new Object();
	public static final Object OUTCOME_UPDATE = new Object();
	
	public static Logger log = Global.getLogger(InvasionIntel.class);
	
	protected MarketAPI from;
	protected MarketAPI target;
	protected FactionAPI targetFaction;
	protected InvasionOutcome outcome;
	protected float startingMarines = 0;
	
	protected InvActionStage action;
	
	public static enum InvasionOutcome {
		TASK_FORCE_DEFEATED,
		MARKET_NO_LONGER_EXISTS,
		SUCCESS,
		FAIL,
		NO_LONGER_HOSTILE,
		OTHER
	}
	
	public InvasionIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(target.getStarSystem(), attacker, null);
		
		log.info("Creating invasion intel");
		
		this.target = target;
		this.delegate = this;
		this.from = from;
		this.target = target;
		targetFaction = target.getFaction();
		
		SectorEntityToken gather = from.getPrimaryEntity();
		
		addStage(new InvOrganizeStage(this, from, orgDur));
		
		float successMult = 0.4f;
		InvAssembleStage assemble = new InvAssembleStage(this, gather);
		assemble.addSource(from);
		assemble.setSpawnFP(fp);
		assemble.setAbortFP(fp * successMult);
		addStage(assemble);
		
		// don't add a travel stage for same-system invasions
		// FIXME: does this fix even work for what we want it to?
		if (from.getContainingLocation() != target.getContainingLocation())
		{
			SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(getFactionForUIColors(), target.getPrimaryEntity());

			InvTravelStage travel = new InvTravelStage(this, gather, raidJump, false);
			travel.setAbortFP(fp * successMult);
			addStage(travel);
		}
		
		action = new InvActionStage(this, target);
		action.setAbortFP(fp * successMult);
		addStage(action);
		
		addStage(new InvReturnStage(this));
		
		Global.getSector().getIntelManager().addIntel(this);
	}
	
	public InvasionOutcome getOutcome() {
		return outcome;
	}
	
	@Override
	public void notifyRaidEnded(RaidIntel raid, RaidStageStatus status) {
		// TBD
	}

	public void sendOutcomeUpdate() {
		sendUpdateIfPlayerHasIntel(OUTCOME_UPDATE, false);
	}
	
	public void sendEnteredSystemUpdate() {
		sendUpdateIfPlayerHasIntel(ENTERED_SYSTEM_UPDATE, false);
	}

	public void setOutcome(InvasionOutcome outcome) {
		this.outcome = outcome;
	}
	
	public float getStartingMarines() {
		return startingMarines;
	}
	
	public List<CampaignFleetAPI> getFleetsThatMadeIt(List<RouteManager.RouteData> routes, List<RouteManager.RouteData> stragglers)
	{
		List<CampaignFleetAPI> fleets = new ArrayList<>();
		for (RouteManager.RouteData route : routes) {
			if (stragglers.contains(route)) continue;
			CampaignFleetAPI fleet = route.getActiveFleet();
			if (fleet != null) {
				fleets.add(fleet);
			}
		}
		return fleets;
	}
	
	// for intel popup in campaign screen's message area
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		float pad = 3f;
		float opad = 10f;
		
		float initPad = pad;
		if (mode == ListInfoMode.IN_DESC) initPad = opad;
		
		Color tc = getBulletColorForMode(mode);
		
		bullet(info);
		boolean isUpdate = getListInfoParam() != null;
		
		float eta = getETA();
		
		info.addPara("Faction: " + faction.getDisplayName(), initPad, tc,
				 	 faction.getBaseUIColor(), faction.getDisplayName());
		initPad = 0f;
		
		if (target != null) {
			FactionAPI other = target.getFaction();
			info.addPara("Target: " + target.getName(), initPad, tc,
					     other.getBaseUIColor(), target.getName());
		}
		
		if (getListInfoParam() == ENTERED_SYSTEM_UPDATE) {
			info.addPara("Invasion force arrived in system", 
						tc, initPad);
			return;
		}
		
		if (outcome != null)
		{
			switch (outcome) {
				case SUCCESS:
					info.addPara("Invasion of " + target.getName() + " successful",
							tc, initPad);
					break;
				case TASK_FORCE_DEFEATED:
					info.addPara("Invasion of " + target.getName() + " has failed",
							tc, initPad);
					info.addPara("Invasion force defeated",
							tc, initPad);
					break;
				case FAIL:
					info.addPara("Invasion force defeated",
							tc, initPad);
					break;
				case MARKET_NO_LONGER_EXISTS:
					info.addPara("Invasion of " + target.getName() + " aborted: target destroyed",
							tc, initPad);
					break;
				case NO_LONGER_HOSTILE:
					info.addPara("Invasion of " + target.getName() + " aborted: no longer hostile",
							tc, initPad);
					break;
				default:
					info.addPara("Invasion of " + target.getName() + " cancelled",
							tc, initPad);
					break;
			}
		} else {
			info.addPara(system.getNameWithLowercaseType(), tc, initPad);
		}
		initPad = 0f;
		if (eta > 1 && failStage < 0) {
			String days = getDaysString(eta);
			info.addPara("Estimated %s " + days + " until arrival", 
					initPad, tc, h, "" + (int)Math.round(eta));
			initPad = 0f;
		}
		
		unindent(info);
	}
	
	// intel long description in intel screen
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		//super.createSmallDescription(info, width, height);
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		info.addImage(getFactionForUIColors().getLogo(), width, 128, opad);
		
		FactionAPI faction = getFaction();
		String has = faction.getDisplayNameHasOrHave();
		String is = faction.getDisplayNameIsOrAre();
		
		String strDesc = getRaidStrDesc();
		
		LabelAPI label = info.addPara(Misc.ucFirst(faction.getDisplayNameWithArticle()) + " " + is + 
				" invading " + target.getName() + " in " + system.getName() + ". The invasion forces are " +
						"projected to be " + strDesc + ".",
				opad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
		label.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(), target.getName(), strDesc);
		label.setHighlightColors(faction.getBaseUIColor(), target.getFaction().getBaseUIColor(), h);
		
		if (outcome == null) {
			addStandardStrengthComparisons(info, target, targetFaction, true, false,
										   "expedition", "expedition's");
		}
		
		info.addSectionHeading("Status", 
				   faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);
		
		for (RaidStage stage : stages) {
			stage.showStageInfo(info);
			if (getStageIndex(stage) == failStage) break;
		}
	}
	
	@Override
	public RouteFleetAssignmentAI createAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route) {
		RaidAssignmentAI raidAI = new RaidAssignmentAI(fleet, route, action);
		return raidAI;
	}
	
	@Override
	public CampaignFleetAPI createFleet(String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
		if (random == null) random = new Random();
				
		RouteManager.OptionalFleetData extra = route.getExtra();
		
		// only one fleet is the actual invasion fleet; rest are strike fleets supporting it
		// not sure that'll even work given the spawn/despawn behavior
		boolean isInvasionFleet = extra.fleetType.equals("exerelinInvasionFleet");
				
		float defenderStrength = InvasionRound.getDefenderStrength(target, 0.5f);
		float distance = ExerelinUtilsMarket.getHyperspaceDistance(market, target);
		int numMarines = (int)(defenderStrength * DEFENDER_STRENGTH_MARINE_MULT);
		
		float fp = extra.fp;
		if (!isInvasionFleet) fp *= 0.75f;
		
		float combat = fp;
		float tanker = fp * (0.1f + random.nextFloat() * 0.05f)
				+ TANKER_FP_PER_FLEET_FP_PER_10K_DIST * distance/10000;
		float transport = isInvasionFleet ? numMarines/100 : 0;
		float freighter = fp * (0.1f + random.nextFloat() * 0.05f);
		
		if (isInvasionFleet) freighter *= 2;
		
		float totalFp = combat + tanker + transport + freighter;
		
		FleetParamsV3 params = new FleetParamsV3(
				market, 
				locInHyper,
				factionId,
				route == null ? null : route.getQualityOverride(),
				extra.fleetType,
				combat, // combatPts
				freighter, // freighterPts 
				tanker, // tankerPts
				transport, // transportPts
				0f, // linerPts
				0f, // utilityPts
				0f // qualityMod, won't get used since routes mostly have quality override set
				);
		//params.ignoreMarketFleetSizeMult = true; // already accounted for in extra.fp
		
		if (route != null) {
			params.timestamp = route.getTimestamp();
		}
		params.random = random;
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		
		if (fleet == null || fleet.isEmpty()) return null;
		
		fleet.setName(InvasionFleetManager.getFleetName(extra.fleetType, factionId, totalFp));
		
		fleet.getCargo().addMarines(numMarines);
		startingMarines += numMarines;
		log.info("Adding marines to cargo: " + numMarines);
		
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		if (isInvasionFleet)
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);	// needed to do raids
		
		if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
		}
		
		String postId = Ranks.POST_PATROL_COMMANDER;
		String rankId = Ranks.SPACE_CAPTAIN;	//isInvasionFleet ? Ranks.SPACE_ADMIRAL : Ranks.SPACE_COMMANDER;
		
		fleet.getCommander().setPostId(postId);
		fleet.getCommander().setRankId(rankId);
		
		log.info("Created fleet " + fleet.getName() + " of strength " + fleet.getFleetPoints() + "/" + totalFp);
		
		return fleet;
	}
	
	@Override
	public String getSortString() {
		return StringHelper.getString("exerelin_invasion", "invasion", true);
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MILITARY);
		tags.add(StringHelper.getString("exerelin_invasion", "invasions", true));
		if (targetFaction.isPlayerFaction())
			tags.add(Tags.INTEL_COLONIES);
		tags.add(getFaction().getId());
		tags.add(target.getFactionId());
		return tags;
	}
	
	@Override
	public String getName() {
		String base = StringHelper.getString("exerelin_invasion", "invasionOf");
		base = StringHelper.substituteToken(base, "$faction", faction.getPersonNamePrefix(), true);
		base = StringHelper.substituteToken(base, "$market", target.getName());
		
		if (isEnding()) {
			if (isSendingUpdate() && failStage >= 0) {
				return base + " - ";
			}
			for (RaidStage stage : stages) {
				if (stage instanceof ActionStage && stage.getStatus() == RaidStageStatus.SUCCESS) {
					return base + " - " + StringHelper.getString("successful", true);
				}
			}
			return base + " - " + StringHelper.getString("over", true);
		}
		return base;
	}
	
}
