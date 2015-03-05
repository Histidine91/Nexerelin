package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.HashMap;

public class AgentLowerRelations extends AgentActionBase {

        protected static final Map<RepLevel, Float> TARGET_WEIGHTINGS = new HashMap<>();
        
        static {
            TARGET_WEIGHTINGS.put(RepLevel.NEUTRAL, 0.5f);
            TARGET_WEIGHTINGS.put(RepLevel.SUSPICIOUS, 1f);
            TARGET_WEIGHTINGS.put(RepLevel.INHOSPITABLE, 2f);
            TARGET_WEIGHTINGS.put(RepLevel.HOSTILE, 5f);
            TARGET_WEIGHTINGS.put(RepLevel.VENGEFUL, 8f);
        }
    
        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                
                boolean superResult = useSpecialPerson("agent", 1);
                if (superResult == false)
                    return false;
                
                SectorAPI sector = Global.getSector();
                SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
                MarketAPI market = target.getMarket();
                String targetFactionId = target.getFaction().getId();
                String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
                FactionAPI playerAlignedFaction = sector.getFaction(playerAlignedFactionId);
                WeightedRandomPicker<String> targetPicker = new WeightedRandomPicker<>();
                
                List<String> factions = SectorManager.getLiveFactionIdsCopy();
                for (String factionId : factions)
                {
                    if (factionId.equals(targetFactionId) || factionId.equals(playerAlignedFactionId)) continue;
                    float weight = 0.001f;
                    RepLevel rep = playerAlignedFaction.getRelationshipLevel(factionId);
                    if (TARGET_WEIGHTINGS.containsKey(rep))
                        weight = TARGET_WEIGHTINGS.get(rep);
                    if (ExerelinUtilsFaction.isPirateFaction(factionId))
                        weight *= 0.25f;
                    
                    targetPicker.add(factionId, weight);
                }
                String targetId = targetPicker.pick();
                if (targetId == null) return false;
                
                CovertOpsManager.agentLowerRelations(market, playerAlignedFaction, target.getFaction(), sector.getFaction(targetId), true);

                return true;
        }
}
