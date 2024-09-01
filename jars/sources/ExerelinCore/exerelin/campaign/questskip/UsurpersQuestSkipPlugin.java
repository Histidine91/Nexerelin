package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.People;

public class UsurpersQuestSkipPlugin extends BaseQuestSkipPlugin {

    @Override
    public void onNewGameAfterTimePass() {
        PersonAPI hyder = Global.getSector().getImportantPeople().getPerson(People.HYDER);
        hyder.getMemoryWithoutUpdate().set("$trust", 2);
        //hyder.getRelToPlayer().setRel(-0.01f);

        PersonAPI macario = Global.getSector().getImportantPeople().getPerson(People.MACARIO);
        macario.getRelToPlayer().setRel(0.01f);

        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        String variantId = "executor_Hull";
        ShipVariantAPI variant = Global.getSettings().getVariant(variantId).clone();
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        pf.getFleetData().addFleetMember(member);

        float crew = member.getMinCrew();
        float supplies = member.getCargoCapacity() * 0.5f;
        pf.getCargo().addCrew((int)crew);
        pf.getCargo().addSupplies(supplies);
        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());

        pf.getCargo().addWeapons("kineticblaster", 3);
        pf.getCargo().addWeapons("gigacannon", 1);
    }
}
