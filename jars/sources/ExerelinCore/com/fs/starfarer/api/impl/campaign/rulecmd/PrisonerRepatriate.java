package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import static com.fs.starfarer.api.Global.getSector;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinReputationPlugin;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;

public class PrisonerRepatriate extends AgentActionBase {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                
                boolean superResult = useSpecialPerson("prisoner", 1);
                if (superResult == false)
                    return false;
                
                // FIXME: may want to migrate to ExerelinReputationPlugin
                SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
                
                FactionAPI faction = target.getFaction();
                TextPanelAPI text = dialog.getTextPanel();
                ExerelinReputationPlugin.adjustPlayerReputation(faction, ExerelinConfig.prisonerRepatriateRepValue, null, text);
                return true;
        }
}
