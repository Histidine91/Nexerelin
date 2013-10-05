package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class FactionDirector {

    private String factionId;
    private StarSystemAPI homeSystem;

    private StarSystemAPI targetSystem;
    private SectorEntityToken targetSectorEntityToken;
    private StarSystemAPI targetResupplySystem;
    private SectorEntityToken targetResupplyEntityToken;

    private StarSystemAPI supportSystem;
    private SectorEntityToken supportSectorEntityToken;

    public FactionDirector(String inFactionId, StarSystemAPI system)
    {
        this.factionId = inFactionId;
        this.homeSystem = system;
        deriveFactionTargetAndSupport();
    }

    public void deriveFactionTargetAndSupport()
    {
        if(this.homeSystem == null)
            return;

        if(ExerelinUtils.doesSystemHaveEntityForFaction(this.homeSystem, this.factionId, -100000f, -0.01f))
            this.targetSystem = this.homeSystem;
        else
            this.targetSystem = ExerelinUtils.getClosestSystemForFaction(this.homeSystem, this.factionId, -100000, -0.01f);

        if(this.targetSystem != null)
        {
            this.targetSectorEntityToken = ExerelinUtils.getClosestEntityToSystemEntrance(this.targetSystem, factionId, -100000, -0.01f);

            if(this.targetSystem.getName().equalsIgnoreCase(this.homeSystem.getName())
                    && ExerelinUtils.doesSystemHaveEntityForFaction(this.homeSystem, this.factionId, 1, 100000))
                this.targetResupplySystem = this.homeSystem;
            else if(ExerelinUtils.doesSystemHaveEntityForFaction(this.targetSystem, this.factionId, 1, 100000))
                this.targetResupplySystem = this.targetSystem;
            else
                this.targetResupplySystem = ExerelinUtils.getClosestSystemForFaction(this.targetSystem, this.factionId, 1, 100000);

            if(this.targetResupplySystem == null)
                this.targetResupplySystem = this.homeSystem;

            this.targetResupplyEntityToken = ExerelinUtils.getClosestEntityToSystemEntrance(this.targetResupplySystem, this.factionId, 1, 100000);

            /*System.out.println(factionId + " home system is: " + homeSystem.getName());
            System.out.println(factionId + " target system is: " + targetSystem.getName());
            if(targetSectorEntityToken != null)
                System.out.println(factionId + " target token is: " + targetSectorEntityToken.getName());
            System.out.println(factionId + " target resupply system is: " + targetResupplySystem.getName());
            if(targetResupplyEntityToken != null)
                System.out.println(factionId + " target resupply token is: " + targetResupplyEntityToken.getName());*/
        }
        else
        {
            this.targetSectorEntityToken = null;
            this.targetResupplySystem = null;
            this.targetResupplyEntityToken = null;
        }

        deriveFactionSupportTarget();
    }

    // bit of a mess, should be redone, and probably shouldn't be in this class...
    private void deriveFactionSupportTarget()
    {
        StationRecord assistStation = null;

        for(int i = 0; i < SectorManager.getCurrentSectorManager().getSystemManagers().length; i++)
        {
            SystemManager systemManager = SectorManager.getCurrentSectorManager().getSystemManagers()[i];
            SystemStationManager systemStationManager = systemManager.getSystemStationManager();
            for(int j = 0; j < systemStationManager.getStationRecords().length; j++)
            {
                StationRecord possibleAssist = systemStationManager.getStationRecords()[j];

                if(possibleAssist.getOwner() == null)
                    continue;

                if(possibleAssist.getOwner().getFactionId().equalsIgnoreCase(this.factionId) || possibleAssist.getOwner().getGameRelationship(this.factionId) >= 1)
                {
                    // Check severity of attack
                    if((assistStation != null && possibleAssist.getNumAttacking() > assistStation.getNumAttacking()) || (assistStation == null && possibleAssist.getNumAttacking() > 0))
                        assistStation = possibleAssist;
                }
            }
        }

        if(assistStation != null)
        {
            this.supportSystem = (StarSystemAPI)assistStation.getStationToken().getContainingLocation();
            this.supportSectorEntityToken = assistStation.getStationToken();
            //System.out.println(factionId + " support system is: " + supportSystem.getName());
            //System.out.println(factionId + " support token is: " + supportSectorEntityToken.getName());
        }
        else
        {
            this.supportSystem = null;
            this.supportSectorEntityToken = null;
        }
    }

    public String getFactionId()
    {
        return this.factionId;
    }

    public StarSystemAPI getHomeSystem()
    {
        return this.homeSystem;
    }

    public StarSystemAPI getTargetSystem()
    {
        return this.targetSystem;
    }

    public StarSystemAPI getTargetResupplySystem()
    {
        return targetResupplySystem;
    }

    public StarSystemAPI getSupportSystem()
    {
        return this.supportSystem;
    }

    public SectorEntityToken getTargetSectorEntityToken()
    {
        return this.targetSectorEntityToken;
    }

    public SectorEntityToken getSupportSectorEntityToken()
    {
        return this.supportSectorEntityToken;
    }

    public SectorEntityToken getTargetResupplyEntityToken()
    {
        return this.targetResupplyEntityToken;
    }


    public void setHomeSystem(StarSystemAPI system)
    {
        this.homeSystem = system;
    }


    // Static helper
    public static FactionDirector getFactionDirectorForFactionId(String factionId)
    {
        return SectorManager.getCurrentSectorManager().getFactionDirector(factionId);
    }

    public static void updateAllFactionDirectors()
    {
        for(int i = 0; i < SectorManager.getCurrentSectorManager().getFactionDirectors().length; i++)
        {
            SectorManager.getCurrentSectorManager().getFactionDirectors()[i].deriveFactionTargetAndSupport();
        }
    }

}
