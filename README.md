# README #

Nexerelin is a mod for the game [Starsector](http://fractalsoftworks.com). It implements 4X-style gameplay with faction wars, diplomacy and planetary conquest.

Current repo version: v0.11.1b

## Setup instructions ##
Check out the repo to Starsector/mods/Nexerelin (or some other folder name) and it can be played immediately. 

If you want to build Nexerelin locally, follow the steps below.
Intellij and Eclipse forks are currently the most popular Java IDE out there.
The following contains instructions on how to set this project up on Intellij. (Eclipse should be similar but you will need to figure out where those dialogs and settings are on your own.)

First of all, Nexerelin also requires several other external libs for compiling. The whole list can be found at the [end of this document](#required-libraries). Download and unzip them somewhere on your computer, you can download them to the starsector mod folder but you don't have to load them in the launcher.
This is just so the jar files contained in those mods can be loaded by nexerelin java classes.

You will also need to use jdk1.7 to compile the jar; starsector is currently using jdk1.7.0.79 but anything newer than that would work as well. i.e. 1.7.0.80 and not java 8, 9, etc.
Depending on your IDE, you may not actually need to download or install JDK 1.7 except for profiling purposes. Simply use a newer JDK and set your project in your IDE to use Java 7.
If you need JDK 7: Since it's past end of life, Oracle requires users to create an account before dling it.
There is a link to the bugmenot website in this Reddit post if you are too lazy to create a new Oracle account:
[https://www.reddit.com/r/java/comments/6ag6qk/oracle_not_allowing_jdk_downloads_without_an/](https://www.reddit.com/r/java/comments/6ag6qk/oracle_not_allowing_jdk_downloads_without_an/)

Download Intellij community edition (which is free unless you somehow want to spend money on the pro edition), just google search it.
All the folder paths mentioned below should be full folder path.
Intellij has built-in git support so i would just clone Nexerelin via Intellij.
- After starting intellij, do File > New > Project from Version Control > Git
- Paste the url in the box, something like https://github.com/Histidine91/Nexerelin.git
- You can hit the test button to see if there is any connection issue.
- Change the directory to be the starsector/mod directory. Usually I recommend separating out source folder and deployment folder, but this will make you life easier without having to set up a configuration to copy files over to the starsector/mod to deploy the changes.

Source Control:
- VCS for intellij is on the botton right corner. Click on that git ... and select some (remote/local) branch to check it out.
- Repo is currently read-only, msg Histidine if you need write access.

Project Settings:
- First mark ExerelinCore folder as a Source folder on the project tab that is on the left.
- Basically in that folder structure navigate to Nexerlin > jars > sources > ExerelinCore ; right click on that folder > Mark Directory As > Sources Root (it should be colored blue now)
- All the java files that needed to be compiled are underneath that folder.
- Then select File > Project Structure dialog. Now we need to set up jdk.
- Click on the New... button, select JDK and navigate to your jdk installation folder. This should say 1.7 for jdk 1.7 after you select it.
- Change project language level to be 7 for java 1.7. (Basically this allows the IDE to complain if you are using other jdk version features that are not compatible with the current language)
- Compiler output folder should go to Starsector\mods\Nexerelin\jars\build (this folder should be under .gitignore so the build is not checked into source control)

- Go to the either Global Libraries or Libraries on the dialog (Global lib is shared between all of your intellij proj, lib is only for your current project)
- Click on the green plus sign and select Java for a new java lib.
- Select the jar folder for those mods libraries we downloaded earlier. i.e. Starsector\mods\LazyLib\jars
- You will also need to select Starsector\starsector-core for the starsector api

- Go to Artifacts on the dialog. This will set up the jar artifact to be created after building.
- Click the green plus and select JAR > From modules with dependencies
- Output directory is: Starsector\mods\Nexerelin\jars
- Check the include in project build
- Rename that jar to be ExerelinCore.jar, it is under the Output Layout tab. We want this jar to overwrite the current one in the folder so it needs the same name.
- Select everything under that ExerelinCore.jar and click on the red minus sign. We dont want it to contain the libs that we set up.
- Then click on the green plus and select Module Output.
- You can include a manifect file if you want, the main class should be: exerelin.utilities.versionchecker.VersionChecker.MainTask

Build Configurations + Debug:
- The green down arrow with binary 011001 will compile and build the project. Due to the previous setup, a new jar will replace that existing ExerelinCore.jar if you build the project.
- To run starsector while within the IDE you will need to get a batch plugin to kick off the starsector.bat file.
- Go to File > Settings > Plugins and search for Batch Scripts Support plugin then download it. (It should be in remote repo). Restart IDE if necessary after installation.
- Create a new configuration by selecting Edit Configurations.. on that dropdown box between the build icon and the run icon (Green triangle should be on top right for default toolbar)
- Click green plus arrow and select Batch
- Put the Starsector/starsector-core/starsector.bat path in there (Windows will use the .bat, Mac/Linux will use the .sh)
- Working directory is Starsector\starsector-core
- Under Before Launch, click green plus and select Build if it is not there already.

- To Debug Starsector, create a new configuration and select Remote. Copy that `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005`
- Edit the starsector.bat and paste that line in there. I just pasted mine before the -Xms ... jvm memory settings.
- While you are there, you might as well edit those memory settings if you have a good computer. Just google search java xms xmx etc.

- Now when you run the batch it should build your project and start starsector. The starsector log should also show up in Intellij.
- To hook the debugger onto startsector, select the debug configuration we set up and click on the Debug 'Run' icon, it is green circle thing next to the green triangle.
- To set up a debug point, go to some line in java code and just left click between the line number and the edge of the text. It should place a red circle which is the debug point.
- You can right click on it if you want to set up some conditions on when it should suspend.

That should be everything that is needed to set up this project. Post a message on the forums if I've missed out anything or if I should upload pictures.
You can use this similar setup for other starsector mods as well.
Kazi also has a nice post on intellij setup as well [http://fractalsoftworks.com/forum/index.php?topic=10057.0](http://fractalsoftworks.com/forum/index.php?topic=10057.0)

Have fun helping us debug the code :)

### Required Libraries ###
These are all the external libraries and referenced mods required to locally build Nexerelin:

- [ApproLight](https://fractalsoftworks.com/forum/index.php?topic=9688.0)
- [Lazylib](https://fractalsoftworks.com/forum/index.php?topic=5444.0)
- [Console Commands](https://fractalsoftworks.com/forum/index.php?topic=4106.0)
- [MagicLib](https://fractalsoftworks.com/forum/index.php?topic=13718.0)
- [Ship/Weapon Pack](https://fractalsoftworks.com/forum/index.php?topic=11018.0)
- [The Knights Templar](https://fractalsoftworks.com/forum/index.php?topic=8095.0)
- [Underworld](https://fractalsoftworks.com/forum/index.php?topic=11002.0)
- [Unofficial New Game Plus](https://fractalsoftworks.com/forum/index.php?topic=16680.0)
- [Vayra's Sector](https://fractalsoftworks.com/forum/index.php?topic=16058.0)
- [Volkov Industrial Conglomerate](https://fractalsoftworks.com/forum/index.php?topic=19603.0)
- [Crew Replacer](https://fractalsoftworks.com/forum/index.php?topic=24249.0)
- [LunaLib](https://github.com/Lukas22041/LunaLib/)
- [GraphicsLib](https://fractalsoftworks.com/forum/index.php?topic=10982)
- [Project Lombok](https://projectlombok.org/)

## License ##
The Prism Freeport code and art assets are taken or adapted from the [Scy Nation mod](http://fractalsoftworks.com/forum/index.php?topic=8010.0) by Tartiflette and licensed under the [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International license](https://creativecommons.org/licenses/by-nc-sa/4.0/).

All other code is licensed under the [MIT License (Expat License version)](https://opensource.org/licenses/MIT) except where otherwise specified.

All other assets are released as [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/) unless otherwise specified.
