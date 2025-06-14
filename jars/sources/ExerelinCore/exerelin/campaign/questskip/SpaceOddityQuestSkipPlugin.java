package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsFleet;

public class SpaceOddityQuestSkipPlugin extends BaseQuestSkipPlugin {

    @Override
    public void onNewGameAfterTimePass() {
        if (quest != null && quest.isEnabled) quest.applyMemKeys();

        SectorEntityToken rock = Global.getSector().getEntityById("nameless_rock");
        LocationAPI loc = rock.getContainingLocation();
        for (SectorEntityToken token : loc.getAllEntities()) {
            if (!token.getMemoryWithoutUpdate().getBoolean("$onslaughtMkI")) continue;
            if (token.getCustomPlugin() instanceof DerelictShipEntityPlugin dsep) {
                FleetMemberAPI member = addShipToPlayerFleet("onslaught_mk1_Ancient");
                NexUtilsFleet.setClonedVariant(member, true);
                member.getVariant().addTag(Tags.SHIP_CAN_NOT_SCUTTLE);
                member.getVariant().addTag(Tags.SHIP_UNIQUE_SIGNATURE);

                member.setShipName(dsep.getData().ship.shipName);
                Misc.fadeAndExpire(token);
                break;
            }
        }
    }
}
