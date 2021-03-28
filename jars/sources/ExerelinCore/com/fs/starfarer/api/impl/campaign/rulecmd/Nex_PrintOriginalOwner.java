package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import java.util.List;
import java.util.Map;

public class Nex_PrintOriginalOwner extends BaseCommandPlugin {
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = dialog.getInteractionTarget().getMarket();
		String origOwnerId = NexUtilsMarket.getOriginalOwner(market);
		FactionAPI origOwner = Global.getSector().getFaction(origOwnerId);
		
		TextPanelAPI text = dialog.getTextPanel();
		text.setFontSmallInsignia();
		text.addPara(StringHelper.getString("exerelin_markets", "originalOwner"), origOwner.getBaseUIColor(), origOwner.getDisplayName());
		text.setFontInsignia();
		
        return true;
    }
}
