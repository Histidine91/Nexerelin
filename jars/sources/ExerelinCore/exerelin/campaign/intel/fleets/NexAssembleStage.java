package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import static com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage.PREP_STAGE;
import static com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage.WAIT_STAGE;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel.OffensiveOutcome;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;

public abstract class NexAssembleStage extends AssembleStage {
	
	protected OffensiveFleetIntel offFltIntel;
	
	public NexAssembleStage(OffensiveFleetIntel intel, SectorEntityToken gatheringPoint) {
		super(intel, gatheringPoint);
		offFltIntel = intel;
	}
	
	protected Object readResolve() {
		if (offFltIntel == null)
			offFltIntel = (OffensiveFleetIntel)intel;
		
		return this;
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		float opad = 10f;
		
		String key = "stageAssemble";
		boolean addLoc = false;
		
		if (isFailed(curr, index)) {
			key = "stageAssembleFail";
		} else if (curr == index) {
			if (isSourceKnown()) {
				addLoc = true;
			} else {
				key = "stageAssembleUnknown";
			}
		} else {
			return;
		}
		String str =  StringHelper.getString("nex_fleetIntel", key);
		str = StringHelper.substituteToken(str, "$theForceType", offFltIntel.getForceTypeWithArticle(), true);
		str = StringHelper.substituteToken(str, "$isOrAre", offFltIntel.getForceTypeIsOrAre());
		str = StringHelper.substituteToken(str, "$hasOrHave", offFltIntel.getForceTypeHasOrHave());
		if (addLoc)
			str = StringHelper.substituteToken(str, "$location", 
					gatheringPoint.getContainingLocation().getNameWithLowercaseType());
		info.addPara(str, opad);
	}
	
	protected boolean isFailed(int curr, int index) {
		if (status == RaidStageStatus.FAILURE)
			return true;
		if (curr == index && offFltIntel.getOutcome() == OffensiveOutcome.FAIL)
			return true;
		
		return false;
	}
	
	// replaces the adjusted strength calculation
	@Override
	protected void addRoutesAsNeeded(float amount) {
		if (spawnFP <= 0) return;
		
		float days = Misc.getDays(amount);
		
		interval.advance(days);
		if (!interval.intervalElapsed()) return;
			
		if (sources.isEmpty()) {
			status = RaidIntel.RaidStageStatus.FAILURE;
			return;
		}
		
		MarketAPI market = sources.get(currSource);
		if (!market.isInEconomy() || !market.getPrimaryEntity().isAlive() || !NexUtilsMarket.hasWorkingSpaceport(market)) {
			sources.remove(market);
			return;
		}
		
		currSource ++;
		currSource %= sources.size();
		
		
		RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(market);
		
		String sid = intel.getRouteSourceId();
		RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, intel, null);
		
		extra.fleetType = pickNextType();
		float fp = getFP(extra.fleetType);
		
		//extra.fp = Misc.getAdjustedFP(fp, market);
		extra.fp = fp;
		extra.strength = getAdjustedStrength(fp, market) * OffensiveFleetIntel.ROUTE_STRENGTH_MULT;
		
		float prepDays = 3f + 3f * (float) Math.random();
		float travelDays = RouteLocationCalculator.getTravelDays(market.getPrimaryEntity(), gatheringPoint);
		
		route.addSegment(new RouteManager.RouteSegment(prepDays, market.getPrimaryEntity(), PREP_STAGE));
		route.addSegment(new RouteManager.RouteSegment(travelDays, market.getPrimaryEntity(), gatheringPoint));
		route.addSegment(new RouteManager.RouteSegment(1000f, gatheringPoint, WAIT_STAGE));
		
		maxDays = Math.max(maxDays, prepDays + travelDays);
		//maxDays = 6f;
		
	}
	
	// same as Misc. version except with "use fleet size mult" check
	public static float getAdjustedStrength(float fp, MarketAPI market) {
		fp *= Math.max(0.25f, 0.5f + Math.min(1f, Misc.getShipQuality(market)));
		
		if (market != null) {
			float numShipsMult = 1;
			if (InvasionFleetManager.USE_MARKET_FLEET_SIZE_MULT)
				numShipsMult = market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
			else
				numShipsMult *= InvasionFleetManager.getFactionDoctrineFleetSizeMult(market.getFaction());
			fp *= numShipsMult;
			
	//		float pts = market.getFaction().getDoctrine().getNumShips() + market.getFaction().getDoctrine().getOfficerQuality();
	//		fp *= 1f + (pts - 2f) / 4f;
			float pts = market.getFaction().getDoctrine().getOfficerQuality();
			fp *= 1f + (pts - 1f) / 4f;
		}
		return fp;
	}
	
	@Override
	public boolean isSourceKnown() {
		MarketAPI market = offFltIntel.getMarketFrom();
		if (market.isHidden()) {
			return !market.getPrimaryEntity().isDiscoverable() || NexUtils.isNonPlaytestDevMode();
		}
		
		return true;
	}
	
	protected float getBaseSize() {
		float base = 120f;
		if (offFltIntel instanceof InvasionIntel || offFltIntel instanceof ColonyExpeditionIntel) 
		{
			base = 180;
		}
		if (offFltIntel.isBrawlMode())
			base *= 1.25f;
		
		base *= (3.5f/3);	// account for 33% chance of 50% larger fleet
				
		return base;
	}
	
	// Same as vanilla, but don't assume it uses fleet size multiplier
	// and get a different base size
	@Override
	protected float getLargeSize(boolean limitToSpawnFP) {
		float mult;
		if (!getSources().isEmpty()) {
			MarketAPI source = getSources().get(0);
			FactionAPI.ShipPickMode mode = Misc.getShipPickMode(source);
			//float base = source.getFaction().getApproximateMaxFPPerFleet(mode);
			float base = getBaseSize();
			
			float numShipsMult = 1;
			if (offFltIntel.useMarketFleetSizeMult)
				numShipsMult = source.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
			//else
			//	numShipsMult = InvasionFleetManager.getFactionDoctrineFleetSizeMult(offFltIntel.getFaction());
			
			if (numShipsMult < 1f) numShipsMult = 1f;
			mult = 1f / numShipsMult;
			if (limitToSpawnFP) {
				return Math.min(spawnFP, base * mult);
			}
			return base * mult;
		} else {
			return 250f;
		}
		
	}
}
