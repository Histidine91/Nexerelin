package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.campaign.intel.fleets.NexReturnStage;
import exerelin.campaign.intel.fleets.NexTravelStage;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseStrikeIntel extends NexRaidIntel {
	
	public BaseStrikeIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(attacker, from, target, fp, orgDur);
	}
	
	@Override
	public void init() {
		log.info("Creating base strike intel");
		
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
		
		action = new BaseStrikeActionStage(this, target);
		action.setAbortFP(fp * successMult);
		addStage(action);

		addStage(new NexReturnStage(this));

		int nexIntelQueued = NexConfig.nexIntelQueued;
		switch (nexIntelQueued) {
			case 0:
				addIntelIfNeeded();
				break;

			case 1:
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
				Global.getSector().getCampaignUI().addMessage("Switch statement within init(), in BaseStrikeIntel, " +
						"defaulted. This is not supposed to happen. If your nexIntelQueued setting within ExerelinConfig " +
						"is below 0 or above 2, that is the likely cause. Otherwise, please contact the mod author!");
		}
	}
	
	// Same as OffensiveFleetIntel's one
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad){
		Color h = Misc.getHighlightColor();
		FactionAPI other = targetFaction;
		
		info.addPara(StringHelper.getString("faction", true) + ": " + faction.getDisplayName(), initPad, tc,
				 	 faction.getBaseUIColor(), faction.getDisplayName());
		initPad = 0f;
		
		if (outcome == null)
		{
			String str = StringHelper.getStringAndSubstituteToken("nex_fleetIntel",
					"bulletTarget", "$targetFaction", other.getDisplayName());
			info.addPara(str, initPad, tc,
						 other.getBaseUIColor(), other.getDisplayName());
		}
		
		if (getListInfoParam() == ENTERED_SYSTEM_UPDATE) {
			addArrivedBullet(info, tc, initPad);
			return;
		}
		
		if (outcome != null) {
			addOutcomeBullet(info, tc, initPad);
		} else {
			info.addPara(target.getName(), tc, initPad);
		}
		initPad = 0f;
		addETABullet(info, tc, h, initPad);
	}
	
	// intel long description in intel screen
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		//super.createSmallDescription(info, width, height);
		
		Color h = Misc.getHighlightColor();
		float opad = 10f;
		
		info.addImage(getFactionForUIColors().getLogo(), width, 128, opad);
		
		FactionAPI attacker = getFaction();
		FactionAPI defender = targetFaction;
		String locationName = target.getContainingLocation().getNameWithLowercaseType();
		boolean known = Global.getSettings().isDevMode() || target.getPrimaryEntity().isVisibleToPlayerFleet();
		
		String strDesc = getRaidStrDesc();
		
		String string = StringHelper.getString("nex_baseStrike", "intelDesc");
		String attackerName = attacker.getDisplayNameWithArticle();
		String defenderName = defender.getPersonNamePrefix();
		int numFleets = (int) getOrigNumFleets();
		
		Map<String, String> sub = new HashMap<>();
		sub.put("$theFaction", attackerName);
		sub.put("$TheFaction", Misc.ucFirst(attackerName));
		sub.put("$targetFaction", defenderName);
		sub.put("$TargetFaction", Misc.ucFirst(defenderName));
		sub.put("$market", target.getName());
		sub.put("$isOrAre", attacker.getDisplayNameIsOrAre());
		sub.put("$location", locationName);
		sub.put("$strDesc", strDesc);
		sub.put("$numFleets", numFleets + "");
		sub.put("$fleetsStr", numFleets > 1 ? StringHelper.getString("fleets") : StringHelper.getString("fleet"));
		string = StringHelper.substituteTokens(string, sub);
		
		LabelAPI label = info.addPara(string, opad);
		label.setHighlight(attacker.getDisplayNameWithArticleWithoutArticle(), 
				defenderName, locationName, strDesc, numFleets + "");
		label.setHighlightColors(attacker.getBaseUIColor(), defender.getBaseUIColor(), h, h, h);
		
		if (Global.getSettings().isDevMode()) {
			float fpRound = Math.round(fp);
			float baseFP = Math.round(InvasionFleetManager.getWantedFleetSize(getFaction(), target, 0, false));
			info.addPara("DEBUG: The strike force's starting FP is " + fpRound 
					+ ". At current strength, the base FP desired for the target is approximately " 
					+ baseFP + ".", opad, Misc.getHighlightColor(), fpRound + "", baseFP + "");
		}
		
		if (outcome == null) {
			addStandardStrengthComparisons(info, target, targetFaction, false, false, 
					getForceType(), getActionName());
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
	public String getSortString() {
		return StringHelper.getString("nex_baseStrike", "strike", true);
	}
	
	@Override
	public String getActionName() {
		return StringHelper.getString("nex_baseStrike", "strike");
	}
	
	@Override
	public String getActionNameWithArticle() {
		return StringHelper.getString("nex_baseStrike", "theStrike");
	}
	
	@Override
	public String getForceType() {
		return StringHelper.getString("nex_baseStrike", "strikeForce");
	}
	
	@Override
	public String getForceTypeWithArticle() {
		return StringHelper.getString("nex_baseStrike", "theStrikeForce");
	}
	
	@Override
	public String getForceTypeHasOrHave() {
		return StringHelper.getString("nex_baseStrike", "forceHasOrHave");
	}
	
	@Override
	public String getForceTypeIsOrAre() {
		return StringHelper.getString("nex_baseStrike", "forceIsOrAre");
	}
	
	@Override
	public String getName() {
		String base = StringHelper.getString("nex_baseStrike", "intelTitle");
		base = StringHelper.substituteToken(base, "$action", getActionName(), true);
		base = StringHelper.substituteToken(base, "$market", target.getName());
		base = StringHelper.substituteToken(base, "$location", target.getContainingLocation().getNameWithTypeIfNebula());
		
		if (isEnding()) {
			if (outcome == OffensiveOutcome.SUCCESS) {
				return base + " - " + StringHelper.getString("successful", true);
			}
			else if (outcome != null && outcome.isFailed()) {
				return base + " - " + StringHelper.getString("failed", true);
			}
			return base + " - " + StringHelper.getString("over", true);
		}
		return base;
	}
	
	@Override
	public void checkForTermination() {
		if (outcome != null) return;
		
		// source captured before launch
		if (getCurrentStage() <= 0 && from.getFaction() != faction) {
			terminateEvent(OffensiveOutcome.FAIL);
		}
		else if (!target.isInEconomy()) {
			if (currentStage >= 2) {	// travel, action, or return stages
				// do nothing, let action stage code handle it
				// needs to cover travel stage as well, because fleet elements can arrive and destroy the base before we get there
			}
			else
				terminateEvent(OffensiveOutcome.MARKET_NO_LONGER_EXISTS);
		}
		else if (isAbortIfNonHostile() && !faction.isHostileTo(target.getFaction())) {
			terminateEvent(OffensiveOutcome.NO_LONGER_HOSTILE);
		}
	}

	@Override
	public boolean isAbortIfNonHostile() {
		return false;
	}

	@Override
	protected List<FactionAPI> getTargetFactions() {
		return new ArrayList<>(Arrays.asList(this.targetFaction));
	}

	@Override
	public InvasionFleetManager.EventType getEventType() {
		return InvasionFleetManager.EventType.BASE_STRIKE;
	}
}
