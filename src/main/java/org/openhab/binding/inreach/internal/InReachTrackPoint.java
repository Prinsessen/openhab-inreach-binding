/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.inreach.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Represents a single parsed KML trackpoint from the MapShare feed.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class InReachTrackPoint {

    private final long id;
    private final long timestampUtcMillis;
    private final double latitude;
    private final double longitude;
    private final double elevationMeters;
    private final double speedKmh;
    private final double courseDegrees;
    private final String event;
    private final boolean validGpsFix;
    private final boolean inEmergency;
    private final @Nullable String text;
    private final @Nullable String imei;
    private final @Nullable String deviceType;
    private final @Nullable String name;

    public InReachTrackPoint(long id, long timestampUtcMillis, double latitude, double longitude,
            double elevationMeters, double speedKmh, double courseDegrees, String event, boolean validGpsFix,
            boolean inEmergency, @Nullable String text, @Nullable String imei, @Nullable String deviceType,
            @Nullable String name) {
        this.id = id;
        this.timestampUtcMillis = timestampUtcMillis;
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevationMeters = elevationMeters;
        this.speedKmh = speedKmh;
        this.courseDegrees = courseDegrees;
        this.event = event;
        this.validGpsFix = validGpsFix;
        this.inEmergency = inEmergency;
        this.text = text;
        this.imei = imei;
        this.deviceType = deviceType;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public long getTimestampUtcMillis() {
        return timestampUtcMillis;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getElevationMeters() {
        return elevationMeters;
    }

    public double getSpeedKmh() {
        return speedKmh;
    }

    public double getCourseDegrees() {
        return courseDegrees;
    }

    public String getEvent() {
        return event;
    }

    public boolean isValidGpsFix() {
        return validGpsFix;
    }

    public boolean isInEmergency() {
        return inEmergency;
    }

    public @Nullable String getText() {
        return text;
    }

    public @Nullable String getImei() {
        return imei;
    }

    public @Nullable String getDeviceType() {
        return deviceType;
    }

    public @Nullable String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("InReachTrackPoint[id=%d, time=%d, lat=%.6f, lon=%.6f, speed=%.1f, event=%s]", id,
                timestampUtcMillis, latitude, longitude, speedKmh, event);
    }
}
