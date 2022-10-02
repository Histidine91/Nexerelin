package exerelin.campaign.ai;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.ai.concern.StrategicConcern;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.util.ArrayList;
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

    public void findConcerns() {
        for (StrategicDefManager.StrategicConcernDef def : getRelevantConcernDefs()) {
            StrategicConcern concern = StrategicDefManager.instantiateConcern(def);
            if (concern == null) continue;

            concern.setAI(this.ai, this);
            boolean have = concern.generate();
            if (have) {
                log.info("Found concern " + concern.getName());
                currentConcerns.add(concern);
            }
        }
    }

    abstract void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI holder);
}
