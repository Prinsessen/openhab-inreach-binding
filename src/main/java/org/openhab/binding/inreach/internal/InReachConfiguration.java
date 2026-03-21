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

/**
 * Configuration class for the Garmin inReach device thing.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class InReachConfiguration {

    /**
     * MapShare URL identifier (last segment of the MapShare URL).
     */
    public String mapShareId = "";

    /**
     * MapShare access code / password. Empty string means no password.
     */
    public String password = "";

    /**
     * Polling interval in seconds. Default 120s (inReach typically reports every 10 min).
     */
    public int refreshInterval = 120;

    /**
     * How many hours of data to request from the feed. Default 24h.
     */
    public int historyHours = 24;
}
