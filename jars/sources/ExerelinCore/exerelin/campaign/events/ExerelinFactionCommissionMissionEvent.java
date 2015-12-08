package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventManagerAPI;
import com.fs.starfarer.api.campaign.events.EventProbabilityAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.events.FactionHostilityEvent;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Events;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.missions.FactionCommissionMissionEvent;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.missions.SSP_FactionCommissionMissionEvent;

public class ExerelinFactionCommissionMissionEvent extends FactionCommissionMissionEvent {
	
	public static final String CUSTOM_COMMISSION_IGNORE_BEING_HOSTILE = "commissionIgnoreBeingHostile";
	public static final String CUSTOM_COMMISSION_IGNORE_NOT_BEING_HOSTILE = "commissionIgnoreNotBeingHostile";
	
	protected boolean ended = false;
	private IntervalUtil monthly = new IntervalUtil(25f, 35f);
	
	// same as vanilla one except public
	public void endEvent() {
		ended = true;
		
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().unset(MemFlags.FCM_FACTION);
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().unset(MemFlags.FCM_EVENT);
	}
	
	@Override
	public boolean isDone() {
		return ended;
	}
	
	// same as vanilla one except public
	@Override
	public SectorEntityToken findMessageSender() {
		WeightedRandomPicker<MarketAPI> military = new WeightedRandomPicker<MarketAPI>();
		WeightedRandomPicker<MarketAPI> nonMilitary = new WeightedRandomPicker<MarketAPI>();
		
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (market.getFaction() != faction) continue;
			if (market.getPrimaryEntity() == null) continue;
			
			float dist = Misc.getDistanceToPlayerLY(market.getPrimaryEntity());
			float weight = Math.max(1f, 10f - dist);
			if (market.hasCondition(Conditions.HEADQUARTERS) ||
					market.hasCondition(Conditions.REGIONAL_CAPITAL) ||
					market.hasCondition(Conditions.MILITARY_BASE)) {
				military.add(market, weight);
			} else {
				nonMilitary.add(market, weight);
			}
		}
		
		MarketAPI pick = military.pick();
		if (pick == null) pick = nonMilitary.pick();
		
		if (pick != null) return pick.getPrimaryEntity();
		return Global.getSector().getPlayerFleet();
	}
	
	// taken from SS+ (ignores famous bounties etc.)
	@Override
	public void advance(float amount) {
		if (!isEventStarted()) {
			return;
		}
		if (isDone()) {
			return;
		}

		float days = Global.getSector().getClock().convertToDays(amount);

		monthly.advance(days);
		if (monthly.intervalElapsed()) {
			FactionAPI player = Global.getSector().getPlayerFaction();
			for (FactionAPI fac : Global.getSector().getAllFactions()) {
				otherFaction = fac;
				if (this.faction.isHostileTo(otherFaction) && !player.isHostileTo(otherFaction)) {
					if (otherFaction.getCustomBoolean(CUSTOM_COMMISSION_IGNORE_NOT_BEING_HOSTILE)) {
						continue;
					}
					log.info("Rep drop for not being hostile to " + otherFaction.getDisplayName());
					Global.getSector().reportEventStage(this, "rep_drop_non_hostile", findMessageSender(), MessagePriority.ENSURE_DELIVERY,
														new BaseOnMessageDeliveryScript() {
															@Override
															public void beforeDelivery(CommMessageAPI message) {
																Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(
																				CoreReputationPlugin.RepActions.COMMISSION_PENALTY_NON_HOSTILE_TO_ENEMY, null, message, true),
																										  ExerelinFactionCommissionMissionEvent.this.faction.getId());
															}
														});
					continue;
				}
				if (!this.faction.isHostileTo(otherFaction) && player.isHostileTo(otherFaction)) {
					if (otherFaction.getCustomBoolean(CUSTOM_COMMISSION_IGNORE_BEING_HOSTILE)) {
						continue;
					}
					log.info("Rep drop for being hostile to " + otherFaction.getDisplayName());
					Global.getSector().reportEventStage(this, "rep_drop_hostile", findMessageSender(), MessagePriority.ENSURE_DELIVERY,
														new BaseOnMessageDeliveryScript() {
															private final FactionAPI nonHostileFaction = ExerelinFactionCommissionMissionEvent.this.otherFaction;

															@Override
															public void beforeDelivery(CommMessageAPI message) {
																Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(
																				CoreReputationPlugin.RepActions.COMMISSION_PENALTY_HOSTILE_TO_NON_ENEMY, null, message, true),
																										  ExerelinFactionCommissionMissionEvent.this.faction.getId());

																CampaignEventManagerAPI eventManager = Global.getSector().getEventManager();
																FactionHostilityEvent.FactionHostilityPairKey key = new FactionHostilityEvent.FactionHostilityPairKey(
																								ExerelinFactionCommissionMissionEvent.this.faction,
																								nonHostileFaction);
																EventProbabilityAPI ep = eventManager.getProbability(Events.FACTION_HOSTILITY, key);

																FactionAPI player = Global.getSector().getPlayerFaction();
																float rel = player.getRelationship(nonHostileFaction.getId());
																float increase = Math.abs(rel) * 0.25f;
																if (increase > 0) {
																	ep.increaseProbability(increase);
																	log.info(String.format(
																					"Monthly - increasing faction hostility probability %s -> %s, by %s, is now %s",
																					ExerelinFactionCommissionMissionEvent.this.faction.getDisplayName(),
																					nonHostileFaction.getDisplayName(), "" + increase,
																					"" + ep.getProbability()));
																}
															}
														});
				}
			}
		}

		RepLevel level = faction.getRelToPlayer().getLevel();
		if (!level.isAtWorst(RepLevel.NEUTRAL)) {
			endEvent();
			Global.getSector().reportEventStage(this, "annul", findMessageSender(),
												MessagePriority.ENSURE_DELIVERY,
												new BaseOnMessageDeliveryScript() {
													@Override
													public void beforeDelivery(CommMessageAPI message) {
													}
												});
		}
	}
	
	@Override
	protected Object readResolve() {
		if (monthly == null) {
			monthly = new IntervalUtil(25f, 35f);
		}
		return this;
	}
}
