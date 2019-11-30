package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidAssignmentAI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Random;
import java.util.Set;

public class SpecialForcesIntel extends BaseIntelPlugin implements RouteFleetSpawner {
	
	public static final String SOURCE_ID = "nex_specialForces";
	
	protected MarketAPI origin;
	protected FactionAPI faction;
	protected float startingFP;
	protected int segmentNum = 1;
	protected RouteData route;
	protected String fleetName;
	protected PersonAPI commander;
	
	/*runcode 
	MarketAPI market = Global.getSector().getEconomy().getMarket("jangala");
	FactionAPI faction = market.getFaction();
	new exerelin.campaign.intel.specialforces.SpecialForcesIntel(market, faction, 200).init(null);
	*/

	public SpecialForcesIntel(MarketAPI origin, FactionAPI faction, float startingFP) 
	{
		this.origin = origin;
		this.faction = faction;
		this.startingFP = startingFP;
	}
	
	public void init(PersonAPI commander) {
		this.commander = commander;
		
		OptionalFleetData extra = new OptionalFleetData(origin);
		extra.factionId = faction.getId();
		extra.fp = startingFP;
		extra.fleetType = "nex_specialForces";
		
		Long seed = new Random().nextLong();

		route = RouteManager.getInstance().addRoute(SOURCE_ID, origin, seed, extra, this);
		float orbitDays = startingFP * 0.1f * (0.75f + (float) Math.random() * 0.5f);

		route.addSegment(new RouteSegment(orbitDays, origin.getPrimaryEntity()));
		segmentNum++;
		
		Global.getSector().getIntelManager().queueIntel(this);
	}
	
	@Override
	public CampaignFleetAPI spawnFleet(RouteData route) 
	{
		MarketAPI market = route.getMarket();
		CampaignFleetAPI fleet = createFleet(route);
		
		if (fleet == null || fleet.isEmpty()) return null;
		
		market.getContainingLocation().addEntity(fleet);
		fleet.setFacing((float) Math.random() * 360f);
		// this will get overridden by the assignment AI, depending on route-time elapsed etc
		fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().x);
		
		// TODO
		fleet.addScript(createAssignmentAI(fleet, route));
		
		return fleet;
	}
	
	public CampaignFleetAPI createFleet(RouteData thisRoute) {
		float fp = thisRoute.getExtra().fp;
		
		FleetParamsV3 params = new FleetParamsV3(
				origin,
				null, // locInHyper
				faction.getId(),
				thisRoute.getQualityOverride(), // qualityOverride
				"nex_specialForces",
				fp, // combatPts
				fp * 0.25f, // freighterPts 
				fp * 0.25f, // tankerPts
				fp * 0.1f, // transportPts
				0, // linerPts
				fp * 0.1f, // utilityPts
				0.25f
		);
		params.timestamp = thisRoute.getTimestamp();
		params.officerLevelBonus = 3;
		params.officerNumberMult = 1.5f;
		params.random = new Random(thisRoute.getSeed());
		params.ignoreMarketFleetSizeMult = true;
		
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		
		if (commander != null) {
			// TODO: apply commander to fleet
		}
		else {
			commander = fleet.getCommander();
		}
		if (fleetName == null) {
			fleetName = "7th Jangala";	// TBD
		}
		
		fleet.setName(faction.getFleetTypeName("nex_specialForces") + " – " + fleetName);
		
		return fleet;
	}
	
	// TODO
	protected EveryFrameScript createAssignmentAI(CampaignFleetAPI fleet, RouteData route) {
		return new RaidAssignmentAI(fleet, route, null);
	}
	
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);
		info.addPara(getSmallDescriptionTitle(), c, 0f);
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		String str = getString("intelTitle");
		
		if (fleetName != null) {
			str += ": " + fleetName;
		}
		else
		{
			str = faction.getDisplayName() + " " + str;
		}
		
		if (isEnding() || isEnded()) {
			str += " - " + StringHelper.getString("over", true);
		}
		
		return str;
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		Color c = getFactionForUIColors().getBaseUIColor();
		Color d = getFactionForUIColors().getDarkUIColor();
		
		// Images
		if (commander != null) {
			info.addImages(width, 128, opad, opad, commander.getPortraitSprite(), faction.getCrest());
		}
		else {
			info.addImage(faction.getCrest(), 128, 128);
		}
		
		String str;
		
		// Event over?
		if (route == null || isEnding() || isEnded()) {
			str = getString("intelDescOver");
			str = StringHelper.substituteToken(str, "$faction", faction.getPersonNamePrefix());
			if (fleetName != null) str = StringHelper.substituteToken(str, "$fleetName", faction.getPersonNamePrefix());

			info.addPara(str, opad);
			return;
		}
		
		// Intro paragraph
		str = getString(fleetName != null? "intelDesc1" : "intelDesc1NoName");
		str = StringHelper.substituteToken(str, "$faction", faction.getPersonNamePrefix());
		if (fleetName != null) str = StringHelper.substituteToken(str, "$fleetName", fleetName);
		
		LabelAPI label = info.addPara(str, opad);
		if (fleetName != null) {
			label.setHighlight(fleetName);
			label.setHighlightColor(faction.getBaseUIColor());
		}
		
		// Commander info
		if (commander != null) {
			str = getString("intelDescCommander");
			str = StringHelper.substituteToken(str, "$rank", commander.getRank());
			str = StringHelper.substituteToken(str, "$name", commander.getNameString());
			
			String levelDesc = "";
			int personLevel = commander.getStats().getLevel();
			if (personLevel <= 5) {
				levelDesc = "an unremarkable officer";
			} else if (personLevel <= 10) {
				levelDesc = "a capable officer";
			} else if (personLevel <= 15) {
				levelDesc = "a highly capable officer";
			} else {
				levelDesc = "an exceptionally capable officer";
			}
			str = StringHelper.substituteToken(str, "$levelDesc", levelDesc);
			
			info.addPara(str, opad);
		}
		
		// Fleet strength
		str = getString("intelDescStr");
		int fp = Math.round(route.getActiveFleet() != null ? route.getActiveFleet().getFleetPoints() : route.getExtra().fp);
		info.addPara(str, opad, h, fp + "");
		
		// Current action
		str = getString("intelDescAction");
		String actionStr = "idling";	// TBD
		str = StringHelper.substituteToken(str, "$action", actionStr);
		info.addPara(str, opad, h, actionStr);
		
		str = getString("intelDescDebug");
		info.addPara(str, Misc.getGrayColor(), opad);
	}
	
	@Override
	protected void advanceImpl(float amount) {
		// TODO: advance the AI script
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(faction.getId());
		tags.add(getString("intelTag"));
		return tags;
	}
	
	@Override
	public String getIcon() {
		return getFactionForUIColors().getCrest();
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return faction;
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		if (route.getActiveFleet() != null) {
			StarSystemAPI sys = route.getActiveFleet().getStarSystem();
			if (sys != null) return sys.getCenter();
		}
		return null;
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_specialForces", id, ucFirst);
	}
	
	
	@Override
	public boolean isHidden() {
		return !Global.getSettings().isDevMode();
	}

	@Override
	public boolean shouldCancelRouteAfterDelayCheck(RouteData arg0) {
		return false;
	}

	@Override
	public boolean shouldRepeat(RouteData arg0) {
		return false;
	}

	@Override
	public void reportAboutToBeDespawnedByRouteManager(RouteData arg0) {
		
	}
}
