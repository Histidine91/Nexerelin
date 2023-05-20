package exerelin.campaign.intel.groundbattle.dialog;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.campaign.intel.groundbattle.plugins.MarketMapDrawer;
import exerelin.campaign.intel.groundbattle.plugins.MarketMapDrawer.IFBPanelPlugin;
import exerelin.campaign.ui.CustomUIPanelInputListener;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dialog for picking an industry in ground battles (e.g. for unit orders or ability targeting). Shows industries as map panels.
 */
@Log4j
public abstract class GBIndustryPickerDialogDelegate extends BaseCustomDialogDelegate implements CustomUIPanelInputListener {

    protected GroundBattleIntel battle;
    protected CustomPanelAPI mainPanel;
    protected List<IndustryForBattle> industries;
    protected List<IFBPanelPlugin> panelPlugins = new ArrayList<>();
    protected IndustryForBattle selectedIndustry;

    public GBIndustryPickerDialogDelegate(GroundBattleIntel battle, List<IndustryForBattle> industries) {
        this.battle = battle;
        this.industries = industries;
    }

    public static float getMaxWidth() {
        return Global.getSettings().getScreenWidth() * 0.8f;
    }

    /**
     * @return [Width in pixels, height in pixels, number of rows, number of elements per row, vertical padding]
     */
    public int[] getWantedDimensions() {
        float pad = 4;

        int numPanels = industries.size();
        float panelWidth = MarketMapDrawer.getIndustryPanelWidth();
        float panelHeight = MarketMapDrawer.getIndustryPanelHeight();
        int numPerRow = (int)(getMaxWidth() / panelWidth);
        numPerRow = Math.min(numPerRow, (int)Math.round(Math.sqrt(numPanels)));
        if (numPerRow < 3) numPerRow = 3;

        float width = numPerRow * (panelWidth + pad);
        float heightPerRow = panelHeight + pad;
        //log.info("Number of panels: " + numPanels);
        int numRows = (int)Math.ceil((float)numPanels / numPerRow);

        float height = numRows * heightPerRow;
        //log.info("Height: " + height);

        return new int[] {(int)width, (int)height, numRows, numPerRow, 48};
    }

    protected abstract String getHeaderString();

    protected void createHeader(TooltipMakerAPI tooltip) {
        tooltip.addSectionHeading(getHeaderString(), battle.getMarket().getFaction().getBaseUIColor(),
                battle.getMarket().getFaction().getDarkUIColor(), Alignment.MID, 0);
    }

    protected void createFooter(TooltipMakerAPI tooltip) {
        tooltip.addPara(StringHelper.getString("nex_invasion2", "actionSelectIndustryFooter"), 10);
    }

    protected CustomPanelAPI createIndustryPanels(CustomPanelAPI outer, TooltipMakerAPI tooltip) {
        float pad = 4;

        // arrange industries in rectangle
        float panelWidth = MarketMapDrawer.getIndustryPanelWidth();
        float panelHeight = MarketMapDrawer.getIndustryPanelHeight();

        int[] dimensions = getWantedDimensions();

        CustomPanelAPI industryHolder = outer.createCustomPanel(dimensions[0], dimensions[1], null);
        List<CustomPanelAPI> industryPanels = new ArrayList<>();
        panelPlugins.clear();
        for (IndustryForBattle ifb : industries) {
            log.info("Adding panel for industry " + ifb.getName());
            IFBPanelPlugin plugin = new IFBPanelPlugin(ifb);
            plugin.addListener(this);
            CustomPanelAPI indPanel = ifb.renderPanelNew(outer, "industryPicker", panelWidth, panelHeight, plugin);
            NexUtilsGUI.placeElementInRows(industryHolder, indPanel, industryPanels, dimensions[3], pad);
            panelPlugins.add(plugin);
            industryPanels.add(indPanel);
        }
        tooltip.addCustom(industryHolder, pad);
        return industryHolder;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        mainPanel = panel;
        TooltipMakerAPI tooltip = panel.createUIElement(panel.getPosition().getWidth(), panel.getPosition().getHeight(), true);
        createHeader(tooltip);
        createIndustryPanels(panel, tooltip);
        createFooter(tooltip);
        panel.addUIElement(tooltip).inTL(0, 0);
    }

    @Override
    public boolean hasCancelButton() {
        return true;
    }

    @Override
    public void notifyInput(CustomUIPanelPlugin plugin, InputEventAPI input, Map<String, Object> params) {
        //log.info("wololo " + input);
        if (!input.isMouseUpEvent()) return;
        if (!(plugin instanceof IFBPanelPlugin)) return;
        IFBPanelPlugin ifbpp = (IFBPanelPlugin)plugin;

        IndustryForBattle ifb = ifbpp.getIFB();
        if (ifb == selectedIndustry) return;

        for (IFBPanelPlugin savedPlugin : panelPlugins) {
            savedPlugin.setHighlight(false);
            savedPlugin.setBgColor(IFBPanelPlugin.BG_COLOR);
        }
        ifbpp.setHighlight(true);
        ifbpp.setBgColor(IFBPanelPlugin.BG_COLOR_HIGHLIGHT);
        selectedIndustry = ifb;
    }
}
