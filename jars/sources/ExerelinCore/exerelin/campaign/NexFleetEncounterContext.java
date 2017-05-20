package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide.OfficerEngagementData;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import static com.fs.starfarer.api.impl.campaign.FleetEncounterContext.prepareShipForRecovery;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class NexFleetEncounterContext extends FleetEncounterContext {

	//==========================================================================
	// START OFFICER DEATH HANDLING
	
    protected List<OfficerDataAPI> playerLostOfficers = new ArrayList<>(10);
    protected List<OfficerDataAPI> playerOfficersEscaped = new ArrayList<>(10);
    protected List<OfficerDataAPI> playerOfficersKIA = new ArrayList<>(10);
    protected List<OfficerDataAPI> playerOfficersMIA = new ArrayList<>(10);
    protected List<OfficerDataAPI> playerRecoverableOfficerLosses = new ArrayList<>(10);
    protected List<OfficerDataAPI> playerUnconfirmedOfficers = new ArrayList<>(10);

    public List<OfficerDataAPI> getPlayerLostOfficers() {
        return Collections.unmodifiableList(playerLostOfficers);
    }

    public List<OfficerDataAPI> getPlayerOfficersEscaped() {
        return Collections.unmodifiableList(playerOfficersEscaped);
    }

    public List<OfficerDataAPI> getPlayerOfficersKIA() {
        return Collections.unmodifiableList(playerOfficersKIA);
    }

    public List<OfficerDataAPI> getPlayerOfficersMIA() {
        return Collections.unmodifiableList(playerOfficersMIA);
    }

    public List<OfficerDataAPI> getPlayerRecoverableOfficers() {
        return Collections.unmodifiableList(playerRecoverableOfficerLosses);
    }

    public List<OfficerDataAPI> getPlayerUnconfirmedOfficers() {
        return Collections.unmodifiableList(playerUnconfirmedOfficers);
    }

    public void recoverPlayerOfficers() {
        for (OfficerDataAPI officer : playerRecoverableOfficerLosses) {
            Global.getSector().getPlayerFleet().getFleetData().addOfficer(officer.getPerson());
            officer.addXP(0);
            if (officer.canLevelUp()) {
                officer.getSkillPicks();
            }
			playerUnconfirmedOfficers.remove(officer);
        }
        playerRecoverableOfficerLosses.clear();
    }

    protected void applyOfficerLosses(EngagementResultAPI result) {
        EngagementResultForFleetAPI winner = result.getWinnerResult();
        EngagementResultForFleetAPI loser = result.getLoserResult();

        /* For display only; no need to retain */
        playerOfficersEscaped.clear();
        playerOfficersKIA.clear();
        playerOfficersMIA.clear();

        boolean playerInvolved = battle.isPlayerInvolved();
        calculateAndApplyOfficerLosses(winner, playerInvolved);
        calculateAndApplyOfficerLosses(loser, playerInvolved);
    }

    @Override
    protected void applyResultToFleets(EngagementResultAPI result) {
        super.applyResultToFleets(result);
        applyOfficerLosses(result);
    }

    protected void calculateAndApplyOfficerLosses(EngagementResultForFleetAPI result, boolean playerInvolved) {
        if (!ExerelinConfig.officerDeaths) {
            return;
        }
        if (!playerInvolved) {
            return;
        }

        DataForEncounterSide data = getDataFor(result.getFleet());

        List<FleetMemberAPI> all = new ArrayList<>(result.getDisabled().size() + result.getDestroyed().size());
        all.addAll(result.getDisabled());
        all.addAll(result.getDestroyed());

        CampaignFleetAPI playerFleet = null;

        for (FleetMemberAPI member : all) {
            if (battle.getSourceFleet(member) == null) {
                continue;
            }
            if (member.isFighterWing()) {
                continue;
            }
            if (member.getCaptain().isDefault()) {
                continue;
            }

            OfficerDataAPI officer = battle.getSourceFleet(member).getFleetData().getOfficerData(member.getCaptain());
            if (officer == null) {
                continue;
            }

            float escapeChance;
            float recoverableChance;
            if (result.getDestroyed().contains(member)) {
                escapeChance = ExerelinUtils.lerp(0.5f, 1f, 1f - member.getStats().getCrewLossMult().getModifiedValue());
                recoverableChance = 0f;
            } else {
                escapeChance = ExerelinUtils.lerp(0.5f, 1f, 1f - member.getStats().getCrewLossMult().getModifiedValue());
                recoverableChance = 0.75f;
            }

            boolean isPlayer;
            if (battle.getSourceFleet(member).isPlayerFleet()) {
                playerFleet = battle.getSourceFleet(member);
                isPlayer = true;
            } else {
                isPlayer = false;
            }

            if ((float) Math.random() < escapeChance) {
                // Escaped!
                if (isPlayer) {
                    playerOfficersEscaped.add(officer);
                }
            } else if (recoverableChance == 0) {
                // KIA
                member.setCaptain(null);
                battle.getSourceFleet(member).getFleetData().removeOfficer(officer.getPerson());
                if (isPlayer) {
                    playerOfficersKIA.add(officer);
                    playerLostOfficers.add(officer);
                }
            } else {
                // MIA
                member.setCaptain(null);
                battle.getSourceFleet(member).getFleetData().removeOfficer(officer.getPerson());
                if (isPlayer) {
                    playerOfficersMIA.add(officer);
                    playerUnconfirmedOfficers.add(officer);
                }
                if ((float) Math.random() < recoverableChance) {
                    if (isPlayer) {
                        playerRecoverableOfficerLosses.add(officer);
                    }
                } else {
                    if (isPlayer) {
                        playerLostOfficers.add(officer);
                    }
                }
            }
        }

        // If everybody blew up...
        if (playerFleet != null && !playerFleet.isValidPlayerFleet()) {
            for (OfficerDataAPI officer : playerOfficersEscaped) {
                playerFleet.getFleetData().removeOfficer(officer.getPerson());
                playerOfficersMIA.add(officer);
                playerLostOfficers.add(officer);
                playerUnconfirmedOfficers.add(officer);
            }
            playerOfficersEscaped.clear();
        }
    }

    @Override
    protected void gainOfficerXP(DataForEncounterSide data, float xp) {
        float max = data.getMaxTimeDeployed();
        if (max < 1) {
            max = 1;
        }
        float num = data.getOfficerData().size();
        if (num < 1) {
            num = 1;
        }
        for (PersonAPI person : data.getOfficerData().keySet()) {
            OfficerEngagementData oed = data.getOfficerData().get(person);
            if (oed.sourceFleet == null || !oed.sourceFleet.isPlayerFleet()) {
                continue;
            }

            OfficerDataAPI od = oed.sourceFleet.getFleetData().getOfficerData(person);
            if (od == null) {
                continue; // shouldn't happen, as this is checked earlier before it goes into the map
            }
            float f = oed.timeDeployed / max;
            if (f < 0) {
                f = 0;
            }
            if (f > 1) {
                f = 1;
            }

            float bonus = 1f;
            if (data.getFleet().getCommanderStats() != null) {
                bonus *= data.getFleet().getCommanderStats().getDynamic().getValue("officerXPMult");
            }
            if (ExerelinConfig.officerDaredevilBonus) {
                FleetMemberAPI member = oed.sourceFleet.getFleetData().getMemberWithCaptain(person);
                if (member != null && member.isFrigate()) {
                    bonus = 2f;
                } else if (member != null && member.isDestroyer()) {
                    bonus = 1.5f;
                }
            }

            od.addXP((long) (bonus * f * xp / num), textPanelForXPGain);
        }
    }
	
	//==========================================================================
	// END OFFICER DEATH HANDLING
	
	//==========================================================================
	// START DYNASECTOR SALVAGE HANDLING
	
	public static final String DS_SALVAGED_TAG = "salvagedDS_";

    private final List<FleetMemberAPI> recoverableShipsDS = new ArrayList<>(20);
    private Random salvageRandomDS = null;

    @Override
    public List<FleetMemberAPI> getRecoverableShips(BattleAPI battle, CampaignFleetAPI winningFleet,
                                                    CampaignFleetAPI otherFleet) {

        List<FleetMemberAPI> result = new ArrayList<>(20);
        int max = Global.getSettings().getMaxShipsInFleet() -
            Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().size();
        if (Misc.isPlayerOrCombinedContainingPlayer(winningFleet) && max <= 0) {
            return result;
        }

        if (Misc.isPlayerOrCombinedContainingPlayer(otherFleet)) {
            return result;
        }

        DataForEncounterSide winnerData = getDataFor(winningFleet);
        DataForEncounterSide loserData = getDataFor(otherFleet);

        float playerContribMult = computePlayerContribFraction();
        List<FleetMemberData> enemyCasualties = winnerData.getEnemyCasualties();
        List<FleetMemberData> ownCasualties = winnerData.getOwnCasualties();
        List<FleetMemberData> all = new ArrayList<>(20);
        all.addAll(ownCasualties);
        all.addAll(enemyCasualties);

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        int count = 0;
        for (FleetMemberData data : all) {
            if (data.getMember().getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE)) {
                continue;
            }
            if (data.getStatus() != Status.DISABLED && data.getStatus() != Status.DESTROYED) {
                continue;
            }

            boolean own = ownCasualties.contains(data);
            if (own && data.getMember().isAlly()) {
                continue;
            }

            float mult = 1f;
            if (data.getStatus() == Status.DESTROYED) {
                mult = 0.5f;
            }
            if (!own) {
                mult *= playerContribMult;
            }

            boolean useOfficerRecovery = false;
            if (own) {
                useOfficerRecovery = winnerData.getMembersWithOfficerOrPlayerAsOrigCaptain().contains(data.getMember());
            }

            boolean noRecovery = false;
            if (battle != null &&
                    battle.getSourceFleet(data.getMember()) != null) {
                CampaignFleetAPI fleet = battle.getSourceFleet(data.getMember());
                if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY)) {
                    noRecovery = true;
                }
            }

            //if (true || Misc.isShipRecoverable(data.getMember(), playerFleet, own, useOfficerRecovery, battle.getSeed(), 1f * mult)) {
            if (!noRecovery && battle != null &&
                    Misc.isShipRecoverable(data.getMember(), playerFleet, own, useOfficerRecovery, battle.getSeed(),
                                           1f * mult)) {
                //if (Misc.isShipRecoverable(data.getMember(), playerFleet, battle.getSeed(), 1f * mult)) {
                data.getMember().setCaptain(Global.getFactory().createPerson());

                ShipVariantAPI variant = data.getMember().getVariant();
                variant = variant.clone();
                variant.setSource(VariantSource.REFIT);
                variant.setHullVariantId(DS_SALVAGED_TAG + variant.getHullVariantId());
                DModManager.setDHull(variant);
                data.getMember().setVariant(variant, false, true);
                DModManager.addDMods(data, own, Global.getSector().getPlayerFleet());

                float weaponProb = Global.getSettings().getFloat("salvageWeaponProb");
                float wingProb = Global.getSettings().getFloat("salvageWingProb");
                if (own) {
                    weaponProb = playerFleet.getStats().getDynamic().getValue(Stats.OWN_WEAPON_RECOVERY_MOD, weaponProb);
                    wingProb = playerFleet.getStats().getDynamic().getValue(Stats.OWN_WING_RECOVERY_MOD, wingProb);
                }

                prepareShipForRecovery(data.getMember(), false, weaponProb, wingProb, salvageRandomDS);

                result.add(data.getMember());

                count++;
                if (count >= max) {
                    break;
                }
            }
        }
        recoverableShipsDS.clear();
        recoverableShipsDS.addAll(result);
        return result;
    }

    @Override
    public Random getSalvageRandom() {
        return salvageRandomDS;
    }

    @Override
    public void setSalvageRandom(Random salvageRandom) {
        this.salvageRandomDS = salvageRandom;
    }

    @Override
    protected void lootWeapons(FleetMemberAPI member, ShipVariantAPI variant, boolean own, float mult) {
        if (variant == null) {
            return;
        }
        if (member.isFighterWing()) {
            return;
        }

        Random random = new Random();
        if (salvageRandomDS != null) {
            random = salvageRandomDS;
        }

        float p = Global.getSettings().getFloat("salvageWeaponProb");
        if (own) {
            p = Global.getSettings().getFloat("salvageOwnWeaponProb");
            p = Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.OWN_WEAPON_RECOVERY_MOD, p);
        } else {
            p = Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.ENEMY_WEAPON_RECOVERY_MOD, p);
        }
        boolean alreadyStripped = recoverableShipsDS.contains(member);

        //ShipVariantAPI variant = member.getVariant();
        //List<String> remove = new ArrayList<>(20);
        for (String slotId : variant.getNonBuiltInWeaponSlots()) {
            //if ((float) Math.random() * mult > 0.75f) {
            if (!alreadyStripped) {
                if (random.nextFloat() > mult) {
                    continue;
                }
                if (random.nextFloat() > p) {
                    continue;
                }
            }

            String weaponId = variant.getWeaponId(slotId);
            loot.addItems(CargoAPI.CargoItemType.WEAPONS, weaponId, 1);
            //remove.add(slotId);
        }

        for (String slotId : variant.getModuleSlots()) {
            WeaponSlotAPI slot = variant.getSlot(slotId);
            if (slot.isStationModule()) {
                ShipVariantAPI module = member.getModuleVariant(slotId);
                lootWeapons(member, module, own, mult);
            }
        }
        // DO NOT DO THIS - no point in removing them here since the ship is scrapped
        // and would need to clone the variant to do this right
//		for (String slotId : remove) {
//			variant.clearSlot(slotId);
//		}
        //System.out.println("Cleared variant: " + variant.getHullVariantId());
    }

    @Override
    protected void lootWings(FleetMemberAPI member, ShipVariantAPI variant, boolean own, float mult) {
        if (variant == null) {
            return;
        }
        if (member.isFighterWing()) {
            return;
        }
        Random random = new Random();
        if (salvageRandomDS != null) {
            random = salvageRandomDS;
        }

        float p = Global.getSettings().getFloat("salvageWingProb");
        if (own) {
            p = Global.getSettings().getFloat("salvageOwnWingProb");
            p = Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.OWN_WING_RECOVERY_MOD, p);
        } else {
            p = Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.ENEMY_WING_RECOVERY_MOD, p);
        }

        boolean alreadyStripped = recoverableShipsDS.contains(member);

        for (String id : variant.getFittedWings()) {
            if (!alreadyStripped) {
                if (random.nextFloat() > mult) {
                    continue;
                }
                if (random.nextFloat() > p) {
                    continue;
                }
            }

            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
            if (spec.hasTag(Tags.WING_NO_DROP)) {
                continue;
            }
            loot.addItems(CargoAPI.CargoItemType.FIGHTER_CHIP, id, 1);
        }

        for (String slotId : variant.getModuleSlots()) {
            WeaponSlotAPI slot = variant.getSlot(slotId);
            if (slot.isStationModule()) {
                ShipVariantAPI module = member.getModuleVariant(slotId);
                lootWings(member, module, own, mult);
            }
        }
    }
    
    //==========================================================================
    // END DYNASECTOR SALVAGE HANDLING
}
