package exerelin.campaign.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import org.lazywizard.lazylib.MathUtils;

// derived from SystemBountyEvent
public class FactionBountyEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(FactionBountyEvent.class);
	
	protected static final String STRING_HELPER_CAT = "exerelin_missions";
	protected static final MessagePriority MESSAGE_PRIORITY = MessagePriority.SECTOR;
	public static final float BOUNTY_PAYMENT_MULT = 1;
	
	public static final float PROBABILITY_INHOSPITABLE = 0;	// for now
	public static final float PROBABILITY_HOSTILE = 0.025f;
	public static final float PROBABILITY_VENGEFUL = 0.06f;
	public static final float PROBABILITY_PER_FP = 0.004f;
	//public static final float PROBABILITY_MARKET_SIZE_MULT = 0.01f;	// meh
	
	protected float elapsedDays = 0f;
	protected float duration = 60f;
	protected IntervalUtil interval = new IntervalUtil(1, 1);
	
	protected float baseBounty = 0;
	protected int lastBounty = 0;
	protected FactionBountyPairKey target;
	protected FactionAPI enemyFaction;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget, false);
	}
	
	@Override
	public void startEvent() {
		super.startEvent(true);
		if (!(eventTarget.getCustom() instanceof FactionBountyPairKey)) {
			endEvent();
			return;
		}
		
		target = (FactionBountyPairKey) eventTarget.getCustom();
		faction = target.one;
		enemyFaction = target.two;
		
		float mult = 1 + 0.05f * MathUtils.getRandomNumberInRange(-3, 4);
		baseBounty = Global.getSettings().getFloat("baseSystemBounty") * mult * BOUNTY_PAYMENT_MULT;
		
		log.info(String.format("Starting faction bounty for [%s], %d credits per frigate", faction.getDisplayName(), (int) baseBounty));
		
		MarketAPI sender = pickMarket();
		if (sender == null)
		{
			endEvent();
			return;
		}
		
		Global.getSector().reportEventStage(this, "start", sender.getPrimaryEntity(), MESSAGE_PRIORITY);
	}
	
	protected MarketAPI pickMarket()
	{
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
		List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(faction.getId());
		for (MarketAPI market : markets)
		{
			float weight = market.getSize();
			if (market.hasCondition(Conditions.DECIVILIZED))
				weight *= 0.01f;
			if (market.hasCondition(Conditions.HEADQUARTERS))
				weight *= 4;
			if (market.hasCondition(Conditions.MILITARY_BASE))
				weight *= 3;
			if (market.hasCondition(Conditions.REGIONAL_CAPITAL))
				weight *= 3;
			picker.add(market, weight);
		}
		return picker.pick();
	}
	
	@Override
	public void advance(float amount) {
		if (!isEventStarted()) return;
		if (isDone()) return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
		
		// check whether event should be terminated prematurely
		interval.advance(days);
		if (interval.intervalElapsed())
		{
			List<String> liveFactions = SectorManager.getLiveFactionIdsCopy();
			if (!liveFactions.contains(faction.getId()))
			{
				Global.getSector().reportEventStage(this, "end_faction_dead", 
						Global.getSector().getPlayerFleet(), MessagePriority.ENSURE_DELIVERY);
				endEvent();
			}
			else if (!liveFactions.contains(enemyFaction.getId()))
			{
				Global.getSector().reportEventStage(this, "end_enemyfaction_dead", 
						Global.getSector().getPlayerFleet(), MessagePriority.ENSURE_DELIVERY);
				endEvent();
			}
			else if (!faction.isHostileTo(enemyFaction))
			{
				Global.getSector().reportEventStage(this, "end_peace", 
						Global.getSector().getPlayerFleet(), MessagePriority.ENSURE_DELIVERY);
				endEvent();
			}
		}
		
		elapsedDays += days;

		if (elapsedDays >= duration && !isDone()) {
			endEvent();
		}
	}
	
	protected boolean ended = false;
	public void endEvent() {
		log.info(String.format("Ending faction bounty for [%s] against [%s]", faction.getDisplayName(), enemyFaction.getDisplayName()));
		ended = true;
	}
	

	@Override
	public boolean isDone() {
		return ended;
	}

	// same as SystemBountyEvent except no enemyFaction null check + own $standing and faction tokens + refactor
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		
		StringHelper.addFactionNameTokensCustom(map, "faction", faction);
		
		RepLevel level = Global.getSector().getPlayerFaction().getRelationshipLevel(faction);
		map.put("$standing", level.getDisplayName().toLowerCase());
		map.put("$Standing", Misc.ucFirst(level.getDisplayName()));
		
		// this is ok because the token replacement happens right as the message is sent, not when it's received
		// so the lastBounty is the correct value for the message
//		map.put("$bountyCredits", "" + lastBounty);
//		map.put("$baseBounty", "" + (int) baseBounty);
		map.put("$bountyCredits", "" + Misc.getWithDGS(lastBounty));
		map.put("$baseBounty", "" + Misc.getWithDGS(baseBounty));
		//map.put("$sender", map.get("$marketFaction") + " administration on " + market.getName());
		map.put("$sender", map.get("$marketFaction"));
		
		map.put("$daysLeft", "" + (int) + Math.max(1, duration - elapsedDays));
		
		StringHelper.addFactionNameTokensCustom(map, "enemyFaction", enemyFaction);
		
		return map;
	}

	// same as SystemBountyEvent
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<String>();
		if (stageId != null && (stageId.equals("bounty_payment") || stageId.equals("bounty_payment_share"))) {
			addTokensToList(result, "$bountyCredits");
		}
		if (stageId != null && stageId.equals("start_with_faction") && enemyFaction != null) {
			addTokensToList(result, "$daysLeft");
			addTokensToList(result, "$enemyFaction");
			addTokensToList(result, "$baseBounty");
		}
		
		if (stageId != null && stageId.equals("start")) {
			addTokensToList(result, "$daysLeft");
			addTokensToList(result, "$baseBounty");
		}
		
		if (stageId != null && stageId.equals("condition")) {
			addTokensToList(result, "$baseBounty");
			addTokensToList(result, "$daysLeft");
		}
		
		if (stageId != null && (
				stageId.equals("bounty_payment") ||
				stageId.equals("bounty_payment_share") ||
				stageId.equals("bounty_no_payment") ||
				stageId.equals("bounty_no_rep"))
				) {
			addTokensToList(result, "$daysLeft");
		}
		return result.toArray(new String[0]);
	}
	
	@Override
	public String getEventName() {
		String factionName = Misc.ucFirst(faction.getDisplayName());
		String enemyFactionName = enemyFaction.getDisplayName();
		
		String text = Misc.ucFirst(StringHelper.getString(STRING_HELPER_CAT, "factionBounty"));
		text = StringHelper.substituteToken(text, "$faction", factionName);
		text = StringHelper.substituteToken(text, "$enemyFaction", enemyFactionName);
		text = StringHelper.substituteToken(text, "$bounty", Misc.getWithDGS(baseBounty) + Strings.C);
		
		if (isDone()) {
			return text + " - " + StringHelper.getString(STRING_HELPER_CAT, "over"); 
		}
		
		int daysLeft = (int) (duration - elapsedDays);
		String daysLeftStr = daysLeft < 1 ? "<1" : daysLeft + "";
		String days = "";
		if (daysLeft > 0) {
			days = ", " + StringHelper.getStringAndSubstituteToken(STRING_HELPER_CAT, "daysLeft", 
				"$days", daysLeftStr);
		}
		
		return text + days;
	}
	
	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.BOUNTY;
	}

	// same as SystemBountyEvent except with a faction check instead of system check + formatting
	@Override
	public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
		if (!isEventStarted()) return;
		
		if (!battle.isPlayerInvolved()) return;
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
				
		lastBounty = 0;
		float fpDestroyed = 0;
		for (CampaignFleetAPI otherFleet : battle.getNonPlayerSide()) {
			if (otherFleet.getFaction() != enemyFaction) continue;
			
			float bounty = 0;
			for (FleetMemberAPI loss : Misc.getSnapshotMembersLost(otherFleet)) {
				float mult = Misc.getSizeNum(loss.getHullSpec().getHullSize());
				bounty += mult * baseBounty;
				fpDestroyed += loss.getFleetPointCost();
			}
			
			lastBounty += (int) (bounty * battle.getPlayerInvolvementFraction());
		}
	
		if (lastBounty > 0) {
			//bounty_no_payment bounty_no_rep
			//RepLevel level = playerFleet.getFaction().getRelationshipLevel(otherFleet.getFaction());
			MarketAPI sender = pickMarket();
			if (sender == null)	// faction annihilated?
			{
				endEvent();
				return;
			}
			
			RepLevel level = faction.getRelationshipLevel(Factions.PLAYER);
			final int payment = lastBounty;
			final float repFP = (int)(fpDestroyed * battle.getPlayerInvolvementFraction());
			if (level.isAtWorst(RepLevel.SUSPICIOUS)) {
				String reportId = "bounty_payment";
				if (battle.getPlayerInvolvementFraction() < 1) {
					reportId = "bounty_payment_share";
				}
				log.info(String.format("Paying bounty of %d from faction [%s]", (int) lastBounty, faction.getDisplayName()));
				Global.getSector().reportEventStage(this, reportId, sender.getPrimaryEntity(),
											MessagePriority.ENSURE_DELIVERY,
							new BaseOnMessageDeliveryScript() {
					public void beforeDelivery(CommMessageAPI message) {
						CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
						playerFleet.getCargo().getCredits().add(payment);
						
						if (repFP > 0) {
							Global.getSector().adjustPlayerReputation(
									new RepActionEnvelope(RepActions.SYSTEM_BOUNTY_REWARD, repFP, message, true), 
									faction.getId());
						}
					}
				});
			} else if (level.isAtWorst(RepLevel.HOSTILE)) {
				log.info(String.format("Not paying bounty, but improving rep with faction [%s]", faction.getDisplayName()));
				Global.getSector().reportEventStage(this, "bounty_no_payment", playerFleet,
											MessagePriority.ENSURE_DELIVERY,
							new BaseOnMessageDeliveryScript() {
					public void beforeDelivery(CommMessageAPI message) {
						if (repFP > 0) {
							Global.getSector().adjustPlayerReputation(
									new RepActionEnvelope(RepActions.SYSTEM_BOUNTY_REWARD, repFP, message, true), 
									faction.getId());
						}
					}
				});
			} else {
				log.info(String.format("Not paying bounty or improving rep with faction [%s]", faction.getDisplayName()));
				Global.getSector().reportEventStage(this, "bounty_no_rep", playerFleet,
											MessagePriority.ENSURE_DELIVERY);
			}
		}
	}
	
	@Override
	public String getEventIcon() {
		return enemyFaction.getCrest();
	}
	
	@Override
	public String getCurrentImage() {
		return enemyFaction.getLogo();
	}
	
	
	public static class FactionBountyPairKey {
		protected FactionAPI one;
		protected FactionAPI two;
		public FactionBountyPairKey(FactionAPI one, FactionAPI two) {
			this.one = one;
			this.two = two;
		}
		public FactionBountyPairKey(String one, String two) {
			this(Global.getSector().getFaction(one), Global.getSector().getFaction(two));
		}
		public FactionAPI getOne() {
			return one;
		}
		public FactionAPI getTwo() {
			return two;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((one == null) ? 0 : one.hashCode());
			result = prime * result + ((two == null) ? 0 : two.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			FactionBountyPairKey other = (FactionBountyPairKey) obj;
			return (one == other.one && two == other.two);
		}
	}
}