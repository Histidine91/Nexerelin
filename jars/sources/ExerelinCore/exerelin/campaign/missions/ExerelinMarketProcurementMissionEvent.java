package exerelin.campaign.missions;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.missions.MarketProcurementMission;
import com.fs.starfarer.api.impl.campaign.shared.PlayerTradeDataForSubmarket;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

// identical to vanilla event except with the no-pirate-markets crash fixed
@Deprecated
public class ExerelinMarketProcurementMissionEvent extends BaseEventPlugin {
	
	private MarketProcurementMission mission = null;
	
	private float elapsedDays = 0;
	
	private boolean wantsToSendPirate = true;
	private boolean sentPirate = false;
	private IntervalUtil pirateTracker = new IntervalUtil(0.1f, .3f);
	private boolean contactWillInitiateComms = false;
	
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget, false);
	}
	
	@Override
	public void setParam(Object param) {
		mission = (MarketProcurementMission) param;
		updateDaysLeft();
	}

	public void startEvent() {
		super.startEvent();
		
		wantsToSendPirate = (float) Math.random() > 0.66f;
		//wantsToSendPirate = true;
		
		String stageId = "accept";
		if (mission.hasBonus(elapsedDays)) {
			stageId = "accept_bonus";
		}
		Global.getSector().reportEventStage(this, stageId, mission.getAcceptLocation(), MessagePriority.DELIVER_IMMEDIATELY, 
				new BaseOnMessageDeliveryScript() {
					public void beforeDelivery(CommMessageAPI message) {
						if (market.getStarSystem() != null) {
							message.setStarSystemId(market.getStarSystem().getId());
						}
					}
				});
		
		//mission.getMarket().addPerson(mission.getContact());
		mission.getMarket().getCommDirectory().addPerson(mission.getContact());

		mission.getContact().getMemoryWithoutUpdate().set("$mpm_isPlayerContact", true, mission.getBaseDuration());
		mission.getContact().getMemoryWithoutUpdate().set("$mpm_eventRef", this, mission.getBaseDuration());
		mission.getContact().getMemoryWithoutUpdate().set("$mpm_commodityName", mission.getCOM().getCommodity().getName().toLowerCase(), mission.getBaseDuration());
		mission.getContact().getMemoryWithoutUpdate().set("$mpm_quantity", Misc.getWithDGS((int)mission.getQuantity()), mission.getBaseDuration());
		Misc.setFlagWithReason(mission.getContact().getMemoryWithoutUpdate(), 
							MemFlags.MEMORY_KEY_REQUIRES_DISCRETION, "mpm_" + mission.getCommodityId(),
							true, mission.getBaseDuration());
		
		Misc.setFlagWithReason(mission.getContact().getMemoryWithoutUpdate(),
							  MemFlags.MEMORY_KEY_MISSION_IMPORTANT,
							  "mpm", true, mission.getBaseDuration());
		
		updateDaysLeft();
		
		contactWillInitiateComms = (float) Math.random() > 0.5f;
		
		boolean illegal = mission.getContact().getFaction().isHostileTo(market.getFaction());
		if (illegal) contactWillInitiateComms = false;
		
		if (contactWillInitiateComms) {
			mission.getContact().incrWantsToContactReasons();
		}
	}
	
	private boolean ended = false;
	private String bonusDaysLeftStr;
	private String daysLeftStr;
	private void endEvent() {
		ended = true;
		
		mission.getContact().getMemoryWithoutUpdate().unset("$mpm_isPlayerContact");
		mission.getContact().getMemoryWithoutUpdate().unset("$mpm_eventRef");
		mission.getContact().getMemoryWithoutUpdate().unset("$mpm_commodityName");
		mission.getContact().getMemoryWithoutUpdate().unset("$mpm_quantity");
		Misc.setFlagWithReason(mission.getContact().getMemoryWithoutUpdate(), 
				MemFlags.MEMORY_KEY_REQUIRES_DISCRETION, "mpm_" + mission.getCommodityId(),
				false, 0f);
		Misc.setFlagWithReason(mission.getContact().getMemoryWithoutUpdate(),
				  MemFlags.MEMORY_KEY_MISSION_IMPORTANT,
				  "mpm", false, 0f);
		
		if (contactWillInitiateComms) {
			mission.getContact().decrWantsToContactReasons();
		}
		
		Global.getSector().getImportantPeople().returnPerson(mission.getContact(), MarketProcurementMission.PERSON_CHECKOUT_REASON);
		if (!Global.getSector().getImportantPeople().isCheckedOutForAnything(mission.getContact())) {
			market.getCommDirectory().removePerson(mission.getContact());
			//mission.getMarket().removePerson(mission.getContact());
		}
	}
	
	public void advance(float amount) {
		if (!isEventStarted()) return;
		if (isDone()) return;
		
		updateDaysLeft();
		
		float days = Global.getSector().getClock().convertToDays(amount);
		
		//memory.advance(days);
		elapsedDays += days;
		
		if (mission.getBaseDuration() - elapsedDays <= 0) {
			Global.getSector().reportEventStage(this, "failure", Global.getSector().getPlayerFleet(), MessagePriority.DELIVER_IMMEDIATELY,
					new BaseOnMessageDeliveryScript() {
						public void beforeDelivery(CommMessageAPI message) {
							ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
									new RepActionEnvelope(RepActions.MISSION_FAILURE, mission.getRepChange(),
														  message, null, true),
														  mission.getContact().getFaction().getId());
							result = Global.getSector().adjustPlayerReputation(
									new RepActionEnvelope(RepActions.MISSION_FAILURE, mission.getRepChange(),
											message, null, true),
											mission.getContact());
						}
					});
			endEvent();
			return;
		}
		
		if (wantsToSendPirate && !sentPirate) {
			pirateTracker.advance(days);
			if (pirateTracker.intervalElapsed()) {
				CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
				float playerQty = playerFleet.getCargo().getCommodityQuantity(mission.getCommodityId());
				if (playerQty >= mission.getQuantity() && playerFleet.isInHyperspace()) {
					CampaignFleetAPI pirateFleet = createPirateFleet();
					if (pirateFleet != null) {
						playerFleet.getContainingLocation().addEntity(pirateFleet);
						Vector2f pirateLoc = Misc.getPointAtRadius(playerFleet.getLocation(), 500f);
						pirateFleet.setLocation(pirateLoc.x, pirateLoc.y);
						//pirateFleet.setLocation(playerFleet.getLocation().x + 300, playerFleet.getLocation().y + 300);
						pirateFleet.getAI().addAssignmentAtStart(FleetAssignment.INTERCEPT, playerFleet, 1000f, null);
						
						pirateFleet.getMemoryWithoutUpdate().set("$mpm_isSpawnedByMPM", true);
						PersonAPI person = pirateFleet.getCommander();
						person.getMemoryWithoutUpdate().set("$mpm_eventRef", this, mission.getBaseDuration());
						person.getMemoryWithoutUpdate().set("$mpm_commodityName", mission.getCOM().getCommodity().getName().toLowerCase(), mission.getBaseDuration());
						person.getMemoryWithoutUpdate().set("$mpm_quantity", Misc.getWithDGS((int)mission.getQuantity()), mission.getBaseDuration());
					}
					sentPirate = true;
				}
			}
		}
	}
	
	// this is the only method that's changed
	protected CampaignFleetAPI createPirateFleet() {
		float pts = 5f + Math.min(10f, mission.getBaseReward() / 10000f);
		float quantity = mission.getQuantity();
		float freighterPts = quantity / 100f;
		if (freighterPts > 8) freighterPts = 8;
		CampaignFleetAPI fleet = FleetFactoryV2.createFleet(new FleetParams(
				Global.getSector().getPlayerFleet().getLocationInHyperspace(),
				null, 
				Factions.PIRATES,
				null, // fleet's faction, if different from above, which is also used for source market picking
				FleetTypes.MERC_PRIVATEER,
				pts, // combatPts
				freighterPts, // freighterPts 
				0f, // tankerPts
				0f, // transportPts
				0f, // linerPts
				0f, // civilianPts 
				0f, // utilityPts
				0f, // qualityBonus
				-1f, // qualityOverride
				1f, // officer num mult
				0 // officer level bonus
				));
		if (fleet == null)
		{
			return null;
		}
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_LOW_REP_IMPACT, "mpm", true, 10000);
		return fleet;
	}
	

	@Override
	public boolean callEvent(String ruleId, final InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String action = params.get(0).getString(memoryMap);
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		CargoAPI cargo = playerFleet.getCargo();
		
		if (action.equals("performDelivery")) {
			cargo.removeItems(CargoItemType.RESOURCES, mission.getCommodityId(), mission.getQuantity());
			int reward = (int) mission.getBaseReward();
			if (mission.hasBonus(elapsedDays)) {
				reward = (int) (mission.getBaseReward() + mission.getBonusReward());
			}
			cargo.getCredits().add(reward);
			mission.getContact().getMemoryWithoutUpdate().set("$mpm_rewardCredits", Misc.getWithDGS(reward), 0f);
			
			applyTradeValueImpact(reward);
			
			Global.getSector().reportEventStage(this, "success", Global.getSector().getPlayerFleet(), MessagePriority.DELIVER_IMMEDIATELY, 
					new BaseOnMessageDeliveryScript() {
						public void beforeDelivery(CommMessageAPI message) {
							ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
									new RepActionEnvelope(RepActions.MISSION_SUCCESS, mission.getRepChange(),
														  message, dialog.getTextPanel(), true), 
														  mission.getContact().getFaction().getId());
							result = Global.getSector().adjustPlayerReputation(
									new RepActionEnvelope(RepActions.MISSION_SUCCESS, mission.getRepChange(),
											message, dialog.getTextPanel(), true), 
											mission.getContact());
						}
					});
		} else if (action.equals("hasEnough")) {
			return cargo.getCommodityQuantity(mission.getCommodityId()) >= mission.getQuantity();
		} else if (action.equals("endEvent")) {
			endEvent();
		} else if (action.equals("handOver")) {
			CargoAPI pirateCargo = dialog.getInteractionTarget().getCargo();
			cargo.removeItems(CargoItemType.RESOURCES, mission.getCommodityId(), mission.getQuantity());
			pirateCargo.addItems(CargoItemType.RESOURCES, mission.getCommodityId(), mission.getQuantity());
			
			CampaignFleetAPI pirateFleet = (CampaignFleetAPI) dialog.getInteractionTarget();
			pirateFleet.getAI().removeFirstAssignment();
			pirateFleet.getAI().addAssignmentAtStart(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, market.getPrimaryEntity(), 1000f, null);
			
			pirateFleet.getMemoryWithoutUpdate().unset("$mpm_isSpawnedByMPM");
			pirateFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, false, 10);
		} else if (action.equals("hasBonus")) {
			return mission.hasBonus(elapsedDays);
		}
		
		return true;
	}
	
	protected void applyTradeValueImpact(float totalReward) {
//		float playerImpactMult = Global.getSettings().getFloat("economyPlayerTradeImpactMult");
//		accumulatedPlayerTradeValueForPositive += info.getPrice() * playerImpactMult * fractionSold * getTransponderMult();
//		accumulatedPlayerTradeValueForNegative += info.getPrice() * playerImpactMult * fractionSold * getTransponderMult();
//		totalPlayerTradeValue += info.getPrice() * playerImpactMult * fractionSold * getTransponderMult();
		
		boolean illegal = mission.getContact().getFaction().isHostileTo(market.getFaction());
		
		SubmarketAPI submarket = null;
		for (SubmarketAPI curr : market.getSubmarketsCopy()) {
			if (!curr.getPlugin().isParticipatesInEconomy()) continue;
			if (mission.getContact().getFaction() == curr.getFaction()) {
				submarket = curr;
				break;
			}
		}
		
		if (submarket == null) {
			for (SubmarketAPI curr : market.getSubmarketsCopy()) {
				if (!curr.getPlugin().isParticipatesInEconomy()) continue;
				
				if (illegal && curr.getPlugin().isBlackMarket()) {
					submarket = curr;
					break;
				}
				if (!illegal && curr.getPlugin().isOpenMarket()) {
					submarket = curr;
					break;
				}
			}
		}
		
		if (submarket == null) return;
		
		PlayerTradeDataForSubmarket tradeData =  SharedData.getData().getPlayerActivityTracker().getPlayerTradeData(submarket);
		CargoStackAPI stack = Global.getFactory().createCargoStack(CargoItemType.RESOURCES, mission.getCommodityId(), null);
		stack.setSize(mission.getQuantity());
		tradeData.addToTrackedPlayerSold(stack, totalReward);
	}
	
	
//	@Override
//	public boolean allowMultipleOngoingForSameTarget() {
//		return true;
//	}

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
		
		mission.getContact().getMemoryWithoutUpdate().set("$mpm_daysLeft", daysLeftStr, 1f);
		mission.getContact().getMemoryWithoutUpdate().set("$mpm_bonusDays", bonusDaysLeftStr, 1f);
	}
	
	public Map<String, String> getTokenReplacements() {
		updateDaysLeft();
		
		Map<String, String> map = super.getTokenReplacements();
		
		map.put("$sender", "Mission Board");
		
		map.put("$quantity", "" + Misc.getWithDGS((int) mission.getQuantity()));
		map.put("$commodity", mission.getCOM().getCommodity().getName().toLowerCase());
		
		map.put("$daysLeft", daysLeftStr);
		map.put("$bonusDays", bonusDaysLeftStr);
		map.put("$rewardCredits", Misc.getWithDGS((int) mission.getBaseReward()) + Strings.C);
		map.put("$bonusCredits", Misc.getWithDGS((int) mission.getBonusReward()) + Strings.C);
		if (mission.hasBonus(elapsedDays)) {
			map.put("$actualReward", (Misc.getWithDGS((int) mission.getBaseReward() + (int) mission.getBonusReward())) + Strings.C);
		} else {
			map.put("$actualReward", Misc.getWithDGS((int) mission.getBaseReward()) + Strings.C);
		}
		map.put("$perUnit", Misc.getWithDGS((int) (mission.getBaseReward() / mission.getQuantity())) + Strings.C);
		
//		map.put("$contactRank", mission.getContact().getRank());
//		map.put("$contactPost", mission.getContact().getPost());
		String desc = mission.getContact().getPost();
		if (desc == null || 
				Ranks.POST_SHADY.equals(mission.getContact().getPostId()) ||
				Ranks.POST_FENCE.equals(mission.getContact().getPostId()) ||
				Ranks.POST_GANGSTER.equals(mission.getContact().getPostId()) ||
				Ranks.POST_SMUGGLER.equals(mission.getContact().getPostId())
				) {
			desc = "one";
		}
		if (desc == null) desc = mission.getContact().getRank();
		
		
		map.put("$contactDesc", desc);
		
		//map.put("$contactName", mission.getContact().getName().getFullName());
		addPersonTokens(map, "contact", mission.getContact());
		
		if (market.getFaction().isHostileTo(mission.getContact().getFaction())) {
			map.put("$contactDetail", "is affiliated with the local underworld and will require clandestine delivery, which may attract the interest of local authorities");
		} else if (market.getFaction() != mission.getContact().getFaction()){
			map.put("$contactDetail", "is " + mission.getContact().getFaction().getPersonNamePrefixAOrAn() + " " +
					mission.getContact().getFaction().getPersonNamePrefix() + " " + desc + 
					" operating with the knowledge of the local authorities and the delivery may be made openly");
		} else {
			map.put("$contactDetail", "is working for the local" + 
					//mission.getContact().getFaction().getDisplayName() + 
					" authorities and the delivery may be made openly");
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
			addTokensToList(result, "$quantity");
			result.add("" + daysLeft);
			addTokensToList(result, "$rewardCredits");
			//result.add("3");
			addTokensToList(result, "$perUnit");
		} else if ("posting_bonus".equals(stageId)) {
			addTokensToList(result, "$quantity");
			result.add("" + daysLeft);
			addTokensToList(result, "$rewardCredits");
			addTokensToList(result, "$bonusCredits");
			result.add("" + bonusDaysLeft);
			//result.add("3,6");
			addTokensToList(result, "$perUnit");
		} else if ("success".equals(stageId) || 
				"success_bonus".equals(stageId)) {
			addTokensToList(result, "$quantity");
			addTokensToList(result, "$actualReward");
			addTokensToList(result, "$bonusCredits");
		} else if ("accept".equals(stageId)) {
			addTokensToList(result, "$quantity");
			result.add("" + daysLeft);
			addTokensToList(result, "$rewardCredits");
			addTokensToList(result, "$perUnit");
		} else if ("accept_bonus".equals(stageId)) {
			addTokensToList(result, "$quantity");
			result.add("" + daysLeft);
			addTokensToList(result, "$rewardCredits");
			addTokensToList(result, "$perUnit");
			addTokensToList(result, "$bonusCredits");
			result.add("" + bonusDaysLeft);
		} else if ("failure".equals(stageId)) {
			addTokensToList(result, "$quantity");
		}
		
		return result.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		return super.getHighlightColors(stageId);
	}

	public boolean isDone() {
		return ended;
	}

	public String getEventName() {
		//return mission.getName();
		CommodityOnMarketAPI com = mission.getCOM();
		float quantity = mission.getQuantity();
		//return "Procurement - " + com.getCommodity().getName() + ", " + (int) quantity + " units";
		int daysLeft = (int) (mission.getBaseDuration() - elapsedDays);
		String days = "";
		if (daysLeft > 0) {
			//days = ", " + daysLeft + " days left";
			days = ", " + daysLeft + "d";
			//days = "" + daysLeft + " days";
		} else {
			days = ", <1d";
		}
		if (isDone()) {
			return "" + (int) quantity + " " + Strings.X + " " + com.getCommodity().getName().toLowerCase() + " - done"; 
		}
		return "" + (int) quantity + " " + Strings.X + " " + com.getCommodity().getName().toLowerCase() + "" + days;
		//return "" + (int) quantity + " " + Strings.X + " " + com.getCommodity().getName().toLowerCase() + " to " + market.getName() + ", " + days;
		//return "Deliver " + (int) quantity + " units of " + com.getCommodity().getName().toLowerCase() + "" + days;
	}

	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.MISSION;
	}

	@Override
	public String getEventIcon() {
		return mission.getCOM().getCommodity().getIconName();
	}

	@Override
	public String getCurrentImage() {
		return Global.getSector().getFaction(mission.getFactionId()).getLogo();
	}
}