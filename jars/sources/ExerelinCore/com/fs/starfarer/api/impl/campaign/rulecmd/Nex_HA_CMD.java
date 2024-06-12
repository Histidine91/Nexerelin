package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.hostileactivity.NexLuddicChurchHostileActivityFactor;
import exerelin.campaign.intel.hostileactivity.NexPerseanLeagueHostileActivityFactor;
import exerelin.campaign.intel.hostileactivity.NexSindrianDiktatHostileActivityFactor;
import exerelin.campaign.intel.hostileactivity.NexSindrianDiktatStandardActivityCause;

import java.util.List;
import java.util.Map;

public class Nex_HA_CMD extends BaseCommandPlugin {

    protected SectorEntityToken other;

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        OptionPanelAPI options = dialog.getOptionPanel();
        TextPanelAPI text = dialog.getTextPanel();
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        other = dialog.getInteractionTarget();
        CargoAPI cargo = pf.getCargo();


        String action = params.get(0).getString(memoryMap);

        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);

        switch (action) {
            case "isPLProtectingPlayerSpace": {
                HostileActivityEventIntel intel = HostileActivityEventIntel.get();
                if (intel != null) {
                    EventFactor factor = intel.getFactorOfClass(NexPerseanLeagueHostileActivityFactor.class);
                    return factor.getProgress(intel) > 0;
                }
                return false;
            }
            case "knightsHasslingPlayerColonies": {
                HostileActivityEventIntel intel = HostileActivityEventIntel.get();
                if (intel != null) {
                    HostileActivityCause2 cause = intel.getActivityCause(NexLuddicChurchHostileActivityFactor.class, LuddicChurchStandardActivityCause.class);
                    if (cause != null) return cause.getProgress() > 0;
                }
                return false;
            }
            case "diktatConcernedByFuelProd": {
                HostileActivityEventIntel intel = HostileActivityEventIntel.get();
                if (intel != null) {
                    HostileActivityCause2 cause = intel.getActivityCause(NexSindrianDiktatHostileActivityFactor.class, NexSindrianDiktatStandardActivityCause.class);
                    if (cause != null) return cause.getProgress() > 0;
                    cause = intel.getActivityCause(NexSindrianDiktatHostileActivityFactor.class, SindrianDiktatStandardActivityCause.class);
                    if (cause != null) return cause.getProgress() > 0;
                }
                return false;
            }
        }

        return false;
    }
}
