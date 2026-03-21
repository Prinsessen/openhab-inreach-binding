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

import static org.openhab.binding.inreach.internal.InReachBindingConstants.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PointType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link InReachHandler} handles polling the Garmin MapShare KML feed
 * and updating channels with the latest trackpoint data.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class InReachHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(InReachHandler.class);

    private final HttpClient httpClient;
    private @Nullable InReachKmlClient kmlClient;
    private @Nullable InReachConfiguration config;
    private @Nullable ScheduledFuture<?> pollingJob;

    // Track the last known point ID to detect new data
    private long lastPointId = 0;

    // Store previous point for distance calculation
    private double prevLat = Double.NaN;
    private double prevLon = Double.NaN;

    public InReachHandler(Thing thing, HttpClient httpClient) {
        super(thing);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        InReachConfiguration cfg = getConfigAs(InReachConfiguration.class);
        config = cfg;

        if (cfg.mapShareId.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "MapShare identifier must be configured");
            return;
        }

        logger.info("Initializing inReach device: MapShare ID = {}", cfg.mapShareId);

        kmlClient = new InReachKmlClient(httpClient, cfg.mapShareId, cfg.password);

        // Schedule initialization task to test connection
        scheduler.execute(this::initializeDevice);
    }

    private void initializeDevice() {
        InReachKmlClient client = kmlClient;
        InReachConfiguration cfg = config;
        if (client == null || cfg == null) {
            return;
        }

        // Test connection
        if (!client.testConnection()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Cannot reach MapShare feed. Check mapShareId and password.");
            // Retry after 60 seconds
            scheduler.schedule(this::initializeDevice, 60, TimeUnit.SECONDS);
            return;
        }

        logger.info("MapShare feed accessible for '{}'", cfg.mapShareId);
        updateStatus(ThingStatus.ONLINE);

        // Do an initial poll
        pollMapShare();

        // Schedule regular polling
        pollingJob = scheduler.scheduleWithFixedDelay(this::pollMapShare, cfg.refreshInterval, cfg.refreshInterval,
                TimeUnit.SECONDS);

        logger.info("inReach polling started: every {}s, {}h history window", cfg.refreshInterval, cfg.historyHours);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
        kmlClient = null;
        config = null;
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // Trigger an immediate poll
            scheduler.execute(this::pollMapShare);
        }
        // All channels are read-only — no other commands to handle
    }

    /**
     * Poll the MapShare KML feed and update channels with the latest data.
     */
    private void pollMapShare() {
        InReachKmlClient client = kmlClient;
        InReachConfiguration cfg = config;
        if (client == null || cfg == null) {
            return;
        }

        try {
            List<InReachTrackPoint> points = client.fetchTrackPoints(cfg.historyHours);

            if (points.isEmpty()) {
                logger.debug("No trackpoints in MapShare feed");
                // Don't go offline — the device might just be turned off, feed is still accessible
                updateChannel(CHANNEL_TRACKING_ACTIVE, OnOffType.OFF);
                return;
            }

            // Use the most recent point
            InReachTrackPoint latest = points.get(points.size() - 1);

            // Check if this is actually new data
            if (latest.getId() != lastPointId) {
                logger.debug("New trackpoint: id={}, time={}, lat={}, lon={}, speed={}", latest.getId(),
                        latest.getTimestampUtcMillis(), latest.getLatitude(), latest.getLongitude(),
                        latest.getSpeedKmh());

                // Calculate distance from previous point
                double distance = 0.0;
                if (!Double.isNaN(prevLat) && !Double.isNaN(prevLon)) {
                    distance = haversineKm(prevLat, prevLon, latest.getLatitude(), latest.getLongitude());
                }

                // Update all channels
                updateChannels(latest, distance);

                // Update device properties on first data or when device info changes
                if (lastPointId == 0) {
                    updateProperties(latest);
                }

                // Store for next comparison
                lastPointId = latest.getId();
                prevLat = latest.getLatitude();
                prevLon = latest.getLongitude();
            } else {
                logger.trace("No new trackpoints (last id={})", lastPointId);
            }

            // Determine if tracking is active (last point within 30 minutes)
            long ageMinutes = (System.currentTimeMillis() - latest.getTimestampUtcMillis()) / 60000;
            updateChannel(CHANNEL_TRACKING_ACTIVE, OnOffType.from(ageMinutes <= TRACKING_INACTIVE_MINUTES));

            // Keep status ONLINE as long as we can reach the feed
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }

        } catch (Exception e) {
            logger.warn("Error polling MapShare feed: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    /**
     * Update all channels from a trackpoint.
     */
    private void updateChannels(InReachTrackPoint point, double distanceKm) {
        // Position (latitude, longitude, altitude)
        PointType location = new PointType(new DecimalType(point.getLatitude()), new DecimalType(point.getLongitude()),
                new DecimalType(point.getElevationMeters()));
        updateChannel(CHANNEL_POSITION, location);

        // Last update timestamp
        ZonedDateTime pointTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(point.getTimestampUtcMillis()),
                ZoneId.of("UTC"));
        updateChannel(CHANNEL_LAST_UPDATE, new DateTimeType(pointTime));

        // Speed in km/h
        updateChannel(CHANNEL_SPEED, new QuantityType<>(point.getSpeedKmh(), SIUnits.KILOMETRE_PER_HOUR));

        // Course in degrees
        updateChannel(CHANNEL_COURSE, new QuantityType<>(point.getCourseDegrees(), Units.DEGREE_ANGLE));

        // Elevation in meters
        updateChannel(CHANNEL_ELEVATION, new QuantityType<>(point.getElevationMeters(), SIUnits.METRE));

        // Event
        updateChannel(CHANNEL_EVENT, new StringType(point.getEvent()));

        // GPS Fix
        updateChannel(CHANNEL_GPS_FIX, OnOffType.from(point.isValidGpsFix()));

        // Emergency / SOS
        updateChannel(CHANNEL_EMERGENCY, OnOffType.from(point.isInEmergency()));

        // Text message
        String text = point.getText();
        if (text != null && !text.isEmpty()) {
            updateChannel(CHANNEL_TEXT, new StringType(text));
        }

        // Distance from previous point (in km, reported as meters to channel)
        updateChannel(CHANNEL_DISTANCE, new QuantityType<>(distanceKm * 1000, SIUnits.METRE));
    }

    /**
     * Update thing channel helper.
     */
    private void updateChannel(String channelId, org.openhab.core.types.State state) {
        updateState(new ChannelUID(getThing().getUID(), channelId), state);
    }

    /**
     * Set device properties from the first trackpoint.
     */
    private void updateProperties(InReachTrackPoint point) {
        String imei = point.getImei();
        String deviceType = point.getDeviceType();
        String name = point.getName();

        Map<String, String> properties = editProperties();
        if (imei != null && !imei.isEmpty()) {
            properties.put("imei", imei);
        }
        if (deviceType != null && !deviceType.isEmpty()) {
            properties.put("deviceType", deviceType);
        }
        if (name != null && !name.isEmpty()) {
            properties.put("deviceName", name);
        }
        updateProperties(properties);

        logger.info("inReach device identified: {} ({}) IMEI: {}", name != null ? name : "Unknown",
                deviceType != null ? deviceType : "Unknown", imei != null ? imei : "Unknown");
    }

    /**
     * Calculate distance between two GPS coordinates using the Haversine formula.
     *
     * @return distance in kilometers
     */
    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
