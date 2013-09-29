package data.scripts.world.exerelin;

/**
 * Created with IntelliJ IDEA.
 * User: Kirk
 * Date: 22/09/13
 * Time: 9:50 AM
 * To change this template use File | Settings | File Templates.
 */
public class ExerelinConfig
{
    // List of ships that can be produced at any station regardless of owner
    public static String[] commonShipList = new String[]{"ox_Hull", "crig_Hull", "shuttle_Attack"};

    // Randomise the location of the omnifactory
    public static boolean randomOmnifactoryLocation = false;

    public static String[] neutralFactions = new String[]{"neutral", "independent"}; // NOT USED

    // Resourcing
    public static String asteroidMiningResource = "supplies"; // NOT USED
    public static String gasgiantMiningResource = "fuel"; // NOT USED
    public static String fleetCostResource = "supplies"; // NOT USED 
    
    // System Generation Minimums
    public static int minimumPlanets = 3;
    public static int minimumStations = 1;
    public static int minimumAsteroidBelts = 0;

    // Threading support for improving/smoothing performance
    public static boolean enableThreading = true;
}
