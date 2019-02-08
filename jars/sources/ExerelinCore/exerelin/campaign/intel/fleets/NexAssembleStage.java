package exerelin.campaign.intel.fleets;

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
import exerelin.campaign.intel.OffensiveFleetIntel;
import exerelin.campaign.intel.OffensiveFleetIntel.OffensiveOutcome;
import exerelin.utilities.StringHelper;
import java.awt.Color;

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
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
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
		if (!market.isInEconomy() || !market.getPrimaryEntity().isAlive() || !market.hasSpaceport()) {
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
		extra.strength = getAdjustedStrength(fp, market);
		
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
	
}
