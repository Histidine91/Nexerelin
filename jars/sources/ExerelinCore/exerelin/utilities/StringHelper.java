package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.util.Misc;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class StringHelper {
    public static final String FLEET_ASSIGNMENT_CATEGORY = "exerelin_fleetAssignments";
    
    public static String getString(String category, String id) {
        String str = "";
        try {
            str = Global.getSettings().getString(category, id);
        }
        catch (Exception ex)
        {
            // could be a string not found
            //str = ex.toString();  // looks really silly
            Global.getLogger(StringHelper.class).warn(ex);
        }
        return str;
    }
    
    public static String getString(String id) {
        return getString("general", id);
    }
    
    public static String substituteToken(String toModify, String token, String replace)
    {
        return toModify.replaceAll("\\"+token, replace);
    }
    
    public static String substituteTokens(String toModify, Map<String, String> replacements)
    {
        for (Map.Entry<String, String> tmp : replacements.entrySet())
        {
            toModify = substituteToken(toModify, tmp.getKey(), tmp.getValue());
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
    
    public static String getFleetAssignmentString(String id, String target, String missionType)
    {
        String str = getString(FLEET_ASSIGNMENT_CATEGORY, id);
        if (target != null) str = substituteToken(str, "$target", target);
        if (missionType != null) str = substituteToken(str, "$missionType", getString(FLEET_ASSIGNMENT_CATEGORY, missionType));
        return str;
    }
	
	public static String getFleetAssignmentString(String id, String target)
	{
		return getFleetAssignmentString(id, target, null);
	}

    // http://stackoverflow.com/a/15191508
    // see https://bitbucket.org/Histidine/exerelin/issues/1/marketarchtype-java-somehow-confuses-the for why this is used
    public static String flattenToAscii(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        string = Normalizer.normalize(string, Normalizer.Form.NFD);
        for (char c : string.toCharArray()) {
            if (c <= '\u007F') sb.append(c);
        }
        return sb.toString();
    }
}
