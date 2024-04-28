package exerelin.campaign.battle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.intel.specialforces.SpecialForcesIntel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.fs.starfarer.api.impl.campaign.fleets.RouteManager.*;

public class NexWarSimScript {

    /**
     * Gets a list of all the military forces present in a system that will side with our faction against the enemy, and their total strength.
     * @param faction
     * @param enemy
     * @param loc
     * @return
     */
    public static FactionStrengthReport getFactionStrengthReport(FactionAPI faction, FactionAPI enemy, LocationAPI loc) {
        FactionStrengthReport report = new FactionStrengthReport(faction.getId());

        Set<CampaignFleetAPI> seenFleets = new HashSet<>();
        Set<RouteData> seenRoutes = new HashSet<>();
        for (CampaignFleetAPI fleet : loc.getFleets()) {
            if (!willFactionSideWithUs(fleet.getFaction(), faction, enemy)) continue;
            if (fleet.isStationMode()) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET)) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_SMUGGLER)) continue;

            if (fleet.isPlayerFleet()) continue;

            report.addEntry(new FactionStrengthReportEntry(fleet));

            seenFleets.add(fleet);
        }

        for (RouteData route : getInstance().getRoutesInLocation(loc)) {
            if (route.getActiveFleet() != null && seenFleets.contains(route.getActiveFleet())) continue;

            OptionalFleetData data = route.getExtra();
            if (data == null) continue;
            if (route.getFactionId() == null) continue;
            if (!faction.getId().equals(route.getFactionId())) continue;

            if (data.strength != null) {
                report.addEntry(new FactionStrengthReportEntry(route));
                seenRoutes.add(route);
            }
        }

        for (IntelInfoPlugin iip : Global.getSector().getIntelManager().getIntel(SpecialForcesIntel.class)) {
            SpecialForcesIntel sf = (SpecialForcesIntel)iip;
            RouteData route = sf.getRoute();
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (seenFleets.contains(fleet) || seenRoutes.contains(route)) {
                continue;
            }
            if (sf.getFaction() != faction) continue;

            if (sf.getRouteAI().getCurrentTask() == null) continue;
            if (sf.getRouteAI().getCurrentTask().getSystem() != loc) continue;

            if (fleet != null) {
                report.addEntry(new FactionStrengthReportEntry(fleet));
                seenFleets.add(fleet);
            } else {
                report.addEntry(new FactionStrengthReportEntry(sf.getName(), route));
                seenRoutes.add(route);
            }
        }

        return report;
    }

    public static float getFactionAndAlliedStrength(String factionId, String enemyFactionId, StarSystemAPI system) {
        return getFactionAndAlliedStrength(Global.getSector().getFaction(factionId), Global.getSector().getFaction(enemyFactionId), system);
    }

    public static float getFactionAndAlliedStrength(FactionAPI faction, FactionAPI enemyFaction, StarSystemAPI system) {
        float strength = 0f;

//		if (system.getName().toLowerCase().contains("naraka") && Factions.PIRATES.equals(faction.getId())) {
//			System.out.println("wefwefwe");
//		}

        Set<CampaignFleetAPI> seenFleets = new HashSet<CampaignFleetAPI>();
        for (CampaignFleetAPI fleet : system.getFleets()) {
            FactionAPI fleetFaction = fleet.getFaction();
            if (fleet.isStationMode()) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET)) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_SMUGGLER)) continue;

            if (fleet.isPlayerFleet()) continue;

            if (!willFactionSideWithUs(fleetFaction, faction, enemyFaction)) continue;

            strength += fleet.getEffectiveStrength();

            seenFleets.add(fleet);
        }

        for (RouteData route : RouteManager.getInstance().getRoutesInLocation(system)) {
            if (route.getActiveFleet() != null && seenFleets.contains(route.getActiveFleet())) continue;

            OptionalFleetData data = route.getExtra();
            if (data == null) continue;
            if (route.getFactionId() == null) continue;
            FactionAPI routeFaction = Global.getSector().getFaction(route.getFactionId());
            if (!willFactionSideWithUs(routeFaction, faction, enemyFaction)) continue;

            strength += data.getStrengthModifiedByDamage();
        }

        return strength;
    }

    /**
     * @param factionToConsider
     * @param us
     * @param them Can be null, but this may lead to inaccurate results.
     * @return
     */
    public static boolean willFactionSideWithUs(FactionAPI factionToConsider, FactionAPI us, FactionAPI them) {
        if (factionToConsider == us) return true;
        if (factionToConsider == them) return false;

        // player will always side with their commissioning faction and vice-versa
        FactionAPI commFaction = Misc.getCommissionFaction();
        if (commFaction != null) {
            if (factionToConsider.isPlayerFaction() && commFaction == us) return true;
            if (us.isPlayerFaction() && factionToConsider == commFaction) return true;
        }

        if (them == null) {
            return AllianceManager.areFactionsAllied(factionToConsider.getId(), us.getId());
        }

        boolean hostileToUs = factionToConsider.isHostileTo(us);
        boolean hostileToThem = factionToConsider.isHostileTo(them);
        return !hostileToUs && hostileToThem;
    }

    public static class FactionStrengthReport {
        public String factionId;
        public List<FactionStrengthReportEntry> entries = new ArrayList<>();
        public float totalStrength;

        public void addEntry(FactionStrengthReportEntry entry) {
            entries.add(entry);
            totalStrength += entry.strength;
        }

        public FactionStrengthReport(String factionId) {
            this.factionId = factionId;
        }
    }

    public static class FactionStrengthReportEntry {
        public String name;
        public CampaignFleetAPI fleet;
        public RouteData route;
        public float strength;

        public FactionStrengthReportEntry(CampaignFleetAPI fleet) {
            this.fleet = fleet;
            name = fleet.getFullName();
            strength = fleet.getEffectiveStrength();
        }

        public FactionStrengthReportEntry(RouteData route) {
            this(String.format("%s route from %s", route.getFactionId(), route.getMarket() != null? route.getMarket().getName() : ""), route);
        }

        public FactionStrengthReportEntry(String name, RouteData route) {
            this.name = name;
            this.route = route;

            float strength = route.getExtra().strength;
            if (route.getExtra().damage != null) strength *= (1f - route.getExtra().damage);
            this.strength = strength;
        }
    }
}
