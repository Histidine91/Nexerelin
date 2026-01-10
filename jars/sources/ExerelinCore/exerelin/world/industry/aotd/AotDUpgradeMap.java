package exerelin.world.industry.aotd;

import com.fs.starfarer.api.impl.campaign.ids.Industries;

import java.util.*;

public class AotDUpgradeMap {
    public static Map<String, Set<String>> UPGRADES = new HashMap<>();

    public static void addUpgrade(String from, String to) {
        if (!UPGRADES.containsKey(from)) {
            UPGRADES.put(from, new LinkedHashSet<>());
        }

        UPGRADES.get(from).add(to);
    }

    static {
        addUpgrade(Industries.AQUACULTURE, AotDIndustries.FISHING);
        addUpgrade(AotDIndustries.MONOCULTURE, Industries.FARMING);
        addUpgrade(AotDIndustries.EXTRACTIVE, Industries.MINING);

        addUpgrade(AotDIndustries.LIGHT_PRODUCTION, Industries.LIGHTINDUSTRY);
        addUpgrade(AotDIndustries.SMELTING, Industries.REFINING);
        addUpgrade(Industries.FUELPROD, AotDIndustries.FUEL_REFINERY);

        addUpgrade(AotDIndustries.TRADE_POST, Industries.COMMERCE);
        addUpgrade(Industries.GROUNDDEFENSES, Industries.HEAVYBATTERIES);
        addUpgrade(Industries.HEAVYINDUSTRY, Industries.ORBITALWORKS);
        addUpgrade(Industries.SPACEPORT, Industries.MEGAPORT);

        addUpgrade(Industries.ORBITALSTATION, Industries.BATTLESTATION);
        addUpgrade(Industries.BATTLESTATION, Industries.STARFORTRESS);
        addUpgrade(Industries.ORBITALSTATION_MID, Industries.BATTLESTATION_MID);
        addUpgrade(Industries.BATTLESTATION_MID, Industries.STARFORTRESS_MID);
        addUpgrade(Industries.ORBITALSTATION_HIGH, Industries.BATTLESTATION_HIGH);
        addUpgrade(Industries.BATTLESTATION_HIGH, Industries.STARFORTRESS_HIGH);

        addUpgrade(Industries.PATROLHQ, Industries.MILITARYBASE);
        addUpgrade(Industries.MILITARYBASE, Industries.HIGHCOMMAND);
    }

    /**
     * Returns only the first upgrade, if there is one. Apparently we need to include (some?) vanilla upgrades too.
     * @param from
     * @return
     */
    public static String getUpgradeId(String from) {
        Set<String> entries = UPGRADES.get(from);
        if (entries == null || entries.isEmpty()) return null;
        return (UPGRADES.get(from).iterator().next());
    }

    /*
        Chains:
            -Aquaculture -> Fishing Harbour

            Monoculture Plots -> Farming -> Artisanal Farming/Subsidized Farming

            Extractive operation -> Mining -> Mining Megaplex/Plasma Harvester

            Smelting -> Refining -> Enrichment Facility/Crystalizer

            Light Production -> Light Industry -> Commercial Manufactory/High-tech Industry/Neurochemical Laboratory

            Heavy Industry
                -> Civilian Heavy Production/Macro-Industrial Complex
                -> Orbital Works -> Orbital Fleetworks Facility/Orbital Skunkworks Facility

            Resort Center

            Maglev Central Hub

            Trade Outpost -> Commerce/Underworld

            Fuel Production -> Fuel Refinery

        Things that need doing:
            - [done?] Industry IDs in industry class constructors (all possible IDs)
            - Industry IDs and commodities in ProductionMap (only buildables, not sure if needed or the vanilla producer IDs will suffice and we let the industry class gen build the correct form)
            - Industry IDs and classes in industryClassDefs.csv (only buildables, see above)
            - [done?] Industry IDs in AotDIndustries and AotDUpgradeMap (all possible for the first, only buildables for the second)
            - [may work as it is, test] Handling for each industry in its industry gen class (only buildables)
     */
}
