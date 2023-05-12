package exerelin.campaign.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.misc.MiscAcademyFleetCreator;
import com.fs.starfarer.api.impl.campaign.fleets.misc.MiscFleetRouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import exerelin.campaign.SectorManager;

import java.util.Random;

public class NexMiscAcademyFleetCreator extends MiscAcademyFleetCreator {

    @Override
    public MiscFleetRouteManager.MiscRouteData createRouteParams(MiscFleetRouteManager manager, Random random) {
        MarketAPI from = pickSourceMarket(manager);
        SectorEntityToken to;
        if (SectorManager.getManager().isCorvusMode()) to = getAcademy();
        else to = (SectorEntityToken) Global.getSector().getMemoryWithoutUpdate().get("$nex_randomSector_galatiaAcademy");

        if (to == null || to.getContainingLocation().hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return null;


//		from = Global.getSector().getEconomy().getMarket("chalcedon");
//		to = Global.getSector().getEconomy().getMarket("eochu_bres").getPrimaryEntity();
//		to = Global.getSector().getEntityById("beholder_station");

        MiscFleetRouteManager.MiscRouteData result = createData(from, to);

        return result;
    }
}
