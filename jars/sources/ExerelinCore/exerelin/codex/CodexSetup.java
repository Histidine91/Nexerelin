package exerelin.codex;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.impl.codex.CodexDialogAPI;
import com.fs.starfarer.api.impl.codex.CodexEntryPlugin;
import com.fs.starfarer.api.impl.codex.CodexEntryV2;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import exerelin.campaign.intel.InsuranceIntelV2;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.utilities.StringHelper;

public class CodexSetup {

    // runcode exerelin.codex.CodexSetup.run();
    public static void run() {
        CodexEntryPlugin mechanicsCat = CodexDataV2.getEntry(CodexDataV2.CAT_GAME_MECHANICS);
        String marineEntryId = CodexDataV2.getCommodityEntryId(Commodities.MARINES);
        String haEntryId = CodexDataV2.getCommodityEntryId(Commodities.HAND_WEAPONS);

        String id = CodexDataV2.getMechanicEntryId("nex_groundBattle");
        CodexEntryV2 groundBattle = new NexCodexEntry(id, getTitle("groundBattle_title"),
                Global.getSettings().getSpriteName("intel", "nex_invasion")) {

            @Override
            public void createCustomDetailImpl(TooltipMakerAPI tt, CustomPanelAPI panel, float ttWidth, UIPanelAPI relatedEntries, CodexDialogAPI codex) {
                GroundBattleIntel.generateHelpDisplay(tt, panel, ttWidth);
            }
        };
        addEntry(groundBattle, mechanicsCat, marineEntryId, haEntryId);
        groundBattle.getTags().add(getString("sortTag"));

        id = CodexDataV2.getMechanicEntryId("nex_insurance");
        CodexEntryV2 insurance = new NexCodexEntry(id, getTitle("insurance_title"),
                Global.getSettings().getSpriteName("intel", "credits")) {

            @Override
            public void createCustomDetailImpl(TooltipMakerAPI tt, CustomPanelAPI panel, float ttWidth, UIPanelAPI relatedEntries, CodexDialogAPI codex) {
                InsuranceIntelV2.createHelpView(tt);
            }
        };
        addEntry(insurance, mechanicsCat);
        insurance.getTags().add(getString("sortTag"));
    }

    public static void addEntry(CodexEntryV2 entry, CodexEntryPlugin parent, String... related) {
        if (related != null) {
            for (String relatedId : related) entry.addRelatedEntry(relatedId);
        }

        // add ourselves as a related entry to the commodities
        if (related != null) {
            for (String relatedId : related) {
                CodexDataV2.getEntry(relatedId).addRelatedEntry(entry);
            }
        }

        CodexDataV2.ENTRIES.put(entry.getId(), entry);
        parent.addChild(entry);
    }

    // runcode exerelin.codex.CodexSetup.rerun();
    public static void rerun() {
        CodexEntryPlugin mechanicsCat = CodexDataV2.getEntry(CodexDataV2.CAT_GAME_MECHANICS);
        mechanicsCat.getChildren().clear();
        run();
    }

    protected static String getTitle(String id) {
        return getString("titlePrefix") +  getString(id);
    }

    protected static String getString(String id) {
        return StringHelper.getString("nex_codex", id);
    }


    public static abstract class NexCodexEntry extends CodexEntryV2 {

        public NexCodexEntry(String id, String title, String icon) {
            super(id, title, icon);
        }

        public NexCodexEntry(String id, String title, String icon, Object param) {
            super(id, title, icon, param);
        }

        @Override
        public boolean isCategory() {
            return false;
        }

        @Override
        public boolean hasCustomDetailPanel() {
            return true;
        }

        @Override
        public void createCustomDetail(CustomPanelAPI panel, UIPanelAPI relatedEntries, CodexDialogAPI codex) {
            float width = panel.getPosition().getWidth();
            float opad = 10f;
            float horzBoxPad = 30f;

            // the right width for a tooltip wrapped in a box to fit next to relatedEntries
            // 290 is the width of the related entries widget, but it may be null
            float tw = width - opad - + 10f;
            if (relatedEntries != null) {
                tw = tw - 290 - horzBoxPad;
            }

            TooltipMakerAPI tt = panel.createUIElement(tw, 0, false);

            createCustomDetailImpl(tt, panel, tw, relatedEntries, codex);

            panel.updateUIElementSizeAndMakeItProcessInput(tt); // this is needed to make it the right size
            UIPanelAPI box = panel.wrapTooltipWithBox(tt);
            panel.addComponent(box).inTL(0, 0);

            if (relatedEntries != null) {
                panel.addComponent(relatedEntries).inTR(0f, 0f);
            }

            float height = tt.getPosition().getHeight();
            if (relatedEntries != null) {
                height = Math.max(height, relatedEntries.getPosition().getHeight());
            }

            panel.getPosition().setSize(width, height + opad * 3);
        }

        public abstract void createCustomDetailImpl(TooltipMakerAPI tt, CustomPanelAPI panel, float ttWidth, UIPanelAPI relatedEntries, CodexDialogAPI codex);
    }
}
