package exerelin.campaign.ai.concern.special;

import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.DiplomacyConcern;
import lombok.extern.log4j.Log4j;

@Log4j
public class TestRaiseRelations extends DiplomacyConcern {

    @Override
    public boolean generate() {
        priority.modifyFlat("base", 100, StrategicAI.getString("statBase", true));
        return true;
    }
}
