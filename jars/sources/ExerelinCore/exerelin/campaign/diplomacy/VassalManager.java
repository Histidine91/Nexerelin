package exerelin.campaign.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.AllianceEventListener;
import exerelin.campaign.intel.AllianceVoteIntel;
import exerelin.utilities.StringHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Because DiplomacyManager is already too damn long
public class VassalManager implements AllianceEventListener, EconomyTickListener {

    public static final String DATA_KEY = "nex_vassalManager";
    public static final float BASE_LIBERTY_DESIRE = 10;
    public static final float BASE_RELATIONSHIP_OFFSET = 50;

    public static final Map<String, Float> LIBERTY_DESIRE_TRAIT_MODS = new HashMap<>();

    static {
        LIBERTY_DESIRE_TRAIT_MODS.put(DiplomacyTraits.TraitIds.WEAK_WILLED, -20f);
        LIBERTY_DESIRE_TRAIT_MODS.put(DiplomacyTraits.TraitIds.STALWART, 20f);
        LIBERTY_DESIRE_TRAIT_MODS.put(DiplomacyTraits.TraitIds.ANARCHIST, 20f);
        LIBERTY_DESIRE_TRAIT_MODS.put(DiplomacyTraits.TraitIds.FOREVERWAR, 40f);
    }

    protected Map<String, String> vassalages = new HashMap<>(); // vassal to overlord
    protected Map<String, MutableStat> libertyDesires = new HashMap<>();

    protected Object readResolve() {
        if (libertyDesires == null) libertyDesires = new HashMap<>();
        return this;
    }

    public static VassalManager getInstance() {
        return (VassalManager)Global.getSector().getPersistentData().get(DATA_KEY);
    }

    public static VassalManager create() {
        VassalManager manager = new VassalManager();
        Global.getSector().getPersistentData().put(DATA_KEY, manager);
        Global.getSector().getListenerManager().addListener(manager, false);
        return manager;
    }

    public void vassalize(String vassalId, String overlordId) {
        FactionAPI vassal = Global.getSector().getFaction(vassalId);
        FactionAPI overlord = Global.getSector().getFaction(overlordId);

        // remove vassal from their current alliance if they have one
        if (!AllianceManager.areFactionsAllied(vassalId, overlordId)) {
            AllianceManager.leaveAlliance(vassalId, false);
        }
        AllianceManager.createAlliance(vassalId, overlordId);

        vassalages.put(vassalId, overlordId);
        libertyDesires.put(vassalId, new MutableStat(BASE_LIBERTY_DESIRE));
        updateLibertyDesire(vassalId);

        // TODO: intel event? maybe just stuff the info in the vassal's diplo profile
    }

    public void devassalize(String vassalId, boolean leaveAlliance) {
        String overlordId = getOverlord(vassalId);
        if (overlordId == null) return;
        FactionAPI vassal = Global.getSector().getFaction(vassalId);

        if (leaveAlliance) AllianceManager.leaveAlliance(vassalId, false);
        vassalages.remove(vassalId);
        libertyDesires.remove(vassalId);
    }

    @Nullable
    public String getOverlord(String vassalId) {
        return vassalages.get(vassalId);
    }

    // TODO: test to make sure it returns non-null
    public List<String> getVassals(String overlordId) {
        return vassalages.entrySet().stream().filter(entry -> entry.getValue().equals(overlordId)).map(it -> it.getKey()).toList();
    }

    public boolean isVassal(String factionId) {
        return vassalages.containsKey(factionId);
    }

    public void updateLibertyDesires() {
        for (String vassalId : vassalages.keySet()) {
            updateLibertyDesire(vassalId);
        }
    }

    public void updateLibertyDesire(String vassalId) {
        FactionAPI vassal = Global.getSector().getFaction(vassalId);
        String overlordId = vassalages.get(vassalId);
        MutableStat desire = getLibertyDesire(vassalId);

        float rel = vassal.getRelationship(overlordId);
        desire.modifyFlat("relationship", -rel + BASE_RELATIONSHIP_OFFSET, Misc.ucFirst(StringHelper.getString("relationship")));

        applyTraitEffects(vassalId, desire);
        // add other things that modify desire here
    }

    protected void applyTraitEffects(String vassalId, MutableStat desire) {
        for (String traitId : LIBERTY_DESIRE_TRAIT_MODS.keySet()) {
            if (DiplomacyTraits.hasTrait(vassalId, traitId)) {
                desire.modifyFlat("trait_" + traitId, LIBERTY_DESIRE_TRAIT_MODS.get(traitId), Misc.ucFirst(DiplomacyTraits.getTrait(traitId).name));
            }
        }
    }

    public MutableStat getLibertyDesire(String vassalId) {
        return libertyDesires.remove(vassalId);
    }

    public void vassalJoinAlliance(Alliance alliance, String overlordId) {
        for (String vassalId : vassalages.keySet()) {
            String thisOverlordId = vassalages.get(vassalId);
            if (thisOverlordId.equals(overlordId)) {
                AllianceManager.getManager().joinAlliance(vassalId, alliance, true);
            }
        }
    }

    public void vassalLeaveAlliance(Alliance alliance, String overlordId) {
        for (String vassalId : vassalages.keySet()) {
            String thisOverlordId = vassalages.get(vassalId);
            if (thisOverlordId.equals(overlordId)) {
                AllianceManager.getManager().leaveAlliance(vassalId, alliance, true, true);
            }
        }
    }

    @Override
    public void reportAllianceFormed(Alliance alliance, FactionAPI faction1, FactionAPI faction2) {
        vassalJoinAlliance(alliance, faction1.getId());
        vassalJoinAlliance(alliance, faction2.getId());
    }

    @Override
    public void reportFactionJoinedAlliance(Alliance alliance, FactionAPI faction) {
        vassalJoinAlliance(alliance, faction.getId());
    }

    @Override
    public void reportFactionLeftAlliance(Alliance alliance, FactionAPI faction) {
        vassalLeaveAlliance(alliance, faction.getId());
    }

    @Override
    public void reportAlliancesMerged(Alliance into, Alliance other) {
        // handled by individual join calls
    }

    @Override
    public void reportAllianceDissolved(Alliance alliance) {
        // handled by individual leave calls
    }

    @Override
    public void reportAllianceVote(Alliance alliance, AllianceVoteIntel vote) {

    }

    @Override
    public void reportEconomyTick(int iterIndex) {

    }

    @Override
    public void reportEconomyMonthEnd() {

    }
}
