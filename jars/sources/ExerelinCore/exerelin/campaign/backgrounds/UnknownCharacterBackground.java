package exerelin.campaign.backgrounds;

import com.fs.starfarer.api.campaign.FactionSpecAPI;
import exerelin.utilities.NexFactionConfig;

public class UnknownCharacterBackground extends BaseCharacterBackground {


    @Override
    public boolean shouldShowInSelection(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return true;
    }


    @Override
    public void executeAfterGameCreation(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }
}
