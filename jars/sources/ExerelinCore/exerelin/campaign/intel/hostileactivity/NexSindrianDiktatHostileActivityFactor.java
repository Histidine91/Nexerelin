package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.SindrianDiktatHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.List;

public class NexSindrianDiktatHostileActivityFactor extends SindrianDiktatHostileActivityFactor implements InvasionListener {

    public NexSindrianDiktatHostileActivityFactor(HostileActivityEventIntel intel) {
        super(intel);
        Global.getSector().getListenerManager().addListener(this);
    }

    @Override
    public float getEventFrequency(HostileActivityEventIntel intel, BaseEventIntel.EventStageData stage) {
        if (stage.id == HostileActivityEventIntel.Stage.HA_EVENT) {
            if (getSindria(true, Factions.DIKTAT) == null) {
                return 0;
            }
        }

        return super.getEventFrequency(intel, stage);
    }

    public static MarketAPI getSindria(boolean requireMilitaryBase, String requiredFactionId) {
        if (requiredFactionId != null) {
            MarketAPI sindria = Global.getSector().getEconomy().getMarket("sindria");
            if (sindria == null || !requiredFactionId.equals(sindria.getFactionId())) {
                return null;
            }
        }

        return SindrianDiktatHostileActivityFactor.getSindria(requireMilitaryBase);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return Global.getSector().getFaction(Factions.DIKTAT).getDisplayNameLong();
    }

    @Override
    public String getNameForThreatList(boolean first) {
        return Global.getSector().getFaction(Factions.DIKTAT).getDisplayName();
    }

    @Override
    public void addBulletPointForEvent(HostileActivityEventIntel intel, BaseEventIntel.EventStageData stage, TooltipMakerAPI info,
                                       IntelInfoPlugin.ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        Color c = Global.getSector().getFaction(Factions.DIKTAT).getBaseUIColor();
        String str = NexHostileActivityManager.getString("generic_eventBullet");
        info.addPara(str, initPad, tc, c, getNameForThreatList(false));
    }

    @Override
    public void addBulletPointForEventReset(HostileActivityEventIntel intel, BaseEventIntel.EventStageData stage, TooltipMakerAPI info,
                                            IntelInfoPlugin.ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        String str = String.format(NexHostileActivityManager.getString("generic_eventBullet"), getNameForThreatList(false));
        info.addPara(str, tc, initPad);
    }

    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                String str = String.format(NexHostileActivityManager.getString("diktat_mainRowTooltip1"),
                        Global.getSector().getFaction(Factions.DIKTAT).getDisplayNameLongWithArticle());
                tooltip.addPara(str, 0f);
                str = NexHostileActivityManager.getString("diktat_mainRowTooltip2");
                tooltip.addPara(str, opad);
            }
        };
    }

    @Override
    public void addStageDescriptionForEvent(HostileActivityEventIntel intel, BaseEventIntel.EventStageData stage, TooltipMakerAPI info) {
        float small = 8f;
        float opad = 10f;

        FactionAPI faction = Global.getSector().getFaction(Factions.DIKTAT);
        Color c = faction.getBaseUIColor();
        String nameLong = faction.getDisplayNameLong();
        String nameShort = NexHostileActivityManager.getString(Global.getSettings().getModManager().isModEnabled("PAGSM") ?
                "diktat_factionNameShortSFC" : "diktat_factionNameShort");
        String loc = NexHostileActivityManager.getString("diktat_eventLoc");

        String str = String.format(NexHostileActivityManager.getString("diktat_eventDesc1"), nameLong);
        info.addPara(str, small, Misc.getNegativeHighlightColor(), NexHostileActivityManager.getString("diktat_eventDesc1Highlight"));

        str = NexHostileActivityManager.getString("diktat_eventDesc2");
        LabelAPI label = info.addPara(str, opad, c, nameShort);
        String highlights = String.format(NexHostileActivityManager.getString("diktat_eventDesc2Highlight"), nameShort);
        label.setHighlight(highlights.split(","));
        label.setHighlightColors(c, Misc.getPositiveHighlightColor(), Misc.getPositiveHighlightColor());

        stage.beginResetReqList(info, true, StringHelper.getString("crisis"), opad);
        str = NexHostileActivityManager.getString("diktat_eventResetReq1");
        str = StringHelper.substituteToken(str, "$faction", nameShort);
        info.addPara(str, 0f, c, loc);
        info.addPara(NexHostileActivityManager.getString("diktat_eventResetReq2"), 0f, c, loc);
        info.addPara(NexHostileActivityManager.getString("diktat_eventResetReq3"), 0f, c, loc);
        stage.endResetReqList(info, false, StringHelper.getString("crisis"), -1, -1);

        addBorder(info, c);
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getStageTooltipImpl(HostileActivityEventIntel intel, BaseEventIntel.EventStageData stage) {
        return super.getStageTooltipImpl(intel, stage);
    }

    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {

    }

    @Override
    public void reportInvasionRound(InvasionRound.InvasionRoundResult result, CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) {

    }

    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market, float numRounds, boolean success) {

    }

    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {
        if (market != getSindria(false)) return;
        if (newOwner.getId().equals(Factions.DIKTAT)) return;

        BaseEventIntel.EventStageData stage = intel.getDataFor(HostileActivityEventIntel.Stage.HA_EVENT);
        if (stage != null && stage.rollData instanceof HostileActivityEventIntel.HAERandomEventData &&
                ((HostileActivityEventIntel.HAERandomEventData)stage.rollData).factor == this) {
            intel.resetHA_EVENT();
        }
    }
}
