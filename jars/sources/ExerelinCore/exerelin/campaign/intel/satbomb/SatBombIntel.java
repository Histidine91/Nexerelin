package exerelin.campaign.intel.satbomb;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.*;
import exerelin.campaign.intel.invasion.InvActionStage;
import exerelin.campaign.intel.raid.NexRaidAssembleStage;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SatBombIntel extends OffensiveFleetIntel {

	public static final String FACTION_MEMORY_KEY = "$nex_satBombCount";
	
	protected boolean isVengeance;
	protected boolean isVirusBomb;
		
	public SatBombIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, 
			float fp, float orgDur) {
		super(attacker, from, target, fp, orgDur);
	}
		
	@Override
	public void init() {
		log.info("Creating saturation bomb intel");
		
		if (faction.getId().equals("vic")) {
			isVirusBomb = Math.random() < Global.getSettings().getFloat("nex_vicVirusBombChance");
		}
		
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
		
		action = new SatBombActionStage(this, target);
		action.setAbortFP(fp * successMult);
		addStage(action);
		
		addStage(new NexReturnStage(this));

		int nexIntelQueued = NexConfig.nexIntelQueued; //TODO: sat bombs are extreme. some players might want it added but everything else queued, do that?
		switch (nexIntelQueued) {

			case 0:

			case 1:

				addIntelIfNeeded();
				break;

			case 2:

				Global.getSector().getIntelManager().queueIntel(this);
				intelQueuedOrAdded = true;
				break;

			default:

				addIntelIfNeeded();
				Global.getSector().getCampaignUI().addMessage("Switch statement within init(), in SatBombIntel, " +
						"defaulted. This is not supposed to happen. If your nexIntelQueued setting within ExerelinConfig " +
						"is below 0 or above 2, that is the likely cause. Otherwise, please contact the mod author!");
		}
	}
	
	public boolean isVengeance() {
		return isVengeance;
	}
	
	public void setVengeance(boolean veng) {
		isVengeance = veng;
	}
	
	@Override
	public boolean shouldMakeImportantIfTargetingPlayer() {
		return true;
	}
	
	public boolean isVicVirusBomb() {
		return isVirusBomb;
	}

	@Override
	public String getType() {
		return "satbomb";
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
		float tanker = getWantedTankerFP(myFP, distance, random);
		float transport = 0;
		float freighter = getWantedFreighterFP(myFP, random);
		
		// Prometheus is 12 FP and has 2500 fuel
		// so estimate we need 12 FP of fuel for each point of ground defense the target has
		float defenderStrength = InvasionRound.getDefenderStrength(target, 1f);
		float bonus = (defenderStrength/2500) * 12 * (1.5f + random.nextFloat() * 0.5f);
		float maxBonus = myFP * (0.4f + random.nextFloat() * 0.2f);
		
		// no, fuck it, I can't get the values right
		//log.info("Sat bomb: Desired bonus " + bonus + ", max " + maxBonus);
		//if (bonus > maxBonus) bonus = maxBonus;
		bonus = maxBonus;
		
		if (isVicVirusBomb()) {
			freighter += bonus;
		} else {
			tanker += bonus;
		}
		
		
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
		if (!useMarketFleetSizeMult)
			params.ignoreMarketFleetSizeMult = true;
		
		params.modeOverride = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;
		params.qualityOverride = this.qualityOverride;
		
		if (route != null) {
			params.timestamp = route.getTimestamp();
		}
		params.random = random;
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		
		if (fleet == null || fleet.isEmpty()) return null;
		
		fleet.setName(InvasionFleetManager.getFleetName(extra.fleetType, factionId, totalFp));
		
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		// makes it not piss around the system instead of heading to objective, see http://fractalsoftworks.com/forum/index.php?topic=5061.msg263438#msg263438
		fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_NO_MILITARY_RESPONSE, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);	// needed to do raids
		
		if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
		}
		
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);
		
		String postId = Ranks.POST_PATROL_COMMANDER;
		String rankId = Ranks.SPACE_CAPTAIN;	//isInvasionFleet ? Ranks.SPACE_ADMIRAL : Ranks.SPACE_COMMANDER;
		
		fleet.getCommander().setPostId(postId);
		fleet.getCommander().setRankId(rankId);
		
		
		if (isVicVirusBomb()) {
			float payload = Math.min(defenderStrength/8, fleet.getCargo().getMaxCapacity()/8);
			fleet.getCargo().addCommodity(Commodities.ORGANICS, payload);
		} else {
			float payload = Math.min(defenderStrength/8, fleet.getCargo().getMaxFuel()/8);
			fleet.getCargo().addCommodity(Commodities.FUEL, payload);
		}
		
		log.info("Created fleet " + fleet.getName() + " of strength " + fleet.getFleetPoints() + "/" + totalFp);
		
		return fleet;
	}
	
	@Override
	public RouteFleetAssignmentAI createAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route) {
		RaidAssignmentAINoWander raidAI = new RaidAssignmentAINoWander(this, fleet, route, (InvActionStage)action);
		return raidAI;
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
		
		String string = StringHelper.getString("nex_satbomb", "intelDesc");
		String attackerName = attacker.getDisplayNameWithArticle();
		String defenderName = defender.getDisplayNameWithArticle();
		String actionName = getActionName();
		int numFleets = (int) getOrigNumFleets();
				
		Map<String, String> sub = new HashMap<>();
		sub.put("$theFaction", attackerName);
		sub.put("$TheFaction", Misc.ucFirst(attackerName));
		sub.put("$theTargetFaction", defenderName);
		sub.put("$TheTargetFaction", Misc.ucFirst(defenderName));
		sub.put("$action", actionName);
		sub.put("$market", target.getName());
		sub.put("$isOrAre", attacker.getDisplayNameIsOrAre());
		sub.put("$location", locationName);
		sub.put("$strDesc", strDesc);
		sub.put("$numFleets", numFleets + "");
		sub.put("$fleetsStr", numFleets > 1 ? StringHelper.getString("fleets") : StringHelper.getString("fleet"));
		string = StringHelper.substituteTokens(string, sub);
		
		LabelAPI label = info.addPara(string, opad);
		label.setHighlight(attacker.getDisplayNameWithArticleWithoutArticle(), actionName, target.getName(), 
				defender.getDisplayNameWithArticleWithoutArticle(), strDesc, numFleets + "");
		label.setHighlightColors(attacker.getBaseUIColor(), Misc.getNegativeHighlightColor(), h, defender.getBaseUIColor(), h, h);
		
		if (isVengeance) {
			string = StringHelper.getString("nex_satbomb", "intelDescVengeance");
			string = StringHelper.substituteToken(string, "$playerName", Global.getSector().getPlayerPerson().getNameString());
			info.addPara(string, opad);
		}
		
		if (outcome == null) {
			addStandardStrengthComparisons(info, target, targetFaction, false, true, 
					getForceType(), StringHelper.getString("nex_satbomb", "expeditionForcePossessive"));
		}

		addStrategicActionInfo(info, width);
		
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
		
		for (RaidStage stage : stages) {
			stage.showStageInfo(info);
			if (getStageIndex(stage) == failStage) break;
		}
	}
	
	@Override
	public String getActionName() {
		String id = "expedition";
		if (isVicVirusBomb()) id += "Vic";
		return StringHelper.getString("nex_satbomb", id);
	}
	
	@Override
	public String getActionNameWithArticle() {
		String id = "theExpedition";
		if (isVicVirusBomb()) id += "Vic";
		return StringHelper.getString("nex_satbomb", id);
	}
	
	@Override
	public String getForceType() {
		return StringHelper.getString("nex_satbomb", "expeditionForce");
	}
	
	@Override
	public String getForceTypeWithArticle() {
		return StringHelper.getString("nex_satbomb", "theExpeditionForce");
	}
	
	@Override
	public String getForceTypeHasOrHave() {
		return StringHelper.getString("nex_satbomb", "forceHasOrHave");
	}
	
	@Override
	public String getForceTypeIsOrAre() {
		return StringHelper.getString("nex_satbomb", "forceIsOrAre");
	}
			
	@Override
	public String getIcon() {
		if (isVicVirusBomb()) return Global.getSettings().getSpriteName("nex_vicVbombing", "nex_vicVbombingIcon");
		return Global.getSettings().getSpriteName("intel", "nex_satbomb");
	}
	
	@Override
	public String getCommMessageSound() {
		if (isPlayerTargeted()) {
			return "nex_alarm";
		}
		return super.getCommMessageSound();
	}

	@Override
	public InvasionFleetManager.EventType getEventType() {
		return InvasionFleetManager.EventType.SAT_BOMB;
	}
	
	public static void createDebugEvent(MarketAPI source, MarketAPI dest, float fp, float orgDur){
		SatBombIntel intel = new SatBombIntel(source.getFaction(), source, dest, fp, orgDur);
		intel.init();
	}
}
