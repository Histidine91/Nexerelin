package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;




public class PrintFactionMarkets extends BaseCommandPlugin {
    
        public class MarketComparator implements Comparator<MarketAPI>
        {
            @Override
            public int compare(MarketAPI market1, MarketAPI market2) {

                String loc1 = market1.getContainingLocation().getName();
                String loc2 = market2.getContainingLocation().getName();
                
                if (loc1.compareToIgnoreCase(loc2) > 0) return 1;
                else if (loc2.compareToIgnoreCase(loc1) > 0) return -1;
                
                int size1 = market1.getSize();
                int size2 = market2.getSize();

                if (size1 > size2) return -1;
                else if (size2 > size1) return 1;
                else return 0;
            }
        }
    
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
                boolean isExiInCorvus = factionId.equals("exigency") && SectorManager.getCorvusMode();
                List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(factionId);
                if (markets.isEmpty())
                {
                        if (!isExiInCorvus) return false;
                }
                
                Collections.sort(markets,new MarketComparator());
                //Collections.reverse(markets);
                FactionAPI faction = Global.getSector().getFaction(factionId);
                TextPanelAPI text = dialog.getTextPanel();
                
                Color hl = Misc.getHighlightColor();

                int numMarkets = markets.size();
                if (isExiInCorvus) numMarkets++;
                text.addParagraph(Misc.ucFirst(faction.getDisplayNameWithArticle()) + " have " + numMarkets + " markets");
                text.highlightInLastPara(hl, "" + numMarkets);
                text.setFontSmallInsignia();
                text.addParagraph("-----------------------------------------------------------------------------");
                
                if (isExiInCorvus)
                {
                    text.addParagraph("Tasserus (size ??)");
                    text.highlightInLastPara(hl, "Tasserus");
                    text.highlightInLastPara(hl, "??");
                }
                
                for (MarketAPI market: markets)
                {
                    String marketName = market.getName();
                    LocationAPI loc = market.getContainingLocation();
                    String locName = loc.getName();
                    if (loc instanceof StarSystemAPI)
                            locName = ((StarSystemAPI)loc).getBaseName();
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