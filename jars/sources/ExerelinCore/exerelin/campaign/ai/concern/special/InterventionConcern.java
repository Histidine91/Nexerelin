package exerelin.campaign.ai.concern.special;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.DiplomacyConcern;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import lombok.Getter;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class InterventionConcern extends DiplomacyConcern {

    protected boolean isFriendOrAlly;

    // first faction will be our ally if applicable, this will be whoever they're fighting with
    @Getter protected FactionAPI otherFaction;


    @Override
    public boolean generate() {
        
        // check every allied or friendly faction, check all their enemies, check if we aren't already hostile against them but could be
        // later, peacekeeper factions could check all factions in the sector, not just allies
        String aiid = ai.getFactionId();
        FactionAPI aif = ai.getFaction();

        Set<String> friendsToCheck = new LinkedHashSet<>();
        Set existingConcerns = getExistingConcernItems();

        Alliance all = AllianceManager.getFactionAlliance(aiid);
        if (all != null) friendsToCheck.addAll(all.getMembersCopy());

        // look at Cooperative allies too if Helps Allies
        if (DiplomacyTraits.hasTrait(aiid, DiplomacyTraits.TraitIds.HELPS_ALLIES)) {
            for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
                if (friendsToCheck.contains(factionId)) continue;
                if (aif.getRelationshipLevel(factionId) == RepLevel.COOPERATIVE) {
                    friendsToCheck.add(factionId);
                }
            }
        }
        friendsToCheck.remove(aiid);

        WeightedRandomPicker<Pair<String, String>> picker = new WeightedRandomPicker<>();

        for (String friendId : friendsToCheck) {
            Set<String> nonCommonEnemies = getNonCommonEnemies(friendId);
            for (String potentialEnemy : nonCommonEnemies) {
                if (existingConcerns.contains(potentialEnemy)) continue;
                if (aif.getRelationshipLevel(potentialEnemy).isAtWorst(RepLevel.FRIENDLY)) continue;
                if (NexFactionConfig.getMinRelationship(aiid, potentialEnemy) > -0.5) continue;
                
                float weight = 100 - aif.getRelationship(potentialEnemy) * 100;

                picker.add(new Pair<String, String>(friendId, potentialEnemy), weight);
            }
        }

        Pair<String, String> intervention = picker.pick();
        if (intervention == null) return false;

        faction = Global.getSector().getFaction(intervention.one);
        otherFaction = Global.getSector().getFaction(intervention.two);

        return true;
    }

    @Override
    public void update() {
        if (!faction.isHostileTo(otherFaction)) {
            end();
            return;
        }
        if (ai.getFaction().isHostileTo(otherFaction)) {
            end();
            return;
        }
        if (isFactionCommissionedPlayer(otherFaction)) {
            end();
            return;
        }

        super.update();
    }

    @Override
    public void reapplyPriorityModifiers() {
        super.reapplyPriorityModifiers();

        float relationshipMod = ai.getFaction().getRelationship(otherFaction.getId()) * 100;
        String desc = String.format(StrategicAI.getString("statRelationship", true), otherFaction.getDisplayName());
        priority.modifyFlat("otherFactionRel", relationshipMod, desc);
    }

    protected Set<String> getNonCommonEnemies(String friendId) {
        FactionAPI friend = Global.getSector().getFaction(friendId);
        Set<String> enemies = new HashSet<>(DiplomacyManager.getFactionsAtWarWithFaction(friend, NexConfig.allowPirateInvasions, true, false));
        enemies.removeAll(DiplomacyManager.getFactionsAtWarWithFaction(ai.getFaction(), NexConfig.allowPirateInvasions, true, false));

        if (Misc.getCommissionFaction() != null) enemies.remove(Factions.PLAYER);

        return enemies;
    }

    @Override
    public Set getExistingConcernItems() {
        Set<FactionAPI> factions = new HashSet<>();
        for (StrategicConcern concern : getExistingConcernsOfSameType()) {
            // if a potential enemy faction is already in another concern, we'll likely intervene against it there, no need anything here
            if (!(concern instanceof InterventionConcern)) continue;
            InterventionConcern ic = (InterventionConcern)concern;
            factions.add(ic.otherFaction);
        }
        return factions;
    }
}
