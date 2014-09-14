package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import data.scripts.world.exerelin.ExerelinSetupData;
import exerelin.ExerelinUtils;
import exerelin.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsMessaging;

import java.awt.*;

public class ExerelinModPlugin extends BaseModPlugin
{
    @Override
    public void beforeGameSave()
    {
        System.out.println("beforeGameSave");
        SectorManager.getCurrentSectorManager().getCommandQueue().executeAllCommands();
    }

    @Override
    public void onGameLoad()
    {
        System.out.println("onGameLoad");
        ExerelinSetupData.resetInstance();
        ExerelinConfig.loadSettings();
        this.checkFactionInstalls();
    }

    @Override
    public void onNewGame() {
        System.out.println("onNewGame");
        ExerelinSetupData.resetInstance();
        ExerelinConfig.loadSettings();
        this.checkFactionInstalls();
    }

    private void checkFactionInstalls()
    {
        System.out.println("Checking installed factions");

        String[] factions = SectorManager.getCurrentSectorManager().getFactionsPossibleInSector();

        for(int i = 0; i < factions.length; i++)
        {
            ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factions[i]);
            Boolean error = false;

            System.out.println("Checking " + factionConfig.factionId);

            error = attemptToCreateFleetMember(factionConfig.startingVariants, false) || error;

            error = attemptToCreateFleetMember((String[])factionConfig.fighterWings.toArray(), true) || error;
            error = attemptToCreateFleetMember((String[])factionConfig.frigateVariants.toArray(), false) || error;
            error = attemptToCreateFleetMember((String[])factionConfig.destroyerVariants.toArray(), false) || error;
            error = attemptToCreateFleetMember((String[])factionConfig.cruiserVariants.toArray(), false) || error;
            error = attemptToCreateFleetMember((String[])factionConfig.capitalVariants.toArray(), false) || error;

            error = attemptToCreateFleetMember((String[])factionConfig.carrierVariants.toArray(), false) || error;
            error = attemptToCreateFleetMember((String[])factionConfig.freighterVariants.toArray(), false) || error;
            error = attemptToCreateFleetMember((String[])factionConfig.tankerVariants.toArray(), false) || error;
            error = attemptToCreateFleetMember((String[])factionConfig.superFreighterVariants.toArray(), false) || error;
            error = attemptToCreateFleetMember((String[])factionConfig.troopTransportVariants.toArray(), false) || error;

            if(error)
            {
                ExerelinUtilsMessaging.addMessage("ERROR: Exerelin mod and " + factionConfig.factionId + " are out of sync.", Color.ORANGE);
            }

            System.out.println("");
        }
    }

    private Boolean attemptToCreateFleetMember(String[] variants, boolean fighters)
    {
        Boolean error = false;

        for(int i = 0; i < variants.length; i++)
        {
            try {
                FleetMemberAPI newMember;
                if (fighters)
                    newMember = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, variants[i]);
                else
                    newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variants[i]);
            }
            catch (Exception e)
            {
                System.out.println(e.getMessage());
                error = true;
            }
        }

        return error;
    }
}
