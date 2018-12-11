package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.events.InvasionFleetEvent;
import exerelin.utilities.StringHelper;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.InvasionFleetManager.InvasionFleetData;
import java.awt.Color;
import java.util.HashMap;

public class FleetRequestFire extends FleetRequestActionBase {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		SectorAPI sector = Global.getSector();
		SectorEntityToken target = dialog.getInteractionTarget();
		MarketAPI targetMarket = target.getMarket();
		if (targetMarket == null) return false;
		
		FactionAPI fleetFaction = sector.getFaction(PlayerFactionStore.getPlayerFactionId());
		
		MarketAPI sourceMarket = getSourceMarketForInvasion(fleetFaction, targetMarket);
		if (targetMarket.getFactionId().equals(ExerelinConstants.PLAYER_NPC_ID))
		{
			sourceMarket = targetMarket;
			fleetFaction = sector.getFaction(ExerelinConstants.PLAYER_NPC_ID);
		}
		else if (targetMarket.getFaction() == fleetFaction) 
			sourceMarket = targetMarket;
		
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		int fp = (int)memory.getFloat("$fleetRequestFP");
		int marines = (int)memory.getFloat("$fleetRequestMarines");
		payForInvasion(fp, marines);
		
		boolean isInvasionFleet = marines > 0;
		boolean isDefenceFleet = !targetMarket.getFaction().isHostileTo(fleetFaction);
		
		String fleetType = "exerelinInvasionSupportFleet";
		if (isInvasionFleet) fleetType = "exerelinInvasionFleet";
		else if (isDefenceFleet) fleetType = "exerelinDefenceFleet";
		
		InvasionFleetManager.FleetSpawnParams fleetParams = new InvasionFleetManager.FleetSpawnParams();
		fleetParams.name = InvasionFleetManager.getFleetName(fleetType, fleetFaction.getId(), fp);
		fleetParams.fleetType = fleetType;
		fleetParams.faction = fleetFaction;
		fleetParams.fp = fp;
		fleetParams.originMarket = sourceMarket;
		fleetParams.targetMarket = targetMarket;
		fleetParams.numMarines = marines;
		fleetParams.noWander = true;
		fleetParams.noWait = true;
		
		InvasionFleetData data = null;	//InvasionFleetManager.spawnFleet(fleetParams);
		
		TextPanelAPI text = dialog.getTextPanel();
		Color hl = Misc.getHighlightColor();
		LocationAPI originLoc = sourceMarket.getPrimaryEntity().getContainingLocation();
		String origin = originLoc.getName();
		String sourceMarketName = sourceMarket.getName();
		if (!originLoc.isHyperspace()) origin = "the " + origin;
		
		String message = "";
		if (sourceMarket == targetMarket)
		{
			message = StringHelper.getStringAndSubstituteToken("exerelin_fleets", "fleetSpawnMessageLocal", "$market", sourceMarketName);
		}
		else
		{
			message = StringHelper.getString("exerelin_fleets", "fleetSpawnMessage");
			message = StringHelper.substituteToken(message, "$market", sourceMarketName);
			message = StringHelper.substituteToken(message, "$location", origin);
		}
		text.addParagraph(message);
		text.highlightInLastPara(hl, sourceMarketName, origin);
		//text.highlightInLastPara(hl, origin);
		
		if (isInvasionFleet)
		{
			SectorManager.createWarmongerEvent(targetMarket.getFactionId(), target);
			RepActionEnvelope envelope = new RepActionEnvelope(RepActions.COMBAT_NORMAL, null, dialog.getTextPanel());
			Global.getSector().adjustPlayerReputation(envelope, targetMarket.getFactionId());
			
			Map<String, Object> eventParams = new HashMap<>();
            eventParams.put("target", targetMarket);
            eventParams.put("dp", data.startingFleetPoints);
            //InvasionFleetEvent event = (InvasionFleetEvent)Global.getSector().getEventManager().startEvent(new CampaignEventTarget(sourceMarket), "exerelin_invasion_fleet", eventParams);
			//data.event = event;
			//event.reportStart();
		}
		return true;
	}
}
