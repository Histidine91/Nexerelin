package exerelin.fleets;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;

public abstract class ExerelinFleetBase
{
    public CampaignFleetAPI fleet;

    abstract void setFleetAssignments();
}
