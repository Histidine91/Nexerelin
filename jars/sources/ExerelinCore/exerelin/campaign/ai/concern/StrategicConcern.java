package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicAIModule;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface StrategicConcern {

    StrategicAI getAI();
    void setAI(StrategicAI ai, StrategicAIModule module);

    /**
     * Review the Sector's situation and fill self with relevant data.
     * @return True if a valid concern has been generated, false otherwise.
     */
    boolean generate();

    /**
     * If returning false, hide/remove the concern. Probably should not require extensive validation (i.e. invalidating
     * a military concern if the target faction is no longer hostile should be fine, calculating whether a target is still vulnerable
     * should probably be left till the next {@code update()}.
     * @return
     */
    boolean isValid();


    /**
     * Used to find and filter duplicate concerns. Probably not very useful.
     * @param otherConcern
     * @param param
     * @return
     */
    boolean isSameAs(StrategicConcern otherConcern, Object param);

    /**
     * e.g. for a market-related concern, this should return a set of markets for which we have existing concerns of the same type.
     * @return
     */
    Set getExistingConcernItems();

    MutableStat getPriority();
    float getPriorityFloat();

    /**
     * Called periodically to make sure the concern is still relevant.
     */
    void update();

    void advance(float days);

    /**
     * Should be called when the concern is generated and each time it is updated.
     */
    void reapplyPriorityModifiers();

    @Nullable MarketAPI getMarket();
    /**
     * Should be overriden for concerns that have a list of multiple markets involved. Return null if empty.
     * @return
     */
    @Nullable List<MarketAPI> getMarkets();
    @Nullable FactionAPI getFaction();
    /**
     * Should be overriden for concerns that have a list of multiple factions involved. Return null if empty.
     * @return
     */
    @Nullable List<FactionAPI> getFactions();

    /**
     * Creates a GUI panel for display in the AI'ss intel screen item.
     * @param holder
     * @return
     */
    CustomPanelAPI createPanel(CustomPanelAPI holder);

    /**
     * Asks the concern to select a strategic action it considers the most suitable (has highest priority score).
     * Does not actually generate or activate the proposed action.
     * @return
     */
    @Nullable StrategicAction pickAction();

    /**
     * Generates and initiates the specified strategic action.
     * @param action
     * @return True if the action was successfully initiated, false otherwise.
     */
    boolean initAction(StrategicAction action);
    //void setCurrentAction(StrategicAction action);
    StrategicAction getCurrentAction();
    /**
     * To be called when an action has any news to report. Note: The completion status update may be called before the starting status update.
     * @param action
     * @param newStatus
     */
    void notifyActionUpdate(StrategicAction action, @Nullable StrategicActionDelegate.ActionStatus newStatus);
	float getActionCooldown();
	boolean canTakeAction(StrategicAction action);

    boolean isEnded();
    void end();

    String getName();
    /**
     * Shown in the concern's GUI panel. Exists because it may need to be shorter than {@code getName()} which is used in logging and elsewhere.
     * @return
     */
    String getDisplayName();
    String getDesc();
    String getId();
    String getIcon();

    void setId(String id);

    StrategicDefManager.StrategicConcernDef getDef();
}
