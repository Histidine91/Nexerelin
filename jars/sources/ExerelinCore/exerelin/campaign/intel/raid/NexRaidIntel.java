package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
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
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.econ.RaidCondition;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.campaign.intel.fleets.NexReturnStage;
import exerelin.campaign.intel.fleets.NexTravelStage;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

public class NexRaidIntel extends OffensiveFleetIntel {
	
	public static Logger log = Global.getLogger(NexRaidIntel.class);

	@Getter	@Setter	protected String preferredIndustryTarget;
		
	public NexRaidIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(attacker, from, target, fp, orgDur);
	}
	
	@Override
	public void init() {
		log.info("Creating raid intel");
		
		SectorEntityToken gather = from.getPrimaryEntity();
		
		addStage(new NexOrganizeStage(this, from, orgDur));
		
		float successMult = 0.4f;
		NexRaidAssembleStage assemble = new NexRaidAssembleStage(this, gather);
		assemble.addSource(from);
		assemble.setSpawnFP(fp);
		assemble.setAbortFP(fp * successMult);
		addStage(assemble);
		
		SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(getFactionForUIColors(), target.getPrimaryEntity());
		if (raidJump == null) {
			endImmediately();
			return;
		}

		NexTravelStage travel = new NexTravelStage(this, gather, raidJump, false);
		travel.setAbortFP(fp * successMult);
		addStage(travel);
		
		action = new NexRaidActionStage(this, system);
		action.setAbortFP(fp * successMult);
		addStage(action);
		
		addStage(new NexReturnStage(this));

		int nexIntelQueued = NexConfig.nexIntelQueued;
		switch (nexIntelQueued) {

			case 0:
				addIntelIfNeeded();
				break;

			case 1:
				if ((isPlayerTargeted() || playerSpawned || targetFaction == Misc.getCommissionFaction())) //TODO all intel has the problem of not updating without active comm relays and not queueing the update
					addIntelIfNeeded();
				else if (shouldDisplayIntel())
					queueIntelIfNeeded();
				break;

			case 2:
				if (playerSpawned)
					addIntelIfNeeded();
				else if (shouldDisplayIntel()) {
					Global.getSector().getIntelManager().queueIntel(this);
					intelQueuedOrAdded = true;
				}
				break;

			default:
				addIntelIfNeeded();
				Global.getSector().getCampaignUI().addMessage("Switch statement within init(), in NexRaidIntel, " +
						"defaulted. This is not supposed to happen. If your nexIntelQueued setting within ExerelinConfig " +
						"is below 0 or above 2, that is the likely cause. Otherwise, please contact the mod author!");
		}
	}
	
	@Override
	public void notifyRaidEnded(RaidIntel raid, RaidStageStatus status) {
		super.notifyRaidEnded(raid, status);
		RaidCondition.removeRaidFromConditions(system, this);
	}
	
	// don't display faction
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad){
		Color h = Misc.getHighlightColor();
		
		if (outcome == null)
		{
			writeRaidTargetsBullet(info, tc, initPad);
			initPad = 0;
		}
		
		if (getListInfoParam() == ENTERED_SYSTEM_UPDATE) {
			addArrivedBullet(info, tc, initPad);
			return;
		}
		
		if (outcome != null) {
			addOutcomeBullet(info, tc, initPad);
		} else {
			String name = system != null ? system.getNameWithLowercaseType() : target.getContainingLocation().getNameWithLowercaseType();
			info.addPara(name, tc, initPad);
		}
		addETABullet(info, tc, h, 0);
	}

	protected List<FactionAPI> getTargetFactions() {
		List<FactionAPI> targetFactions = new ArrayList<>();

		List<MarketAPI> targets = ((NexRaidActionStage)action).getTargets();
		for (MarketAPI target : targets) {
			if (targetFactions.contains(target.getFaction())) continue;
			targetFactions.add(target.getFaction());
		}
		return targetFactions;
	}
	
	protected void writeRaidTargetsBullet(TooltipMakerAPI info, Color tc, float pad) {
		List<FactionAPI> targetFactions = getTargetFactions();
		
		Collections.sort(targetFactions, Nex_FactionDirectoryHelper.NAME_COMPARATOR);
		List<String> factionNames = new ArrayList<>();
		List<Color> colors = new ArrayList<>();
		for (FactionAPI faction : targetFactions) {
			factionNames.add(faction.getDisplayName());
			colors.add(faction.getBaseUIColor());
		}
		
		String concat = StringHelper.writeStringCollection(factionNames, false, true);
		
		String str = StringHelper.getStringAndSubstituteToken("nex_fleetIntel",
					"bulletTargetRaid", "$factions", concat);
		LabelAPI label = info.addPara(str, tc, pad);
		label.setHighlight(factionNames.toArray(new String[0]));
		label.setHighlightColors(colors.toArray(new Color[0]));
	}
	
	@Override
	public String getActionName() {
		return StringHelper.getString("exerelin_raid", "raid");
	}
	
	@Override
	public String getActionNameWithArticle() {
		return StringHelper.getString("exerelin_raid", "theRaid");
	}
	
	@Override
	public String getForceType() {
		return StringHelper.getString("exerelin_raid", "raidForce");
	}
	
	@Override
	public String getForceTypeWithArticle() {
		return StringHelper.getString("exerelin_raid", "theRaidForce");
	}
	
	@Override
	public String getForceTypeHasOrHave() {
		return StringHelper.getString("exerelin_raid", "forceHasOrHave");
	}
	
	@Override
	public String getForceTypeIsOrAre() {
		return StringHelper.getString("exerelin_raid", "forceIsOrAre");
	}
	
	protected float getDistanceToTarget(MarketAPI market) {
		return NexUtilsMarket.getHyperspaceDistance(market, target);
	}

	@Override
	public String getType() {
		return "raid";
	}
	
	@Override
	public CampaignFleetAPI createFleet(String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
		if (random == null) random = new Random();
				
		RouteManager.OptionalFleetData extra = route.getExtra();
		
		float distance = getDistanceToTarget(market);
		
		float myFP = extra.fp;
		if (!useMarketFleetSizeMult)
			myFP *= InvasionFleetManager.getFactionDoctrineFleetSizeMult(faction);
		
		float combat = myFP;
		float tanker = getWantedTankerFP(myFP, distance, random);
		float transport = myFP * 0.1f;
		float freighter = getWantedFreighterFP(myFP, random) * 0.75f;
		
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
		// we don't need the variability involved in this
		// ...no, too much relies on fleet size mult (e.g. doctrine modifiers are piped through here)
		// ... so apply the doctrine modifiers separately
		if (!useMarketFleetSizeMult)
			params.ignoreMarketFleetSizeMult = true;
		
		params.modeOverride = ShipPickMode.PRIORITY_THEN_ALL;
		
		if (route != null) {
			params.timestamp = route.getTimestamp();
		}
		params.random = random;
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		
		if (fleet == null || fleet.isEmpty()) return null;
		
		fleet.setName(InvasionFleetManager.getFleetName(extra.fleetType, factionId, totalFp));
		
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);	// needed to do raids
		
		if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
		}
		
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);
		
		String postId = Ranks.POST_PATROL_COMMANDER;
		String rankId = Ranks.SPACE_CAPTAIN;	//isInvasionFleet ? Ranks.SPACE_ADMIRAL : Ranks.SPACE_COMMANDER;
		
		fleet.getCommander().setPostId(postId);
		fleet.getCommander().setRankId(rankId);
		
		log.info("Created fleet " + fleet.getName() + " of strength " + fleet.getFleetPoints() + "/" + totalFp);
		
		return fleet;
	}
	
	@Override
	public void checkForTermination() {
		if (outcome != null) return;
		
		// source captured before launch
		if (getCurrentStage() <= 0 && from.getFaction() != faction) {
			terminateEvent(OffensiveOutcome.FAIL);
		}
		else if (!doesSystemHaveHostileMarkets()) {
			terminateEvent(OffensiveOutcome.NO_LONGER_HOSTILE);
		}
	}
	
	public boolean doesSystemHaveHostileMarkets() {
		// speedup: try not to get markets-in-location unless we have to
		if (target.getFaction().isHostileTo(faction)) return true;
		
		// original target no longer hostile, see if there's another one we can target
		for (MarketAPI potentialTarget : ((NexRaidActionStage)action).getTargets())
		{
			target = potentialTarget;
			targetFaction = potentialTarget.getFaction();
			return true;
		}
		
		return false;
	}
	
	public boolean shouldRetreatIfOvermatched() {
		if (faction.getDoctrine().getAggression() == 5)
			return false;
		
		if (faction.getCustomBoolean(Factions.CUSTOM_FIGHT_TO_THE_LAST))
			return false;
		
		return true;
	}

	@Override
	public String getSortString() {
		//return StringHelper.getString("exerelin_raid", "raid", true);
		return super.getSortString();
	}
	
	@Override
	public String getName() {
		String base = Misc.ucFirst(getFaction().getPersonNamePrefix()) + " " + StringHelper.getString("exerelin_raid", "raid", true);
		if (isEnding()) {
			if (isSendingUpdate() && failStage >= 0) {
				return base + " - " + StringHelper.getString("failed");
			}
			// action can be null if failure to find a jump point causes init() to exit prematurely
			if (action != null && action.getStatus() == RaidStageStatus.SUCCESS)
				return base + " - " + StringHelper.getString("successful", true);
			return base + " - " + StringHelper.getString("over", true);
		}
		return base;
	}
			
	@Override
	public String getIcon() {
		//return Global.getSettings().getSpriteName("intel", "nex_invasion");
		return faction.getCrest();
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		for (FactionAPI faction : getTargetFactions()) {
			tags.add(faction.getId());
			if (faction.isPlayerFaction()) tags.add(Tags.INTEL_COLONIES);
		}
		return tags;
	}
}
