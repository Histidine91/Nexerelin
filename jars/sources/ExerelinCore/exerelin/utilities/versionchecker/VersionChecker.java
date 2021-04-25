package exerelin.utilities.versionchecker;

import com.fs.starfarer.api.Global;
import exerelin.utilities.versionchecker.UpdateInfo.ModInfo;
import exerelin.utilities.versionchecker.UpdateInfo.VersionFile;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.*;

// TEMP: runcode String path = (System.getProperty("user.dir")+"/"+System.getProperty("com.fs.starfarer.settings.paths.mods")); path = path.replace("\\\\", "\\").replace("\\","/");System.out.println(path);
final class VersionChecker
{
    private static final String VANILLA_UPDATE_URL
            = "https://raw.githubusercontent.com/LazyWizard/version-checker/master/vanilla.txt";
    private static int MAX_THREADS = 12;

    static void setMaxThreads(int maxThreads)
    {
        MAX_THREADS = maxThreads;
    }

    private static JSONObject sanitizeJSON(final String rawJSON) throws JSONException
    {
        StringBuilder result = new StringBuilder(rawJSON.length());

        // Remove elements that default JSON implementation can't parse
        for (final String str : rawJSON.split("\n"))
        {
            // Strip out whole-line comments
            if (str.trim().startsWith("#"))
            {
                continue;
            }

            // Strip out end-line comments
            if (str.contains("#"))
            {
                result.append(str.substring(0, str.indexOf('#')));
            }
            else
            {
                result.append(str);
            }
        }

        return new JSONObject(result.toString());
    }

    private static Object getRemoteVersionFile(final String versionFileURL)
    {
        // No valid master version URL entry was found in the .version file
        if (versionFileURL == null)
        {
            return "no update URL was found in .version file";
        }

        // Don't allow local files outside of dev mode
        if (!Global.getSettings().isDevMode()
                && versionFileURL.trim().toLowerCase().startsWith("file:"))
        {
            Log.warn("Local URLs are not allowed unless devmode is enabled: \""
                    + versionFileURL + "\"");
            return "local URLs are not allowed unless devmode is enabled: \""
                    + versionFileURL + "\"";
        }

        Log.info("Loading version info from remote URL " + versionFileURL);

        // Load JSON from external URL and parse version info from it
        try (InputStream stream = new URL(versionFileURL).openStream();
             Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A"))
        {
            return new VersionFile(sanitizeJSON(scanner.next()), true);

        }
        catch (MalformedURLException ex)
        {
            Log.warn("Invalid master version file URL \"" + versionFileURL + "\"", ex);
            return "invalid master version file URL \"" + versionFileURL + "\"";
        }
        catch (IOException ex)
        {
            Log.warn("Failed to load master version file from URL \"" + versionFileURL + "\"", ex);
            return "failed to load master version file from URL \"" + versionFileURL + "\"";
        }
        catch (JSONException ex)
        {
            Log.warn("Malformed JSON in remote version file at URL \"" + versionFileURL + "\"", ex);
            return "malformed JSON in remote version file at URL \"" + versionFileURL + "\"";
        }
    }

    private static String getLatestSSVersion() throws IOException, NoSuchElementException
    {
        Log.info("Loading starsector update info from remote URL " + VANILLA_UPDATE_URL);

        // Get latest Starsector version from remote URL
        try (InputStream stream = new URL(VANILLA_UPDATE_URL).openStream();
             Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A"))
        {
            return scanner.next();
        }
        // This should never happen as the URL is hardcoded
        catch (MalformedURLException ex)
        {
            throw new RuntimeException("Invalid vanilla update URL \"" + VANILLA_UPDATE_URL + "\"", ex);
        }
    }

    private static ModInfo checkForUpdate(final VersionFile localVersion)
    {
        // Download the master version file for this mod
        final Object remoteVersion = getRemoteVersionFile(localVersion.getMasterURL());

        // Return null master and register error if downloading/parsing the master file failed
        if (remoteVersion instanceof String)
        {
            return new ModInfo(localVersion, (String) remoteVersion);
        }

        // Return a container for version files that lets us compare the two
        return new ModInfo(localVersion, (VersionFile) remoteVersion);
    }

    static Future<UpdateInfo> scheduleUpdateCheck(final List<VersionFile> localVersions)
    {
        // Start another thread to handle the update checks and wait on the results
        FutureTask<UpdateInfo> task = new FutureTask<>(new MainTask(localVersions));
        Thread thread = new Thread(task, "Thread-VC-Main");
        thread.setDaemon(true);
        thread.start();
        return task;
    }

    private static final class MainTask implements Callable<UpdateInfo>
    {
        private final List<VersionFile> localVersions;

        private MainTask(final List<VersionFile> localVersions)
        {
            this.localVersions = localVersions;
        }

        private int getNumberOfThreads()
        {
            return Math.max(1, Math.min(MAX_THREADS, localVersions.size()));
        }

        private CompletionService<ModInfo> createCompletionService()
        {
            // Create thread pool and executor
            ExecutorService serviceInternal = Executors.newFixedThreadPool(
                    getNumberOfThreads(), new VCThreadFactory());
            CompletionService<ModInfo> service = new ExecutorCompletionService<>(serviceInternal);

            // Register update checks with thread executor
            for (final VersionFile version : localVersions)
            {
                service.submit(new SubTask(version));
            }

            return service;
        }

        public static void main(String[] args)
        {
            final String[] allVersions = new String[]
                    {
                            "Starsector 0.35a-pre-RC2",
                            "Starsector 0.5a-pre-RC3",
                            "Starsector 0.51a-RC1",
                            "Starsector 0.51a-RC3",
                            "Starsector 0.52a-RC2",
                            "Starsector 0.52.1a-RC4",
                            "Starsector 0.53a-RC4",
                            "Starsector 0.53.1a-RC5",
                            "Starsector 0.54a-RC5",
                            "Starsector 0.54.1a-RC2",
                            "Starsector 0.6a-RC1",
                            "Starsector 0.6a-RC4",
                            "Starsector 0.6.1a-RC2",
                            "Starsector 0.6.2a-RC2",
                            "Starsector 0.6.2a-RC3",
                            "Starsector 0.65a-RC1",
                            "Starsector 0.65.1a-RC1",
                            "Starsector 0.65.2a-RC1",
                            "Starsector 0.7a-RC7",
                            "Starsector 0.7a-RC10",
                            "Starsector 0.7.1a-RC3",
                            "Starsector 0.7.1a-RC4",
                            "Starsector 0.7.1a-RC5",
                            "Starsector 0.7.2a-RC1",
                            "Starsector 0.7.2a-RC2",
                            "Starsector 0.7.2a-RC3",
                            "Starsector 0.8a-RC17",
                            "Starsector 0.8a-RC18",
                            "Starsector 0.8a-RC19",
                            "Starsector 0.8.1a-RC5",
                            "Starsector 0.8.1a-RC6",
                            "Starsector 0.9a-RC6",
                            "Starsector 0.9a-RC7",
                            "Starsector 0.9a-RC8",
                            "Starsector 0.9a-RC9",
                            "Starsector 0.9a-RC10",

                    };

            // Proper order, all should be true
            System.out.println(" Proper order\n--------------");
            for (int x = 0; x < allVersions.length - 1; x++)
            {
                String vOld = allVersions[x], vNew = allVersions[x + 1];
                System.out.printf("%-55s %5s\n", vOld + " vs " + vNew + ": ",
                        isRemoteNewer(vOld, vNew));
            }

            // Reverse order, all should be false
            System.out.println("\n Reverse order\n---------------");
            for (int x = allVersions.length - 1; x > 0; x--)
            {
                String vOld = allVersions[x], vNew = allVersions[x - 1];
                System.out.printf("%-55s %5s\n", vOld + " vs " + vNew + ": ",
                        isRemoteNewer(vOld, vNew));
            }
        }

        private static boolean isRemoteNewer(String localVersion, String remoteVersion)
        {
            // Sanity check
            if (localVersion == null || remoteVersion == null)
            {
                return false;
            }

            // Remove all non-version data from the version information,
            // then split the version number and release candidate number
            // (ex: "Starsector 0.65.2a-RC1" becomes {"0.65.2","1"})
            final String[] localRaw = localVersion.replaceAll("[^0-9.-]", "").split("-", 2),
                    remoteRaw = remoteVersion.replaceAll("[^0-9.-]", "").split("-", 2);

            // Assign array values to variables (solely for clarity's sake)
            final String vLocal = localRaw[0], vRemote = remoteRaw[0],
                    rcLocalRaw = (localRaw.length > 1 ? localRaw[1].replaceAll("\\D", "") : "0"),
                    rcRemoteRaw = (remoteRaw.length > 1 ? remoteRaw[1].replaceAll("\\D", "") : "0");
            final int rcLocal = (rcLocalRaw.isEmpty() ? 0 : Integer.parseInt(rcLocalRaw)),
                    rcRemote = (rcRemoteRaw.isEmpty() ? 0 : Integer.parseInt(rcRemoteRaw));

            // Check major.minor versions to see if remote version is newer
            // Based on StackOverflow answer by Alex Gitelman found here:
            // http://stackoverflow.com/a/6702029/1711452
            if (!vLocal.equals(vRemote))
            {
                // Split version number into major, minor, patch, etc
                final String[] localMajorMinor = vLocal.split("\\."),
                        remoteMajorMinor = vRemote.split("\\.");
                int i = 0;
                // Iterate through all subversions until we find one that's not equal
                while (i < localMajorMinor.length && i < remoteMajorMinor.length
                        && localMajorMinor[i].equals(remoteMajorMinor[i]))
                {
                    i++;
                }
                // Compare first non-equal subversion number
                if (i < localMajorMinor.length && i < remoteMajorMinor.length)
                {
                    // Pad numbers so ex: 0.65 is considered higher than 0.6
                    final String localPadded = String.format("%-3s", localMajorMinor[i]).replace(' ', '0'),
                            remotePadded = String.format("%-3s", remoteMajorMinor[i]).replace(' ', '0');
                    return remotePadded.compareTo(localPadded) > 0;
                }
                // If version length differs but up to that length they are equal,
                // then the longer one is a patch of the shorter
                else
                {
                    return remoteMajorMinor.length > localMajorMinor.length;
                }
            }

            // Check release candidate if major.minor versions are the same
            return (Integer.compare(rcRemote, rcLocal) > 0);
        }

        @Override
        public UpdateInfo call() throws InterruptedException, ExecutionException
        {
            Log.info("Starting update checks");
            final long startTime = System.nanoTime();

            // Check for updates in separate threads for faster execution
            CompletionService<ModInfo> service = createCompletionService();
            final UpdateInfo results = new UpdateInfo();

            // Poll for SS update, can block if site is unresponsive
            if (VCModPluginCustom.checkSSVersion)
            {
                try
                {
                    final String currentVanilla = Global.getSettings().getVersionString(),
                            latestVanilla = getLatestSSVersion();
                    Log.info("Local Starsector version is " + currentVanilla
                            + ", latest known is " + latestVanilla);
                    if (isRemoteNewer(currentVanilla, latestVanilla))
                    {
                        Log.info("Starsector update available!");
                        results.setSSUpdate(latestVanilla);
                    }
                }
                catch (IOException ex)
                {
                    Log.warn("Failed to load vanilla update data from URL \""
                            + VANILLA_UPDATE_URL + "\"", ex);
                    results.setFailedSSError(ex.getClass().getSimpleName());
                }
                catch (Exception ex)
                {
                    Log.warn("Failed to parse vanilla update data from URL \""
                            + VANILLA_UPDATE_URL + "\"", ex);
                    results.setFailedSSError(ex.getClass().getSimpleName());
                }
            }

            // Poll for results from the other threads until all have finished
            int modsToCheck = localVersions.size();
            while (modsToCheck > 0)
            {
                ModInfo tmp = service.take().get(); // Throws exceptions
                modsToCheck--;

                // Update check failed for some reason
                if (tmp.failedUpdateCheck())
                {
                    results.addFailed(tmp);
                }
                // Remote version is newer than local
                else if (tmp.isUpdateAvailable())
                {
                    results.addUpdate(tmp);
                }
                // Remote version is older/same as local
                else
                {
                    results.addNoUpdate(tmp);
                }
            }

            // Report how long the check took
            final String elapsedTime = DecimalFormat.getNumberInstance().format(
                    (System.nanoTime() - startTime) / 1000000000.0d);
            Log.info("Checked game and " + results.getNumModsChecked()
                    + " mods in " + elapsedTime + " seconds");
            return results;
        }

        private static class SubTask implements Callable<ModInfo>
        {
            final VersionFile version;

            private SubTask(VersionFile version)
            {
                this.version = version;
            }

            @Override
            public ModInfo call() throws Exception
            {
                return checkForUpdate(version);
            }
        }
    }

    private static final class VCThreadFactory implements ThreadFactory
    {
        private int threadNum = 0;

        @Override
        public Thread newThread(Runnable r)
        {
            threadNum++;
            Thread thread = new Thread(r, "Thread-VC-" + threadNum);
            thread.setDaemon(true);
            thread.setPriority(3);
            return thread;
        }
    }

    private VersionChecker()
    {
    }
}