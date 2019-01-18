package bin.evgenij.bachelor;

import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;

/**
 * This class provides functionality to measure elapsed time.
 */
public class StopwatchService {

    private long startTime,endTime,result;
    private ArrayList<Long> roundTimes = new ArrayList<>();
    private long lastRoundTime = 0;

    /**
     * Sets the "startTime" variable to the elapsed time since device's boot in milliseconds.
     * Used to start the stopwatch.
     */
    public void start() {
        startTime = SystemClock.elapsedRealtime();
    }

    /**
     * Sets the "endTime" variable to the elapsed time since device's boot in milliseconds.
     * Used to stop the stopwatch and calculate the elapsed time since the stopwatch was started in milliseconds.
     * The result is saved in the "result" variable.
     */
    public void stop() {
        endTime = SystemClock.elapsedRealtime();
        result = endTime-startTime;
    }

    /**
     * Returns the elapsed time in milliseconds.
     * @return The elapsed time in milliseconds.
     */
    public long getResultMillis() {
        return result;
    }

    /**
     * Stops the round time and saves it in a list.
     */
    public void stopRound() {
        long currentTime = SystemClock.elapsedRealtime();
        if(lastRoundTime == 0) {
            roundTimes.add(currentTime-startTime);
        } else {
            roundTimes.add(currentTime-lastRoundTime);
        }
        lastRoundTime = currentTime;
    }
    /**
     * Clears the list with the round times. Basically it resets the stopwatch.
     */
    public void reset() {
        roundTimes.clear();
    }

    /**
     * Returns an Array of type long with the stopped round times.
     * @return  long Array with round times.
     */
    public long[] getRoundTimesMillis() {
        long[] rTimes = new long[roundTimes.size()];
        for(int i = 0; i < roundTimes.size(); i++) {
            rTimes[i] = roundTimes.get(i);
        }
        return rTimes;
    }
}

