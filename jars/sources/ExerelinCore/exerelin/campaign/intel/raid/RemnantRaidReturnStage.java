package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import static com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage.STRAGGLER;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import java.util.List;

/**
 *
 * @author Histidine
 */
public class RemnantRaidReturnStage extends ReturnStage {
	
	public RemnantRaidReturnStage(RaidIntel raid) {
		super(raid);
	}
	
	@Override
	public void giveReturnOrdersToStragglers(List<RouteManager.RouteData> stragglers) 
	{
		((RemnantRaidIntel)intel).giveReturnOrdersToStragglers(this, stragglers);
	}
}
