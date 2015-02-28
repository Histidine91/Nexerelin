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
                SectorAPI sector = Global.getSector();
                SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();

                List<CargoStackAPI> stacks = sector.getPlayerFleet().getCargo().getStacksCopy();
                for (CargoStackAPI stack : stacks)
                {
                    if (stack.getCommodityId().equals("agent"))
                    {
                        if (stack.getSize() < 1) return false;
                        stack.subtract(1f);
                        break;
                    }
                }
                
                MarketAPI market = target.getMarket();
                FactionAPI playerAlignedFaction = sector.getFaction(PlayerFactionStore.getPlayerFactionId());
                WeightedRandomPicker<String> targetPicker = new WeightedRandomPicker<>();
                
                List<String> factions = SectorManager.getLiveFactionIdsCopy();
                for (String factionId : factions)
                {
                    float weight = 0.001f;
                    RepLevel rep = playerAlignedFaction.getRelationshipLevel(factionId);
                    if (TARGET_WEIGHTINGS.containsKey(rep))
                        weight = TARGET_WEIGHTINGS.get(rep);
                    
                    targetPicker.add(factionId, weight);
                }
                String targetId = targetPicker.pick();
                if (targetId == null) return false;
                
                CovertOpsManager.agentLowerRelations(market, playerAlignedFaction, market.getFaction(), sector.getFaction(targetId), true);

                return true;
        }
}
