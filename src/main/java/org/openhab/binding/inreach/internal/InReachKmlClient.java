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

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * The {@link InReachKmlClient} fetches and parses KML data from the Garmin
 * MapShare feed. Uses HTTP Basic Auth and parses KML ExtendedData fields
 * to extract trackpoints.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class InReachKmlClient {

    private final Logger logger = LoggerFactory.getLogger(InReachKmlClient.class);

    private final HttpClient httpClient;
    private final String feedUrl;
    private final String authHeader;

    /**
     * Create a new KML client for the given MapShare feed.
     *
     * @param httpClient Jetty HttpClient from the openHAB runtime
     * @param mapShareId MapShare identifier (last segment of the MapShare URL)
     * @param password MapShare access code (empty string for no password)
     */
    public InReachKmlClient(HttpClient httpClient, String mapShareId, String password) {
        this.httpClient = httpClient;
        this.feedUrl = MAPSHARE_BASE_URL + mapShareId;

        // MapShare uses HTTP Basic Auth — username can be anything, password is the access code
        String credentials = mapShareId + ":" + password;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Test connectivity to the MapShare feed.
     *
     * @return true if feed is accessible and responds with valid KML
     */
    public boolean testConnection() {
        try {
            ContentResponse response = httpClient.newRequest(feedUrl).method(HttpMethod.GET)
                    .header("Authorization", authHeader).timeout(15, TimeUnit.SECONDS).send();

            if (response.getStatus() == HttpStatus.OK_200) {
                String content = response.getContentAsString();
                return content.contains("kml") || content.contains("Placemark");
            } else if (response.getStatus() == HttpStatus.UNAUTHORIZED_401) {
                logger.warn("MapShare feed returned 401 — check access code / password");
            } else {
                logger.warn("MapShare feed returned HTTP {}", response.getStatus());
            }
            return false;
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Failed to connect to MapShare feed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fetch the latest trackpoints from the MapShare KML feed.
     *
     * @param historyHours number of hours of data to request
     * @return list of trackpoints sorted by time (oldest first), or empty list on error
     */
    public List<InReachTrackPoint> fetchTrackPoints(int historyHours) {
        String url = buildUrl(historyHours);

        try {
            ContentResponse response = httpClient.newRequest(url).method(HttpMethod.GET)
                    .header("Authorization", authHeader).timeout(30, TimeUnit.SECONDS).send();

            if (response.getStatus() == HttpStatus.UNAUTHORIZED_401) {
                logger.warn("MapShare authentication failed (401). Check password.");
                return Collections.emptyList();
            }

            if (response.getStatus() != HttpStatus.OK_200) {
                logger.warn("MapShare feed returned HTTP {}", response.getStatus());
                return Collections.emptyList();
            }

            String kml = response.getContentAsString();

            // Strip UTF-8 BOM if present (Garmin MapShare includes BOM which breaks XML parser)
            if (kml.length() > 0 && kml.charAt(0) == '\uFEFF') {
                kml = kml.substring(1);
            }

            logger.trace("Received {} bytes of KML data", kml.length());

            return parseKml(kml);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("KML fetch interrupted");
            return Collections.emptyList();
        } catch (TimeoutException | ExecutionException e) {
            logger.warn("Failed to fetch MapShare KML: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Build the feed URL with date range parameters.
     */
    private String buildUrl(int historyHours) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime from = now.minusHours(historyHours);

        String d1 = from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        String d2 = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

        return feedUrl + "?d1=" + d1 + "&d2=" + d2;
    }

    /**
     * Parse KML XML into a list of trackpoints.
     */
    private List<InReachTrackPoint> parseKml(String kml) {
        List<InReachTrackPoint> points = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Security: disable external entities
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(kml)));

            NodeList placemarks = doc.getElementsByTagNameNS(KML_NAMESPACE, "Placemark");
            logger.debug("Found {} Placemarks in KML", placemarks.getLength());

            for (int i = 0; i < placemarks.getLength(); i++) {
                Element pm = (Element) placemarks.item(i);
                InReachTrackPoint point = parsePlacemark(pm);
                if (point != null) {
                    points.add(point);
                }
            }

            // Sort by timestamp ascending
            points.sort((a, b) -> Long.compare(a.getTimestampUtcMillis(), b.getTimestampUtcMillis()));
            logger.debug("Parsed {} valid trackpoints", points.size());

        } catch (Exception e) {
            logger.warn("Failed to parse KML: {}", e.getMessage());
        }

        return points;
    }

    /**
     * Parse a single KML Placemark element into an InReachTrackPoint.
     */
    private @Nullable InReachTrackPoint parsePlacemark(Element placemark) {
        try {
            String timeUtc = getExtendedDataValue(placemark, "Time UTC");
            if (timeUtc == null || timeUtc.isEmpty()) {
                return null; // Not a trackpoint (might be a LineString)
            }

            long timestamp = parseTimestamp(timeUtc);
            if (timestamp == 0) {
                return null;
            }

            long id = parseLong(getExtendedDataValue(placemark, "Id"), 0);
            double lat = parseDouble(getExtendedDataValue(placemark, "Latitude"), 0.0);
            double lon = parseDouble(getExtendedDataValue(placemark, "Longitude"), 0.0);
            double elev = parseElevation(getExtendedDataValue(placemark, "Elevation"));
            double speed = parseSpeed(getExtendedDataValue(placemark, "Velocity"));
            double course = parseCourse(getExtendedDataValue(placemark, "Course"));
            String event = getExtendedDataValueOrEmpty(placemark, "Event");
            boolean gpsFix = "True".equals(getExtendedDataValue(placemark, "Valid GPS Fix"));
            boolean emergency = "True".equals(getExtendedDataValue(placemark, "In Emergency"));
            String text = getExtendedDataValue(placemark, "Text");
            String imei = getExtendedDataValue(placemark, "IMEI");
            String deviceType = getExtendedDataValue(placemark, "Device Type");
            String name = getExtendedDataValue(placemark, "Name");

            // Ignore "None" text values
            if ("None".equals(text)) {
                text = null;
            }

            return new InReachTrackPoint(id, timestamp, lat, lon, elev, speed, course, event, gpsFix, emergency, text,
                    imei, deviceType, name);

        } catch (Exception e) {
            logger.trace("Failed to parse Placemark: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract a value from KML ExtendedData/Data elements.
     */
    private @Nullable String getExtendedDataValue(Element placemark, String dataName) {
        NodeList dataElements = placemark.getElementsByTagNameNS(KML_NAMESPACE, "Data");
        for (int i = 0; i < dataElements.getLength(); i++) {
            Element data = (Element) dataElements.item(i);
            if (dataName.equals(data.getAttribute("name"))) {
                NodeList values = data.getElementsByTagNameNS(KML_NAMESPACE, "value");
                if (values.getLength() > 0) {
                    String val = values.item(0).getTextContent();
                    return val != null ? val.trim() : null;
                }
            }
        }
        return null;
    }

    private String getExtendedDataValueOrEmpty(Element placemark, String dataName) {
        String val = getExtendedDataValue(placemark, dataName);
        return val != null ? val : "";
    }

    /**
     * Parse inReach timestamp format: "M/d/yyyy h:mm:ss a"
     */
    private long parseTimestamp(String timeUtc) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy h:mm:ss a", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(timeUtc);
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) {
            logger.trace("Failed to parse timestamp '{}': {}", timeUtc, e.getMessage());
            return 0;
        }
    }

    /**
     * Parse elevation string, e.g., "12.48 m from MSL"
     */
    private double parseElevation(@Nullable String elevation) {
        if (elevation == null || elevation.isEmpty()) {
            return 0.0;
        }
        try {
            String[] parts = elevation.split(" ");
            return Double.parseDouble(parts[0]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return 0.0;
        }
    }

    /**
     * Parse speed string, e.g., "0.0 km/h"
     */
    private double parseSpeed(@Nullable String velocity) {
        if (velocity == null || velocity.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(velocity.replace(" km/h", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Parse course string, e.g., "123.45 ° True"
     */
    private double parseCourse(@Nullable String course) {
        if (course == null || course.isEmpty()) {
            return 0.0;
        }
        try {
            String[] parts = course.split(" ");
            return Double.parseDouble(parts[0]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return 0.0;
        }
    }

    private double parseDouble(@Nullable String value, double defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long parseLong(@Nullable String value, long defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
