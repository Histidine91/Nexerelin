package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.*;
import com.fs.starfarer.api.impl.campaign.rulecmd.HA_CMD;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsFaction;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NexHostileActivityEventIntel extends HostileActivityEventIntel {

    // Just makes it so outposts don't count for HA reduction when fighting near player colonies
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (isEnded() || isEnding()) return;

        if (!battle.isPlayerInvolved()) return;

        if (Global.getSector().getCurrentLocation() instanceof StarSystemAPI &&
                battle.getPlayerSide().contains(primaryWinner)) {
            StarSystemAPI system = (StarSystemAPI) Global.getSector().getCurrentLocation();
            for (CampaignFleetAPI otherFleet : battle.getNonPlayerSideSnapshot()) {
                if (otherFleet.isStationMode()) {
                    {
                        PirateBaseIntel intel = PirateBaseIntel.getIntelFor(system);
                        if (intel != null && Misc.getStationFleet(intel.getMarket()) == otherFleet &&
                                HA_CMD.baseInvolved(system, intel)) {
                            int tier = intel.getTier().ordinal();
                            if (tier < 0) tier = 0;
                            if (tier > 4) tier = 4;
                            int points = -1 * Global.getSettings().getIntFromArray("HA_pirateBase", tier);
                            HAPirateBaseDestroyedFactor factor = new HAPirateBaseDestroyedFactor(points);
                            addFactor(factor);
                            return;
                        }
                    }
                    {
                        LuddicPathBaseIntel intel = LuddicPathBaseIntel.getIntelFor(system);
                        if (intel != null && Misc.getStationFleet(intel.getMarket()) == otherFleet) {
                            float totalInterest = 0f;
                            float activeCells = 0f;
                            for (StarSystemAPI curr : Misc.getPlayerSystems(false)) {
                                totalInterest += StandardLuddicPathActivityCause2.getPatherInterest(curr, 0f, 0f, 1f);
                                activeCells += StandardLuddicPathActivityCause2.getPatherInterest(curr, 0f, 0f, 1f, true);
                            }

                            if (totalInterest > 0) {
                                int flat = Global.getSettings().getInt("HA_patherBaseFlat");
                                int perCell = Global.getSettings().getInt("HA_patherBasePerActiveCell");
                                int max = Global.getSettings().getInt("HA_patherBaseMax");

                                int points = -1 * Math.min(max, (flat + perCell * (int) Math.round(activeCells)));
                                HAPatherBaseDestroyedFactor factor = new HAPatherBaseDestroyedFactor(points);
                                addFactor(factor);
                            }
                            return;
                        }
                    }
                }
            }

        }

        boolean nearAny = false;
        // MODIFIED
        for (StarSystemAPI system : getSystemsWithTruePlayerColonies()) {
            nearAny |= Misc.isNear(primaryWinner, system.getLocation());
            if (nearAny) break;
        }
        if (!nearAny) return;

        float fpDestroyed = 0;
        CampaignFleetAPI first = null;
        for (CampaignFleetAPI otherFleet : battle.getNonPlayerSideSnapshot()) {
            //if (!Global.getSector().getPlayerFaction().isHostileTo(otherFleet.getFaction())) continue;
            for (FleetMemberAPI loss : Misc.getSnapshotMembersLost(otherFleet)) {
                fpDestroyed += loss.getFleetPointCost();
                if (first == null) {
                    first = otherFleet;
                }
            }
        }

        int points = computeProgressPoints(fpDestroyed);
        if (points > 0) {
            //points = 700;
            HAShipsDestroyedFactor factor = new HAShipsDestroyedFactor(-1 * points);
            //sendUpdateIfPlayerHasIntel(factor, false); // addFactor now sends update
            addFactor(factor);
        }
    }

    public Set<StarSystemAPI> getSystemsWithTruePlayerColonies() {
        List<MarketAPI> markets = NexUtilsFaction.getPlayerMarkets(true, false);
        Set<StarSystemAPI> systems = new LinkedHashSet<>();
        for (MarketAPI market : markets) {
            StarSystemAPI system = market.getStarSystem();
            if (system != null && !systems.contains(system)) {
                systems.add(system);
            }
        }
        return systems;
    }
}
