package org.dekoboko.datagatherprototype.model;

import android.location.Location;

/**
 * Created by dirkschumacher on 25.06.16.
 */
public final class DataPoint {
    private final float accuracy;
    private final double latitude;
    private final double longitude;
    private final float speed;
    private final long time;
    private final int average;
    private final int max;

    public DataPoint(Location location, int average, int max) {
        accuracy = location.getAccuracy();
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        speed = location.getSpeed();
        time = location.getTime();
        this.average = average;
        this.max = max;
    }
}
