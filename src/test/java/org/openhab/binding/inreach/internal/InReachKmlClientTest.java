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

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * The MapShare client against a local server, through a real Jetty client: the Basic Auth header, the time
 * window in the query, the KML parsed into track points (with Garmin's byte order mark), and 401/500 as an
 * empty list. Written for the move to Jetty 12 (openHAB 5.3): the same tests pass on the Jetty 9 client
 * (openHAB 5.2) and the Jetty 12 one.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
class InReachKmlClientTest {

    /** The shape of a MapShare feed (two trackpoints and the track line), made-up values. */
    private static final String KML = "\uFEFF<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><Folder>"
            + placemark("101", "11/26/2026 9:15:00 AM", "57.0921", "9.5245", "12.5 m from MSL", "0.0 km/h",
                    "0.00 ° True", "Tracking turned on from device.", "None")
            + placemark("102", "11/26/2026 9:25:00 AM", "57.1000", "9.6000", "20.0 m from MSL", "42.5 km/h",
                    "90.00 ° True", "Tracking message received.", "On my way")
            + "<Placemark><LineString><coordinates>9.5245,57.0921,0 9.6,57.1,0</coordinates></LineString></Placemark>"
            + "</Folder></Document></kml>";

    private static String placemark(String id, String time, String lat, String lon, String elev, String speed,
            String course, String event, String text) {
        String[][] data = { { "Id", id }, { "Time UTC", time }, { "Name", "Test Rider" },
                { "Device Type", "inReach Mini 2" }, { "IMEI", "300000000000000" }, { "Latitude", lat },
                { "Longitude", lon }, { "Elevation", elev }, { "Velocity", speed }, { "Course", course },
                { "Valid GPS Fix", "True" }, { "In Emergency", "False" }, { "Text", text }, { "Event", event } };
        StringBuilder b = new StringBuilder("<Placemark><ExtendedData>");
        for (String[] d : data) {
            b.append("<Data name=\"").append(d[0]).append("\"><value>").append(d[1]).append("</value></Data>");
        }
        return b.append("</ExtendedData><Point><coordinates>").append(lon).append(',').append(lat)
                .append(",0</coordinates></Point></Placemark>").toString();
    }

    private @Nullable HttpServer server;
    private @Nullable HttpClient client;
    private final List<Map<String, String>> requests = new ArrayList<>();
    private int status = 200;
    private String base = "";

    @BeforeEach
    void start() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/Feed/Share/", exchange -> {
            Map<String, String> r = new ConcurrentHashMap<>();
            r.put("method", exchange.getRequestMethod());
            r.put("path", exchange.getRequestURI().getPath());
            String q = exchange.getRequestURI().getQuery();
            r.put("query", q == null ? "" : q);
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            r.put("auth", auth == null ? "" : auth);
            synchronized (requests) {
                requests.add(r);
            }
            byte[] body = KML.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
            if (status == 200) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        s.start();
        server = s;
        base = "http://127.0.0.1:" + s.getAddress().getPort() + "/Feed/Share/";
        HttpClient c = new HttpClient();
        c.start();
        client = c;
    }

    @AfterEach
    void stop() throws Exception {
        HttpServer s = server;
        if (s != null) {
            s.stop(0);
        }
        HttpClient c = client;
        if (c != null) {
            c.stop();
        }
    }

    private InReachKmlClient kml() {
        HttpClient c = client;
        assertNotNull(c);
        return new InReachKmlClient(c, base, "TestShare", "code-1");
    }

    @Test
    void trackPointsComeBackParsedWithBasicAuthAndTheWindow() {
        List<InReachTrackPoint> points = kml().fetchTrackPoints(24);
        assertEquals(2, points.size(), points.toString());
        InReachTrackPoint last = points.get(points.size() - 1);
        assertEquals(102, last.getId());
        assertEquals(57.1, last.getLatitude(), 1e-9);
        assertEquals(9.6, last.getLongitude(), 1e-9);
        assertEquals(42.5, last.getSpeedKmh(), 1e-9);
        assertEquals("On my way", last.getText());
        assertNull(points.get(0).getText(), "\"None\" is no text");

        assertEquals(1, requests.size());
        Map<String, String> r = requests.get(0);
        assertEquals("GET", r.get("method"));
        assertEquals("/Feed/Share/TestShare", r.get("path"));
        String expected = "Basic "
                + Base64.getEncoder().encodeToString("TestShare:code-1".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, r.get("auth"));
        assertTrue(r.get("query").matches("d1=\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ&d2=.*Z"), r.get("query"));
    }

    @Test
    void theConnectionTestSeesTheFeed() {
        assertTrue(kml().testConnection());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("TestShare:code-1".getBytes(StandardCharsets.UTF_8)),
                requests.get(0).get("auth"));
    }

    @Test
    void aWrongAccessCodeIsNoPointsAndAFailedTest() {
        status = 401;
        assertEquals(List.of(), kml().fetchTrackPoints(24));
        assertFalse(kml().testConnection());
    }

    @Test
    void aServerErrorIsNoPoints() {
        status = 500;
        assertEquals(List.of(), kml().fetchTrackPoints(24));
    }
}
