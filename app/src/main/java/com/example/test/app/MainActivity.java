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
    // 定位相关
    LocationClient mLocClient;
    public MyLocationListenner myListener = new MyLocationListenner();
    private MyLocationConfiguration.LocationMode mCurrentMode;
    BitmapDescriptor mCurrentMarker;
    // UI相关
    boolean isFirstLoc = true;// 是否首次定位
    //指南针
    private static final int EXIT_TIME = 2000;// 两次按返回键的间隔判断
    private long firstExitTime = 0L;// 用来保存第一次按返回键的时间
    public String mData;
    public String address;
    private SensorManager mSensorManager;// 传感器管理对象
    private Sensor mOrientationSensor;// 传感器对象
    private float mTargetDirection;// 目标浮点方向
    TextView mLatitudeTV;// 纬度
    TextView mLongitudeTV;// 经度
    //Fragments
    GuideFragment mGuideFragment=null;             //导航模块
    SurfaceFragment mSurfaceFragment=null;         //相机视图模块
    //搜索相关
    RoutePlanSearch mSearch = null;    // 搜索模块，也可去掉地图模块独立使用
    RouteLine route = null;
    OverlayManager routeOverlay = null;
    boolean useDefaultIcon = false;
    private TextView popupText = null;//泡泡view
    int nodeIndex = -1;//节点索引,供浏览节点时使用




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        EasyAR.initialize(this, key);
        nativeInit();

        //设置无标题
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //设置全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        setDefaultFragment();                                  //设置默认模块
        GLView glView = new GLView(this);
        glView.setRenderer(new Renderer());
        glView.setZOrderMediaOverlay(true);
        ((ViewGroup) findViewById(R.id.id_content)).addView(glView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        nativeRotationChange(getWindowManager().getDefaultDisplay().getRotation() == android.view.Surface.ROTATION_0);


        //获取地图控件引用
        mMapView = (MapView) findViewById(R.id.bmapView);
        RelativeLayout raly=(RelativeLayout)findViewById(R.id.relativeLayout);
        //mMapView=new MapView(this,new BaiduMapOptions().compassEnabled(false));
        //raly.addView(mMapView);
        mMapView.showZoomControls(false);
        mMapView.bringToFront();
        mCurrentMode = MyLocationConfiguration.LocationMode.COMPASS;//地图罗盘模式
        mBaiduMap = mMapView.getMap();
        mBaiduMap.getUiSettings().setCompassEnabled(false);         //去掉指南针
        float zoomLevel=18.0f;
        MapStatusUpdate u=MapStatusUpdateFactory.zoomTo(zoomLevel);
        mBaiduMap.animateMapStatus(u);
        mBaiduMap.setMyLocationConfigeration(new MyLocationConfiguration(mCurrentMode, true, mCurrentMarker));
        // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);

        // 定位初始化
        mLocClient = new LocationClient(this);
        mLocClient.registerLocationListener(myListener);           //注册定位监听
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true);// 打开gps
        option.setCoorType("bd09ll"); // 设置坐标类型
        option.setScanSpan(1000);
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);//高精度模式
        mLocClient.setLocOption(option);
        //地图点击事件处理
        mBaiduMap.setOnMapClickListener(this);
        // 初始化搜索模块，注册事件监听
        mSearch = RoutePlanSearch.newInstance();
        mSearch.setOnGetRoutePlanResultListener(this);

        //Compass
        //setContentView(R.layout.activity_main);
        initResources();// 初始化view
        initServices();// 初始化传感器和位置服务
        mLocClient.start();                                   //开始定位
        ////mCompassView.bringToFront();


    }

    private void setDefaultFragment()                                //设置默认模块
    {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        mSurfaceFragment = new SurfaceFragment();
        transaction.replace(R.id.id_content, mSurfaceFragment);
        transaction.commit();
    }




    /**
     * 定位SDK监听函数
     */
    public class MyLocationListenner implements BDLocationListener {

        @Override
        public void onReceiveLocation(BDLocation location) {
            // map view 销毁后不在处理新接收的位置
            if (location == null || mMapView == null)
                return;
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                            // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(mTargetDirection*-1.0f)      //      *-1.0f反相效果
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
            // sb.append("时间: ");
            // sb.append(location.getTime());
            sb.append("Lat : ");
            sb.append(location.getLatitude() );
            sb.append(", Lon : ");
            sb.append(location.getLongitude());
//			sb.append(", 精度 : ");
//			sb.append(location.getRadius() + " 米");
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
     * 发起路线规划搜索示例
     *
     * @param v
     */
    public void SearchButtonProcess(View v,LatLng mDestination) {
        //重置浏览节点的路线数据
        route = null;
        mBaiduMap.clear();
        // 处理搜索按钮响应
        //EditText editSt = (EditText)findViewById(R.id.start);
        EditText editEn = (EditText)findViewById(R.id.end);
        //设置起终点信息，对于tranist search 来说，城市名无意义
        if(editEn.getText()!=null&&!editEn.getText().toString().equals(""))
        {
            PlanNode stNode = PlanNode.withLocation(new LatLng(mBaiduMap.getLocationData().latitude,mBaiduMap.getLocationData().longitude));//根据定位，判断所在城市

            if(mDestination==null)
            {
                Toast.makeText(this,"没有目的地",Toast.LENGTH_SHORT);return;
            }
            PlanNode enNode = PlanNode.withLocation(mDestination);
            mSurfaceFragment.poiinfos=null;
            // 实际使用中请对起点终点城市进行正确的设定
            if (v.getId() == R.id.drive) {
                mSearch.drivingSearch((new DrivingRoutePlanOption())
                        .from(stNode)
                        .to(enNode));
            } else if (v.getId() == R.id.transit) {
                mSearch.transitSearch((new TransitRoutePlanOption())
                        .from(stNode)
                        .city("北京")
                        .to(enNode));
            } else if (v.getId() == R.id.walk) {
                mSearch.walkingSearch((new WalkingRoutePlanOption())
                        .from(stNode)
                        .to(enNode));
            }
        }
        else
        {
                Toast.makeText(this,"请输入终点",Toast.LENGTH_SHORT);
        }

    }

    /**
     * 节点浏览示例
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
        //设置节点索引
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
        //获取节结果信息
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
        //移动节点至中心
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(nodeLocation));
        // show popup
        popupText = new TextView(this);
        popupText.setBackgroundResource(R.drawable.popup);
        popupText.setTextColor(0xFF000000);
        popupText.setText(nodeTitle);
        mBaiduMap.showInfoWindow(new InfoWindow(popupText, nodeLocation, 0));

    }

    /**
     * 切换路线图标，刷新地图使其生效
     * 注意： 起终点图标使用中心对齐.
     */
    public void changeRouteIcon(View v) {
        if (routeOverlay == null) {
            return;
        }
        if (useDefaultIcon) {
            ((Button) v).setText("自定义起终点图标");
            Toast.makeText(this,
                    "将使用系统起终点图标",
                    Toast.LENGTH_SHORT).show();

        } else {
            ((Button) v).setText("系统起终点图标");
            Toast.makeText(this,
                    "将使用自定义起终点图标",
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
            Toast.makeText(this, "抱歉，未找到结果", Toast.LENGTH_SHORT).show();
        }
        if (result.error == SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR) {
            //起终点或途经点地址有岐义，通过以下接口获取建议查询信息
            result.getSuggestAddrInfo();
            return;
        }
        if (result.error == SearchResult.ERRORNO.NO_ERROR) {
            nodeIndex = -1;
            if(mGuideFragment!=null)                                          //显示按钮
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
            // 添加折线
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
            Toast.makeText(this, "抱歉，未找到结果", Toast.LENGTH_SHORT).show();
        }
        if (result.error == SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR) {
            //起终点或途经点地址有岐义，通过以下接口获取建议查询信息
            result.getSuggestAddrInfo();
            return;
        }
        if (result.error == SearchResult.ERRORNO.NO_ERROR) {                       //搜索无误后，显示按钮
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
            Toast.makeText(this, "抱歉，未找到结果", Toast.LENGTH_SHORT).show();
        }
        if (result.error == SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR) {
            //起终点或途经点地址有岐义，通过以下接口获取建议查询信息
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

    //定制RouteOverly
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
    protected void onPause() {// 在暂停的生命周期里注销传感器服务和位置更新服务

        // activity 暂停时同时暂停地图控件
        mMapView.onPause();
        super.onPause();
        if (mOrientationSensor != null) {
            mSensorManager.unregisterListener(mOrientationSensorEventListener);
        }
    }

    @Override
    protected void onResume() {// 在恢复的生命周期里判断、启动位置更新服务和传感器服务

        mMapView.onResume();
        super.onResume();
        // activity 恢复时同时恢复地图控件
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
        // 退出时销毁定位
        mLocClient.stop();
        // 关闭定位图层
        mBaiduMap.setMyLocationEnabled(false);
        mSearch.destroy();
        mMapView.onDestroy();
        mMapView = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed()
    //无意中按返回键时要释放内存
    {
        // TODO Auto-generated method stub
        long curTime = System.currentTimeMillis();
        if (curTime - firstExitTime < EXIT_TIME) {// 两次按返回键的时间小于2秒就退出应用
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



    // 初始化view
    private void initResources() {
        mTargetDirection = 0.0f;// 初始化目标方向

    }
    // 初始化传感器和位置服务
    private void initServices() {
        // sensor manager
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mOrientationSensor = mSensorManager.getSensorList(
                Sensor.TYPE_ORIENTATION).get(0);//
        // Log.i("way", mOrientationSensor.getName());
        // location manager
        // mLocationManager = (LocationManager)
        // getSystemService(Context.LOCATION_SERVICE);
        // Criteria criteria = new Criteria();// 条件对象，即指定条件过滤获得LocationProvider
        // criteria.setAccuracy(Criteria.ACCURACY_FINE);// 较高精度
        // criteria.setAltitudeRequired(false);// 是否需要高度信息
        // criteria.setBearingRequired(false);// 是否需要方向信息
        // criteria.setCostAllowed(true);// 是否产生费用
        // criteria.setPowerRequirement(Criteria.POWER_LOW);// 设置低电耗
        // mLocationProvider = mLocationManager.getBestProvider(criteria,
        // true);// 获取条件最好的Provider

    }


    // 方向传感器变化监听
    private SensorEventListener mOrientationSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float direction = event.values[mSensorManager.DATA_X] * -1.0f;
            mTargetDirection = normalizeDegree(direction);// 赋值给全局变量，让指南针旋转
            // Log.i("way", event.values[mSensorManager.DATA_Y] + "");
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    // 调整方向传感器获取的值
    private float normalizeDegree(float degree) {
        return (degree + 720) % 360;
    }


}
