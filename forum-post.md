Binding for Garmin inReach satellite communicators via MapShare KML feed. Track real-time GPS position, speed, elevation, course, and message events — even when off-grid on the Iridium satellite network.

## More:

Integrates any **Garmin inReach device** with openHAB by polling the free MapShare KML feed over HTTPS. No API key required — just your MapShare identifier. Works with inReach Mini/Mini 2, Explorer/Explorer+, GPSMAP 66i/67i/86sci, Montana 700i/750i, and Garmin Messenger.

### Use Cases
- Live motorcycle/vehicle trip tracking via satellite
- Hiking & adventure tracking with no cellular dependency
- Emergency SOS monitoring with instant home automation triggers
- Remote asset tracking in areas without cellular coverage
- Combined with cellular GPS (e.g. Teltonika FMM) for hybrid tracking

### Channels

| Channel | Type | Description |
|---------|------|-------------|
| `position` | Location | GPS coordinates (lat, lon, elevation) |
| `lastUpdate` | DateTime | Timestamp of last satellite report |
| `speed` | Number:Speed | Ground speed |
| `course` | Number:Angle | Heading / bearing |
| `elevation` | Number:Length | Altitude above sea level |
| `event` | String | Event type (e.g. Tracking, SOS) |
| `gpsFix` | Switch | Whether GPS has a valid fix |
| `emergency` | Switch | SOS/emergency state |
| `text` | String | inReach message text |
| `trackingActive` | Switch | Whether active tracking is on |
| `distance` | Number:Length | Distance from home/reference |

### Configuration

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `mapShareId` | String | Yes | — | Your MapShare identifier from [share.garmin.com](https://share.garmin.com) |
| `password` | String | No | — | MapShare password if feed is protected |
| `refreshInterval` | Integer | No | 120 | Poll interval in seconds |
| `historyHours` | Integer | No | 24 | Hours of track history to fetch |

### Prerequisites
1. A Garmin inReach device with an **active satellite subscription**
2. **MapShare enabled** at [share.garmin.com](https://share.garmin.com)
3. Note your **MapShare identifier** from the MapShare URL

### Quick Start

**Thing:**
```java
Thing inreach:tracker:mytracker "My inReach" [ mapShareId="YourMapShareID", refreshInterval=120, historyHours=24 ]
```

**Items:**
```java
Location      InReach_Position    "Position"          { channel="inreach:tracker:mytracker:position" }
DateTime      InReach_LastUpdate  "Last Update"       { channel="inreach:tracker:mytracker:lastUpdate" }
Number:Speed  InReach_Speed       "Speed"             { channel="inreach:tracker:mytracker:speed" }
Number:Angle  InReach_Course      "Course"            { channel="inreach:tracker:mytracker:course" }
Number:Length InReach_Elevation   "Elevation"         { channel="inreach:tracker:mytracker:elevation" }
String        InReach_Event       "Event"             { channel="inreach:tracker:mytracker:event" }
Switch        InReach_Emergency   "Emergency"         { channel="inreach:tracker:mytracker:emergency" }
String        InReach_Text        "Message"           { channel="inreach:tracker:mytracker:text" }
Switch        InReach_Tracking    "Tracking Active"   { channel="inreach:tracker:mytracker:trackingActive" }
Number:Length InReach_Distance    "Distance from Home" { channel="inreach:tracker:mytracker:distance" }
```

**Example Rule — Emergency Alert:**
```javascript
rules.JSRule({
  name: "InReach SOS Alert",
  triggers: [triggers.ItemStateChangeTrigger("InReach_Emergency", undefined, "ON")],
  execute: () => {
    actions.NotificationAction.sendNotification("admin@home.local", "🚨 EMERGENCY SOS activated on inReach!");
  }
});
```

### How It Works

The binding polls the Garmin MapShare KML feed at your configured interval. MapShare is a free Garmin service that exposes your inReach device's position and messages as a KML/XML feed over HTTPS. The feed is updated each time the device sends a satellite report (typically every 2–10 minutes depending on your tracking interval).

### Known Quirks
- **Satellite latency** — inReach reports arrive with ~30–90 second delay via Iridium
- **Feed caching** — Garmin's MapShare servers may cache data for 1–2 minutes
- **UTF-8 BOM** — The binding handles Garmin's non-standard BOM in KML responses
- **Poll-based** — No real-time push; set `refreshInterval` to balance freshness vs. server load

## Changelog

### Version 1.0.0
- Initial release
- 11 channels: position, speed, course, elevation, events, SOS, messages, tracking state, distance
- MapShare KML feed polling via Iridium satellite network
- Optional password-protected feed support
- Configurable poll interval and history window
- UTF-8 BOM handling for Garmin's non-standard KML
- Developed and tested with openHAB 5.x

## Resources

[Download JAR](https://github.com/Prinsessen/openhab-inreach-binding/releases/download/v1.0.0/org.openhab.binding.inreach-5.2.0-SNAPSHOT.jar)

[Source Code](https://github.com/Prinsessen/openhab-inreach-binding)

---
*Developed by [@Prinsessen](https://github.com/Prinsessen) — Licensed under EPL-2.0*
