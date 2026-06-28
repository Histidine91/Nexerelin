package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.listeners.ColonySizeChangeListener;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.EventFactor;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.RaiseRelations;
import exerelin.utilities.AgentActionListener;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;
import org.magiclib.util.MagicTxt;

import java.awt.*;
import java.util.*;
import java.util.List;

public class FactionRecognitionIntel extends BaseEventIntel implements ColonyPlayerHostileActListener, EconomyTickListener,
        InvasionListener, AgentActionListener, ColonySizeChangeListener, PlayerColonizationListener {

    public static final String DATA_KEY = "nex_factionRecognition";
    public static final String MEMORY_KEY_RECOGNIZED = "$nex_factionRecognized";
    public static final int PROGRESS_MAX = 1000;

    public enum Stage {
        START,
        END
    }

    public enum RespectStage {
        RESPECTABLE,
        RENEGADE
    }

    public static Color BAR_COLOR = Global.getSettings().getColor("progressBarFleetPointsColor");

    @Getter protected int progress2;
    @Getter protected int maxProgress2 = PROGRESS_MAX;
    protected float progressDeltaRemainder2 = 0;
    @Getter protected boolean active;
    @Getter @Setter protected boolean suspended;
    //@Getter protected Set<Integer> sizesAttained = new HashSet<>();   // don't overcomplicate it, any upsize is good

    @Getter protected List<EventStageData> stages2 = new ArrayList<EventStageData>();
    @Getter protected Set<String> completedCrises = new HashSet<>();

    // the same factors are used to track both of our progress bars
    // this boolean is used to signal to the factors if they should be dealing with the respect score instead of the recognition one
    protected boolean forRespectValues = false;

    public static boolean isRecognized() {
        return Global.getSector().getCharacterData().getMemoryWithoutUpdate().getBoolean(MEMORY_KEY_RECOGNIZED);
    }

    public static FactionRecognitionIntel createIfNeeded() {
        if (getInstance() != null) return getInstance();
        if (isRecognized()) return null;

        FactionRecognitionIntel intel = new FactionRecognitionIntel();
        intel.setup();
        Global.getSector().getListenerManager().addListener(intel);
        Global.getSector().getIntelManager().addIntel(intel, true);
        Global.getSector().addScript(intel);
        Global.getSector().getPersistentData().put(DATA_KEY, intel);
        return intel;
    }

    public static FactionRecognitionIntel getInstance() {
        return (FactionRecognitionIntel)Global.getSector().getPersistentData().get(DATA_KEY);
    }

    public FactionRecognitionIntel() {
    }

    protected Object readResolve() {
        if (completedCrises == null) completedCrises = new HashSet<>();
        return this;
    }

    public void debug() {
        setup();
    }

    protected void setup() {
        factors.clear();
        stages.clear();
        stages2.clear();

        setMaxProgress(PROGRESS_MAX);

        addStage(Stage.START, 0);
        addStage(Stage.END, PROGRESS_MAX, true, StageIconSize.LARGE);

        ColonyIncomeFactor income = new ColonyIncomeFactor();
        addFactor(income);
        CommissionFactor comm = new CommissionFactor();
        addFactor(comm);
        EmbassyFactor emb = new EmbassyFactor();
        addFactor(emb);
        FreePortFactor fp = new FreePortFactor();
        addFactor(fp);

        addStage2(RespectStage.RESPECTABLE, PROGRESS_MAX, StageIconSize.MEDIUM);
        addStage2(RespectStage.RENEGADE, 0, StageIconSize.MEDIUM);
        maxProgress2 = PROGRESS_MAX;
        setProgress2(PROGRESS_MAX/2);

        getDataFor(Stage.START).keepIconBrightWhenLaterStageReached = true;
        getDataFor(Stage.END).keepIconBrightWhenLaterStageReached = true;

        checkInitialProgress();
    }

    protected void checkInitialProgress() {
        for (MarketAPI market : Misc.getPlayerMarkets(false)) {
            if (!market.isPlayerOwned()) continue;
            ColonySizeAchievedFactor size = new ColonySizeAchievedFactor(market, market.getSize());
            addFactor(size);
        }
    }

    public void activate() {
        if (active) return;
        active = true;
        TextPanelAPI text = null;
        if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) {
            text = Global.getSector().getCampaignUI().getCurrentInteractionDialog().getTextPanel();
        }
        sendUpdateIfPlayerHasIntel(null, text);
    }

    public void addStage2(Object id, int progress) {
        addStage2(id, progress, StageIconSize.MEDIUM);
    }
    public void addStage2(Object id, int progress, StageIconSize iconSize) {
        addStage2(id, progress, false, iconSize);
    }
    public void addStage2(Object id, int progress, boolean isOneOffEvent) {
        addStage2(id, progress, isOneOffEvent, StageIconSize.MEDIUM);
    }
    public void addStage2(Object id, int progress, boolean isOneOffEvent, StageIconSize iconSize) {
        stages2.add(new EventStageData(id, progress, isOneOffEvent, iconSize));
    }

    public void setProgress2(int progress2) {
        if (this.progress2 == progress2) return;

        if (progress2 < 0) progress2 = 0;
        if (progress2 > maxProgress2) progress2 = maxProgress2;

        EventStageData prev = getLastActiveStage(true);
        prevProgressDeltaWasPositive = this.progress2 < progress2;

        this.progress2 = progress2;


        if (progress2 < 0) {
            progress2 = 0;
        }
        if (progress2 > getMaxProgress2()) {
            progress2 = getMaxProgress2();
        }
        // the stages are purely cosmetic, so we don't need the parts that check completion etc.
    }

    public int getMonthlyProgress2() {
        int total = 0;
        float mult = 1f;
        for (EventFactor factor : factors) {
            if (factor.isOneTime()) continue;
            total += factor.getProgress(this);
            mult *= factor.getAllProgressMult(this);
        }

        if (total != 0) {
            float sign = Math.signum(total);
            total = Math.round(sign * Math.abs(total) * mult);
            if (total == 0) total = (int) Math.round(1f * sign);
        }

        total = Math.min(total, getMaxMonthlyProgress());

        return total;
    }

    @Override
    public int getMonthlyProgress() {
        int total = 0;
        float mult = 1f;
        for (EventFactor factor : factors) {
            if (factor.isOneTime()) continue;
            if (suspended) {    // MODIFIED
                if (factor instanceof BaseRecognitionEventFactor bref && !bref.ignoreSuspension()) {
                    continue;
                }
            }
            total += factor.getProgress(this);
            mult *= factor.getAllProgressMult(this);
        }

        if (total != 0) {
            float sign = Math.signum(total);
            total = Math.round(sign * Math.abs(total) * mult);
            if (total == 0) total = (int) Math.round(1f * sign);
        }

        total = Math.min(total, getMaxMonthlyProgress());

        return total;
    }

    @Override
    public int getProgress() {
        if (forRespectValues) return progress2;
        return super.getProgress();
    }

    public int getProgress1() {
        return progress;
    }

    protected void addOneTimeFactorEffects(EventFactor factor, InteractionDialogAPI dialog) {
        if (factor.getProgress(this) != 0) {
            TextPanelAPI textPanel = dialog == null ? null : dialog.getTextPanel();
            sendUpdateIfPlayerHasIntel(factor, textPanel);
        }
        setProgress(progress + factor.getProgress(this));
        forRespectValues = true;
        setProgress2(progress2 + factor.getProgress(this));
        forRespectValues = false;
    }

    @Override
    public void addFactor(EventFactor factor, InteractionDialogAPI dialog) {
        if (!active) return;
        if (suspended) {    // MODIFIED
            if (factor.isOneTime() && factor instanceof BaseRecognitionEventFactor bref && !bref.ignoreSuspension()) {
                return;
            }
        }

        addingFactorDialog = dialog;
        factors.add(factor);
        if (factor.isOneTime()) {
            addOneTimeFactorEffects(factor, dialog);
        }
        addingFactorDialog = null;
    }

    public void reportCrisisCompleted(CrisisChecker.CrisisCheckerEntry entry) {
        CrisisCompletedFactor ccf = new CrisisCompletedFactor(entry);
        addFactor(ccf);
        completedCrises.add(entry.crisisId);
    }

    public static String getString(String id) {
        return StringHelper.getString("nex_factionRecognition", id);
    }

    protected void addPanel(TooltipMakerAPI main, boolean first) {
        float opad = 10;
        float barPad = 24;

        float visualProgressMult = first ? 1 : maxProgress/maxProgress2;

        if (!first) forRespectValues = true;
        List<EventStageData> thisStages = first ? stages : stages2;
        float thisProgress = first ? progress : progress2;

        EventProgressBarAPI bar = main.addEventProgressBar(this, 100f);
        bar.getPosition().setXAlignOffset(barPad);
        TooltipMakerAPI.TooltipCreator barTC = null;    // getBarTooltip();
        if (barTC != null) {
            main.addTooltipToPrevious(barTC, TooltipMakerAPI.TooltipLocation.BELOW, false);
        }

        for (EventStageData curr : thisStages) {
            // MODIFIED
            //if (curr.progress <= 0) continue; // no icon for "starting" stage
            //if (curr.rollData == null || curr.rollData.equals(RANDOM_EVENT_NONE)) continue;
            if (RANDOM_EVENT_NONE.equals(curr.rollData)) continue;
            if (curr.wasEverReached && curr.isOneOffEvent && !curr.isRepeatable) continue;

            if (curr.hideIconWhenPastStageUnlessLastActive &&
                    curr.progress <= thisProgress &&
                    getLastActiveStage(true) != curr) {
                continue;
            }

            EventStageDisplayData data = createDisplayData(curr.id);
            UIComponentAPI marker = main.addEventStageMarker(data);
            float xOff = bar.getXCoordinateForProgress(curr.progress * visualProgressMult) - bar.getPosition().getX();
            marker.getPosition().aboveLeft(bar, data.downLineLength).setXAlignOffset(xOff - data.size / 2f - 1);

            TooltipMakerAPI.TooltipCreator tc = getStageTooltip(curr.id);
            if (tc != null) {
                main.addTooltipTo(tc, marker, TooltipMakerAPI.TooltipLocation.LEFT, false);
            }
        }

        // progress indicator
        {
            UIComponentAPI marker = main.addEventProgressMarker(this);
            float xOff = bar.getXCoordinateForProgress(thisProgress * visualProgressMult) - bar.getPosition().getX();
            marker.getPosition().belowLeft(bar, -getBarProgressIndicatorHeight() * 0.5f - 2)
                    .setXAlignOffset(xOff - getBarProgressIndicatorWidth() / 2 - 1);
        }

        main.addSpacer(opad).getPosition().setXAlignOffset(-barPad);
        main.addSpacer(opad);

        // MODIFIED
        /*
        for (EventStageData curr : thisStages) {
            if (curr.wasEverReached && curr.isOneOffEvent && !curr.isRepeatable) continue;
            addStageDescriptionWithImage(main, curr.id);
        }
        */

        afterStageDescriptions(main);

        float barW = getBarWidth() + barPad * 2;    // MODIFIED
        float factorWidth = (barW) / 2f - opad;

        if (withMonthlyFactors() != withOneTimeFactors()) {
            //factorWidth = barW;
            factorWidth = (int) (barW * 0.6f);
        }

        // factors table
        // the holder works around a bug where if the one-time factors table is longer than the monthly factors table,
        // things added to the main tooltip after the factors tables get added too high up
        TooltipMakerAPI facHolder = main.beginSubTooltip(barW);

        TooltipMakerAPI mFac = facHolder.beginSubTooltip(factorWidth);

        Color c = getFactionForUIColors().getBaseUIColor();
        Color bg = getFactionForUIColors().getDarkUIColor();
        mFac.addSectionHeading(getString("tableHeader_factorsMonthly"), c, bg, Alignment.MID, opad).getPosition().setXAlignOffset(0);
        float strW = 40f;
        float rh = 20f;
        //rh = 15f;
        UIPanelAPI tableM = mFac.beginTable2(getFactionForUIColors(), rh, false, false,
                getString("tableHeader_factorsMonthly"), factorWidth - strW - 3,
                getString("tableHeader_progress"), strW
        );

        for (EventFactor factor : factors) {
            if (factor.isOneTime()) continue;
            if (!factor.shouldShow(this)) continue;

            String desc = factor.getDesc(this);
            if (desc != null) {
                mFac.addRowWithGlow(Alignment.LMID, factor.getDescColor(this), desc,
                        Alignment.RMID, factor.getProgressColor(this), factor.getProgressStr(this));
                TooltipMakerAPI.TooltipCreator t = factor.getMainRowTooltip(this);
                if (t != null) {
                    mFac.addTooltipToAddedRow(t, TooltipMakerAPI.TooltipLocation.RIGHT, false);
                }
            }
            factor.addExtraRows(mFac, this);
        }

        //mFac.addButton("TEST", new String(), factorWidth, 20f, opad);
        mFac.addTable(StringHelper.getString("none", true), -1, opad);
        mFac.getPrev().getPosition().setXAlignOffset(-5);

        facHolder.endSubTooltip();

        TooltipMakerAPI oFac = facHolder.beginSubTooltip(factorWidth);

        oFac.addSectionHeading(getString("tableHeader_factorsOneTimeRecent"), c, bg, Alignment.MID, opad).getPosition().setXAlignOffset(0);

        strW = 80f;
        UIPanelAPI tableO = oFac.beginTable2(getFactionForUIColors(), rh, false, false,
                getString("tableHeader_factorsOneTime"), factorWidth - strW - 3,
                getString("tableHeader_progress"), strW
        );

        List<EventFactor> reversed = new ArrayList<EventFactor>(factors);
        Collections.reverse(reversed);
        for (EventFactor factor : reversed) {
            if (!factor.isOneTime()) continue;
            if (!factor.shouldShow(this)) continue;

            String desc = factor.getDesc(this);
            if (desc != null) {
                oFac.addRowWithGlow(Alignment.LMID, factor.getDescColor(this), desc,
                        Alignment.RMID, factor.getProgressColor(this), factor.getProgressStr(this));
                TooltipMakerAPI.TooltipCreator t = factor.getMainRowTooltip(this);
                if (t != null) {
                    oFac.addTooltipToAddedRow(t, TooltipMakerAPI.TooltipLocation.LEFT);
                }
            }
            factor.addExtraRows(oFac, this);
        }

        oFac.addTable(StringHelper.getString("none", true), -1, opad);
        oFac.getPrev().getPosition().setXAlignOffset(-5);
        facHolder.endSubTooltip();


        float factorHeight = Math.max(mFac.getHeightSoFar(), oFac.getHeightSoFar());
        mFac.setHeightSoFar(factorHeight);
        oFac.setHeightSoFar(factorHeight);

        if (withMonthlyFactors() && withOneTimeFactors()) {
            facHolder.addCustom(mFac, opad * 2f);
            facHolder.addCustomDoNotSetPosition(oFac).getPosition().rightOfTop(mFac, opad);
        } else if (withMonthlyFactors()) {
            facHolder.addCustom(mFac, opad * 2f);
        } else if (withOneTimeFactors()) {
            facHolder.addCustom(oFac, opad * 2f);
        }
        facHolder.setHeightSoFar(factorHeight);

        main.endSubTooltip();
        main.addCustom(facHolder, opad);

        forRespectValues = false;
    }

    @Override
    protected void notifyStageReached(EventStageData stage) {
        if (stage.id == Stage.END) {
            applyRecognition();
            endAfterDelay(15);
        }
    }

    protected void applyRecognition() {
        Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(MEMORY_KEY_RECOGNIZED, true);
        // anything else?
    }

    public Color getProgressColor(int delta) {
        if (forRespectValues && delta > 0) return Misc.getPositiveHighlightColor();
        return super.getProgressColor(delta);
    }

    @Override
    protected void notifyEnding() {
        Global.getSector().getListenerManager().removeListener(this);
    }

    // =================================================================================================================
    // general intel plugin methods

    @Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        // guess we'll have to replicate the entire super method aaaaaahhh

        uiWidth = width;
        float ttWidth = getBarWidth() + 50;

        TooltipMakerAPI main = panel.createUIElement(ttWidth, height, true);

        main.setTitleOrbitronVeryLarge();
        main.addTitle(getName(), Misc.getBasePlayerColor());

        addPanel(main, true);
        addPanel(main, false);

        panel.addUIElement(main).inTL(0, 0);
	}

    @Override
    public void addStageDescriptionText(TooltipMakerAPI info, float width, Object stageId) {
        float small = 0f;

        EventStageData stage = getDataFor(stageId);
        if (stage == null) return;

        if (isStageActive(stageId)) {
            addStageDesc(info, stageId, small, false);
        }
    }

    @Override
    public void afterStageDescriptions(TooltipMakerAPI info) {
        float opad = 10f;
        float small = 0f;
        Color text = Misc.getTextColor();
        Color h = Misc.getHighlightColor();

        if (!this.forRespectValues) {
            MagicTxt.addPara(info, getString("intro1"), opad, text, h);
            MagicTxt.addPara(info, getString("intro2"), opad, text, h);
            MagicTxt.addPara(info, getString("intro3"), opad, text, h);
        } else {
            MagicTxt.addPara(info, getString("introRespect1"), opad, text, h);
        }
    }

    public void addStageDesc(TooltipMakerAPI info, Object stageId, float initPad, boolean forTooltip) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color text = Misc.getTextColor();
        if (stageId == Stage.START) {
            MagicTxt.addPara(info, getString("tooltipStageStart"), initPad, text, h);
        }
        if (stageId == Stage.END) {
            MagicTxt.addPara(info, getString("tooltipStageEnd"), initPad, text, h);
        }
        else if (stageId == RespectStage.RESPECTABLE) {
            MagicTxt.addPara(info, getString("tooltipStageRespectable"), initPad, text, h);
        }
        else if (stageId == RespectStage.RENEGADE) {
            MagicTxt.addPara(info, getString("tooltipStageRogue"), initPad, text, h);
        }
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getStageTooltipImpl(Object stageId) {
        return new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return BaseEventFactor.TOOLTIP_WIDTH;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                addStageDesc(tooltip, stageId, 0, true);
            }
        };
    }

    @Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        if (addEventFactorBulletPoints(info, mode, isUpdate, tc, initPad)) {
            return;
        }

		Object param = getListInfoParam();

	}

	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {


        ui.updateUIForItem(this);
	}

	@Override
	public boolean hasSmallDescription() {
		return false;
	}

	@Override
	public boolean hasLargeDescription() {
		return true;
	}

	@Override
	public String getIcon() {
		return getFactionForUIColors().getCrest();
	}

    protected String getStageIconImpl(Object stageId) {
        if (stageId == Stage.START) {
            return Global.getSettings().getSpriteName("intel", "nex_recognition_stage0");
        }
        if (stageId == Stage.END) {
            return Global.getSector().getPlayerFaction().getCrest();
        }
        if (stageId == RespectStage.RESPECTABLE) {
            return "graphics/exerelin/icons/intel/tuxedo.png";
        }
        if (stageId == RespectStage.RENEGADE) {
            return Global.getSector().getFaction(Factions.PIRATES).getCrest();
        }

        // should not happen - the above cases should handle all possibilities - but just in case
        return Global.getSettings().getSpriteName("events", "hyperspace_topography");
    }

    @Override
    public Color getBarColor() {
        if (forRespectValues) {
            return Misc.interpolateColor(Misc.getNegativeHighlightColor(), Misc.getPositiveHighlightColor(), (float)progress2/maxProgress2);
        }

        Color color = BAR_COLOR;
        color = Misc.interpolateColor(color, Color.black, 0.25f);
        return color;
    }

    @Override
    public Color getBarProgressIndicatorColor() {
        return super.getBarProgressIndicatorColor();
    }

    @Override
    protected int getStageImportance(Object stageId) {
        return super.getStageImportance(stageId);
    }

    @Override
    public EventStageData getDataFor(Object stageId) {
        for (EventStageData curr : stages) {
            if (stageId.equals(curr.id)) return curr;
        }
        for (EventStageData curr : stages2) {
            if (stageId.equals(curr.id)) return curr;
        }
        return null;
    }

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_STORY);
        tags.add(Tags.INTEL_FLEET_LOG);
        tags.add(StringHelper.getString("exerelin_misc", "intelTagPersonal"));
		return tags;
	}

	@Override
	public IntelSortTier getSortTier() {
		return IntelSortTier.TIER_3;
	}

	@Override
	protected String getName() {
		String str = getString("intelTitle");
        if (isEnding() || isEnded()) str += " — " + StringHelper.getString("completed");
        return str;
	}

	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getPlayerFaction();
	}

    @Override
    public boolean isHidden() {
        return !active;
    }



    // =================================================================================================================
    // listeners


    @Override
    public void reportEconomyTick(int iterIndex) {
        if (!isActive()) return;

        super.reportEconomyTick(iterIndex);

        forRespectValues = true;

        float delta = getMonthlyProgress2();
        float numIter = Global.getSettings().getFloat("economyIterPerMonth");
        float f = 1f / numIter;

        delta *= f;
        delta += progressDeltaRemainder2;

        int apply = (int) delta;

        progressDeltaRemainder2 = delta - (float) apply;

        setProgress2(progress2 + apply);

        forRespectValues = false;

        CrisisChecker.checkCrises(this);
    }

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {}

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, Industry industry) {}

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {}

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
        if (!active) return;
        FactionAPI faction = market.getFaction();
        if (actionData instanceof Nex_MarketCMD.NexTempData ntd) {
            faction = ntd.targetFaction;
        }
        SatBombFactor sb = new SatBombFactor(market, faction, actionData);
        addFactor(sb, dialog);
    }

    @Override
    public void reportAgentAction(CovertActionIntel action) {
        if (!active) return;
        if (!action.isPlayerInvolved() || !action.getResult().isSuccessful() || BaseRecognitionEventFactor.isOutlawFaction(action.getTargetFaction()))
            return;

        if (action instanceof RaiseRelations rr) {
            RaiseRelationsFactor factor = new RaiseRelationsFactor(rr);
            addFactor(factor);
        }
    }

    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {}

    @Override
    public void reportInvasionRound(InvasionRound.InvasionRoundResult result, CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) {}

    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market, float numRounds, boolean success) {}

    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {
        if (market.isHidden()) return;

        if (newOwner.isPlayerFaction())
            activate();

        if (!active) return;

        String origOwner = NexUtilsMarket.getOriginalOwner(market);

        if (newOwner.isPlayerFaction() && isCapture) {
            ConquerMarketFactor conq = new ConquerMarketFactor(market, oldOwner, oldOwner.getId().equals(origOwner));
            addFactor(conq);
        }
        if (oldOwner.isPlayerFaction() && !isCapture && newOwner.getId().equals(origOwner)) {
            ReturnMarketFactor ret = new ReturnMarketFactor(market, oldOwner);
            addFactor(ret);
        }
    }

    @Override
    public void reportColonySizeChanged(MarketAPI market, int prevSize) {
        if (!market.getFaction().isPlayerFaction()) return;

        activate();

        int size = market.getSize();
        if (size <= prevSize) return;
        ColonySizeAchievedFactor sizeFac = new ColonySizeAchievedFactor(market, size);
        addFactor(sizeFac);
    }

    @Override
    public void reportPlayerColonizedPlanet(PlanetAPI planet) {
        activate();
        // don't add a factor here, will call reportColonySizeChanged on its own
        //ColonySizeAchievedFactor size = new ColonySizeAchievedFactor(planet.getMarket(), planet.getMarket().getSize());
        //addFactor(size);
    }

    @Override
    public void reportPlayerAbandonedColony(MarketAPI colony) {

    }
}
