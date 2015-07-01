package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class StringHelper {
    
    public static String getString(String category, String id) {
        String str = Global.getSettings().getString(category, id);
        return str;
    }
    
    public static String getString(String id) {
        return getString("general", id);
    }
    
    public static String substituteToken(String toModify, String token, String replace)
    {
        return toModify.replaceAll(token, replace);
    }
    
    public static String substituteTokens(String toModify, Map<String, String> replacements)
    {
        Iterator<Map.Entry<String, String>> iter = replacements.entrySet().iterator();
        while (iter.hasNext())
        {
            Map.Entry<String, String> tmp = iter.next();
            substituteToken(toModify, tmp.getKey(), tmp.getValue());
        }
        return toModify;
    }
        
    public static String getStringAndSubstituteToken(String category, String id, String token, String replace)
    {
        String str = getString(category, id);
        return substituteToken(str, token, replace);
    }
    
    public static String getStringAndSubstituteToken(String id, String token, String replace)
    {
        return getStringAndSubstituteToken("general", id, token, replace);
    }
    
    public static String getStringAndSubstituteTokens(String category, String id, Map<String, String> replacements)
    {
        String str = getString(category, id);
        return substituteTokens(str, replacements);
    }
    
    public static String getStringAndSubstituteTokens(String id, Map<String, String> replacements)
    {
        return getStringAndSubstituteTokens("general", id, replacements);
    }
    
    public static String substituteFactionTokens(String str, FactionAPI faction)
    {
        Map<String, String> replacements = new HashMap<>();
        String name = faction.getEntityNamePrefix();
        String theName = faction.getDisplayNameWithArticle();
        replacements.put("$faction", name);
        replacements.put("$Faction", Misc.ucFirst(name));
        replacements.put("$theFaction", theName);
        replacements.put("$TheFaction", Misc.ucFirst(theName));
        
        return substituteTokens(str, replacements);
    }
}
