package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.EventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.utilities.AgentActionListener;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class FactionRecognitionIntel extends BaseEventIntel implements ColonyPlayerHostileActListener, EconomyTickListener, InvasionListener, AgentActionListener {

    public static final String DATA_KEY = "nex_factionRecognition";
    public static final String MEMORY_KEY_RECOGNIZED = "$nex_factionRecognized";
    public static final int PROGRESS_MAX = 1000;

    public enum Stage {
        START,
        END
    }

    public enum PolityAlignment {
        RESPECTABLE,
        RENEGADE
    }

    public static Color BAR_COLOR = Global.getSettings().getColor("progressBarFleetPointsColor");

    protected int progress2 = 0;
    protected int maxProgress2 = PROGRESS_MAX;

    //protected Object startingStage;
    protected List<EventStageData> stages2 = new ArrayList<EventStageData>();
    protected List<EventFactor> factors2 = new ArrayList<EventFactor>();

    public static FactionRecognitionIntel createIfNeeded() {
        if (getInstance() != null) return getInstance();
        if (Global.getSector().getCharacterData().getMemoryWithoutUpdate().getBoolean(MEMORY_KEY_RECOGNIZED)) return null;

        FactionRecognitionIntel intel = new FactionRecognitionIntel();
        intel.setup();
        Global.getSector().getListenerManager().addListener(intel);
        Global.getSector().addScript(intel);
        Global.getSector().getPersistentData().put(DATA_KEY, intel);
        return intel;
    }

    public static FactionRecognitionIntel getInstance() {
        return (FactionRecognitionIntel)Global.getSector().getPersistentData().get(DATA_KEY);
    }

    public FactionRecognitionIntel() {

    }

    protected void setup() {
        factors.clear();
        stages.clear();

        setMaxProgress(PROGRESS_MAX);


        addStage(Stage.START, 0);
        addStage(Stage.END, PROGRESS_MAX, true, StageIconSize.LARGE);

        addStage2(PolityAlignment.RESPECTABLE, 0, StageIconSize.MEDIUM);
        addStage2(PolityAlignment.RENEGADE, PROGRESS_MAX, StageIconSize.MEDIUM);

        getDataFor(Stage.START).keepIconBrightWhenLaterStageReached = true;
        getDataFor(Stage.END).keepIconBrightWhenLaterStageReached = true;
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

    // =================================================================================================================
    // general intel plugin methods

    @Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		TooltipMakerAPI superheaderHolder = panel.createUIElement(width/2, 40, false);
		TooltipMakerAPI superheader = superheaderHolder.beginImageWithText(getIcon(), 40);
		superheader.setParaOrbitronVeryLarge();
		superheader.addPara(getName(), 3);
		superheaderHolder.addImageWithText(3);

		panel.addUIElement(superheaderHolder).inTL(width*0.3f, 0);

		TooltipMakerAPI tableHolder = panel.createUIElement(width, height - 50, true);



		panel.addUIElement(tableHolder).inTL(3, 48);
	}

	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
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
        EventStageData esd = getDataFor(stageId);
        if (esd == null) return null;

        if (EnumSet.of(HyperspaceTopographyEventIntel.Stage.SLIPSTREAM_DETECTION, HyperspaceTopographyEventIntel.Stage.SLIPSTREAM_NAVIGATION, HyperspaceTopographyEventIntel.Stage.HYPERFIELD_OPTIMIZATION,
                HyperspaceTopographyEventIntel.Stage.TOPOGRAPHIC_DATA, HyperspaceTopographyEventIntel.Stage.START).contains(esd.id)) {
            return Global.getSettings().getSpriteName("events", "hyperspace_topography_" + ((HyperspaceTopographyEventIntel.Stage)esd.id).name());
        }
        if (stageId == HyperspaceTopographyEventIntel.Stage.REVERSE_POLARITY) {
            return Global.getSettings().getAbilitySpec(Abilities.REVERSE_POLARITY).getIconName();
        }
        if (stageId == HyperspaceTopographyEventIntel.Stage.GENERATE_SLIPSURGE) {
            return Global.getSettings().getAbilitySpec(Abilities.GENERATE_SLIPSURGE).getIconName();
        }
        // should not happen - the above cases should handle all possibilities - but just in case
        return Global.getSettings().getSpriteName("events", "hyperspace_topography");
    }


    @Override
    public Color getBarColor() {
        Color color = BAR_COLOR;
        //color = Misc.getBasePlayerColor();
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
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("exerelin_misc", "intelTagStrategicAI"));
		//tags.add(DiplomacyProfileIntel.getString("intelTag"));
		return tags;
	}

	@Override
	public IntelSortTier getSortTier() {
		return IntelSortTier.TIER_5;
	}

	@Override
	protected String getName() {
		return "[TODO] Earning Recognition";  //getString("intelTitle");
	}

	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getPlayerFaction();
	}

    // =================================================================================================================
    // listeners

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {

    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, Industry industry) {

    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {

    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {

    }

    @Override
    public void reportAgentAction(CovertActionIntel action) {

    }

    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {}

    @Override
    public void reportInvasionRound(InvasionRound.InvasionRoundResult result, CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) {}

    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market, float numRounds, boolean success) {}

    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {

    }
}
