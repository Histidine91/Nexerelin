package exerelin.campaign.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import java.util.EnumSet;

public class ExpiringDebrisFieldTerrainPlugin extends DebrisFieldTerrainPlugin
{
	protected Object readResolve() {
        layers = EnumSet.of(CampaignEngineLayers.TERRAIN_7A);
        return this;
    }
	
	@Override
	public void advance(float amount)
	{
		if (amount <= 0) {
			return; // happens during game load
		}
		if (!entity.isInCurrentLocation())
		{
			float days = Global.getSector().getClock().convertToDays(amount);
			elapsed += days;
			if (params.lastsDays - elapsed <= 0) {
				getEntity().setExpired(true);
			}
			return;
		}
		super.advance(amount);
	}
}