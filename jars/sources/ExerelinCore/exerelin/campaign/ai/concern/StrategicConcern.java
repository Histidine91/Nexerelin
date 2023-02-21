package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicAIModule;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.action.StrategicAction;

import java.util.List;
import java.util.Set;

public interface StrategicConcern {

    public static final String TAG_MILITARY = "military";
    public static final String TAG_ECONOMY = "economy";
    public static final String TAG_DIPLOMACY = "diplomacy";
    public static final String TAG_FRIENDLY = "friendly";
    public static final String TAG_UNFRIENDLY = "unfriendly";
    public static final String TAG_COVERT = "covert";

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

    MarketAPI getMarket();
    FactionAPI getFaction();

    CustomPanelAPI createPanel(CustomPanelAPI holder);

    /**
     * Creates a list of actions we can take in response to this concern.
     * @return
     */
    List<StrategicAction> generateActions();

    StrategicAction pickAction();
    void notifyActionUpdate();

    boolean isEnded();
    void end();

    String getName();
    String getDesc();
    String getId();
    String getIcon();

    void setId(String id);

    StrategicDefManager.StrategicConcernDef getDef();
}
