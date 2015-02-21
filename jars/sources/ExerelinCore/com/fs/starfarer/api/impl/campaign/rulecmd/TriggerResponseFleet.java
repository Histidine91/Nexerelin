package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.EveryFrameScript;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.world.ResponseFleetManager;

public class TriggerResponseFleet extends BaseCommandPlugin {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                final SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
                
                EveryFrameScript script = new EveryFrameScript() {
			private boolean done = false;
                        private float timeElapsed = 0f;
                        private final float RESPONSE_DELAY = 0.25f;
                        
                        @Override
			public boolean runWhilePaused() {
				return false;
			}
                        @Override
			public boolean isDone() {
				return done;
			}
                        @Override
			public void advance(float amount) {
                            float days = Global.getSector().getClock().convertToDays(amount);
                            timeElapsed += days;
                            if (timeElapsed >= RESPONSE_DELAY)
                            {
                                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                                ResponseFleetManager.requestResponseFleet(target.getMarket(), playerFleet);
                                done = true;
                            }
			}
		};
                
		Global.getSector().addScript(script);
                

                return true;
        }
}
