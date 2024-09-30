package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.StringHelper;
import lombok.Getter;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class InterventionConcern extends DiplomacyConcern {

    protected boolean isFriendOrAlly;

    @Getter protected FactionAPI friendFaction;


    @Override
    public boolean generate() {
        
        // check every allied or friendly faction, check all their enemies, check if we aren't already hostile against them but could be
        // later, peacekeeper factions could check all factions in the sector, not just allies
        String aiid = ai.getFactionId();
        FactionAPI aif = ai.getFaction();
        DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(aiid);

        Set<String> friendsToCheck = new LinkedHashSet<>();

        // process existing concerns set
        Set existingConcerns = getExistingConcernItems();
        Set<String> existingConcerns2 = new HashSet<>();
        for (Object obj : existingConcerns) {
            FactionAPI faction = (FactionAPI)obj;
            Alliance all = AllianceManager.getFactionAlliance(faction.getId());
            if (all != null) existingConcerns2.addAll(all.getMembersCopy());
            else existingConcerns2.add(faction.getId());
        }

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
                if (existingConcerns2.contains(potentialEnemy)) continue;
                if (aif.getRelationshipLevel(potentialEnemy).isAtWorst(RepLevel.FRIENDLY)) continue;
                if (NexFactionConfig.getMinRelationship(aiid, potentialEnemy) > -0.5) continue;
                if (brain != null && brain.getCeasefires().containsKey(potentialEnemy)) continue;
                
                float weight = 100 - aif.getRelationship(potentialEnemy) * 100;

                picker.add(new Pair<String, String>(friendId, potentialEnemy), weight);
            }
        }

        Pair<String, String> intervention = picker.pick();
        if (intervention == null) return false;

        faction = Global.getSector().getFaction(intervention.two);
        friendFaction = Global.getSector().getFaction(intervention.one);

        return true;
    }

    @Override
    public void update() {
        if (!SectorManager.isFactionAlive(friendFaction.getId()) || !SectorManager.isFactionAlive(faction.getId())) {
            end();
            return;
        }
        if (!faction.isHostileTo(friendFaction)) {
            end();
            return;
        }
        if (ai.getFaction().isHostileTo(faction)) {
            end();
            return;
        }
        if (isFactionCommissionedPlayer(faction)) {
            end();
            return;
        }

        super.update();
    }

    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        LabelAPI label = super.createTooltipDesc(tooltip, holder, pad);
        label.setText(StringHelper.substituteFactionTokens(label.getText(), "other", friendFaction));
        label.setHighlight(friendFaction.getDisplayNameWithArticleWithoutArticle(), faction.getDisplayNameWithArticleWithoutArticle());
        label.setHighlightColors(friendFaction.getBaseUIColor(), faction.getBaseUIColor());

        return label;
    }

    @Override
    public void reapplyPriorityModifiers() {
        priority.modifyFlat("base", 100, StrategicAI.getString("statBase", true));
        super.reapplyPriorityModifiers();
        float relationshipMod = ai.getFaction().getRelationship(friendFaction.getId()) * 100;
        String desc = String.format(StrategicAI.getString("statRelationship", true), friendFaction.getDisplayName());
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
            factions.add(ic.faction);
        }
        return factions;
    }

    @Override
    public String getIcon() {
        return faction.getCrest();
    }
}
