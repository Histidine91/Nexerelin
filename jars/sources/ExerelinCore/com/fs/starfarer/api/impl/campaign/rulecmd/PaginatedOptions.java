package com.fs.starfarer.api.impl.campaign.rulecmd;  
  
import com.fs.starfarer.api.GameState;
import java.util.ArrayList;  
import java.util.List;  
import java.util.Map;  
  
import com.fs.starfarer.api.Global;  
import com.fs.starfarer.api.campaign.InteractionDialogAPI;  
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;  
import com.fs.starfarer.api.campaign.rules.MemoryAPI;  
import com.fs.starfarer.api.combat.EngagementResultAPI;  
import com.fs.starfarer.api.impl.campaign.DevMenuOptions;  
import com.fs.starfarer.api.util.Misc.Token;  

// Alex's version + added optionsAllPages function and NGC handling
public class PaginatedOptions extends BaseCommandPlugin implements InteractionDialogPlugin {  
     
   public static String OPTION_NEXT_PAGE = "core_option_next_page";  
   public static String OPTION_PREV_PAGE = "core_option_prev_page";  
     
   public static class PaginatedOption {  
      public String text;  
      public String id;  
      public PaginatedOption(String text, String id) {  
         this.text = text;  
         this.id = id;  
      }  
   }  
     
   protected InteractionDialogPlugin originalPlugin;  
   protected InteractionDialogAPI dialog;  
   protected Map<String, MemoryAPI> memoryMap;
     
   protected List<PaginatedOption> options = new ArrayList<PaginatedOption>();
   protected List<PaginatedOption> optionsAllPages = new ArrayList<PaginatedOption>();
   protected int optionsPerPage = 5;  
   protected int currPage = 0;
  
   public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, final Map<String, MemoryAPI> memoryMap) {  
      this.dialog = dialog;  
      this.memoryMap = memoryMap;  
  
      originalPlugin = dialog.getPlugin();  
        
      //FireBest.fire(null, dialog, memoryMap, "SalvageSpecialFinishedNoContinue");  
  
      dialog.setPlugin(this);  
      init(dialog);  
        
      for (int i = 0; i < params.size(); i += 2) {  
         String text = params.get(i).getString(memoryMap);  
         String id = params.get(i + 1).getString(memoryMap);  
         addOption(text, id);  
      }  
      if (params.size() > 0) {  
         showOptions();  
      }  
        
      return true;  
   }  
     
   public void addOption(String text, String id) {  
      options.add(new PaginatedOption(text, id));  
   }  

   public void addOptionAllPages(String text, String id) {  
      optionsAllPages.add(new PaginatedOption(text, id));  
   } 
   
   public void showOptions() {  
      dialog.getOptionPanel().clearOptions();  
  
      int maxPages = (int) Math.ceil((float)options.size() / (float)optionsPerPage);  
      if (currPage > maxPages - 1) currPage = maxPages - 1;  
      if (currPage < 0) currPage = 0;  
        
      int start = currPage * optionsPerPage;  
      for (int i = start; i < start + optionsPerPage; i++) {  
         if (i >= options.size()) {  
            if (maxPages > 1) {
			   //I don't like the spacer
               //dialog.getOptionPanel().addOption("", "spacer" + i);  
               //dialog.getOptionPanel().setEnabled("spacer" + i, false);  
            }  
         } else {  
            PaginatedOption option = options.get(i);  
            dialog.getOptionPanel().addOption(option.text, option.id);  
         }  
      }
        
      if (maxPages > 1) {  
         dialog.getOptionPanel().addOption(getPreviousPageText(), OPTION_PREV_PAGE);  
         dialog.getOptionPanel().addOption(getNextPageText(), OPTION_NEXT_PAGE);  
        
         if (currPage >= maxPages - 1) {  
            dialog.getOptionPanel().setEnabled(OPTION_NEXT_PAGE, false);  
         }  
         if (currPage <= 0) {  
            dialog.getOptionPanel().setEnabled(OPTION_PREV_PAGE, false);  
         }  
      }
      
	  for (PaginatedOption option : optionsAllPages) {  
         dialog.getOptionPanel().addOption(option.text, option.id);
      }
  
      if (Global.getSettings().isDevMode()) {  
         DevMenuOptions.addOptions(dialog);  
      }  
   }  
     
   public String getPreviousPageText() {  
      return "Previous page";  
   }  
     
   public String getNextPageText() {  
      return "Next page";  
   }
   
   public boolean isNewGameDialog() {
	   //return false;
	   return Global.getCurrentState() == GameState.TITLE;
   }
  
   public void optionSelected(String optionText, Object optionData) {  
      if (optionData == OPTION_PREV_PAGE) {  
         currPage--;  
         showOptions();  
         return;  
      }  
      if (optionData == OPTION_NEXT_PAGE) {  
         currPage++;  
         showOptions();  
         return;  
      }  
        
      if (optionText != null) {  
         dialog.getTextPanel().addParagraph(optionText, Global.getSettings().getColor("buttonText"));  
      }  
        
      if (optionData == DumpMemory.OPTION_ID) {  
         new DumpMemory().execute(null, dialog, null, getMemoryMap());  
         return;  
      } else if (DevMenuOptions.isDevOption(optionData)) {  
         DevMenuOptions.execute(dialog, (String) optionData);  
         return;  
      }  
        
      dialog.setPlugin(originalPlugin);  
      MemoryAPI memory = dialog.getInteractionTarget().getMemory();  
      memory.set("$option", optionData);  
      memory.expire("$option", 0);  
      boolean fired = FireBest.fire(null, dialog, memoryMap, isNewGameDialog() ? "NewGameOptionSelected" : "DialogOptionSelected");
      if (!fired) {  
         dialog.setPlugin(this); // failsafe for selecting an option with no matching rule  
      }  
   }
  
     
  
   public void advance(float amount) {  
   }  
   public void backFromEngagement(EngagementResultAPI battleResult) {  
   }  
   public Object getContext() {  
      return null;  
   }  
   public Map<String, MemoryAPI> getMemoryMap() {  
      return memoryMap;  
   }  
   public void optionMousedOver(String optionText, Object optionData) {  
   }  
  
   public void init(InteractionDialogAPI dialog) {  
   }  
}  