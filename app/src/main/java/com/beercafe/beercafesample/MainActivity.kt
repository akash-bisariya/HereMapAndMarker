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
    private lateinit var mLocationManager: LocationManager
    private lateinit var mLocation: Location
    private lateinit var mAddress: Address
    private lateinit var mLocationClient: FusedLocationProviderClient
    private lateinit var mMap: Map
    private lateinit var mapFragment: MapFragment
    private lateinit var task: Task<LocationSettingsResponse>
    private var locationRequest:LocationRequest? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapFragment = (fragmentManager.findFragmentById(R.id.map_fragment)) as MapFragment
        tv_title.text = "Near-By Places"
        bottomSheetBehavior = BottomSheetBehavior.from(bottom_sheet)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1111)

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
                //
                }
            // Permission is not granted
        } else {

            startProcess()
            mLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
//        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0f, mLocationListener)
            mLocationClient.lastLocation.addOnSuccessListener {
                Log.d("LocationClient","Result : "+it.latitude+""+it.longitude)
                mLocation = it
                initializeMap()
            }
                .addOnFailureListener {
                    if (it is ResolvableApiException) try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        it.startResolutionForResult(
                            this@MainActivity,
                            11111
                        )
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                }


        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        startProcess()
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("MissingPermission")
    private fun startProcess() {
        locationRequest = LocationRequest.create()?.apply {
            interval = 5000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest!!)
        mLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { locationSettingsResponse ->
            // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...
            Log.d("LocationResponse", locationSettingsResponse.toString())
            mLocationClient.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult?) {
                        super.onLocationResult(result)
                        mLocation = result!!.locations[0]

                        initializeMap()

//                            mLocationClient.removeLocationUpdates(this)

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
                    exception.startResolutionForResult(this@MainActivity, 11111)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    private fun initializeMap() {
        if (mLocation != null) {
            mapFragment.init {
                if (it == OnEngineInitListener.Error.NONE) {
                    mMap = mapFragment.map
                    mMap.setCenter(GeoCoordinate(mLocation.latitude, mLocation.longitude), Map.Animation.LINEAR)

                    mapFragment.mapGesture.addOnGestureListener(this@MainActivity)
                    getCurrentAddress()

                    val exploreRequest =
                        ExploreRequest().setSearchCenter(GeoCoordinate(mLocation.latitude, mLocation.longitude))
                    exploreRequest.collectionSize = 10
                    exploreRequest.execute { result, error ->
                        if (error == ErrorCode.NONE) {
                            result.toString()
                            var locations = result!!.items

                            for (i in locations) {

                                var place = i as PlaceLink

                                Log.d(
                                    "NearByPlaces",
                                    place.title + " " + place.averageRating + " " + place.category.name + " " + place.distance + " - " + place.position.latitude + "," + place.position.longitude
                                )
                                var marker = MapMarker()


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
        if(requestCode==11111)
        {
            Log.d("LocationResolution","Resolved")
        }
    }

    var mLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location?) {
            Log.d("location_change", location.toString())
            mLocation = location!!
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.d("status_change", provider.toString())
        }

        override fun onProviderEnabled(provider: String?) {
            Log.d("on_provider_change", provider.toString())
        }

        override fun onProviderDisabled(provider: String?) {
            Log.d("provider disabled", provider.toString())
        }
    }


    fun calculateRoute( location: String)
    {
        val intent = Intent(
            android.content.Intent.ACTION_VIEW,
            Uri.parse("http://maps.google.com/maps?saddr="+mLocation.latitude+","+mLocation.longitude+"&daddr="+location)
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


    fun getCurrentAddress()
    {
        val request = ReverseGeocodeRequest(GeoCoordinate(mLocation.latitude, mLocation.longitude))
        request.execute(object : ResultListener<Address> {
            override fun onCompleted(address: Address?, error: ErrorCode?) {
                if (error == ErrorCode.NONE) {
                    mAddress = address!!
                    Log.d("CurrentLocation", "" + address.text)
                    tv_title.text = "Near-By Places (" + address.text + ")"
                }
            }

        })
    }
}
