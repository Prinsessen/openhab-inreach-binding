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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link InReachBindingConstants} class defines common constants used across
 * the Garmin inReach binding.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class InReachBindingConstants {

    private static final String BINDING_ID = "inreach";

    // Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_DEVICE = new ThingTypeUID(BINDING_ID, "device");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_DEVICE);

    // Channel IDs
    public static final String CHANNEL_POSITION = "position";
    public static final String CHANNEL_LAST_UPDATE = "lastUpdate";
    public static final String CHANNEL_SPEED = "speed";
    public static final String CHANNEL_COURSE = "course";
    public static final String CHANNEL_ELEVATION = "elevation";
    public static final String CHANNEL_EVENT = "event";
    public static final String CHANNEL_GPS_FIX = "gpsFix";
    public static final String CHANNEL_EMERGENCY = "emergency";
    public static final String CHANNEL_TEXT = "text";
    public static final String CHANNEL_TRACKING_ACTIVE = "trackingActive";
    public static final String CHANNEL_DISTANCE = "distance";

    // MapShare KML feed base URL
    public static final String MAPSHARE_BASE_URL = "https://share.garmin.com/Feed/Share/";

    // KML namespace
    public static final String KML_NAMESPACE = "http://www.opengis.net/kml/2.2";

    // Tracking active timeout — if last point is older than this, tracking is considered inactive
    public static final int TRACKING_INACTIVE_MINUTES = 30;
}
