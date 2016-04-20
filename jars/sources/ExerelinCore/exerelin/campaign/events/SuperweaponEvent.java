package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class SuperweaponEvent extends BaseEventPlugin {

	public static final float DAYS_PER_STAGE = 20f;
	public static final float REPUTATION_PENALTY_BASE = -0.02f;
	
	protected float elapsedDays = 0f;
	protected int stabilityPenalty = 0;
	protected String conditionToken = null;
	
	protected Map<String, Object> params;
	
	protected FactionAPI lastAttackerFaction = null;
	protected float repPenalty = 0;
	protected int numFactions = 0;
	protected float lastRepEffect = 0;
	protected float avgRepLoss = 0;
	protected boolean wasPlayer = false;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
	}
	
	@Override
	public void startEvent() {
		super.startEvent();
		if (market == null) {
			endEvent();
			return;
		}
		conditionToken = market.addCondition("exerelin_superweapon_condition", true, this);
	}
	
	@Override
	public void advance(float amount) {
		if (!isEventStarted()) return;
		if (isDone()) return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
		elapsedDays += days;
		
		if (elapsedDays >= DAYS_PER_STAGE) {
			elapsedDays -= DAYS_PER_STAGE;
			stabilityPenalty--;
			market.reapplyCondition(conditionToken);
		}
		
		if (stabilityPenalty <= 0) {
			endEvent();
		}
	}
	
	protected boolean ended = false;
	public void endEvent() {
		if (market != null && conditionToken != null) {
			market.removeSpecificCondition(conditionToken);
		}
		ended = true;
	}

	@Override
	public boolean isDone() {
		return ended;
	}

	public int getStabilityPenalty() {
		return stabilityPenalty;
	}

	public void setStabilityPenalty(int stabilityPenalty) {
		this.stabilityPenalty = stabilityPenalty;
		if (stabilityPenalty <= 0) {
			endEvent();
		} else {
			market.reapplyCondition(conditionToken);
		}
	}
	
	public void increaseStabilityPenalty(int penalty) {
		this.stabilityPenalty += penalty;
		if (stabilityPenalty <= 0) {
			endEvent();
		} else {
			market.reapplyCondition(conditionToken);
		}
	}
	
	public void reduceStabilityPenalty(int penalty) {
		this.stabilityPenalty -= penalty;
		if (stabilityPenalty <= 0) {
			endEvent();
		} else {
			market.reapplyCondition(conditionToken);
		}
	}
	
	public void reportSuperweaponUse(CampaignFleetAPI attackingFleet) {
		String stage = "weaponUsed";
		FactionAPI attackerFaction = attackingFleet.getFaction();
		FactionAPI targetFaction = market.getFaction();
		String targetFactionId = market.getFactionId();
		final Map<String, Float> repLossThirdParty = new HashMap<>();
		
		if (attackingFleet == Global.getSector().getPlayerFleet()) {
			stage += "_player";
			wasPlayer = true;
			String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
			attackerFaction = Global.getSector().getFaction(playerAlignedFactionId);
			
			float totalRepLoss = 0;
			
			List<String> factions = SectorManager.getLiveFactionIdsCopy();
			for (String factionId : factions)
			{
				if (factionId.equals(targetFactionId)) continue;
				//if (factionId.equals("player_npc")) continue;
				if (targetFaction.isHostileTo(factionId)) continue;
				if (factionId.equals("player_npc") || factionId.equals(playerAlignedFactionId))
					continue;

				float loss = REPUTATION_PENALTY_BASE;
				float repMult = 0;
				RepLevel level = targetFaction.getRelationshipLevel(factionId);
				if (level == RepLevel.COOPERATIVE)
					repMult = 1;
				else if (level == RepLevel.FRIENDLY)
					repMult = 0.8f;
				else if (level == RepLevel.WELCOMING)
					repMult = 0.6f;
				else if (level == RepLevel.FAVORABLE)
					repMult = 0.4f;
				else if (level == RepLevel.NEUTRAL)
					repMult = 0.2f;
				else if (level == RepLevel.SUSPICIOUS)
					repMult = 0.1f;
				else if (level == RepLevel.INHOSPITABLE)
				    repMult = 0.05f;

				loss *= repMult * market.getSize();
				if (loss <= 0) continue;

				numFactions++;
				totalRepLoss += loss;
				//DiplomacyManager.adjustRelations(Global.getSector().getFaction(factionId), attackerFaction, loss, null, null, null);
				repLossThirdParty.put(factionId, loss);
			}
			
			avgRepLoss = totalRepLoss/numFactions;
		}
		else {
			wasPlayer = false;
		}
		
		repPenalty = market.getSize() * REPUTATION_PENALTY_BASE * 2;
		RepLevel worst = RepLevel.HOSTILE;
		if (market.getSize() >= 5) worst = RepLevel.VENGEFUL;
		repPenalty = DiplomacyManager.adjustRelations(targetFaction, attackerFaction, repPenalty, worst, null, null).delta;
		
		lastAttackerFaction = attackerFaction;
		
		MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;
		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority, new BaseOnMessageDeliveryScript() {
			public void beforeDelivery(CommMessageAPI message) {
								Iterator<Map.Entry<String, Float>> iter = repLossThirdParty.entrySet().iterator();
								while (iter.hasNext())
                                {
									Map.Entry<String, Float> tmp = iter.next();
									String factionId = tmp.getKey();
									float loss = tmp.getValue();
									ExerelinUtilsReputation.adjustPlayerReputation(Global.getSector().getFaction(factionId), null, -loss);
                                }
								ExerelinUtilsReputation.syncFactionRelationshipsToPlayer();
			}
		});	
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		FactionAPI playerAlignedFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		
		Map<String, String> map = super.getTokenReplacements();
		addFactionNameTokens(map, "attacker", lastAttackerFaction);
		//addFactionNameTokens(map, "target", market.getFaction());
		map.put("$stabilityPenalty", Math.abs(stabilityPenalty)+"");
		map.put("$relDeltaAbs", "" + (int)Math.ceil(Math.abs(repPenalty*100f)));
		map.put("$newRelationStr", CovertOpsEventBase.getNewRelationStr(lastAttackerFaction, market.getFaction()));
		
		if (wasPlayer)
		{
			map.put("$numFactions", "" + numFactions);
			map.put("$repPenaltyAvgAbs", "" + (int)Math.ceil(avgRepLoss*100f));
		}
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$stabilityPenalty");
		addTokensToList(result, "$relDeltaAbs");
		addTokensToList(result, "$newRelationStr");
		
		if (stageId.contains("player"))
		{
			addTokensToList(result, "$numFactions");
			addTokensToList(result, "$repPenaltyAvgAbs");
		}
		
		return result.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		List<Color> colors = new ArrayList<>();
		colors.add(Misc.getNegativeHighlightColor());	// stability penalty
		colors.add(Misc.getNegativeHighlightColor());	// relationship loss
		colors.add(lastAttackerFaction.getRelColor(market.getFactionId()));	// new relationship color
		if (stageId.contains("player")) {
			colors.add(Misc.getHighlightColor());	// number of factions for reputation penalty
			colors.add(Misc.getNegativeHighlightColor());	// reputation penalty
		}
		
		return colors.toArray(new Color[0]);
	}
	
	@Override
	public String getEventName() {
		return StringHelper.getStringAndSubstituteToken("exerelin_events", "superweapon", 
				"$market", market.getName());
	}
	
	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.EVENT;
	}
}
