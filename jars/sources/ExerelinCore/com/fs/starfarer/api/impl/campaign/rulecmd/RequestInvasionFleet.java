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
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.PlayerFactionStore;
import exerelin.world.InvasionFleetManager;
import java.awt.Color;

public class RequestInvasionFleet extends InvasionFleetActionBase {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                
                SectorAPI sector = Global.getSector();
                SectorEntityToken target = dialog.getInteractionTarget();
                MarketAPI targetMarket = target.getMarket();
                if (targetMarket == null) return false;
                
                FactionAPI invaderFaction = sector.getFaction(PlayerFactionStore.getPlayerFactionId());
                
                MarketAPI sourceMarket = getSourceMarketForInvasion(invaderFaction, targetMarket);
                payForInvasion(targetMarket);
                
                InvasionFleetManager.spawnFleet(invaderFaction, sourceMarket, targetMarket, MARINE_DEFENDER_MULT, false);
                
                TextPanelAPI text = dialog.getTextPanel();
                Color hl = Misc.getHighlightColor();
                LocationAPI originLoc = sourceMarket.getPrimaryEntity().getContainingLocation();
                String origin = originLoc.getName();
                String sourceMarketName = sourceMarket.getName();
                
                text.addParagraph("An invasion fleet is being assembled at " + sourceMarketName + ", in " + origin + ". It will be underway within a few days.");
                text.highlightInLastPara(hl, sourceMarketName);
                text.highlightInLastPara(hl, origin);
                
		RepActionEnvelope envelope = new RepActionEnvelope(RepActions.COMBAT_NORMAL, null, dialog.getTextPanel());
		Global.getSector().adjustPlayerReputation(envelope, targetMarket.getFactionId());
                return true;
        }
}
