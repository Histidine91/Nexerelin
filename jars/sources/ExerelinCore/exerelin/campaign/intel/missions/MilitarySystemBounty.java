package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.PirateSystemBounty;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.FactionBountyIntel;
import exerelin.utilities.StringHelper;

import java.awt.*;

public class MilitarySystemBounty extends PirateSystemBounty {
    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        //genRandom = Misc.random;

        if (barEvent) {
            String post = null;
            post = pickOne(Ranks.POST_BASE_COMMANDER, Ranks.POST_STATION_COMMANDER, Ranks.POST_SENIOR_EXECUTIVE);
            setGiverTags(Tags.CONTACT_MILITARY);
            setGiverPost(post);
            if (post.equals(Ranks.POST_SENIOR_EXECUTIVE)) {
                setGiverImportance(pickHighImportance());
            } else {
                setGiverImportance(pickImportance());
            }
            findOrCreateGiver(createdAt, false, false);
            setGiverIsPotentialContactOnSuccess();
        }

        PersonAPI person = getPerson();
        if (person == null) return false;

        if (!setPersonMissionRef(person, "$nex_msb_ref")) {
            return false;
        }

        //requireMarketFaction(Factions.PIRATES);
        requireMarketFactionHostileTo(person.getFaction().getId());
        requireMarketIsMilitary();
        requireMarketFactionNotPlayer();
        requireMarketFactionNot(Factions.PIRATES, Factions.LUDDIC_PATH);
        int maxSize = getMaxMarketSize(person.getImportance());
        int minSize = maxSize - 2;  // not actually minimum, just preferred
        if (minSize > 6) minSize = 6;
        requireMarketSizeAtMost(maxSize);
        preferMarketSizeAtLeast(minSize);
        preferMarketFactionHostileTo(Factions.PLAYER);

        market = pickMarket();

        if (market == null || market.getStarSystem() == null) return false;
        if (!setMarketMissionRef(market, "$nex_msb_ref")) { // just to avoid targeting the same base with multiple bounties
            return false;
        }

        makeImportant(market, "$nex_msb_ref", Stage.BOUNTY);

        system = market.getStarSystem();

        faction = person.getFaction();
        enemy = market.getFaction();

        baseBounty = getRoundNumber(BASE_BOUNTY * getRewardMult());

        setStartingStage(Stage.BOUNTY);
        setSuccessStage(Stage.DONE);
        setNoAbandon();
        setNoRepChanges();
        setStageOnHostilitiesEnded(Stage.DONE, person, market);

        connectWithDaysElapsed(Stage.BOUNTY, Stage.DONE, BOUNTY_DAYS);

        addTag(Tags.INTEL_BOUNTY);

        int extraPatrols = genRandom.nextInt(3) + genRandom.nextInt(2);

        FleetSize [] sizes = new FleetSize [] {
                FleetSize.MEDIUM,
                FleetSize.MEDIUM,
                FleetSize.LARGE,
                FleetSize.VERY_LARGE,
        };

        for (int i = 0; i < extraPatrols; i++) {
            FleetSize size = sizes[i % sizes.length];
            beginWithinHyperspaceRangeTrigger(system, 3f, false, Stage.BOUNTY);
            triggerCreateFleet(size, FleetQuality.DEFAULT, market.getFactionId(), FleetTypes.PATROL_MEDIUM, system);
            triggerAutoAdjustFleetStrengthMajor();
            triggerSpawnFleetNear(market.getPrimaryEntity(), null, null);
            triggerOrderFleetPatrol(system, true, Tags.PLANET, Tags.JUMP_POINT, Tags.OBJECTIVE);
            endTrigger();
        }

        return true;
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$nex_msb_barEvent", isBarEvent());
        set("$nex_msb_faction", market.getFaction().getDisplayName());
        set("$nex_msb_theFaction", market.getFaction().getDisplayNameWithArticle());
        set("$nex_msb_factionColor", market.getFaction().getBaseUIColor());
        set("$nex_msb_manOrWoman", getPerson().getManOrWoman());
        set("$nex_msb_baseBounty", Misc.getWithDGS(baseBounty));
        set("$nex_msb_days", "" + (int) BOUNTY_DAYS);
        set("$nex_msb_systemName", system.getNameWithLowercaseType());
        set("$nex_msb_systemNameShort", system.getNameWithLowercaseTypeShort());
        set("$nex_msb_baseName", market.getName());
        set("$nex_msb_dist", getDistanceLY(market));
    }

    @Override
    public void addDescriptionForCurrentStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        float pad = 3f;
        Color h = Misc.getHighlightColor();
        Color tc = getBulletColorForMode(ListInfoMode.IN_DESC);
        if (currentStage == Stage.BOUNTY) {
            float elapsed = getElapsedInCurrentStage();
            int d = (int) Math.round(BOUNTY_DAYS - elapsed);
            PersonAPI person = getPerson();

            String str = FactionBountyIntel.getString("systemBountyIntel_desc");
            str = StringHelper.substituteToken(str, "$location", system.getNameWithLowercaseType());
            str = StringHelper.substituteToken(str, "$onOrAt", market.getOnOrAt());
            str = StringHelper.substituteToken(str, "$market", market.getName());

            info.addPara(str, opad, enemy.getBaseUIColor(), enemy.getPersonNamePrefix());

            if (isEnding()) {
                info.addPara(FactionBountyIntel.getString("factionBountyIntel_descEnded"), opad);
                return;
            }

            bullet(info);
            info.addPara(FactionBountyIntel.getString("factionBountyIntel_bulletBaseReward"),
                    opad, tc, h, Misc.getDGSCredits(baseBounty));
            addDays(info, FactionBountyIntel.getString("factionBountyIntel_bulletRemaining"), d, tc);
            unindent(info);

            str = FactionBountyIntel.getString("systemBountyIntel_desc2");
            str = StringHelper.substituteToken(str, "$person", getPerson().getNameString());
            str = StringHelper.substituteFactionTokens(str, faction);

            info.addPara(str, opad);

        } else if (currentStage == Stage.DONE) {
            info.addPara(FactionBountyIntel.getString("factionBountyIntel_descEnded"), opad);
        }

        if (latestResult != null) {
            info.addPara(FactionBountyIntel.getString("factionBountyIntel_descResult1"), opad);
            bullet(info);
            info.addPara(FactionBountyIntel.getString("factionBountyIntel_descResult2"), pad, tc, h,
                    Misc.getDGSCredits(latestResult.payment));
            if (Math.round(latestResult.fraction * 100f) < 100f) {
                info.addPara(FactionBountyIntel.getString("factionBountyIntel_descResult3"), 0f, tc, h,
                        "" + (int) Math.round(latestResult.fraction * 100f) + "%");
            }
            if (latestResult.repPerson != null) {
                CoreReputationPlugin.addAdjustmentMessage(latestResult.repPerson.delta, null, getPerson(),
                        null, null, info, tc, false, 0f);
            }
            if (latestResult.repFaction != null) {
                CoreReputationPlugin.addAdjustmentMessage(latestResult.repFaction.delta, faction, null,
                        null, null, info, tc, false, 0f);
            }
            unindent(info);
        }
    }

    @Override
    public String getBaseName() {
        return StringHelper.getString("nex_bounties", "systemBountyIntel_title") + ": " + system.getBaseName();
    }

    protected int getMaxMarketSize(PersonImportance importance) {
        switch (importance) {
            case VERY_LOW:
                return 4;
            case LOW:
                return 5;
            case MEDIUM:
                return 6;
            case HIGH:
                return 7;
            case VERY_HIGH:
            default:
                return 99;
        }
    }
}
