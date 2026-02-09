This app allows you to control devices such as the Sonoff Basic Wi-Fi switch via the eWeLink API.

When a user enters a geofence area with a 1500-meter radius, the app starts measuring their precise location. The center of the circle is calculated as the centroid of a polygon.

If the user enters the selected area, a toggle signal is sent to the device (e.g., to open a gate).

<div style="display: flex; flex-wrap: wrap; justify-content: space-between">
    <img src="AppScreenshots/1.jpg" width="200">
    <img src="AppScreenshots/2.jpg" width="200">
    <img src="AppScreenshots/3.jpg" width="200">
</div>

ToDo:
- Add option to set geofence radius
- change local.defaults.properties to secrets.properties.default
- check if ewelink secret expired and refresh it
- do not fetch devices every time app starts
- prompt user for high accuracy location permission 


Compilation:
1. Open project in android studio 
2. Create secrets.properties file and paste here your apis like just like in local.defaults.properties file
3. Run
