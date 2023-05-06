package com.fs.starfarer.api.impl.campaign.rulecmd.salvage

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_PrintMiningInfoV2.SortColumn
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.util.Pair
import exerelin.utilities.NexUtils

class Nex_PrintMiningInfoSort {

    companion object {
        @JvmField val COMPARE_NAME =
            Comparator<Pair<*, Float>> { p1, p2 ->
                val n1 : String
                val n2: String
                when (p1.one) {
                    is ShipHullSpecAPI -> {
                        n1 = (p1.one as ShipHullSpecAPI).hullName
                        n2 = (p2.one as ShipHullSpecAPI).hullName
                    }
                    is FleetMemberAPI -> {
                        n1 = (p1.one as FleetMemberAPI).hullSpec.hullName
                        n2 = (p2.one as FleetMemberAPI).hullSpec.hullName
                    }
                    is FighterWingSpecAPI -> {
                        n1 = (p1.one as FighterWingSpecAPI).wingName
                        n2 = (p2.one as FighterWingSpecAPI).wingName
                    }
                    is WeaponSpecAPI -> {
                        n1 = (p1.one as WeaponSpecAPI).weaponName
                        n2 = (p2.one as WeaponSpecAPI).weaponName
                    }
                    is String -> {
                        return@Comparator (p1.one as String).compareTo((p2.one as String))
                    }
                    else -> return@Comparator 0
                }
                return@Comparator n1.compareTo(n2)
            }

        @JvmField val COMPARE_STRENGTH = NexUtils.PairWithFloatComparator(false)

        @JvmField val COMPARE_SIZE = Comparator<Pair<*, Float>> { p1, p2 ->
            when (p1.one) {
                is ShipHullSpecAPI -> {
                    val n1 = (p1.one as ShipHullSpecAPI).hullSize
                    val n2 = (p2.one as ShipHullSpecAPI).hullSize
                    return@Comparator n1.compareTo(n2)
                }
                is FleetMemberAPI -> {
                    val n1 = (p1.one as FleetMemberAPI).hullSpec.hullSize
                    val n2 = (p2.one as FleetMemberAPI).hullSpec.hullSize
                    return@Comparator n1.compareTo(n2)
                }
                is FighterWingSpecAPI -> {
                    return@Comparator 0
                }
                is WeaponSpecAPI -> {
                    val n1 = (p1.one as WeaponSpecAPI).size
                    val n2 = (p2.one as WeaponSpecAPI).size
                    return@Comparator n1.compareTo(n2)
                }
                else -> 0
            }
        }

        @JvmField val COMPARE_WEAPON_TYPE = Comparator<Pair<*, Float>> { p1, p2 ->
            if (p1.one is WeaponSpecAPI) {
                val n1 = (p1.one as WeaponSpecAPI).mountType
                val n2 = (p2.one as WeaponSpecAPI).mountType
                return@Comparator n1.compareTo(n2)
            }
            0
        }

        @JvmField val COMPARE_DESIGN_TYPE = Comparator<Pair<*, Float>> { p1, p2 ->
            val n1 : String
            val n2 : String
            when (p1.one) {
                is ShipHullSpecAPI -> {
                    n1 = (p1.one as ShipHullSpecAPI).manufacturer
                    n2 = (p2.one as ShipHullSpecAPI).manufacturer
                }
                is FleetMemberAPI -> {
                    n1 = (p1.one as FleetMemberAPI).hullSpec.manufacturer
                    n2 = (p2.one as FleetMemberAPI).hullSpec.manufacturer
                }
                is FighterWingSpecAPI -> {
                    n1 = (p1.one as FighterWingSpecAPI).variant.hullSpec.manufacturer
                    n2 = (p2.one as FighterWingSpecAPI).variant.hullSpec.manufacturer
                }
                is WeaponSpecAPI -> {
                    n1 = (p1.one as WeaponSpecAPI).manufacturer
                    n2 = (p2.one as WeaponSpecAPI).manufacturer
                }
                else -> return@Comparator 0
            }
            return@Comparator n1.compareTo(n2)
        }

        @JvmField val COMPARE_OP_COST = Comparator<Pair<*, Float>> { p1, p2 ->
            val n1 : Float
            val n2 : Float
            if (p1.one is ShipHullSpecAPI) {
                return@Comparator 0
            } else if (p1.one is FleetMemberAPI) {
                if (!(p1.one as FleetMemberAPI).isFighterWing) return@Comparator 0
                n1 = Global.getSettings().getFighterWingSpec((p1.one as FleetMemberAPI).specId).getOpCost(null)
                n2 = Global.getSettings().getFighterWingSpec((p2.one as FleetMemberAPI).specId).getOpCost(null)
            }else if (p1.one is FighterWingSpecAPI) {
                n1 = (p1.one as FighterWingSpecAPI).getOpCost(null)
                n2 = (p2.one as FighterWingSpecAPI).getOpCost(null)
            } else if (p1.one is WeaponSpecAPI) {
                n1 = (p1.one as WeaponSpecAPI).getOrdnancePointCost(null)
                n2 = (p2.one as WeaponSpecAPI).getOrdnancePointCost(null)
            } else return@Comparator 0
            return@Comparator n1.compareTo(n2)
        }

        @JvmField val COMPARE_SUPPLY_COST = Comparator<Pair<*, Float>> { p1, p2 ->
            val n1 : Float
            val n2 : Float
            when (p1.one) {
                is ShipHullSpecAPI -> {
                    n1 = (p1.one as ShipHullSpecAPI).suppliesPerMonth
                    n2 = (p2.one as ShipHullSpecAPI).suppliesPerMonth
                }
                is FleetMemberAPI -> {
                    n1 = (p1.one as FleetMemberAPI).hullSpec.suppliesPerMonth
                    n2 = (p2.one as FleetMemberAPI).hullSpec.suppliesPerMonth
                }
                else -> return@Comparator 0
            }
            return@Comparator n1.compareTo(n2)
        }

        @JvmStatic fun getComparator(sortColumn : SortColumn): Comparator<Pair<*, Float>> {
            when (sortColumn) {
                SortColumn.NAME -> return COMPARE_NAME
                SortColumn.STRENGTH -> return COMPARE_STRENGTH
                SortColumn.SIZE -> return COMPARE_SIZE
                SortColumn.WEAPON_TYPE -> return COMPARE_WEAPON_TYPE
                SortColumn.DESIGN_TYPE -> return COMPARE_DESIGN_TYPE
                SortColumn.OP_COST -> return COMPARE_OP_COST
                SortColumn.SUPPLY_COST -> return COMPARE_SUPPLY_COST
            }
        }
    }
}