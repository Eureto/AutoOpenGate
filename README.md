This app allows you to control devices such as the Sonoff Basic Wi-Fi switch via the eWeLink API.

When a user enters a geofence area with a 1500-meter radius, the app starts measuring their precise location. The center of the circle is calculated as the centroid of a polygon.

If the user enters the selected area, a toggle signal is sent to the device (e.g., to open a gate).

<div style="display: flex; flex-wrap: wrap; justify-content: space-between">
    <img src="AppScreenshots/1.jpg" width="200">
    <img src="AppScreenshots/2.jpg" width="200">
    <img src="AppScreenshots/3.jpg" width="200">
</div>

ToDo:
- turn off location check until user connects to car and start driving, only check location when user is in car
- before turning on device check if gete is not opened 
- do not fetch devices every time app starts
- prompt user for all required permissions
- add screens to learn users how to use app
- redesign main screen
- change log output from start to bottom so the newest messages appear on top


Compilation:
1. Open project in android studio 
2. Create secrets.properties file and paste here your apis like just like in local.defaults.properties file
3. Run
