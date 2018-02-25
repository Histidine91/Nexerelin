package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.fleets.DefenceStationManager;
import exerelin.utilities.StringHelper;
import exerelin.campaign.fleets.ResponseFleetManager;
import java.awt.Color;

public class AgentGatherIntel extends AgentActionBase {

	protected Color getColorFromScale(float number, float max, boolean reverse)
	{
		float proportion = number/max;
		if (proportion > 1) proportion = 1;
		else if (proportion < 0) proportion = 0;
		
		int r = (int)(255*(1-proportion));
		int g = (int)(255*proportion);
		int b = 96;
		if (reverse)
		{
			r = 255 - r;
			g = 255 - g;
		}
		return new Color(r, g, b);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		// useAgent arg
		if (params.get(0).getBoolean(memoryMap) == true)
		{
			boolean superResult = useSpecialPerson("agent", 1);
			if (superResult == false)
				return false;
		}
		
		SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
		MarketAPI market = target.getMarket();
		Color highlightColor = Misc.getHighlightColor();
		Color negativeColor = Misc.getNegativeHighlightColor();
		
		TextPanelAPI text = dialog.getTextPanel();
		text.addParagraph(market.getName() + " intel report");
		text.setFontSmallInsignia();
		text.addParagraph(StringHelper.HR);
		
		float stability = market.getStabilityValue();
		text.addParagraph(Misc.ucFirst(StringHelper.getString("stability")) + ": " + stability);
		text.highlightFirstInLastPara("" + stability, getColorFromScale(stability, 10, false));
		
		int alertLevel = Math.round(CovertOpsManager.getAlertLevel(market) * 100);
		text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_agents", "alertLevel")) + ": " + alertLevel + "%");
		text.highlightFirstInLastPara("" + alertLevel, getColorFromScale(alertLevel, 100, true));
		
		float reserveSize = ResponseFleetManager.getReserveSize(market);
		text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_agents", "reserveSize")) + ": " + reserveSize);
		text.highlightFirstInLastPara("" + reserveSize, highlightColor);
		
		if (reserveSize > ResponseFleetManager.MIN_FP_TO_SPAWN)
		{
			CampaignFleetAPI responseFleet;
			String defenderMemFlag = "$nex_invasionResponseFleet";
			MemoryAPI mem = memoryMap.get(MemKeys.MARKET);
			if (mem.contains(defenderMemFlag))
			{
				responseFleet = mem.getFleet(defenderMemFlag);
			}
			else
			{
				responseFleet = ResponseFleetManager.getManager().getResponseFleet(market, (int)(reserveSize));
				mem.set(defenderMemFlag, responseFleet, 5);
			}
			
			String fleetTitle = Misc.ucFirst(StringHelper.getString("exerelin_agents", "responseFleetPreview"));
			dialog.getVisualPanel().showFleetInfo(fleetTitle, responseFleet, null, null);
			//responseFleet.despawn();
		}
		
		DefenceStationManager statMan = DefenceStationManager.getManager();
		if (statMan.getFleet(market) == null && statMan.getMaxStations(market) > 0)
		{
			float stationPoints = statMan.getConstructionPoints(market);
			String stationPointsStr = String.format("%.1f", stationPoints) + "%";
			text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_agents", "stationPoints")) + ": " + stationPointsStr);
			text.highlightFirstInLastPara("" + stationPointsStr, highlightColor);
		}
		
		if (Global.getSector().getEventManager().isOngoing(new CampaignEventTarget(market), "nex_rebellion"))
		{
			RebellionEvent rebellion = (RebellionEvent)Global.getSector().getEventManager().getOngoingEvent(
					new CampaignEventTarget(market), "nex_rebellion");
			float govtStrength = rebellion.getGovtStrength();
			float rebelStrength = rebellion.getRebelStrength();
			FactionAPI rebelFaction = rebellion.getRebelFaction();
			
			String header = Misc.ucFirst(StringHelper.getString("exerelin_agents", "rebellionStatus") + ":");
			String rebelName = rebelFaction.getDisplayName();
			header = StringHelper.substituteToken(header, "$faction", rebelName);
			text.addParagraph(header);
			text.highlightFirstInLastPara(rebelName, rebelFaction.getBaseUIColor());
			
			text.addParagraph("  " + Misc.ucFirst(StringHelper.getString("exerelin_agents", "govtStrength")) + ": " + govtStrength);
			text.highlightFirstInLastPara( "" + govtStrength, govtStrength > market.getSize() * 5 ? 
					highlightColor : negativeColor);
			text.addParagraph("  " + Misc.ucFirst(StringHelper.getString("exerelin_agents", "rebelStrength")) + ": " + rebelStrength);
			text.highlightFirstInLastPara("" + rebelStrength, rebelStrength > market.getSize() * 5 ? 
					highlightColor : negativeColor);
		}
		
				
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
                
		StatsTracker.getStatsTracker().notifyAgentsUsed(1);
		return super.execute(ruleId, dialog, params, memoryMap);
	}
}
