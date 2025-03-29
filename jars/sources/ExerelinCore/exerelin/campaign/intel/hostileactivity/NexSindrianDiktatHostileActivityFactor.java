package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.SindrianDiktatHostileActivityFactor;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

import java.awt.*;

public class NexSindrianDiktatHostileActivityFactor extends SindrianDiktatHostileActivityFactor {

    public NexSindrianDiktatHostileActivityFactor(HostileActivityEventIntel intel) {
        super(intel);
        Global.getSector().getListenerManager().addListener(this);
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
}
