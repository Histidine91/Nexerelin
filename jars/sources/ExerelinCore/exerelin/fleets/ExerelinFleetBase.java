package exerelin.fleets;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;

@Deprecated
public abstract class ExerelinFleetBase
{
    public CampaignFleetAPI fleet;

    abstract void setFleetAssignments();
}
