package exerelin.utilities.versionchecker;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import exerelin.utilities.versionchecker.UpdateInfo.ModInfo;
import exerelin.utilities.versionchecker.UpdateInfo.VersionFile;
import lombok.Getter;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class UpdateNotificationScript implements EveryFrameScript
{
    private float timeUntilWarn = .75f; // Ensures text appears
    private boolean isUpdateCheckDone = false, hasWarned = false, isDone = false;
    private transient Future<UpdateInfo> futureUpdateInfo;
    @Getter private transient UpdateInfo updateInfo;
    @Getter private transient List<ModSpecAPI> unsupportedMods;

    UpdateNotificationScript(final List<ModSpecAPI> unsupportedMods,
                             final Future<UpdateInfo> updateInfo)
    {
        this.unsupportedMods = unsupportedMods;
        this.futureUpdateInfo = updateInfo;
    }

    @Override
    public boolean isDone()
    {
        return isDone;
    }

    @Override
    public boolean runWhilePaused()
    {
        return true;
    }

    private void warnUpdates(CampaignUIAPI ui)
    {
        final int modsWithoutUpdates = updateInfo.getHasNoUpdate().size(),
                modsWithUpdates = updateInfo.getHasUpdate().size(),
                modsThatFailedUpdateCheck = updateInfo.getFailed().size(),
                modsRequiringManualCheck = unsupportedMods.size();

        // Display number of mods that are up-to-date
        if (modsWithoutUpdates > 0)
        {
            ui.addMessage(modsWithoutUpdates + (modsWithoutUpdates == 1
                            ? " mod is" : " mods are") + " up to date.", Color.WHITE,
                    Integer.toString(modsWithoutUpdates), "", Color.GREEN, Color.BLACK);
        }

        // Display number of mods with an update available
        if (modsWithUpdates > 0)
        {
            ui.addMessage("Found updates for " + modsWithUpdates
                            + (modsWithUpdates == 1 ? " mod." : " mods."), Color.WHITE,
                    Integer.toString(modsWithUpdates), "", Color.YELLOW, Color.BLACK);
        }

        // Display number of mods that failed the update check
        if (modsThatFailedUpdateCheck > 0)
        {
            ui.addMessage("Update check failed for " + modsThatFailedUpdateCheck
                            + (modsThatFailedUpdateCheck == 1 ? " mod." : " mods."), Color.WHITE,
                    Integer.toString(modsThatFailedUpdateCheck), "", Color.RED, Color.BLACK);
        }

        // Display number of mods that require manual version checking
        if (modsRequiringManualCheck > 0)
        {
            ui.addMessage("Manual version checking required for "
                            + modsRequiringManualCheck + (modsRequiringManualCheck == 1
                            ? " unsupported mod." : " unsupported mods."), Color.WHITE,
                    Integer.toString(modsRequiringManualCheck), "", Color.YELLOW, Color.BLACK);
        }

        // Warn if a Starsector update is available
        if (updateInfo.getSSUpdate() != null)
        {
            ui.addMessage("There is a game update available: " + updateInfo.getSSUpdate(), Color.WHITE,
                    updateInfo.getSSUpdate(), "", Color.YELLOW, Color.BLACK);
        }
        else if (updateInfo.getFailedSSError() != null)
        {
            ui.addMessage("Failed to retrieve latest SS version: "
                    + updateInfo.getFailedSSError(), Color.RED);
        }

        String keyName = Keyboard.getKeyName(VCModPluginCustom.notificationKey);
        ui.addMessage("Press " + keyName + " for detailed update information.", Color.WHITE,
                keyName, "", Color.CYAN, Color.BLACK);
    }

    @Override
    public void advance(float amount)
    {
        // Don't do anything while in a menu/dialog
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (Global.getSector().isInNewGameAdvance() || ui.isShowingDialog())
        {
            return;
        }

        // Check if the update thread has finished
        if (!isUpdateCheckDone)
        {
            // We can't do anything if it's not done checking for updates
            if (!futureUpdateInfo.isDone())
            {
                return;
            }

            // Attempt to retrieve the update results from the other thread
            try
            {
                updateInfo = futureUpdateInfo.get(1l, TimeUnit.SECONDS);
                futureUpdateInfo = null;
            }
            catch (Exception ex)
            {
                Log.warn("Failed to retrieve mod update info", ex);
                ui.addMessage("Failed to retrieve mod update info!", Color.RED);
                ui.addMessage("Check starsector.log for details.", Color.RED);
                Global.getSector().removeTransientScript(this);
                isDone = true; // Just in case
                return;
            }

            isUpdateCheckDone = true;
        }

        // On first game load, warn about any updates available
        if (!hasWarned && timeUntilWarn <= 0f)
        {
            warnUpdates(ui);
            hasWarned = true;
        }
        else
        {
            timeUntilWarn -= amount;
        }

        // User can press a key to summon a detailed update report
        if (Keyboard.isKeyDown(VCModPluginCustom.notificationKey))
        {
            ui.showInteractionDialog(new UpdateNotificationDialog(
                    updateInfo, unsupportedMods), Global.getSector().getPlayerFleet());
        }
    }

    private static class UpdateNotificationDialog implements InteractionDialogPlugin
    {
        private static final String ANNOUNCEMENT_BOARD
                = "http://fractalsoftworks.com/forum/index.php?board=1.0";
        private static final String MOD_INDEX_THREAD
                = "http://fractalsoftworks.com/forum/index.php?topic=177.0";
        private static final int ENTRIES_PER_PAGE = 5, LINE_LENGTH = 45;
        private final String ssUpdate, ssUpdateError;
        private final List<ModInfo> hasUpdate, hasNoUpdate, failedCheck;
        private final List<ModSpecAPI> unsupported;
        private InteractionDialogAPI dialog;
        private TextPanelAPI text;
        private OptionPanelAPI options;
        private List<ModInfo> currentList;
        private int currentPage = 1;

        private enum Menu
        {
            MAIN_MENU,
            UPDATE_VANILLA,
            UPDATE_MANUALLY,
            LIST_UPDATES,
            LIST_NO_UPDATES,
            LIST_FAILED,
            PREVIOUS_PAGE,
            NEXT_PAGE,
            OPEN_ALL_UPDATES,
            RETURN,
            EXIT
        }

        private UpdateNotificationDialog(UpdateInfo updateInfo, List<ModSpecAPI> unsupported)
        {
            hasUpdate = updateInfo.getHasUpdate();
            hasNoUpdate = updateInfo.getHasNoUpdate();
            failedCheck = updateInfo.getFailed();
            ssUpdate = updateInfo.getSSUpdate();
            ssUpdateError = updateInfo.getFailedSSError();
            this.unsupported = unsupported;

            // Sort by mod name
            Collections.sort(hasUpdate);
            Collections.sort(hasNoUpdate);
            Collections.sort(failedCheck);
            Collections.sort(unsupported, new Comparator<ModSpecAPI>()
            {
                @Override
                public int compare(ModSpecAPI o1, ModSpecAPI o2)
                {
                    return o1.getName().compareTo(o2.getName());
                }
            });
        }

        // Taken from LazyLib's StringUtils, still up-to-date as of LazyLib v2.1
        private static String wrap(String toWrap)
        {
            if (toWrap == null || LINE_LENGTH <= 1)
            {
                return "";
            }

            // Analyse each line of the message seperately
            String[] lines = toWrap.split("\n");
            // StringBuilder doesn't auto-resize down, so setting the length here
            // is an optimization even though length is reset to 0 each line
            StringBuilder line = new StringBuilder(LINE_LENGTH);
            StringBuilder message = new StringBuilder((int) (toWrap.length() * 1.1f));
            for (String rawLine : lines)
            {
                // Check if the string even needs to be broken up
                if (rawLine.length() <= LINE_LENGTH)
                {
                    // Entire message fits into a single line
                    message.append(rawLine).append("\n");
                }
                else
                {
                    // Clear the StringBuilder so we can generate a new line
                    line.setLength(0);
                    // Split the line up into the individual words, and append each
                    // word to the next line until the character limit is reached
                    String[] words = rawLine.split(" ");
                    for (int y = 0; y < words.length; y++)
                    {
                        // If this word by itself is longer than the line limit,
                        // break it up into multiple sub-lines separated by a dash
                        if (words[y].length() >= LINE_LENGTH)
                        {
                            // Make sure to post the previous line in queue, if any
                            if (line.length() > 0)
                            {
                                message.append(line.toString()).append("\n");
                                line.setLength(0);
                            }

                            // Break up word into multiple lines separated with dash
                            while (words[y].length() > LINE_LENGTH)
                            {
                                message.append(words[y].substring(0, LINE_LENGTH - 1))
                                        .append("-\n");
                                words[y] = words[y].substring(LINE_LENGTH - 1);
                            }

                            // Add any remaining text to the next line
                            if (!words[y].isEmpty())
                            {
                                // If we have reached the end of the message, ensure
                                // that we post the remaining part of the queue
                                if (y == (words.length - 1))
                                {
                                    message.append(words[y]).append("\n");
                                }
                                else
                                {
                                    line.append(words[y]);
                                }
                            }
                        }
                        // If this word would put us over the length limit, post
                        // the queue and back up a step (re-check this word with
                        // a blank line - this is in case it trips the above block)
                        else if (words[y].length() + line.length() >= LINE_LENGTH)
                        {
                            message.append(line.toString()).append("\n");
                            line.setLength(0);
                            y--;
                        }
                        // This word won't put us over the limit, add it to the queue
                        else
                        {
                            line.append(words[y]);
                            line.append(" ");

                            // If we have reached the end of the message, ensure
                            // that we post the remaining part of the queue
                            if (y == (words.length - 1))
                            {
                                message.append(line.toString()).append("\n");
                            }
                        }
                    }
                }
            }

            // Don't end with a newline if the original string didn't do so
            if (!toWrap.endsWith("\n"))
            {
                message.deleteCharAt(message.length() - 1);
            }

            return message.toString();
        }

        private void generateModMenu()
        {
            // Show as many mods as can fit into one page of options
            final int offset = (currentPage - 1) * ENTRIES_PER_PAGE,
                    max = Math.min(offset + ENTRIES_PER_PAGE, currentList.size()),
                    numPages = 1 + ((currentList.size() - 1) / ENTRIES_PER_PAGE);
            for (int x = offset, y = 1; x < max; x++, y++)
            {
                ModInfo mod = currentList.get(x);
                VersionFile local = mod.getLocalVersion();
                options.addOption(local.getName(), local);
                options.setEnabled(local, local.getUpdateURL() != null);
                if (local.getUpdateURL() != null)
                {
                    options.setTooltip(local, "URL: " + local.getUpdateURL());
                }
            }

            // Support for multiple pages of options
            if (currentPage > 1)
            {
                options.addOption("Previous page", Menu.PREVIOUS_PAGE);
                options.setShortcut(Menu.PREVIOUS_PAGE, Keyboard.KEY_LEFT,
                        false, false, false, true);
            }
            if (currentPage < numPages)
            {
                options.addOption("Next page", Menu.NEXT_PAGE);
                options.setShortcut(Menu.NEXT_PAGE, Keyboard.KEY_RIGHT,
                        false, false, false, true);
            }

            // Show page number in prompt if multiple pages are present
            dialog.setPromptText("Select a mod to go to its forum thread"
                    + (numPages > 1 ? " (page " + currentPage + "/" + numPages + ")" : "") + ":");
            options.addOption("Main menu", Menu.MAIN_MENU);
            options.setShortcut(Menu.MAIN_MENU, Keyboard.KEY_ESCAPE,
                    false, false, false, true);
        }

        private void goToMenu(Menu menu)
        {
            options.clearOptions();

            switch (menu)
            {
                case MAIN_MENU:
                    text.clear();
                    final int numUpToDate = hasNoUpdate.size(),
                            numHasUpdate = hasUpdate.size(),
                            numFailed = failedCheck.size(),
                            numUnsupported = unsupported.size();

                    text.addParagraph((numUpToDate == 1)
                            ? "There is 1 up-to-date mod"
                            : "There are " + numUpToDate + " up-to-date mods");
                    text.highlightInLastPara(Color.GREEN,
                            Integer.toString(numUpToDate));
                    for (ModInfo info : hasNoUpdate)
                    {
                        text.addParagraph(wrap(" - " + info.getName() + " ("
                                + info.getVersionString() + ")"));
                        text.highlightInLastPara(info.isLocalNewer() ? Color.CYAN
                                : Color.GREEN, info.getName(), " vs ");
                    }

                    text.addParagraph((numHasUpdate == 1)
                            ? "There is 1 mod with an update available"
                            : "There are " + numHasUpdate + " mods with updates available");
                    text.highlightInLastPara((numHasUpdate > 0 ? Color.YELLOW
                            : Color.GREEN), Integer.toString(numHasUpdate));
                    for (ModInfo info : hasUpdate)
                    {
                        text.addParagraph(wrap(" - " + info.getName() + " ("
                                + info.getVersionString() + ")"));
                        text.highlightInLastPara(Color.YELLOW, info.getName(), " vs ");
                    }

                    if (numFailed > 0)
                    {
                        text.addParagraph((numFailed == 1)
                                ? "There is 1 mod that failed its update check"
                                : "There are " + numFailed + " mods that failed their update checks");
                        text.highlightInLastPara((numFailed > 0 ? Color.RED
                                : Color.GREEN), Integer.toString(numFailed));
                        for (ModInfo info : failedCheck)
                        {
                            text.addParagraph(wrap(" - " + info.getName() + " ("
                                    + info.getVersionString() + ", "
                                    + info.getErrorMessage() + ")"));
                            text.highlightInLastPara(Color.RED, info.getName());
                        }
                    }

                    if (numUnsupported > 0)
                    {
                        text.addParagraph((numUnsupported == 1)
                                ? "There is 1 unsupported mod enabled"
                                : "There are " + numUnsupported + " unsupported mods enabled");
                        text.highlightInLastPara((numUnsupported > 0 ? Color.YELLOW
                                : Color.GREEN), Integer.toString(numUnsupported));
                        for (ModSpecAPI mod : unsupported)
                        {
                            text.addParagraph(wrap(" - " + mod.getName() + " ("
                                    + mod.getVersion() + ")"));
                            text.highlightInLastPara(Color.YELLOW, mod.getName());
                        }
                    }

                    dialog.setPromptText("Select a category for forum thread links:");
                    options.addOption("List mods without updates", Menu.LIST_NO_UPDATES);
                    options.setEnabled(Menu.LIST_NO_UPDATES, !hasNoUpdate.isEmpty());
                    options.addOption("List mods with updates", Menu.LIST_UPDATES);
                    options.setEnabled(Menu.LIST_UPDATES, !hasUpdate.isEmpty());

                    // Only show this option if there are updates available
                    if (!hasUpdate.isEmpty())
                    {
                        options.addOption("Open page for all mods with an update available", Menu.OPEN_ALL_UPDATES);

                        // Launching the browser doesn't work on some Linux distros
                        if (!Desktop.isDesktopSupported())
                        {
                            options.setEnabled(Menu.OPEN_ALL_UPDATES, false);
                            options.setTooltip(Menu.OPEN_ALL_UPDATES, "Not supported on this OS!");
                        }
                    }

                    // Only show this option if an update check has actually failed
                    if (!failedCheck.isEmpty())
                    {
                        options.addOption("List mods that failed update check", Menu.LIST_FAILED);
                    }

                    // Only show this option if an unsupported mod is enabled
                    if (!unsupported.isEmpty())
                    {
                        options.addOption("Go to mod index forum thread", Menu.UPDATE_MANUALLY);
                    }

                    // Notify of game update if available
                    if (ssUpdate != null)
                    {
                        text.addParagraph("There is a game update available:\n - " + ssUpdate);
                        text.highlightInLastPara(Color.YELLOW, ssUpdate);

                        options.addOption("Download " + ssUpdate, Menu.UPDATE_VANILLA);
                    }
                    // Notify that update check failed
                    else if (ssUpdateError != null)
                    {
                        final String curVersion = Global.getSettings().getVersionString();
                        text.addParagraph("Failed to retrieve latest Starsector version ("
                                + ssUpdateError + "):\n - Current: " + curVersion);
                        text.highlightFirstInLastPara(curVersion, Color.RED);
                        options.addOption("Go to Starsector announcement board", Menu.UPDATE_VANILLA);
                    }

                    options.addOption("Exit", Menu.EXIT);
                    options.setShortcut(Menu.EXIT, Keyboard.KEY_ESCAPE,
                            false, false, false, true);
                    break;
                case UPDATE_MANUALLY:
                    goToMenu(Menu.MAIN_MENU);
                    text.addParagraph("Opening mod index thread...");
                    options.setEnabled(Menu.UPDATE_MANUALLY, false);
                    try
                    {
                        Desktop.getDesktop().browse(URI.create(MOD_INDEX_THREAD));
                    }
                    catch (IOException ex)
                    {
                        Log.warn("Failed to launch browser: ", ex);
                        text.addParagraph("Failed to launch browser: "
                                + ex.getMessage(), Color.RED);
                    }
                    break;
                case UPDATE_VANILLA:
                    goToMenu(Menu.MAIN_MENU);
                    text.addParagraph("Opening update announcement subforum...");
                    options.setEnabled(Menu.UPDATE_VANILLA, false);
                    try
                    {
                        Desktop.getDesktop().browse(URI.create(ANNOUNCEMENT_BOARD));
                    }
                    catch (IOException ex)
                    {
                        Log.warn("Failed to launch browser: ", ex);
                        text.addParagraph("Failed to launch browser: "
                                + ex.getMessage(), Color.RED);
                    }
                    break;
                case LIST_UPDATES:
                    currentList = hasUpdate;
                    currentPage = 1;
                    generateModMenu();
                    break;
                case LIST_NO_UPDATES:
                    currentList = hasNoUpdate;
                    currentPage = 1;
                    generateModMenu();
                    break;
                case LIST_FAILED:
                    currentList = failedCheck;
                    currentPage = 1;
                    generateModMenu();
                    break;
                case PREVIOUS_PAGE:
                    currentPage--;
                    generateModMenu();
                    break;
                case NEXT_PAGE:
                    currentPage++;
                    generateModMenu();
                    break;
                case OPEN_ALL_UPDATES:
                    for (ModInfo mod : hasUpdate)
                    {
                        openModThread(mod.getLocalVersion());
                    }
                    goToMenu(Menu.MAIN_MENU);
                    options.setEnabled(Menu.OPEN_ALL_UPDATES, false);
                    break;
                case RETURN:
                    generateModMenu();
                    break;
                case EXIT:
                default:
                    dialog.dismiss();
            }
        }

        @Override
        public void init(InteractionDialogAPI dialog)
        {
            this.dialog = dialog;
            this.options = dialog.getOptionPanel();
            this.text = dialog.getTextPanel();

            dialog.hideVisualPanel();
            //dialog.setTextWidth(Display.getWidth() * .9f);
            goToMenu(Menu.MAIN_MENU);
        }

        private void openModThread(VersionFile mod)
        {
            // Some flavors of Linux don't support the Desktop API without certain libraries installed
            if (!Desktop.isDesktopSupported())
            {
                final StringSelection modUrl = new StringSelection(mod.getUpdateURL());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(modUrl, modUrl);
                text.addParagraph("Opening the browser directly is not supported on this OS!\n" +
                        "The forum thread URL has been copied to the clipboard instead.");
                return;
            }

            // Open the mod forum thread in the user's default browser
            try
            {
                final String url = mod.getUpdateURL();
                if (url != null)
                {
                    text.addParagraph("Opening " + mod.getName() + " forum thread...");
                    options.setEnabled(mod, false);
                    Desktop.getDesktop().browse(URI.create(url));
                }
            }
            catch (Exception ex)
            {
                Log.warn("Failed to launch browser: ", ex);
                text.addParagraph("Failed to launch browser: "
                        + ex.getMessage(), Color.RED);
            }
        }

        @Override
        public void optionSelected(String optionText, Object optionData)
        {
            text.addParagraph(optionText, Color.CYAN);

            // Option was a menu? Go to that menu
            if (optionData instanceof Menu)
            {
                goToMenu((Menu) optionData);
            }
            // Option was version data? Launch that mod's forum thread
            else if (optionData instanceof VersionFile)
            {
                openModThread((VersionFile) optionData);
            }
        }

        @Override
        public void optionMousedOver(String optionText, Object optionData)
        {
        }

        @Override
        public void advance(float amount)
        {
        }

        @Override
        public void backFromEngagement(EngagementResultAPI battleResult)
        {
        }

        @Override
        public Object getContext()
        {
            return null;
        }

        @Override
        public Map<String, MemoryAPI> getMemoryMap()
        {
            return null;
        }
    }
}