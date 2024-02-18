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

    public static FactionStrengthReport getFactionStrengthReport(FactionAPI faction, LocationAPI loc) {
        FactionStrengthReport report = new FactionStrengthReport(faction.getId());

        Set<CampaignFleetAPI> seenFleets = new HashSet<>();
        Set<RouteData> seenRoutes = new HashSet<>();
        for (CampaignFleetAPI fleet : loc.getFleets()) {
            if (fleet.getFaction() != faction) continue;
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
                float mult = 1f;
                if (data.damage != null) mult *= (1f - data.damage);
                report.addEntry(new FactionStrengthReportEntry("Route " + route.toString(), route, (float)Math.round(data.strength * mult)));
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
                float mult = 1f;
                if (route.getExtra().damage != null) mult *= (1f - route.getExtra().damage);
                report.addEntry(new FactionStrengthReportEntry(sf.getName(), route, (float)Math.round(route.getExtra().strength * mult)));
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
            if (willFactionSideWithUs(fleetFaction, faction, enemyFaction)) continue;
            if (fleet.isStationMode()) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET)) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_SMUGGLER)) continue;

            if (fleet.isPlayerFleet()) continue;

            strength += fleet.getEffectiveStrength();

            seenFleets.add(fleet);
        }

        for (RouteData route : RouteManager.getInstance().getRoutesInLocation(system)) {
            if (route.getActiveFleet() != null && seenFleets.contains(route.getActiveFleet())) continue;

            OptionalFleetData data = route.getExtra();
            if (data == null) continue;
            if (route.getFactionId() == null) continue;
            FactionAPI routeFaction = Global.getSector().getFaction(route.getFactionId());
            if (willFactionSideWithUs(routeFaction, faction, enemyFaction)) continue;

            strength += data.getStrengthModifiedByDamage();
        }

        return strength;
    }

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
            return factionToConsider.getRelationshipLevel(us).isAtWorst(RepLevel.FRIENDLY) || AllianceManager.areFactionsAllied(factionToConsider.getId(), us.getId());
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

        public FactionStrengthReportEntry(String name, RouteData route, float strength) {
            this.name = name;
            this.route = route;
            this.strength = strength;
        }
    }
}
