package exerelin.campaign.intel.defensefleet;

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
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidDelegate;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.campaign.intel.fleets.NexReturnStage;
import exerelin.campaign.intel.fleets.NexTravelStage;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class DefenseFleetIntel extends OffensiveFleetIntel implements RaidDelegate {
	
	public static Logger log = Global.getLogger(DefenseFleetIntel.class);
	
	protected boolean singleTarget = true;
		
	public DefenseFleetIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(attacker, from, target, fp, orgDur);
		abortIfNonHostile = false;
	}
	
	@Override
	public void init() {
		log.info("Creating defense intel");
		
		SectorEntityToken gather = from.getPrimaryEntity();
		
		addStage(new NexOrganizeStage(this, from, orgDur));
		
		float successMult = 0.3f;
		DefenseAssembleStage assemble = new DefenseAssembleStage(this, gather);
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
		
		action = new DefenseActionStage(this, target);
		action.setAbortFP(fp * successMult);
		addStage(action);
		
		addStage(new NexReturnStage(this));

		if ((NexConfig.nexIntelQueued <= 0) || (NexConfig.nexIntelQueued == 1 && playerSpawned))
			addIntelIfNeeded();
		else {
			Global.getSector().getIntelManager().queueIntel(this);
			intelQueuedOrAdded = true;
		}
	}
	
	public void giveReturnOrders() {
		action.giveReturnOrdersToStragglers(action.getRoutes());
	}
	
	protected String getDescString() {
		return StringHelper.getString("nex_defenseFleet", "intelDesc");
	}
	
	// intel long description in intel screen
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		//super.createSmallDescription(info, width, height);
		
		Color h = Misc.getHighlightColor();
		float opad = 10f;
		
		info.addImage(getFactionForUIColors().getLogo(), width, 128, opad);
		
		FactionAPI attacker = getFaction();
		FactionAPI defender = target.getFaction();
		if (defender == attacker) defender = targetFaction;
		String locationName = target.getContainingLocation().getNameWithLowercaseType();
		
		String strDesc = getRaidStrDesc();
		
		String string = getDescString();
		String attackerName = attacker.getDisplayNameWithArticle();
		String defenderName = defender.getDisplayNameWithArticle();
		int numFleets = (int) getOrigNumFleets();
				
		Map<String, String> sub = new HashMap<>();
		sub.put("$theFaction", attackerName);
		sub.put("$TheFaction", Misc.ucFirst(attackerName));
		sub.put("$theTargetFaction", defenderName);
		sub.put("$TheTargetFaction", Misc.ucFirst(defenderName));
		sub.put("$market", target.getName());
		sub.put("$isOrAre", attacker.getDisplayNameIsOrAre());
		sub.put("$location", locationName);
		sub.put("$strDesc", strDesc);
		sub.put("$numFleets", numFleets + "");
		sub.put("$fleetsStr", numFleets > 1 ? StringHelper.getString("fleets") : StringHelper.getString("fleet"));
		string = StringHelper.substituteTokens(string, sub);
		
		LabelAPI label = info.addPara(string, opad);
		label.setHighlight(attacker.getDisplayNameWithArticleWithoutArticle(), target.getName(), 
				defender.getDisplayNameWithArticleWithoutArticle(), strDesc, numFleets + "");
		label.setHighlightColors(attacker.getBaseUIColor(), h, defender.getBaseUIColor(), h, h);
				
		info.addSectionHeading(StringHelper.getString("status", true), 
				   attacker.getBaseUIColor(), attacker.getDarkUIColor(), Alignment.MID, opad);
		
		// write our own status message for certain cancellation cases
		if (outcome == OffensiveOutcome.NO_LONGER_HOSTILE)
		{
			string = StringHelper.getString("nex_fleetIntel", "outcomeNoLongerHostile");
			string = StringHelper.substituteToken(string, "$target", target.getName());
			string = StringHelper.substituteToken(string, "$theAction", getActionNameWithArticle());
			//String factionName = target.getFaction().getDisplayName();
			//string = StringHelper.substituteToken(string, "$otherFaction", factionName);
			
			info.addPara(string, opad);
			return;
		}
		else if (outcome == OffensiveOutcome.MARKET_NO_LONGER_EXISTS)
		{
			string = StringHelper.getString("nex_fleetIntel", "outcomeNoLongerExists");
			string = StringHelper.substituteToken(string, "$target", target.getName());
			//string = StringHelper.substituteToken(string, "$theAction", getActionNameWithArticle());
			info.addPara(string, opad);
			return;
		}
		else if (outcome == OffensiveOutcome.SUCCESS)
		{
			string = StringHelper.getString("nex_defenseFleet", "intelOutcomeSuccess");
			info.addPara(string, opad);
			return;
		}
		
		for (RaidStage stage : stages) {
			stage.showStageInfo(info);
			if (getStageIndex(stage) == failStage) break;
		}
	}
	
	@Override
	public CampaignFleetAPI createFleet(String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
		if (random == null) random = new Random();
				
		RouteManager.OptionalFleetData extra = route.getExtra();
		
		float distance = NexUtilsMarket.getHyperspaceDistance(market, target);
		
		float myFP = extra.fp;
		if (!useMarketFleetSizeMult)
			myFP *= InvasionFleetManager.getFactionDoctrineFleetSizeMult(faction);
		
		float combat = myFP;
		float tanker = 0;
		if (market.getContainingLocation() != target.getContainingLocation()) {
			tanker = getWantedTankerFP(myFP, distance, random);
		}
		float transport = 0;
		float freighter = getWantedFreighterFP(myFP, random)/2;
		
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
		if (!useMarketFleetSizeMult)
			params.ignoreMarketFleetSizeMult = true;
		
		params.modeOverride = ShipPickMode.PRIORITY_THEN_ALL;
		params.qualityOverride = this.qualityOverride;
		
		if (route != null) {
			params.timestamp = route.getTimestamp();
		}
		params.random = random;
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		
		if (fleet == null || fleet.isEmpty()) return null;
		
		fleet.setName(InvasionFleetManager.getFleetName(extra.fleetType, factionId, totalFp));
		
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);
		
		String postId = Ranks.POST_FLEET_COMMANDER;
		String rankId = Ranks.SPACE_CAPTAIN;	//isInvasionFleet ? Ranks.SPACE_ADMIRAL : Ranks.SPACE_COMMANDER;
		
		fleet.getCommander().setPostId(postId);
		fleet.getCommander().setRankId(rankId);
		
		log.info("Created fleet " + fleet.getName() + " of strength " + fleet.getFleetPoints() + "/" + totalFp);
		
		return fleet;
	}
	
	@Override
	public RouteFleetAssignmentAI createAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route) {
		DefenseAssignmentAI defAI = new DefenseAssignmentAI(this, fleet, route, (DefenseActionStage)action);
		return defAI;
	}
	
	@Override
	public String getSortString() {
		return StringHelper.getString("nex_defenseFleet", "defense", true);
	}
	
	@Override
	public String getActionName() {
		return StringHelper.getString("nex_defenseFleet", "defense");
	}
	
	@Override
	public String getActionNameWithArticle() {
		return StringHelper.getString("nex_defenseFleet", "theDefense");
	}
	
	@Override
	public String getForceType() {
		return StringHelper.getString("nex_defenseFleet", "defenseForce");
	}
	
	@Override
	public String getForceTypeWithArticle() {
		return StringHelper.getString("nex_defenseFleet", "theDefenseForce");
	}
	
	@Override
	public String getForceTypeHasOrHave() {
		return StringHelper.getString("nex_defenseFleet", "forceHasOrHave");
	}
	
	@Override
	public String getForceTypeIsOrAre() {
		return StringHelper.getString("nex_defenseFleet", "forceIsOrAre");
	}

	@Override
	public String getType() {
		return "defense";
	}

	@Override
	public InvasionFleetManager.EventType getEventType() {
		return InvasionFleetManager.EventType.DEFENSE;
	}

	public static void createDebugEvent(MarketAPI source, MarketAPI dest, float fp, float orgDur){
		DefenseFleetIntel intel = new DefenseFleetIntel(source.getFaction(), source, dest, fp, orgDur);
		intel.init();
	}
}
