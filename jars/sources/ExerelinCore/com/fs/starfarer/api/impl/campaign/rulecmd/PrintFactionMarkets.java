package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;




public class PrintFactionMarkets extends BaseCommandPlugin {
        public static Comparator<MarketAPI> marketSizeComparator 
                          = new Comparator<MarketAPI>() {

            public int compare(MarketAPI market1, MarketAPI market2) {

              int size1 = market1.getSize();
              int size2 = market2.getSize();

              if (size1 < size2) return -1;
              else if (size2 > size1) return 1;
              else return 0;
            }

        };
    
        static final HashMap<Integer, Color> colorByMarketSize = new HashMap<>();
        static {
            colorByMarketSize.put(2, Color.BLUE);
            colorByMarketSize.put(3, Color.CYAN);
            colorByMarketSize.put(4, Color.GREEN);
            colorByMarketSize.put(5, Color.YELLOW);
            colorByMarketSize.put(6, Color.ORANGE);
            colorByMarketSize.put(7, Color.PINK);
            colorByMarketSize.put(8, Color.RED);
        }
    
    
        @Override
        public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
                if (dialog == null) return false;
                
                String factionId = params.get(0).getString(memoryMap);
                List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(factionId);
                if (markets.isEmpty())
                        return false;
                
                //markets.sort(marketSizeComparator);  // crashes with MethodNotFound exception
                //Collections.reverse(markets);
                FactionAPI faction = Global.getSector().getFaction(factionId);
                TextPanelAPI text = dialog.getTextPanel();
                
                Color hl = Misc.getHighlightColor();

                text.addParagraph(Misc.ucFirst(faction.getDisplayNameWithArticle()) + " have " + markets.size() + " markets");
                text.highlightInLastPara(hl, "" + markets.size());
                text.setFontSmallInsignia();
                text.addParagraph("-----------------------------------------------------------------------------");
                for (MarketAPI market: markets)
                {
                    String marketName = market.getName();
                    String locName = market.getContainingLocation().getName();
                    int size = market.getSize();
                    Color sizeColor = Color.WHITE;
                    if (colorByMarketSize.containsKey(size))
                            sizeColor = colorByMarketSize.get(size);
                    
                    text.addParagraph(marketName + ", " + locName + " (size " + size + ")");
                    text.highlightInLastPara(hl, marketName);
                    text.highlightInLastPara(sizeColor, "" + size);
                }
                text.addParagraph("-----------------------------------------------------------------------------");
                text.setFontInsignia();

                return true;
        }
}