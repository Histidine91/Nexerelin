package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.events.SuperweaponEvent;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import exerelin.campaign.fleets.ResponseFleetManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

public class Exerelin_UseSuperweapon extends BaseCommandPlugin {
	
	public static final float STOCKPILE_DESTRUCTION_BASE_MULT = 0.80f;
	public static final float STOCKPILE_DESTRUCTION_SIZE_DIV_MULT = 0.5f;
	public static final float STOCKPILE_DESTRUCTION_VARIANCE = 0.25f;
	public static final int STABILITY_BASE_PENALTY = 7;
	public static final float STABILITY_SIZE_DIVISOR = 3f;
	public static final int STABILITY_MAX_SIZE_REDUCTION = 3;
	
	public static Logger log = Global.getLogger(Exerelin_UseSuperweapon.class);
	
	public static final Map<String, Float> CR_COSTS = new HashMap<>();
	
	protected static final String STRING_CATEGORY = "exerelin_superweapon";
	
	static {
		CR_COSTS.put("ii_olympus_t", 0.4f);
		CR_COSTS.put("ii_boss_olympus", 0f);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		//log.info("Picking target");
		SectorEntityToken target = dialog.getInteractionTarget();
		if (target == null) return false;
		MarketAPI market = target.getMarket();
		if (market == null) return false;
		
		CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
		FleetMemberAPI bestMember = null;
		float bestCR = 0;
		
		//log.info("Picking ship");
		List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
		for (FleetMemberAPI member : members) {
			float cr = member.getRepairTracker().getCR();
			if (cr <= 0) continue;
			
			if (member.getHullId().equals("ii_boss_olympus")) {
				bestMember = member;
				break;
			}
			else if (member.getHullId().equals("ii_olympus_t")) {
				if (cr > bestCR) {
					bestMember = member;
					bestCR = cr;
				}
			}
		}
		if (bestMember == null) return false;
		String hullId = bestMember.getHullId();
		
		// deduct CR
		//log.info("Doing CR stuff");
		float crCost = 0;
		if (CR_COSTS.containsKey(hullId))
			crCost = CR_COSTS.get(hullId);
		if (crCost != 0) {
			if (crCost > bestCR) return false;
			bestMember.getRepairTracker().applyCREvent(-crCost, StringHelper.getString("exerelin_superweapon", "crText"));
		}
		
		if (target.getId().equals("tem_ascalon")) {
			memoryMap.get(MemKeys.LOCAL).set("$superweaponSuccess", true, 0);
			Global.getSoundPlayer().playUISound("ii_titan_explode_close", 1, 1);
			return true;
		}
		
		// wreck stuff
		int size = market.getSize();
		float destructionMult = STOCKPILE_DESTRUCTION_BASE_MULT / (STOCKPILE_DESTRUCTION_SIZE_DIV_MULT * size);
		ExerelinUtilsMarket.destroyAllCommodityStocks(market, destructionMult, STOCKPILE_DESTRUCTION_VARIANCE);
		
		int reduction = (int)(size / STABILITY_SIZE_DIVISOR);
		if (reduction > STABILITY_MAX_SIZE_REDUCTION)
			reduction = STABILITY_MAX_SIZE_REDUCTION;
		int stabilityPenalty = STABILITY_BASE_PENALTY - reduction;
		
		SectorAPI sector = Global.getSector();
		CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(new CampaignEventTarget(market), "exerelin_superweapon");
		if (eventSuper == null) 
			eventSuper = sector.getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_superweapon", null);
		SuperweaponEvent event = (SuperweaponEvent)eventSuper;
		event.setStabilityPenalty(stabilityPenalty);
		event.reportSuperweaponUse(fleet);
		
		ResponseFleetManager.modifyReserveSize(market, -999);
		
		//ExerelinUtilsMarket.refreshMarket(market, true);
		
		Global.getSoundPlayer().playUISound("ii_titan_explode_close", 1, 1);
		
		// lock out of market
		MemoryAPI memMarket= memoryMap.get(MemKeys.MARKET);
		memMarket.set(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET, true, 180);
		
		// print results
		TextPanelAPI text = dialog.getTextPanel();
		String destructionStr = String.format("%.0f", destructionMult * 100);
		text.addParagraph(StringHelper.getStringAndSubstituteToken(STRING_CATEGORY, "successText", "$market", market.getName()));
		
		text.setFontVictor();
		text.setFontSmallInsignia();

		text.addParagraph("-----------------------------------------------------------------------------");
		text.addParagraph(StringHelper.getStringAndSubstituteToken(STRING_CATEGORY, "stabilityPenaltyReport", "$stability", Math.abs(stabilityPenalty)+""));
		text.highlightInLastPara(Misc.getNegativeHighlightColor(), stabilityPenalty+"");
		text.addParagraph(StringHelper.getStringAndSubstituteToken(STRING_CATEGORY, "commodityDestructionReport", "$percent", destructionStr));
		text.highlightInLastPara(Misc.getNegativeHighlightColor(), destructionStr);
		text.addParagraph("-----------------------------------------------------------------------------");
		text.setFontInsignia();
		
		MemoryAPI memLocal = memoryMap.get(MemKeys.LOCAL);
		memLocal.set("$superweaponSuccess", true, 0);
		memLocal.set("$superweaponStabilityPenalty", stabilityPenalty, 0);
		memLocal.set("$superweaponCommodityDestructionMult", destructionStr, 0);
		
		return true;
	}
	
	
}
