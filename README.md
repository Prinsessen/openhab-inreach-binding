# Garmin inReach Binding

This binding integrates [Garmin inReach](https://www.garmin.com/inreach/) satellite communicators with openHAB via the [MapShare](https://support.garmin.com/en-US/?faq=vhMqEYOMoK5DKCnHje8eB9) KML feed.

## Overview

Garmin inReach devices (inReach Mini 2, inReach Messenger, GPSMAP 67i, Montana 700i, etc.) transmit GPS position and status data over the **Iridium satellite network**, providing global coverage — including oceans, polar regions, and areas without cellular infrastructure.

This binding polls the device's MapShare KML feed at a configurable interval to retrieve tracking data and make it available as openHAB channels.
It provides satellite-based GPS tracking that works everywhere, making it an excellent complement to cellular-based trackers for use cases such as:

- **Adventure and expedition tracking** — Monitor position in areas without cell coverage
- **Vehicle backup tracking** — Satellite fallback when a cellular GPS tracker (e.g., Teltonika FMM920) loses reception
- **Safety monitoring** — Detect SOS/emergency activation and track position during emergencies
- **Fleet management** — Combine with cellular GPS for complete coverage

## Supported Things

| Thing Type ID | Description |
|---------------|-------------|
| `device` | A Garmin inReach device tracked via its MapShare KML feed |

## Prerequisites

Before using this binding, you must enable MapShare on your inReach device:

1. Log in to [Garmin Explore](https://explore.garmin.com/)
2. Navigate to **Account** → **MapShare**
3. Enable MapShare and note your **MapShare URL** (the identifier is the last part of the URL)
4. Optionally set a **MapShare Password** (access code) for privacy
5. Enable **Tracking** on the device and set your desired tracking interval

The MapShare URL follows the pattern `https://share.garmin.com/Feed/Share/{YourMapShareId}`.

## Discovery

This binding does not support automatic discovery.
Devices must be manually configured using the MapShare identifier.

## Thing Configuration

### `device` Thing Configuration

| Parameter | Type | Required | Default | Advanced | Description |
|-----------|------|----------|---------|----------|-------------|
| `mapShareId` | text | **Yes** | — | No | The MapShare URL identifier (the last segment of your MapShare URL) |
| `password` | text | No | *(empty)* | No | The MapShare access code, if password protection is enabled |
| `refreshInterval` | integer | No | `120` | No | Polling interval in seconds (minimum: 30) |
| `historyHours` | integer | No | `24` | Yes | Number of hours of historical tracking data to request (1–720) |

> **Note:** The minimum `refreshInterval` is 30 seconds.
> Most inReach devices report at 10-minute intervals, so a polling interval of 60–120 seconds is typically sufficient.
> Setting `historyHours` too high increases response size; 24 hours is recommended for most use cases.

## Channels

All channels are **read-only**.

| Channel ID | Item Type | Description |
|------------|-----------|-------------|
| `position` | Location | GPS coordinates (latitude, longitude, altitude) |
| `lastUpdate` | DateTime | UTC timestamp of the most recent tracking point |
| `speed` | Number:Speed | Speed at time of reporting (km/h) |
| `course` | Number:Angle | Heading / direction of travel (0–360°) |
| `elevation` | Number:Length | Altitude above mean sea level (meters) |
| `event` | String | Event type (e.g., "Tracking message received.", "Tracking turned on from device.") |
| `gpsFix` | Switch | `ON` if the device had a valid GPS fix at time of reporting |
| `emergency` | Switch | `ON` if the device is in SOS/emergency mode |
| `text` | String | Most recent text message sent from the device |
| `trackingActive` | Switch | `ON` if tracking data was received within the last 30 minutes |
| `distance` | Number:Length | Distance traveled since the previous tracking point (km, Haversine) |

### Thing Properties

The binding automatically populates the following thing properties from the first received tracking point:

| Property | Description |
|----------|-------------|
| `imei` | The device's IMEI number |
| `deviceType` | Device model (e.g., "inReach Mini 2") |
| `deviceName` | Account display name |

## Full Example

### Thing Configuration

```java
// things/inreach.things

Thing inreach:device:mytracker "inReach Tracker" [
    mapShareId="YourMapShareId",
    password="YourAccessCode",
    refreshInterval=120,
    historyHours=24
]
```

### Item Configuration

```java
// items/inreach.items

Group    gInReach               "inReach GPS Tracker"   <location>

Location InReach_Position       "Position [%s]"                     <location>   (gInReach) ["Point"]     { channel="inreach:device:mytracker:position" }
DateTime InReach_LastUpdate     "Last Update [%1$tF %1$tR]"         <time>       (gInReach) ["Timestamp"] { channel="inreach:device:mytracker:lastUpdate" }
Number:Speed InReach_Speed      "Speed [%.1f km/h]"                 <motion>     (gInReach) ["Measurement"] { channel="inreach:device:mytracker:speed" }
Number:Angle InReach_Course     "Course [%.0f °]"                   <compass>    (gInReach) ["Measurement"] { channel="inreach:device:mytracker:course" }
Number:Length InReach_Elevation  "Elevation [%.0f m]"               <elevation>  (gInReach) ["Measurement"] { channel="inreach:device:mytracker:elevation" }
Number:Length InReach_Distance   "Distance [%.2f km]"               <distance>   (gInReach) ["Measurement"] { channel="inreach:device:mytracker:distance" }
String   InReach_Event          "Event [%s]"                        <text>       (gInReach) ["Status"]    { channel="inreach:device:mytracker:event" }
Switch   InReach_GPSFix         "GPS Fix [%s]"                      <network>    (gInReach) ["Status"]    { channel="inreach:device:mytracker:gpsFix" }
Switch   InReach_TrackingActive "Tracking Active [%s]"              <switch>     (gInReach) ["Status"]    { channel="inreach:device:mytracker:trackingActive" }
Switch   InReach_Emergency      "Emergency SOS [%s]"                <alarm>      (gInReach) ["Alarm"]     { channel="inreach:device:mytracker:emergency" }
String   InReach_Text           "Message [%s]"                      <text>       (gInReach) ["Status"]    { channel="inreach:device:mytracker:text" }
```

### Sitemap Configuration

```perl
// sitemaps/inreach.sitemap

sitemap inreach label="inReach Tracker" {
    Frame label="Position & Navigation" {
        Mapview item=InReach_Position height=10
        Text item=InReach_Position
        Text item=InReach_Speed
        Text item=InReach_Course
        Text item=InReach_Elevation
        Text item=InReach_Distance
    }
    Frame label="Status" {
        Text item=InReach_LastUpdate
        Text item=InReach_Event
        Switch item=InReach_GPSFix
        Switch item=InReach_TrackingActive
    }
    Frame label="Safety" {
        Switch item=InReach_Emergency
        Text item=InReach_Text
    }
}
```

### Persistence Configuration

```java
// persistence/influxdb.persist (excerpt)

InReach_Position       : strategy = everyMinute, restoreOnStartup
InReach_Speed          : strategy = everyMinute, restoreOnStartup
InReach_Course         : strategy = everyMinute, restoreOnStartup
InReach_Elevation      : strategy = everyMinute, restoreOnStartup
InReach_Distance       : strategy = everyMinute, restoreOnStartup
InReach_LastUpdate     : strategy = everyMinute, restoreOnStartup
InReach_Event          : strategy = everyChange, restoreOnStartup
InReach_GPSFix         : strategy = everyChange, restoreOnStartup
InReach_TrackingActive : strategy = everyChange, restoreOnStartup
InReach_Emergency      : strategy = everyChange, restoreOnStartup
InReach_Text           : strategy = everyChange, restoreOnStartup
```

### Rule Example (JS Scripting)

```javascript
// automation/js/inreach-alert.js

rules.when()
  .item("InReach_Emergency")
  .changed()
  .to("ON")
  .then(event => {
    let position = items.getItem("InReach_Position").state;
    let lat = position.split(",")[0];
    let lon = position.split(",")[1];
    let mapsUrl = `https://www.google.com/maps?q=${lat},${lon}`;
    actions.NotificationAction.sendBroadcastNotification(
      `SOS EMERGENCY! Device reports emergency at ${mapsUrl}`
    );
  })
  .build("InReach Emergency Alert", "Sends notification on SOS activation");
```

## How It Works

### Data Flow

1. The binding sends an HTTP GET request to the MapShare KML feed:
   `https://share.garmin.com/Feed/Share/{mapShareId}?d1={startTime}&d2={endTime}`
2. If a password is configured, HTTP Basic Authentication is used (the MapShare ID is sent as the username, the access code as the password)
3. Garmin returns a KML document containing `Placemark` elements with `ExtendedData` fields
4. The binding parses the most recent trackpoint and updates all channels
5. Distance between consecutive points is calculated using the **Haversine formula**
6. The `trackingActive` channel is set to `ON` if the latest point is less than 30 minutes old

### KML Feed Details

Each trackpoint in the KML feed contains 19 `ExtendedData` fields:

| Field | Format | Description |
|-------|--------|-------------|
| `Id` | Integer | Unique tracking point ID |
| `Time UTC` | `M/d/yyyy h:mm:ss a` | UTC timestamp |
| `Time` | `M/d/yyyy h:mm:ss a` | Device local time |
| `Name` | Text | Account owner name |
| `Map Display Name` | Text | Display name on MapShare page |
| `Device Type` | Text | Device model name |
| `IMEI` | Text | Unique device identifier |
| `Incident Id` | Text | SOS incident reference (if applicable) |
| `Latitude` | Decimal degrees | WGS84 latitude |
| `Longitude` | Decimal degrees | WGS84 longitude |
| `Elevation` | `{value} m from MSL` | Altitude above mean sea level |
| `Velocity` | `{value} km/h` | Speed |
| `Course` | `{value} ° True` | True heading |
| `Valid GPS Fix` | `True` / `False` | GPS quality indicator |
| `In Emergency` | `True` / `False` | SOS state |
| `Text` | Text | Text message content |
| `Event` | Text | Event description |
| `Device Identifier` | Text | Additional device ID |
| `SpatialRefSystem` | Text | Coordinate reference system (WGS84) |

### Known Quirks

- **UTF-8 BOM**: Garmin's MapShare feed includes a UTF-8 Byte Order Mark (`U+FEFF`) at the beginning of the response. The binding strips this before XML parsing.
- **Timestamp format**: The KML feed uses US-style date format (`M/d/yyyy h:mm:ss a`) in the UTC timezone, which the binding parses with `Locale.US`.
- **Velocity/Elevation units**: Values include unit suffixes (e.g., `"12.48 m from MSL"`, `"5.2 km/h"`). The binding extracts the numeric portion.
- **Tracking interval**: The inReach device controls the reporting interval (typically 2, 5, 10, 20, 30, or 60 minutes). The binding's `refreshInterval` only controls how often openHAB polls the feed — it cannot change the device interval.

## Supported Devices

This binding should work with any Garmin device that supports MapShare, including:

- Garmin inReach Mini 2
- Garmin inReach Messenger
- Garmin GPSMAP 67i
- Garmin Montana 700i / 750i
- Garmin Overlander
- Garmin inReach SE+ / Explorer+

## Building from Source

This binding is built within the [openHAB-addons](https://github.com/openhab/openhab-addons) build framework:

```bash
# Clone the openHAB-addons repository (if not already done)
git clone https://github.com/openhab/openhab-addons.git
cd openhab-addons

# Copy the binding source into the addons bundle structure
cp -r /path/to/inreach-binding bundles/org.openhab.binding.inreach

# Build the binding
cd bundles/org.openhab.binding.inreach
mvn clean package -pl . -DskipChecks
```

The resulting JAR file will be at:

```
target/org.openhab.binding.inreach-{version}.jar
```

### Installation

Copy the JAR to the openHAB addons folder:

```bash
cp target/org.openhab.binding.inreach-*.jar {OPENHAB_HOME}/addons/
```

The binding will be loaded automatically.
No restart is required — openHAB hot-deploys addon JARs.

## License

This binding is licensed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
