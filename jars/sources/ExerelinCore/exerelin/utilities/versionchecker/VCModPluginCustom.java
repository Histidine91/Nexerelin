package exerelin.utilities.versionchecker;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import exerelin.utilities.versionchecker.UpdateInfo.VersionFile;
import org.apache.log4j.Level;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public final class VCModPluginCustom
{
    private static final String SETTINGS_FILE = "data/config/version/version_checker_nex.json";
    private static final String CSV_PATH = "data/config/version/version_files.csv";
    private static UpdateNotificationScript script = null;
    static boolean checkSSVersion = false, preferNexus = false;
    static int notificationKey;

    private static boolean isIgnored(ModSpecAPI mod)
    {
        try
        {
            final JSONObject modInfo = Global.getSettings().loadJSON("mod_info.json", mod.getId());
            return modInfo.optBoolean("suppressVCUnsupported", false);
        }
        catch (Exception ex)
        {
            Log.error("Failed to load mod_info.json for mod " + mod.getId(), ex);
            return false;
        }
    }

    // Note: if there's any significant change to how this function works,
    // the RecheckVersions console command will need to be updated as well
    public static void onApplicationLoad() throws Exception
    {
        // Disable URL caching - fixes an issue with re-uploaded BitBucket files
        new URLConnection(null)
        {
            @Override
            public void connect() throws IOException
            {
            }
        }.setDefaultUseCaches(false);
		
		// Enable TLS v1.2 (required to use Bitbucket past December 1st, 2018)
		System.setProperty("https.protocols", "SSLv3,TLSv1,TLSv1.1,TLSv1.2");
		
        final JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);
        notificationKey = settings.getInt("summonUpdateNotificationKey");
        checkSSVersion = settings.getBoolean("checkStarsectorVersion");
		preferNexus = settings.getBoolean("preferNexusLinks");
        VersionChecker.setMaxThreads(settings.getInt("maxUpdateThreads"));
        Log.setLevel(Level.toLevel(settings.getString("logLevel"), Level.WARN));

        final List<VersionFile> versionFiles = new ArrayList<>();
        final JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "version file", CSV_PATH, "lw_version_checker");

        final int numMods = csv.length(),
                csvPathLength = CSV_PATH.length() + 1;
        final List<String> modPaths = new ArrayList<>(numMods);
        Log.info("Found " + numMods + " mods with version info");
        for (int x = 0; x < numMods; x++)
        {
            final JSONObject row = csv.getJSONObject(x);
            final String versionFile = row.getString("version file");
            final String source = row.optString("fs_rowSource", null);
            if (source != null && source.length() > csvPathLength)
            {
                modPaths.add(source.substring(0, source.length() - csvPathLength));
            }

            try
            {
                versionFiles.add(new VersionFile(
                        Global.getSettings().loadJSON(versionFile), false));
            }
            catch (JSONException ex)
            {
                throw new RuntimeException("Failed to parse version file \""
                        + versionFile + "\"", ex);
            }
        }

        final List<ModSpecAPI> unsupportedMods = new ArrayList<>();
        for (ModSpecAPI mod : Global.getSettings().getModManager().getEnabledModsCopy())
        {
            if (!modPaths.contains(mod.getPath()) && !isIgnored(mod))
            {
                unsupportedMods.add(mod);
            }
        }

        if (!versionFiles.isEmpty())
        {
            script = new UpdateNotificationScript(unsupportedMods,
                    VersionChecker.scheduleUpdateCheck(versionFiles));
        }
    }
	
    public static void onGameLoad(boolean newGame)
    {
        if (script != null && !script.isDone())
        {
            Global.getSector().addTransientScript(script);
        }
    }
}