package com.example.test.app;

import android.app.Activity;
import cn.easyar.engine.EasyAR;
import android.support.v7.app.ActionBarActivity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.hardware.*;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.*;
import com.baidu.mapapi.model.LatLng;
import android.view.View.OnClickListener;
import com.baidu.mapapi.overlayutil.DrivingRouteOverlay;
import com.baidu.mapapi.overlayutil.OverlayManager;
import com.baidu.mapapi.overlayutil.TransitRouteOverlay;
import com.baidu.mapapi.overlayutil.WalkingRouteOverlay;
import com.baidu.mapapi.search.core.RouteLine;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiSearch;
import com.baidu.mapapi.search.route.*;
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener;
import com.baidu.mapapi.search.sug.SuggestionSearch;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends ActionBarActivity implements BaiduMap.OnMapClickListener,
        OnGetRoutePlanResultListener {

    /*
    * Steps to create the key for this sample:
    *  1. login www.easyar.com
    *  2. create app with
    *      Name: HelloARMultiTarget-MT
    *      Package Name: cn.easyar.samples.helloarmultitargetmt
    *  3. find the created item in the list and show key
    *  4. set key string bellow
    */
    static String key = "vTYtPFFNmNMsnQcIoZcBKPwbkbO26BVow9Srw4m6HQkSbf9c9xK0PtWd8J6vpyv2PAGCLFNMPYZDWZiDBIoXGURJMC9JctI9vFOr88d7dc1b58e63be61f27b8196f70c7f2SUQMp7ag9rkiRcWuvy0RKu0avmzuvy3HotW9jkXQdB3Oxp0VPcn6nuWBnYnqYEp9uhLp";

    static {
        System.loadLibrary("EasyAR");
        System.loadLibrary("HelloARMultiTargetMTNative");
    }

    public static native void nativeInitGL();
    public static native void nativeResizeGL(int w, int h);
    public static native void nativeRender();
    private native boolean nativeInit();
    private native void nativeDestory();
    private native void nativeRotationChange(boolean portrait);

    MapView mMapView=null;
    BaiduMap mBaiduMap;
    // ��λ���
    LocationClient mLocClient;
    public MyLocationListenner myListener = new MyLocationListenner();
    private MyLocationConfiguration.LocationMode mCurrentMode;
    BitmapDescriptor mCurrentMarker;
    // UI���
    boolean isFirstLoc = true;// �Ƿ��״ζ�λ
    //ָ����
    private static final int EXIT_TIME = 2000;// ���ΰ����ؼ��ļ���ж�
    private long firstExitTime = 0L;// ���������һ�ΰ����ؼ���ʱ��
    public String mData;
    public String address;
    private SensorManager mSensorManager;// ��������������
    private Sensor mOrientationSensor;// ����������
    private float mTargetDirection;// Ŀ�긡�㷽��
    TextView mLatitudeTV;// γ��
    TextView mLongitudeTV;// ����
    //Fragments
    GuideFragment mGuideFragment=null;             //����ģ��
    SurfaceFragment mSurfaceFragment=null;         //�����ͼģ��
    //�������
    RoutePlanSearch mSearch = null;    // ����ģ�飬Ҳ��ȥ����ͼģ�����ʹ��
    RouteLine route = null;
    OverlayManager routeOverlay = null;
    boolean useDefaultIcon = false;
    private TextView popupText = null;//����view
    int nodeIndex = -1;//�ڵ�����,������ڵ�ʱʹ��




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        EasyAR.initialize(this, key);
        nativeInit();

        //�����ޱ���
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //����ȫ��
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        setDefaultFragment();                                  //����Ĭ��ģ��
        GLView glView = new GLView(this);
        glView.setRenderer(new Renderer());
        glView.setZOrderMediaOverlay(true);
        ((ViewGroup) findViewById(R.id.id_content)).addView(glView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        nativeRotationChange(getWindowManager().getDefaultDisplay().getRotation() == android.view.Surface.ROTATION_0);


        //��ȡ��ͼ�ؼ�����
        mMapView = (MapView) findViewById(R.id.bmapView);
        RelativeLayout raly=(RelativeLayout)findViewById(R.id.relativeLayout);
        //mMapView=new MapView(this,new BaiduMapOptions().compassEnabled(false));
        //raly.addView(mMapView);
        mMapView.showZoomControls(false);
        mMapView.bringToFront();
        mCurrentMode = MyLocationConfiguration.LocationMode.COMPASS;//��ͼ����ģʽ
        mBaiduMap = mMapView.getMap();
        mBaiduMap.getUiSettings().setCompassEnabled(false);         //ȥ��ָ����
        float zoomLevel=18.0f;
        MapStatusUpdate u=MapStatusUpdateFactory.zoomTo(zoomLevel);
        mBaiduMap.animateMapStatus(u);
        mBaiduMap.setMyLocationConfigeration(new MyLocationConfiguration(mCurrentMode, true, mCurrentMarker));
        // ������λͼ��
        mBaiduMap.setMyLocationEnabled(true);

        // ��λ��ʼ��
        mLocClient = new LocationClient(this);
        mLocClient.registerLocationListener(myListener);           //ע�ᶨλ����
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true);// ��gps
        option.setCoorType("bd09ll"); // ������������
        option.setScanSpan(1000);
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);//�߾���ģʽ
        mLocClient.setLocOption(option);
        //��ͼ����¼�����
        mBaiduMap.setOnMapClickListener(this);
        // ��ʼ������ģ�飬ע���¼�����
        mSearch = RoutePlanSearch.newInstance();
        mSearch.setOnGetRoutePlanResultListener(this);

        //Compass
        //setContentView(R.layout.activity_main);
        initResources();// ��ʼ��view
        initServices();// ��ʼ����������λ�÷���
        mLocClient.start();                                   //��ʼ��λ
        ////mCompassView.bringToFront();


    }

    private void setDefaultFragment()                                //����Ĭ��ģ��
    {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        mSurfaceFragment = new SurfaceFragment();
        transaction.replace(R.id.id_content, mSurfaceFragment);
        transaction.commit();
    }




    /**
     * ��λSDK��������
     */
    public class MyLocationListenner implements BDLocationListener {

        @Override
        public void onReceiveLocation(BDLocation location) {
            // map view ���ٺ��ڴ����½��յ�λ��
            if (location == null || mMapView == null)
                return;
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                            // �˴����ÿ����߻�ȡ���ķ�����Ϣ��˳ʱ��0-360
                    .direction(mTargetDirection*-1.0f)      //      *-1.0f����Ч��
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude()).build();
            mBaiduMap.setMyLocationData(locData);
            if (isFirstLoc) {
                isFirstLoc = false;
                LatLng ll = new LatLng(location.getLatitude(),
                        location.getLongitude());
                MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(ll);
                mBaiduMap.animateMapStatus(u);
            }
            //Compass
            StringBuffer sb = new StringBuffer(256);
            // sb.append("ʱ��: ");
            // sb.append(location.getTime());
            sb.append("Lat : ");
            sb.append(location.getLatitude() );
            sb.append(", Lon : ");
            sb.append(location.getLongitude());
//			sb.append(", ���� : ");
//			sb.append(location.getRadius() + " ��");
            mData = sb.toString();
            if(mLatitudeTV != null) mLatitudeTV.setText(sb);

            if (location.getLocType() == BDLocation.TypeGpsLocation) {
                address = "Speed : " + location.getSpeed();
                // sb.append("\n??? : ");
                // sb.append(location.getSpeed());
            } else if (location.getLocType() == BDLocation.TypeNetWorkLocation) {
                // sb.append("\n??? : ");
                // sb.append(location.getAddrStr());
                address = "Address : " + location.getAddrStr();
            }
            if(mLongitudeTV !=null) mLongitudeTV.setText(address);


        }

        public void onReceivePoi(BDLocation poiLocation) {
        }
    }

    /**
     * ����·�߹滮����ʾ��
     *
     * @param v
     */
    public void SearchButtonProcess(View v,LatLng mDestination) {
        //��������ڵ��·������
        route = null;
        mBaiduMap.clear();
        // ����������ť��Ӧ
        //EditText editSt = (EditText)findViewById(R.id.start);
        EditText editEn = (EditText)findViewById(R.id.end);
        //�������յ���Ϣ������tranist search ��˵��������������
        if(editEn.getText()!=null&&!editEn.getText().toString().equals(""))
        {
            PlanNode stNode = PlanNode.withLocation(new LatLng(mBaiduMap.getLocationData().latitude,mBaiduMap.getLocationData().longitude));//���ݶ�λ���ж����ڳ���

            if(mDestination==null)
            {
                Toast.makeText(this,"û��Ŀ�ĵ�",Toast.LENGTH_SHORT);return;
            }
            PlanNode enNode = PlanNode.withLocation(mDestination);
            mSurfaceFragment.poiinfos=null;
            // ʵ��ʹ�����������յ���н�����ȷ���趨
            if (v.getId() == R.id.drive) {
                mSearch.drivingSearch((new DrivingRoutePlanOption())
                        .from(stNode)
                        .to(enNode));
            } else if (v.getId() == R.id.transit) {
                mSearch.transitSearch((new TransitRoutePlanOption())
                        .from(stNode)
                        .city("����")
                        .to(enNode));
            } else if (v.getId() == R.id.walk) {
                mSearch.walkingSearch((new WalkingRoutePlanOption())
                        .from(stNode)
                        .to(enNode));
            }
        }
        else
        {
                Toast.makeText(this,"�������յ�",Toast.LENGTH_SHORT);
        }

    }

    /**
     * �ڵ����ʾ��
     *
     * @param v
     */
    public void nodeClick(View v) {
        if (route == null ||
                route.getAllStep() == null) {
            return;
        }
        if (nodeIndex == -1 && v.getId() == R.id.pre) {
            return;
        }
        //���ýڵ�����
        if (v.getId() == R.id.next) {
            if (nodeIndex < route.getAllStep().size() - 1) {
                nodeIndex++;
            } else {
                return;
            }
        } else if (v.getId() == R.id.pre) {
            if (nodeIndex > 0) {
                nodeIndex--;
            } else {
                return;
            }
        }
        //��ȡ�ڽ����Ϣ
        LatLng nodeLocation = null;
        String nodeTitle = null;
        Object step = route.getAllStep().get(nodeIndex);
        if (step instanceof DrivingRouteLine.DrivingStep) {
            nodeLocation = ((DrivingRouteLine.DrivingStep) step).getEntrance().getLocation();
            nodeTitle = ((DrivingRouteLine.DrivingStep) step).getInstructions();
        } else if (step instanceof WalkingRouteLine.WalkingStep) {
            nodeLocation = ((WalkingRouteLine.WalkingStep) step).getEntrance().getLocation();
            nodeTitle = ((WalkingRouteLine.WalkingStep) step).getInstructions();
        } else if (step instanceof TransitRouteLine.TransitStep) {
            nodeLocation = ((TransitRouteLine.TransitStep) step).getEntrance().getLocation();
            nodeTitle = ((TransitRouteLine.TransitStep) step).getInstructions();
        }

        if (nodeLocation == null || nodeTitle == null) {
            return;
        }
        //�ƶ��ڵ�������
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(nodeLocation));
        // show popup
        popupText = new TextView(this);
        popupText.setBackgroundResource(R.drawable.popup);
        popupText.setTextColor(0xFF000000);
        popupText.setText(nodeTitle);
        mBaiduMap.showInfoWindow(new InfoWindow(popupText, nodeLocation, 0));

    }

    /**
     * �л�·��ͼ�꣬ˢ�µ�ͼʹ����Ч
     * ע�⣺ ���յ�ͼ��ʹ�����Ķ���.
     */
    public void changeRouteIcon(View v) {
        if (routeOverlay == null) {
            return;
        }
        if (useDefaultIcon) {
            ((Button) v).setText("�Զ������յ�ͼ��");
            Toast.makeText(this,
                    "��ʹ��ϵͳ���յ�ͼ��",
                    Toast.LENGTH_SHORT).show();

        } else {
            ((Button) v).setText("ϵͳ���յ�ͼ��");
            Toast.makeText(this,
                    "��ʹ���Զ������յ�ͼ��",
                    Toast.LENGTH_SHORT).show();

        }
        useDefaultIcon = !useDefaultIcon;
        routeOverlay.removeFromMap();
        routeOverlay.addToMap();
    }

    /*
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

*/
    @Override
    public void onGetWalkingRouteResult(WalkingRouteResult result) {
        if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
            Toast.makeText(this, "��Ǹ��δ�ҵ����", Toast.LENGTH_SHORT).show();
        }
        if (result.error == SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR) {
            //���յ��;�����ַ����壬ͨ�����½ӿڻ�ȡ�����ѯ��Ϣ
            result.getSuggestAddrInfo();
            return;
        }
        if (result.error == SearchResult.ERRORNO.NO_ERROR) {
            nodeIndex = -1;
            if(mGuideFragment!=null)                                          //��ʾ��ť
            {
                mGuideFragment.mBtnPre.setVisibility(View.VISIBLE);
                mGuideFragment.mBtnNext.setVisibility(View.VISIBLE);
            }
            //Toast.makeText(this, "mGuideFragment==null", Toast.LENGTH_SHORT).show();
            route = result.getRouteLines().get(0);
            WalkingRouteOverlay overlay = new MyWalkingRouteOverlay(mBaiduMap);
            mBaiduMap.setOnMarkerClickListener(overlay);
            routeOverlay = overlay;
            overlay.setData(result.getRouteLines().get(0));
            overlay.addToMap();
            overlay.zoomToSpan();
            if(mSurfaceFragment!=null)
            {
                mSurfaceFragment.mroute=route;
                mSurfaceFragment.isGuiding=true;
            }
            // ��������
//            LatLng p1 = new LatLng(mBaiduMap.getLocationData().latitude, mBaiduMap.getLocationData().longitude);
//            LatLng p2 = new LatLng(p1.latitude+0.003f, p1.longitude+0.003f);
//            LatLng p3 = new LatLng(p1.latitude, p1.longitude+0.003f);
//            List<LatLng> points = new ArrayList<LatLng>();
//            points.add(p1);
//            points.add(p2);
//            points.add(p3);
//            OverlayOptions ooPolyline = new PolylineOptions().width(10)
//                    .color(0xAAFF0000).points(points);
//            mBaiduMap.addOverlay(ooPolyline);
        }

    }

    @Override
    public void onGetTransitRouteResult(TransitRouteResult result) {

        if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
            Toast.makeText(this, "��Ǹ��δ�ҵ����", Toast.LENGTH_SHORT).show();
        }
        if (result.error == SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR) {
            //���յ��;�����ַ����壬ͨ�����½ӿڻ�ȡ�����ѯ��Ϣ
            result.getSuggestAddrInfo();
            return;
        }
        if (result.error == SearchResult.ERRORNO.NO_ERROR) {                       //�����������ʾ��ť
            nodeIndex = -1;
            if(mGuideFragment!=null)
            {
                mGuideFragment.mBtnPre.setVisibility(View.VISIBLE);
                mGuideFragment.mBtnNext.setVisibility(View.VISIBLE);
            }
            //Toast.makeText(this, "mGuideFragment==null", Toast.LENGTH_SHORT).show();
            route = result.getRouteLines().get(0);
            TransitRouteOverlay overlay = new MyTransitRouteOverlay(mBaiduMap);
            mBaiduMap.setOnMarkerClickListener(overlay);
            routeOverlay = overlay;
            overlay.setData(result.getRouteLines().get(0));
            overlay.addToMap();
            overlay.zoomToSpan();
        }
    }

    @Override
    public void onGetDrivingRouteResult(DrivingRouteResult result) {
        if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
            Toast.makeText(this, "��Ǹ��δ�ҵ����", Toast.LENGTH_SHORT).show();
        }
        if (result.error == SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR) {
            //���յ��;�����ַ����壬ͨ�����½ӿڻ�ȡ�����ѯ��Ϣ
            //result.getSuggestAddrInfo()
            return;
        }
        if (result.error == SearchResult.ERRORNO.NO_ERROR) {
            nodeIndex = -1;
            if(mGuideFragment!=null)
            {
                mGuideFragment.mBtnPre.setVisibility(View.VISIBLE);
                mGuideFragment.mBtnNext.setVisibility(View.VISIBLE);
            }
            //Toast.makeText(this, "mGuideFragment==null", Toast.LENGTH_SHORT).show();
            route = result.getRouteLines().get(0);
            DrivingRouteOverlay overlay = new MyDrivingRouteOverlay(mBaiduMap);
            routeOverlay = overlay;
            mBaiduMap.setOnMarkerClickListener(overlay);
            overlay.setData(result.getRouteLines().get(0));
            overlay.addToMap();
            overlay.zoomToSpan();
        }
    }

    //����RouteOverly
    private class MyDrivingRouteOverlay extends DrivingRouteOverlay {

        public MyDrivingRouteOverlay(BaiduMap baiduMap) {
            super(baiduMap);
        }

        @Override
        public BitmapDescriptor getStartMarker() {
            if (useDefaultIcon) {
                return BitmapDescriptorFactory.fromResource(R.drawable.icon_st);
            }
            return null;
        }

        @Override
        public BitmapDescriptor getTerminalMarker() {
            if (useDefaultIcon) {
                return BitmapDescriptorFactory.fromResource(R.drawable.icon_en);
            }
            return null;
        }
    }

    private class MyWalkingRouteOverlay extends WalkingRouteOverlay {

        public MyWalkingRouteOverlay(BaiduMap baiduMap) {
            super(baiduMap);
        }

        @Override
        public BitmapDescriptor getStartMarker() {
            if (useDefaultIcon) {
                return BitmapDescriptorFactory.fromResource(R.drawable.icon_st);
            }
            return null;
        }

        @Override
        public BitmapDescriptor getTerminalMarker() {
            if (useDefaultIcon) {
                return BitmapDescriptorFactory.fromResource(R.drawable.icon_en);
            }
            return null;
        }
    }

    private class MyTransitRouteOverlay extends TransitRouteOverlay {

        public MyTransitRouteOverlay(BaiduMap baiduMap) {
            super(baiduMap);
        }

        @Override
        public BitmapDescriptor getStartMarker() {
            if (useDefaultIcon) {
                return BitmapDescriptorFactory.fromResource(R.drawable.icon_st);
            }
            return null;
        }

        @Override
        public BitmapDescriptor getTerminalMarker() {
            if (useDefaultIcon) {
                return BitmapDescriptorFactory.fromResource(R.drawable.icon_en);
            }
            return null;
        }
    }

    @Override
    public void onMapClick(LatLng point) {
        mBaiduMap.hideInfoWindow();
    }

    @Override
    public boolean onMapPoiClick(MapPoi poi) {
        return false;
    }

    @Override
    protected void onPause() {// ����ͣ������������ע�������������λ�ø��·���

        // activity ��ͣʱͬʱ��ͣ��ͼ�ؼ�
        mMapView.onPause();
        super.onPause();
        if (mOrientationSensor != null) {
            mSensorManager.unregisterListener(mOrientationSensorEventListener);
        }
    }

    @Override
    protected void onResume() {// �ڻָ��������������жϡ�����λ�ø��·���ʹ���������

        mMapView.onResume();
        super.onResume();
        // activity �ָ�ʱͬʱ�ָ���ͼ�ؼ�
        if (mOrientationSensor != null) {
            mSensorManager.registerListener(mOrientationSensorEventListener,
                    mOrientationSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            // Toast.makeText(this, R.string.cannot_get_sensor,
            // Toast.LENGTH_SHORT)
            // .show();
        }
        //mStopDrawing=false;
        //mHandler.postDelayed(mCompassViewUpdater,20);
    }

    @Override
    protected void onDestroy() {
        // �˳�ʱ���ٶ�λ
        mLocClient.stop();
        // �رն�λͼ��
        mBaiduMap.setMyLocationEnabled(false);
        mSearch.destroy();
        mMapView.onDestroy();
        mMapView = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed()
    //�����а����ؼ�ʱҪ�ͷ��ڴ�
    {
        // TODO Auto-generated method stub
        long curTime = System.currentTimeMillis();
        if (curTime - firstExitTime < EXIT_TIME) {// ���ΰ����ؼ���ʱ��С��2����˳�Ӧ��
            finish();
            super.onBackPressed();
        } else {
            if(mGuideFragment!=null)
            {
                super.onBackPressed();
            }
            else{
                Toast.makeText(this, "Press again to Exit", Toast.LENGTH_SHORT).show();
                firstExitTime = curTime;
            }

        }
        //MainActivity.this.finish();

    }



    // ��ʼ��view
    private void initResources() {
        mTargetDirection = 0.0f;// ��ʼ��Ŀ�귽��

    }
    // ��ʼ����������λ�÷���
    private void initServices() {
        // sensor manager
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mOrientationSensor = mSensorManager.getSensorList(
                Sensor.TYPE_ORIENTATION).get(0);//
        // Log.i("way", mOrientationSensor.getName());
        // location manager
        // mLocationManager = (LocationManager)
        // getSystemService(Context.LOCATION_SERVICE);
        // Criteria criteria = new Criteria();// �������󣬼�ָ���������˻��LocationProvider
        // criteria.setAccuracy(Criteria.ACCURACY_FINE);// �ϸ߾���
        // criteria.setAltitudeRequired(false);// �Ƿ���Ҫ�߶���Ϣ
        // criteria.setBearingRequired(false);// �Ƿ���Ҫ������Ϣ
        // criteria.setCostAllowed(true);// �Ƿ��������
        // criteria.setPowerRequirement(Criteria.POWER_LOW);// ���õ͵��
        // mLocationProvider = mLocationManager.getBestProvider(criteria,
        // true);// ��ȡ������õ�Provider

    }


    // ���򴫸����仯����
    private SensorEventListener mOrientationSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float direction = event.values[mSensorManager.DATA_X] * -1.0f;
            mTargetDirection = normalizeDegree(direction);// ��ֵ��ȫ�ֱ�������ָ������ת
            // Log.i("way", event.values[mSensorManager.DATA_Y] + "");
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    // �������򴫸�����ȡ��ֵ
    private float normalizeDegree(float degree) {
        return (degree + 720) % 360;
    }


}