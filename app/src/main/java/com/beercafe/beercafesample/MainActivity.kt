package com.beercafe.beercafesample

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.PointF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.opengl.Visibility
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.support.design.widget.BottomSheetBehavior
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.Image
import com.here.android.mpa.common.OnEngineInitListener
import com.here.android.mpa.common.ViewObject
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.search.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.bottom_sheet.*


class MainActivity : AppCompatActivity(),MapGesture.OnGestureListener {
    private lateinit var bottomSheetBehavior:BottomSheetBehavior<View>
    private lateinit var mLastLocation: Location
    private lateinit var mAddress: Address
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mMap: Map
    private lateinit var mapFragment: MapFragment
    private lateinit var task: Task<LocationSettingsResponse>
    private var locationRequest:LocationRequest? = null
    private val REQUEST_LOCATION_PERMISSION: Int=1111
    private val Request_GPS_Permission: Int = 11111
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapFragment = (fragmentManager.findFragmentById(R.id.map_fragment)) as MapFragment
        tv_title.text = getString(R.string.txt_near_places)
        bottomSheetBehavior = BottomSheetBehavior.from(bottom_sheet)

        //Requesting permisions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,  arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)

        } else {
            Log.d("TAG", "getLocation: permissions granted")
            getLocation()

        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode)
        {
            REQUEST_LOCATION_PERMISSION->
            {
                if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocation();
                } else {
                    Toast.makeText(this, "location_permission_denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * Getting current location after permission has been granted
     */
    private fun getLocation() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        mFusedLocationClient.lastLocation.addOnSuccessListener(
            object : OnSuccessListener<Location>{
                override fun onSuccess(location: Location?) {
                    if (location != null) {
                        mLastLocation = location
                        Log.d("LastLocation",""+
                                mLastLocation.getLatitude()+
                                mLastLocation.getLongitude()+
                                mLastLocation.getTime())

                        initializeMap()
                    } else {
                        Toast.makeText(this@MainActivity,"Waiting for Current Location",Toast.LENGTH_SHORT).show()
                        locationRequest = LocationRequest.create()?.apply {
                            interval = 5000
                            fastestInterval = 1000
                            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                        }
                        val builder = LocationSettingsRequest.Builder()
                            .addLocationRequest(locationRequest!!)
                        val client: SettingsClient = LocationServices.getSettingsClient(this@MainActivity)
                        task = client.checkLocationSettings(builder.build())
                        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        task.addOnSuccessListener { locationSettingsResponse ->
                            // All location settings are satisfied. The client can initialize
                            Log.d("LocationResponse", locationSettingsResponse.toString())
                            mFusedLocationClient.requestLocationUpdates(
                                locationRequest,
                                object : LocationCallback() {
                                    override fun onLocationResult(result: LocationResult?) {
                                        super.onLocationResult(result)
                                        mLastLocation = result!!.locations[0]
                                        getLocation()
                                        mFusedLocationClient.removeLocationUpdates(this)

                                    }
                                },
                                Looper.getMainLooper() /* Looper */
                            )
                        }

                        task.addOnFailureListener { exception ->
                            Log.d("ResolutionFailed","onFailure")
                            if (exception is ResolvableApiException) {
                                // Location settings are not satisfied, but this can be fixed
                                // by showing the user a dialog.
                                try {
                                    // Show the dialog by calling startResolutionForResult(),
                                    // and check the result in onActivityResult().
                                    exception.startResolutionForResult(this@MainActivity, Request_GPS_Permission)
                                } catch (sendEx: IntentSender.SendIntentException) {
                                    // Ignore the error.
                                }
                            }
                        }
                    }
                }
            }

        ).addOnFailureListener {
            Log.d("ResolutionFailed","onFailure")
            if (it is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    it.startResolutionForResult(this@MainActivity, Request_GPS_Permission)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    /**
     * Initializing the Here Map and creating markers
     */
    private fun initializeMap() {
        if (mLastLocation != null) {
            mapFragment.init {
                if (it == OnEngineInitListener.Error.NONE) {
                    mMap = mapFragment.map
                    mMap.setCenter(GeoCoordinate(mLastLocation.latitude, mLastLocation.longitude), Map.Animation.LINEAR)

                    mapFragment.mapGesture.addOnGestureListener(this@MainActivity)
                    getCurrentAddress()

                    val exploreRequest =
                        ExploreRequest().setSearchCenter(GeoCoordinate(mLastLocation.latitude, mLastLocation.longitude))
                    exploreRequest.collectionSize = 10
                    exploreRequest.execute { result, error ->
                        if (error == ErrorCode.NONE) {
                            result.toString()
                            var locations = result!!.items

                            for (i in locations) {
                                val place = i as PlaceLink
                                Log.d(
                                    "NearByPlaces",
                                    place.title + " " + place.averageRating + " " + place.category.name + " " + place.distance + " - " + place.position.latitude + "," + place.position.longitude
                                )
                                val marker = MapMarker()
                                marker.coordinate =
                                        GeoCoordinate(place.position.latitude, place.position.longitude)
                                marker.title = place.title
                                marker.description = place.category.name + "-" + place.distance + "-" +
                                        place.position.latitude + "," + place.position.longitude
                                mMap.addMapObject(marker)
                            }
                        }
                    }
                }
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode==Request_GPS_Permission)
        {
            Log.d("LocationResolution","Resolved")
            getLocation()
        }
    }


    /**
     * Showing route in google map through intent
     */
    fun calculateRoute( location: String)
    {
        val intent = Intent(
            android.content.Intent.ACTION_VIEW,
            Uri.parse("http://maps.google.com/maps?saddr="+mLastLocation.latitude+","+mLastLocation.longitude+"&daddr="+location)
        )
        startActivity(intent)

    }


    override fun onRotateEvent(p0: Float): Boolean {
        Log.d("GestureTest","test")
        return false
    }

    override fun onMultiFingerManipulationStart() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPinchLocked() {
        Log.d("GestureTest","test")
    }

    override fun onPinchZoomEvent(p0: Float, p1: PointF?): Boolean {
        Log.d("GestureTest","test")
        return false
    }

    override fun onTapEvent(p0: PointF?): Boolean {
        Log.d("GestureTest","test")
        return false
    }

    override fun onPanStart() {
        Log.d("GestureTest","test")
    }

    override fun onMultiFingerManipulationEnd() {
        Log.d("GestureTest","test")
    }

    override fun onDoubleTapEvent(p0: PointF?): Boolean {
        Log.d("GestureTest","test")
        return false
    }

    override fun onPanEnd() {
        Log.d("GestureTest","test")
    }

    override fun onTiltEvent(p0: Float): Boolean {
        Log.d("GestureTest","test")
        return false
    }

    override fun onRotateLocked() {
        Log.d("GestureTest","test")
    }

    override fun onLongPressEvent(p0: PointF?): Boolean {
        Log.d("GestureTest","test")
        return false
    }

    override fun onTwoFingerTapEvent(p0: PointF?): Boolean {
        Log.d("GestureTest","test")
        return false
    }

    override fun onLongPressRelease() {
        Log.d("GestureTest","test")
    }

    override fun onMapObjectsSelected(objects: MutableList<ViewObject>): Boolean {
        for (i in objects) {
            if (i.baseType == ViewObject.Type.USER_OBJECT) {
                if ((i as MapObject).type == MapObject.Type.MARKER) {
                    tv_title.text = (i as MapMarker).title
                    var string  = i.description
                    tv_desc.text = "Place : "+string.split("-")[0]
                    tv_coordinate.text = "Distance : "+string.split("-")[1]
                    tv_distance.text = "Coordinates : "+string.split("-")[2]
                    tv_distance.visibility =View.VISIBLE
                    tv_desc.visibility =View.VISIBLE
                    tv_coordinate.visibility =View.VISIBLE
                    btn_show_route.visibility =View.VISIBLE

                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    btn_show_route.setOnClickListener(object :View.OnClickListener{
                        override fun onClick(v: View?) {
                            calculateRoute(string.split("-")[2])
                        }

                    })
                }
            }
        }
        // return false to allow the map to handle this callback also
        return false;
    }

    override fun onBackPressed() {
        if(bottomSheetBehavior.state==BottomSheetBehavior.STATE_EXPANDED)
        {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        else
        super.onBackPressed()
    }

    /**
     * Getting the address string from current location coordinates
     */
    private fun getCurrentAddress()
    {
        val request = ReverseGeocodeRequest(GeoCoordinate(mLastLocation.latitude, mLastLocation.longitude))
        request.execute { address, error ->
            if (error == ErrorCode.NONE) {
                mAddress = address!!
                Log.d("CurrentLocation", "" + address.text)
                tv_title.text = "Near-By Places (" + address.text + ")"
            }
        }
    }
}
