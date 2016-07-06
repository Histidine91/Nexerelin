package exerelin.campaign.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConquestMissionEvent extends BaseEventPlugin {

	protected ConquestMission mission;
	protected float elapsedDays = 0;
	protected String daysLeftStr = "";
	protected String bonusDaysLeftStr = "";
	
	protected boolean ended = false;
	
	@Override
	public void setParam(Object param) {
		mission = (ConquestMission) param;
	}
	
	@Override
	public void startEvent() {
		super.startEvent();
		
		String stageId = "accept";
		if (mission.hasBonus(elapsedDays)) {
			stageId = "accept_bonus";
		}
		Global.getSector().reportEventStage(this, stageId, mission.getAcceptLocation(), MessagePriority.ENSURE_DELIVERY);
	}
	
	protected SectorEntityToken findMessageSender() {
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
		return mission.target.getPrimaryEntity();
	}
	
	@Override
	public void advance(float amount) {
		String targetFactionId = mission.getTarget().getFactionId();
		
		if (targetFactionId.equals(ExerelinConstants.PLAYER_NPC_ID) || targetFactionId.equals(PlayerFactionStore.getPlayerFactionId()))
		{
			String stageId = "success";
			if (mission.hasBonus(elapsedDays))
				stageId = "success_bonus";
			// success
			Global.getSector().reportEventStage(this, "success", findMessageSender(), MessagePriority.ENSURE_DELIVERY, 
					new BaseOnMessageDeliveryScript() {
						boolean hasBonus = mission.hasBonus(elapsedDays);
						public void beforeDelivery(CommMessageAPI message) {
							ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
									new RepActionEnvelope(RepActions.MISSION_SUCCESS, mission.getRepChange(hasBonus),
														  message, null, true), 
														  mission.getIssuer().getId());
							float reward = mission.baseReward;
							if (hasBonus) reward += mission.bonusReward;
							Global.getSector().getPlayerFleet().getCargo().getCredits().add(reward);
						}
					});
			endEvent();
		}
		else if (!mission.getIssuer().isHostileTo(targetFactionId))
		{
			// cancel event
			Global.getSector().reportEventStage(this, "cancelled", Global.getSector().getPlayerFleet(), MessagePriority.DELIVER_IMMEDIATELY);
			endEvent();
		}
		
		float days = Global.getSector().getClock().convertToDays(amount);
		elapsedDays += days;
		
		if (mission.getBaseDuration() - elapsedDays <= 0) {
			Global.getSector().reportEventStage(this, "failure", Global.getSector().getPlayerFleet(), MessagePriority.DELIVER_IMMEDIATELY,
					new BaseOnMessageDeliveryScript() {
						public void beforeDelivery(CommMessageAPI message) {
							ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
									new RepActionEnvelope(RepActions.MISSION_FAILURE, mission.getRepChange(false),
														  message, null, true),
														  mission.getIssuer().getId());
						}
					});
			endEvent();
		}
	}
	
	public void endEvent()
	{
		// todo?
		ended = true;
	}
	
	private void updateDaysLeft() {
		int daysLeft = (int) (mission.getBaseDuration() - elapsedDays);
		if (daysLeft < 1) daysLeft = 1;
		daysLeftStr = daysLeft + " days";
		if (daysLeft <= 1) {
			daysLeftStr = daysLeft + " day";
		}
		
		int bonusDaysLeft = (int) (mission.getBonusDuration() - elapsedDays);
		if (bonusDaysLeft < 1) bonusDaysLeft = 1;
		bonusDaysLeftStr = bonusDaysLeft + " days";
		if (bonusDaysLeft <= 1) {
			bonusDaysLeftStr = bonusDaysLeft + " day";
		}
	}
	
	public Map<String, String> getTokenReplacements() {
		updateDaysLeft();
		
		Map<String, String> map = super.getTokenReplacements();
		
		map.put("$sender", "Mission Board");	// TODO externalise
		
		LocationAPI loc = market.getContainingLocation();
		String locName = Misc.lcFirst(loc.getName());
		if (loc instanceof StarSystemAPI)
			locName = ((StarSystemAPI)loc).getBaseName();
		
		map.put("$target", "" + mission.getTarget().getName());
		map.put("$targetLocation", locName);
		map.put("$targetSize", "" + mission.getTarget().getSize());
		addFactionNameTokens(map, "target", mission.getTarget().getFaction());
		addFactionNameTokens(map, "issuer", mission.getIssuer());
		addFactionNameTokens(map, "player", Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId()));
		
		map.put("$daysLeft", daysLeftStr);
		map.put("$bonusDays", bonusDaysLeftStr);
		map.put("$rewardCredits", Misc.getWithDGS((int) mission.getBaseReward()) + Strings.C);
		map.put("$bonusCredits", Misc.getWithDGS((int) mission.getBonusReward()) + Strings.C);
		if (mission.hasBonus(elapsedDays)) {
			map.put("$actualReward", (Misc.getWithDGS((int) mission.getBaseReward() + (int) mission.getBonusReward())) + Strings.C);
		} else {
			map.put("$actualReward", Misc.getWithDGS((int) mission.getBaseReward()) + Strings.C);
		}
		
		return map;
	}

	@Override
	public String[] getHighlights(String stageId) {
		int daysLeft = (int) (mission.getBaseDuration() - elapsedDays);
		if (daysLeft < 1) daysLeft = 1;
		int bonusDaysLeft = (int) (mission.getBonusDuration() - elapsedDays);
		
		List<String> result = new ArrayList<String>();
		
		if ("posting".equals(stageId)) {
			result.add("" + daysLeft);
			addTokensToList(result, "$rewardCredits");
			addTokensToList(result, "$targetFaction");
			addTokensToList(result, "$targetSize");
		} else if ("posting_bonus".equals(stageId)) {
			result.add("" + daysLeft);
			addTokensToList(result, "$rewardCredits");
			addTokensToList(result, "$bonusCredits");
			result.add("" + bonusDaysLeft);
			addTokensToList(result, "$targetFaction");
			addTokensToList(result, "$targetSize");
		} else if ("success".equals(stageId) || 
				"success_bonus".equals(stageId)) {
			addTokensToList(result, "$actualReward");
			addTokensToList(result, "$bonusCredits");
		} else if ("accept".equals(stageId)) {
			result.add("" + daysLeft);
			addTokensToList(result, "$rewardCredits");
		} else if ("accept_bonus".equals(stageId)) {
			result.add("" + daysLeft);
			addTokensToList(result, "$rewardCredits");
			addTokensToList(result, "$bonusCredits");
			result.add("" + bonusDaysLeft);
		} else if ("failure".equals(stageId)) {
		}
		
		return result.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		return super.getHighlightColors(stageId);
	}

	@Override
	public boolean isDone() {
		return ended;
	}
	
	@Override
	public String getEventIcon() {
		return mission.issuer.getCrest();
	}

	// TODO externalise
	@Override
	public String getEventName() {
		int daysLeft = (int) (mission.getBaseDuration() - elapsedDays);
		String days = "";
		if (daysLeft > 0) {
			days = ", " + daysLeft + " days left";
		}
		if (isDone()) {
			return "Conquer " + mission.getTarget().getName() + " - over"; 
		}
		return "Conquer " + mission.getTarget().getName() + "" + days;
	}

	@Override
	public String getCurrentImage() {
		return mission.issuer.getLogo();
	}
	
	@Override
	public boolean allowMultipleOngoingForSameTarget() {
		return true;
	}
	
	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.MISSION;
	}
}
