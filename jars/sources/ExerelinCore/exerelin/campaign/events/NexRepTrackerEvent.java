package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.events.RepTrackerEvent;
import com.fs.starfarer.api.impl.campaign.events.RepTrackerEvent.FactionTradeRepData;
import com.fs.starfarer.api.impl.campaign.events.RepTrackerEvent.MarketTradeInfo;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.shared.PlayerTradeDataForSubmarket;
import com.fs.starfarer.api.impl.campaign.shared.PlayerTradeProfitabilityData;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

/**
 * The same as RepTrackerEvent except I changed all the things that annoy me.
 * @author Histidine
 */
public class NexRepTrackerEvent extends BaseEventPlugin {
	
	public static final String DATA_ID = "nex_repTracker";
	
	public static Logger log = Global.getLogger(RepTrackerEvent.class);
	
	protected IntervalUtil tracker;
	protected IntervalUtil repDecayTracker;
	protected Map<String, FactionTradeRepData> repData = new HashMap<String, FactionTradeRepData>();
	
	public static NexRepTrackerEvent getTracker() {
		return (NexRepTrackerEvent)Global.getSector().getPersistentData().get(DATA_ID);
	}
	
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		readResolve();
		Global.getSector().getPersistentData().put(DATA_ID, this);
	}
	
	protected Object readResolve() {
		if (repDecayTracker == null) {
			repDecayTracker = new IntervalUtil(130f, 170f);
		}
		if (tracker == null) {
			tracker = new IntervalUtil(3f, 7f);
		}
		return this;
	}
	
	protected float [] getRepPointsAndVolumeUsedFor(float volume, FactionAPI faction) {
		FactionTradeRepData data = repData.get(faction.getId());
		if (data == null) {
			data = new FactionTradeRepData();
			repData.put(faction.getId(), data);
		}
		return data.getRepPointsAndVolumeUsedFor(volume);
	}
	
	public void advance(float amount) {
		if (!isEventStarted()) return;
		if (isDone()) return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
		
		tracker.advance(days);
		if (tracker.intervalElapsed()) {
			for (FactionAPI faction : Global.getSector().getAllFactions()) {
			//List<FactionAPI> factions = Global.getSector().getAllFactions();
			//FactionAPI faction = factions.get(new Random().nextInt(factions.size()));
				if (!faction.isPlayerFaction() && !faction.isNeutralFaction()) {
					checkForTradeReputationChanges(faction);
				}
			}
			checkForXPGain();
		}
		
//		repDecayTracker.advance(days);
//		if (repDecayTracker.intervalElapsed()) {
//			checkForRepDecay();
//		}
	}	
	
	protected void checkForXPGain() {
		PlayerTradeProfitabilityData data = SharedData.getData().getPlayerActivityTracker().getProfitabilityData();
		final long gain = data.getAccruedXP();
		//final long gain = 1000;
		if (gain > 0) {
			data.setAccruedXP(0);
			Global.getSector().getCampaignUI().addMessage(StringHelper.getString("exerelin_misc", "xpProfitableTradeMsg"), Misc.getBasePlayerColor());
			Global.getSector().getCharacterData().getPerson().getStats().addXP(gain);
		}
	}
	
	public void checkForTradeReputationChanges(final FactionAPI faction) {
		checkForTradeReputationChanges(faction, false);
	}
	
	// MODIFIED
	public void checkForTradeReputationChanges(final FactionAPI faction, boolean force) {
		float playerTradeVolume = 0;
		float playerSmugglingVolume = 0;
		
		final List<MarketTradeInfo> info = new ArrayList<MarketTradeInfo>();
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			
			MarketTradeInfo curr = new MarketTradeInfo();
			curr.market = market;
			
			for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
				SubmarketPlugin plugin = submarket.getPlugin();
				if (!plugin.isParticipatesInEconomy()) continue;
				
				//isFactionInvolved |= submarket.getFaction() == faction;
				
				PlayerTradeDataForSubmarket tradeData =  SharedData.getData().getPlayerActivityTracker().getPlayerTradeData(submarket);
				
				//if (plugin.isBlackMarket()) {
				if (market.getFaction() == faction && (submarket.getFaction().isHostileTo(faction) || submarket.getPlugin().isBlackMarket())) {
					curr.smugglingTotal += tradeData.getAccumulatedPlayerTradeValueForNegative();
				} else if (submarket.getFaction() == faction) {
					curr.tradeTotal += tradeData.getAccumulatedPlayerTradeValueForPositive();
				}
			}
			
			//if (!isFactionInvolved) continue;
			if (curr.tradeTotal == 0 && curr.smugglingTotal == 0) continue;
			
			info.add(curr);
			
			playerTradeVolume += curr.tradeTotal;
			playerSmugglingVolume += curr.smugglingTotal;
		}
		
//		if (faction.getId().equals("pirates")) {
//			System.out.println("23dsfsdf");
//		}
		
		float [] repPlus = getRepPointsAndVolumeUsedFor(playerTradeVolume, faction);
		float [] repMinus = getRepPointsAndVolumeUsedFor(playerSmugglingVolume, faction);
		final float repChange = repPlus[0] - repMinus[0];
		
		if (Math.abs(repChange) < 1 && !force) {
			log.info("Not enough trade/smuggling with " + faction.getDisplayNameWithArticle() + " for a rep change (" + playerTradeVolume + ", " + playerSmugglingVolume + ")");
			return;
		}

		log.info("Sending rep change of " + repChange + " with " + faction.getDisplayNameWithArticle() + " due to trade/smuggling");
		
		float tradeUsed = repPlus[1];
		float smugglingUsed = repMinus[1];
		
		// remove the player trade volume used for the rep change from the accumulated volume
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			//if (market.getFaction() != faction) continue;
			for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
				SubmarketPlugin plugin = submarket.getPlugin();
				if (!plugin.isParticipatesInEconomy()) continue;
				
				if (market.getFaction() != faction && submarket.getFaction() != faction) {
					continue;
				}
				
				PlayerTradeDataForSubmarket tradeData = SharedData.getData().getPlayerActivityTracker().getPlayerTradeData(submarket);
				
				//if (playerTradeVolume > 0 && !plugin.isBlackMarket()) {
				if (playerSmugglingVolume > 0 && market.getFaction() == faction && (submarket.getFaction().isHostileTo(faction) || submarket.getPlugin().isBlackMarket())) {
					float value = tradeData.getAccumulatedPlayerTradeValueForNegative();
					tradeData.setAccumulatedPlayerTradeValueForNegative(value - smugglingUsed * value / playerSmugglingVolume);
				} else if (playerTradeVolume > 0 && submarket.getFaction() == faction) {
					float value = tradeData.getAccumulatedPlayerTradeValueForPositive();
					tradeData.setAccumulatedPlayerTradeValueForPositive(value - tradeUsed * value / playerTradeVolume);
				}
			}
		}
		
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

		if (repChange > 0) {
			Collections.sort(info, new Comparator<MarketTradeInfo>() {
				public int compare(MarketTradeInfo o1, MarketTradeInfo o2) {
					return (int) (o2.tradeTotal - o1.tradeTotal);
				}
			});

			CoreReputationPlugin.RepActions action = CoreReputationPlugin.RepActions.TRADE_EFFECT;
			Global.getSector().adjustPlayerReputation(
					new CoreReputationPlugin.RepActionEnvelope(action, new Float(Math.abs(repChange)), null, null, false, true, "Change caused by trade with faction"), 
					faction.getId());

			causeNegativeRepChangeWithEnemies(info);
		} else if (repChange < 0) {
			Collections.sort(info, new Comparator<MarketTradeInfo>() {
				public int compare(MarketTradeInfo o1, MarketTradeInfo o2) {
					return (int) (o2.smugglingTotal - o1.smugglingTotal);
				}
			});
			
			CoreReputationPlugin.RepActions action = CoreReputationPlugin.RepActions.SMUGGLING_EFFECT;
			
			Global.getSector().adjustPlayerReputation(
					new CoreReputationPlugin.RepActionEnvelope(action, new Float(Math.abs(repChange)), null, null, false, true, "Change caused by black-market trade"), 
					faction.getId());

			causeNegativeRepChangeWithEnemies(info);
		}
	}
	
	
	public void causeNegativeRepChangeWithEnemies(List<MarketTradeInfo> info) {
		
		int maxToSend = 3;
		int sent = 0;
		for (final MarketTradeInfo curr : info) {		
			// MODIFIED
			float actionableTotal = curr.tradeTotal * 2f;	// + curr.smugglingTotal * 0.5f;
			if (actionableTotal <= 0) continue;
			
			//float repMinus = (float) Math.floor(actionableTotal / volumeForRepChange);
			List<FactionAPI> factions = new ArrayList<FactionAPI>(Global.getSector().getAllFactions());
			Collections.shuffle(factions);
			for (final FactionAPI faction : factions) {
				// pirates don't get mad about you trading with someone else
				//if (faction.getId().equals(Factions.PIRATES)) {
				if (faction.getCustom().optBoolean(Factions.CUSTOM_IGNORE_TRADE_WITH_ENEMIES)) {
					continue;
				}
				if (faction.isPlayerFaction()) continue; // don't report player to themselves for trading with their own enemies
				
				final MarketAPI other = BaseEventPlugin.findNearestMarket(curr.market, new MarketFilter() {
					public boolean acceptMarket(MarketAPI market) {
						if (!market.getFactionId().equals(faction.getId())) {
							return false;
						}
						if (market.getFaction().isAtBest(curr.market.getFaction(), RepLevel.HOSTILE)) {
							return true;
						}
						return false;
					}
				});
				if (other == null) continue;
	
				float dist = Misc.getDistanceLY(curr.market.getLocationInHyperspace(), other.getLocationInHyperspace());
				//if (dist > 2f) continue;
				if (dist > Global.getSettings().getFloat("economyMaxRangeForNegativeTradeRepImpactLY")) continue;
				
				
				float [] repMinus = getRepPointsAndVolumeUsedFor(actionableTotal, faction);
				if (repMinus[0] <= 0) continue;
				
				if (dist <= 0) { // same star system
					repMinus[0] *= 2f;
				}
				
				final float repChange = -repMinus[0];
				
				log.info("Sending rep change of " + repChange + " with " + other.getFaction().getDisplayNameWithArticle() + 
						" due to trade with enemy (" + curr.market.getFaction().getDisplayNameWithArticle() + ")");
				
				CoreReputationPlugin.RepActions action = CoreReputationPlugin.RepActions.TRADE_WITH_ENEMY;
				Global.getSector().adjustPlayerReputation(
						new CoreReputationPlugin.RepActionEnvelope(action, new Float(Math.abs(repChange)), null, null, false, true, "Change caused by trade with enemies"), 
						other.getFactionId());

				sent++;
				if (sent >= maxToSend) break;
			}
			if (sent >= maxToSend) break;
		}
	}
	
	public boolean isDone() {
		return false;
	}
}