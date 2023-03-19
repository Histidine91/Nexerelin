package exerelin.campaign.ai;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.NexUtilsGUI;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Log4j
public abstract class StrategicAIModule {

    protected final StrategicAI ai;
    protected final StrategicDefManager.ModuleType module;
    @Getter protected List<StrategicConcern> currentConcerns = new ArrayList<>();

    public StrategicAIModule(StrategicAI ai, StrategicDefManager.ModuleType module) {
        this.ai = ai;
        this.module = module;
    }

    public List<StrategicDefManager.StrategicConcernDef> getRelevantConcernDefs() {
        List<StrategicDefManager.StrategicConcernDef> list = new ArrayList<>();
        for (StrategicDefManager.StrategicConcernDef def : StrategicDefManager.CONCERN_DEFS_BY_ID.values()) {
            if (this.module != null && this.module != def.module) continue;
            list.add(def);
        }
        return list;
    }

    public List<StrategicDefManager.StrategicActionDef> getRelevantActionDefs(StrategicConcern concern) {
        List<StrategicDefManager.StrategicActionDef> list = new ArrayList<>();
        for (StrategicDefManager.StrategicActionDef def : StrategicDefManager.ACTION_DEFS_BY_ID.values()) {
            List<String> tagsCopy = new ArrayList<>(def.tags);
            tagsCopy.retainAll(concern.getDef().tags);
            if (tagsCopy.isEmpty()) continue;
            list.add(def);
        }
        return list;
    }

    public void init() {}

    public void advance(float days) {
        for (StrategicConcern concern : currentConcerns) {
            concern.advance(days);
        }
    };

    /**
     * @return List of new concerns found.
     */
    public List<StrategicConcern> findConcerns() {
        List<StrategicConcern> newConcerns = new ArrayList<>();

        for (StrategicDefManager.StrategicConcernDef def : getRelevantConcernDefs()) {
            StrategicConcern concern = StrategicDefManager.instantiateConcern(def);
            if (concern == null) continue;

            concern.setAI(this.ai, this);
            boolean have = concern.generate();
            if (have) {
                //log.info("Found concern " + concern.getName());
                currentConcerns.add(concern);
                newConcerns.add(concern);
            }
        }
        return newConcerns;
    }

    public List<StrategicConcern> updateConcerns() {
        List<StrategicConcern> toRemove = new ArrayList<>();
        for (StrategicConcern concern : currentConcerns) {
            concern.update();
            if (!concern.isValid() || concern.isEnded())
                toRemove.add(concern);
        }
        currentConcerns.removeAll(toRemove);
        return toRemove;
    }

    public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI holder, float width) {
        float pad = 3, opad = 10;
        int concernCount = currentConcerns.size();
        NexUtilsGUI.RowSortCalc dimensions = new NexUtilsGUI.RowSortCalc(concernCount, width, SAIConstants.CONCERN_ITEM_WIDTH + 3, SAIConstants.CONCERN_ITEM_HEIGHT + 3);
        
        CustomPanelAPI outer = holder.createCustomPanel(width, dimensions.height, null);

        List<CustomPanelAPI> existingPanels = new ArrayList<>();
        for (StrategicConcern concern : currentConcerns) {
            CustomPanelAPI ct = concern.createPanel(outer);
            NexUtilsGUI.placeElementInRows(outer, ct, existingPanels, dimensions.numPerRow, 3);
            existingPanels.add(ct);
        }
        tooltip.addCustom(outer, opad);
    }
}
