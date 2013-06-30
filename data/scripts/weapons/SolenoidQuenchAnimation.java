package data.scripts.weapons;

public class SolenoidQuenchAnimation extends BaseAnimateOnFireEffect
{
    public SolenoidQuenchAnimation()
    {
        super();
        setFramesPerSecond(15);
        pauseOnFrame(13, 4);
    }
}