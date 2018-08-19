package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StringHelper {
	public static final String FLEET_ASSIGNMENT_CATEGORY = "exerelin_fleetAssignments";
	
	public static final String HR = "-----------------------------------------------------------------------------";
	
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
			str = "INVALID_STRING";
		}
		return str;
	}
	
	public static String getString(String id) {
		return getString("general", id);
	}
	
	public static String ucFirstIgnore$(String str)
	{
		if (str == null) return "Null";
		if (str.isEmpty()) return "";
		if (str.charAt(0) != '$') return Misc.ucFirst(str);
		return ("" + str.charAt(0)) + ("" + str.charAt(1)).toUpperCase() + str.substring(2);
	}
	
	/**
	 *
	 * @param toModify
	 * @param token
	 * @param replace
	 * @param ucFormToo In addition to replacing $token with $replace, also replace $Token with $Replace
	 * @return
	 */
	public static String substituteToken(String toModify, String token, String replace, boolean ucFormToo)
	{
		String str = toModify.replaceAll("\\"+token, replace);
		if (ucFormToo) str = str.replaceAll("\\"+ucFirstIgnore$(token), Misc.ucFirst(replace));
		return str;
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
		return getStringAndSubstituteToken(category, id, token, replace, false);
	}
	
	public static String getStringAndSubstituteToken(String category, String id, String token, String replace, boolean ucFormToo)
	{
		String str = getString(category, id);
		return substituteToken(str, token, replace, ucFormToo);
	}
	
	public static String getStringAndSubstituteToken(String id, String token, String replace)
	{
		return getStringAndSubstituteToken("general", id, token, replace, false);
	}
	
	public static String getStringAndSubstituteToken(String id, String token, String replace, boolean ucFormToo)
	{
		return getStringAndSubstituteToken("general", id, token, replace, ucFormToo);
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
		String name = ExerelinUtilsFaction.getFactionShortName(faction);
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
	
	public static String getHisOrHer(PersonAPI person)
	{
		switch(person.getGender()) {
			case MALE:
				return getString("his");
			case FEMALE:
				return getString("her");
			default:
				return getString("their");
		}
	}
	
	public static String getShipOrFleet(CampaignFleetAPI fleet)
	{
		String fleetOrShip = getString("general", "fleet");
		if (fleet != null) {
			if (fleet.getFleetData().getMembersListCopy().size() == 1) {
				fleetOrShip = getString("general", "ship");
				if (fleet.getFleetData().getMembersListCopy().get(0).isFighterWing()) {
					fleetOrShip = getString("general", "fighterWing");
				}
			}
		}
		return fleetOrShip;
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
	
	public static List<String> factionIdListToFactionNameList(List<String> factionIds, boolean ucFirst)
	{
		List<String> result = new ArrayList<>();
		for (String factionId : factionIds)
		{
			FactionAPI faction = Global.getSector().getFaction(factionId);
			String name = faction.getDisplayName();
			if (ucFirst)
				name = Misc.ucFirst(name);
			result.add(name);
		}
		return result;
	}
	
	public static String writeStringCollection(Collection<String> strings)
	{
		return writeStringCollection(strings, false, false);
	}
	
	public static String writeStringCollection(Collection<String> strings, boolean includeAnd, boolean oxfordComma)
	{
		String str = "";
		int num = 0;
		for (String entry : strings)
		{
			str += entry;
			num++;
			if (num < strings.size()) 
			{
				if (oxfordComma || !includeAnd || num <= strings.size() - 1)
					str += ", ";
				if (includeAnd)
					str += StringHelper.getString("and") + " ";
			}
		}
		return str;
	}
	
	public static void addFactionNameTokensCustom(Map<String, String> tokens, String str, FactionAPI faction) {
		if (faction != null) {
			String factionName = faction.getEntityNamePrefix();
			if (factionName == null || factionName.isEmpty()) {
				factionName = faction.getDisplayName();
			}
			String strUc = Misc.ucFirst(str);
			tokens.put("$" + str, factionName);
			tokens.put("$" + strUc, Misc.ucFirst(factionName));
			tokens.put("$the" + strUc, faction.getDisplayNameWithArticle());
			tokens.put("$The" + strUc, Misc.ucFirst(faction.getDisplayNameWithArticle()));
			
			tokens.put("$" + str + "Long", faction.getDisplayNameLong());
			tokens.put("$" + strUc + "Long", Misc.ucFirst(faction.getDisplayNameLong()));
			tokens.put("$the" + strUc + "Long", faction.getDisplayNameLongWithArticle());
			tokens.put("$The" + strUc + "Long", Misc.ucFirst(faction.getDisplayNameLongWithArticle()));
			
			tokens.put("$" + str + "IsOrAre", faction.getDisplayNameIsOrAre());
		}
	}
}
