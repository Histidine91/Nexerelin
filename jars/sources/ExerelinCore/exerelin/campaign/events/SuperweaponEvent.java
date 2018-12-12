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
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.NexUtilsMath;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: figure out what to do with this
public class SuperweaponEvent extends BaseEventPlugin {

	public static final float DAYS_PER_STAGE = 20f;
	public static final float REPUTATION_PENALTY_BASE = 0.03f;
	
	protected float elapsedDays = 0f;
	protected int stabilityPenalty = 0;
	
	protected FactionAPI lastAttackerFaction = null;
	protected float repPenalty = 0;
	protected int numFactions = 0;
	protected float lastRepEffect = 0;
	protected float avgRepLoss = 0;
	protected boolean wasPlayer = false;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
		
	@Override
	public void startEvent() {
		super.startEvent();
		if (market == null) {
			endEvent();
			return;
		}
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
		}
		
		if (stabilityPenalty <= 0) {
			endEvent();
		}
	}
	
	protected boolean ended = false;
	public void endEvent() {
		ended = true;
	}

	@Override
	public boolean isDone() {
		return ended;
	}

	public int getStabilityPenalty() {
		return stabilityPenalty;
	}
	
	public void reportSuperweaponUse(CampaignFleetAPI attackingFleet) {
		String stage = "weaponUsed";
		final FactionAPI attackerFaction;
		final FactionAPI targetFaction = market.getFaction();
		String targetFactionId = market.getFactionId();
		final Map<String, Float> repLossThirdParty = new HashMap<>();
		
		int marketSizeForRep = market.getSize();
		if (marketSizeForRep <= 2) marketSizeForRep = 2;
		if (marketSizeForRep > 8) marketSizeForRep = 8;
		
		// if player was the superweapon user, determine reputation loss with other factions
		// (and set other relevant variables accordingly)
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
				//if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID)) continue;
				if (targetFaction.isHostileTo(factionId)) continue;
				if (factionId.equals(Factions.PLAYER) || factionId.equals(playerAlignedFactionId))
					continue;
				
				// non-victim reputation loss is based on their relationship with the victim
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
					repMult = 0.25f;
				else if (level == RepLevel.SUSPICIOUS)
					repMult = 0.15f;
				else if (level == RepLevel.INHOSPITABLE)
				    repMult = 0.1f;

				loss *= repMult * NexUtilsMath.round(Math.pow(1.5, marketSizeForRep));
				if (loss <= 0) continue;

				numFactions++;
				totalRepLoss += loss;
				//DiplomacyManager.adjustRelations(Global.getSector().getFaction(factionId), attackerFaction, loss, null, null, null);
				repLossThirdParty.put(factionId, loss);
			}
			
			avgRepLoss = totalRepLoss/numFactions;
		}
		else {
			attackerFaction = attackingFleet.getFaction();
			wasPlayer = false;
		}
		
		// relationship loss with attacking faction (even if player wasn't the user)
		repPenalty = (float)(NexUtilsMath.round(Math.pow(1.5, marketSizeForRep)) * 2f * REPUTATION_PENALTY_BASE);
		final RepLevel worst = (market.getSize() >= 5) ? RepLevel.VENGEFUL : RepLevel.HOSTILE;
		repPenalty = DiplomacyManager.adjustRelations(targetFaction, attackerFaction, -repPenalty, worst, null, null).delta;
		
		lastAttackerFaction = attackerFaction;
		final boolean wasPlayerFinal = wasPlayer;
		
		MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;
		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority, new BaseOnMessageDeliveryScript() {
			public void beforeDelivery(CommMessageAPI message) {
								for (Map.Entry<String, Float> tmp : repLossThirdParty.entrySet())
                                {
									String factionId = tmp.getKey();
									FactionAPI faction = Global.getSector().getFaction(factionId);
									float loss = tmp.getValue();
									// use adjustPlayerReputation instead of adjustRelations to print it in console
									if (wasPlayerFinal)
										NexUtilsReputation.adjustPlayerReputation(Global.getSector().getFaction(factionId), -loss);
									else
										DiplomacyManager.adjustRelations(faction, attackerFaction, -loss, null, null, worst);
									
                                }
								if (wasPlayerFinal)
									NexUtilsReputation.syncFactionRelationshipsToPlayer();
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
		map.put("$newRelationStr", NexUtilsReputation.getRelationStr(lastAttackerFaction, market.getFaction()));
		
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
